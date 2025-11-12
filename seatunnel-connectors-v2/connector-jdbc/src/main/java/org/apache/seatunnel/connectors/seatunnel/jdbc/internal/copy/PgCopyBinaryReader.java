package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.copy;

import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public final class PgCopyBinaryReader implements PgCopyReader {
    private static final byte[] SIGNATURE =
            new byte[] {'P', 'G', 'C', 'O', 'P', 'Y', '\n', (byte) 0xFF, '\r', '\n', 0};
    private static final LocalDate EPOCH_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDateTime EPOCH_DATETIME = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final int BUFFER_SIZE = 64 * 1024;

    private final InputStream stream;
    private final SeaTunnelRowType rowType;
    private final SeaTunnelDataType<?>[] fieldTypes;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    private final Deque<SeaTunnelRow> queue = new ArrayDeque<>();

    private boolean headerParsed = false;
    private boolean eof = false;

    public PgCopyBinaryReader(InputStream stream, TableSchema schema) {
        this.stream = stream;
        this.rowType = schema.toPhysicalRowDataType();
        this.fieldTypes = rowType.getFieldTypes();
    }

    @Override
    public boolean hasNext() {
        try {
            if (!queue.isEmpty()) return true;
            if (eof) return false;
            fillAndParse();
            return !queue.isEmpty();
        } catch (IOException e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED, "Binary COPY read failed", e);
        }
    }

    @Override
    public SeaTunnelRow next() {
        return queue.poll();
    }

    private void fillAndParse() throws IOException {
        fillBufferBlocking();
        if (!headerParsed) parseHeader();
        parseRows();
    }

    /** 第一次读取 buffer 使用 clear，之后使用 compact，保证 PG COPY 流懒加载生效 */
    private void fillBufferBlocking() throws IOException {
        boolean initial = buffer.position() == 0 && buffer.limit() == buffer.capacity();
        if (initial) {
            buffer.clear();
        } else {
            buffer.compact();
        }

        int pos = buffer.position();
        int len = buffer.capacity() - pos;
        int bytesRead = stream.read(buffer.array(), pos, len);
        if (bytesRead > 0) {
            buffer.position(pos + bytesRead);
        }
        buffer.flip();
    }

    private void parseHeader() {
        if (buffer.remaining() < SIGNATURE.length + 8) return; // 11 bytes + 4 flags + 4 extlen
        int savedPos = buffer.position();
        byte[] sig = new byte[SIGNATURE.length];
        buffer.get(sig);
        if (!Arrays.equals(sig, SIGNATURE)) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                    "Invalid COPY header signature");
        }
        buffer.getInt(); // flags
        int extLen = buffer.getInt();
        if (extLen > 0) {
            if (buffer.remaining() < extLen) {
                buffer.position(savedPos);
                return;
            }
            buffer.position(buffer.position() + extLen);
        }
        headerParsed = true;
    }

    private void parseRows() {
        while (buffer.remaining() >= 2) {
            int start = buffer.position();
            short fields = buffer.getShort();

            if (fields == -1) { // EOF marker
                eof = true;
                return;
            }

            if (fields != rowType.getTotalFields()) {
                throw new JdbcConnectorException(
                        CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                        "Column count mismatch: " + fields);
            }

            Object[] values = new Object[fields];
            if (!parseFields(values, fields)) {
                buffer.position(start);
                return;
            }
            queue.add(new SeaTunnelRow(values));
        }
    }

    private boolean parseFields(Object[] values, int fields) {
        for (int i = 0; i < fields; i++) {
            if (buffer.remaining() < 4) return false;
            int len = buffer.getInt();
            if (len == -1) {
                values[i] = null;
                continue;
            }
            if (buffer.remaining() < len) return false;
            byte[] data = new byte[len];
            buffer.get(data);
            values[i] =
                    PgCopyUtils.parseBinaryField(data, fieldTypes[i], EPOCH_DATE, EPOCH_DATETIME);
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
