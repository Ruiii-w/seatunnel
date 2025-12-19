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
import java.util.Deque;

public final class PgCopyBinaryReader implements PgCopyReader {
    private static final byte[] SIGNATURE = {
        'P', 'G', 'C', 'O', 'P', 'Y', '\n', (byte) 0xFF, '\r', '\n', 0
    };

    private static final LocalDate EPOCH_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDateTime EPOCH_DATETIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
    //    private static final int MAX_BUFFER_SIZE =
    //            BUFFER_SIZE * 1024; // upper bound to prevent unbounded growth
    //    // main read buffer (big-endian as per PG COPY binary format)
    //    private ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);

    private static int BUFFER_SIZE;
    private static int MAX_BUFFER_SIZE; // upper bound to prevent unbounded growth
    private ByteBuffer buffer;

    private final InputStream stream;
    private final SeaTunnelRowType rowType;
    private final SeaTunnelDataType<?>[] fieldTypes;

    // parsed rows waiting to be consumed by upper layer
    private final Deque<SeaTunnelRow> queue = new ArrayDeque<>();

    // state for an in-progress row when data spans multiple fills
    private int pendingFields = -1; // -1 means no active row
    private Object[] pendingValues; // holds field values for the active row
    private int pendingIndex = 0; // next field index to parse
    private int pendingFieldLen = -1; // current field length; -1 means length not read yet

    private boolean headerParsed = false;
    private boolean eof = false;

    //    public PgCopyBinaryReader(InputStream stream, TableSchema schema) {
    //        this.stream = stream;
    //        this.rowType = schema.toPhysicalRowDataType();
    //        this.fieldTypes = rowType.getFieldTypes();
    //    }

    public PgCopyBinaryReader(InputStream stream, TableSchema schema, Integer pgCopyBufferSize) {
        this.stream = stream;
        this.rowType = schema.toPhysicalRowDataType();
        this.fieldTypes = rowType.getFieldTypes();
        BUFFER_SIZE =
                pgCopyBufferSize == null
                        ? DEFAULT_BUFFER_SIZE
                        : 1
                                << (32
                                        - Integer.numberOfLeadingZeros(
                                                pgCopyBufferSize
                                                        - 1)); // 大于等于 pgCopyBufferSize 的最小的 2 的幂
        MAX_BUFFER_SIZE = BUFFER_SIZE * 1024;
        this.buffer = ByteBuffer.allocate(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }

    @Override
    public boolean hasNext() {
        if (!queue.isEmpty()) {
            return true;
        }
        return !eof;
    }

    @Override
    public SeaTunnelRow next() {
        try {
            if (queue.isEmpty() && !eof) {
                fillAndParse();
                while (queue.isEmpty() && !eof) {
                    fillAndParse();
                }
            }
            return queue.poll();
        } catch (IOException e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED, "Binary COPY read failed", e);
        }
    }

    private void fillAndParse() throws IOException {
        fillBufferBlocking();
        if (!headerParsed) parseHeader();

        if (headerParsed) parseRows();
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
        } else if (bytesRead == -1) {
            eof = true;
        } else {
            // buffer 满，不用处理
        }
        buffer.flip();
    }

    // ensure the buffer has capacity for the upcoming contiguous read (single field payload)
    private void ensureCapacityFor(int required) {
        if (required <= buffer.capacity()) return;
        if (required > MAX_BUFFER_SIZE) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                    "COPY buffer expansion exceeds max limit: required="
                            + required
                            + ", max="
                            + MAX_BUFFER_SIZE);
        }
        int unread = buffer.remaining();
        int newCap = buffer.capacity();
        while (newCap < required && newCap < MAX_BUFFER_SIZE) newCap = newCap << 1;
        if (newCap < required) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                    "Unable to expand buffer to required size: required="
                            + required
                            + ", max="
                            + MAX_BUFFER_SIZE);
        }
        ByteBuffer newBuf = ByteBuffer.allocate(newCap).order(ByteOrder.BIG_ENDIAN);
        // copy unread bytes to the start of the new buffer to preserve parser state
        newBuf.put(buffer.array(), buffer.position(), unread);
        newBuf.flip();
        buffer = newBuf;
    }

    private void parseHeader() {
        if (buffer.remaining() < SIGNATURE.length + 8) {
            // 不足这些字节，说明头部数据还未完整到达，不能开始解析
            // 返回上层循环加载 buffer
            return; // 11 bytes + 4 flags + 4 extlen
        }

        int savedPos = buffer.position();

        for (byte b : SIGNATURE) {
            if (buffer.get() != b) {
                throw new JdbcConnectorException(
                        CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                        "Invalid COPY header signature");
            }
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

    // parse as many rows as possible from the buffer; keep state across fills
    private void parseRows() {
        while (true) {
            // start a new row when there is no active one
            if (pendingFields < 0) {
                if (buffer.remaining() < 2) return; // need row header (short fields)
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
                pendingFields = fields;
                pendingValues = new Object[fields];
                pendingIndex = 0;
                pendingFieldLen = -1;
            }

            // parse fields of the active row; may pause if data is incomplete
            while (pendingIndex < pendingFields) {
                // read the length prefix for the current field
                if (pendingFieldLen < 0) {
                    if (buffer.remaining() < 4) return; // need 4 bytes length
                    pendingFieldLen = buffer.getInt();
                }
                // -1 denotes NULL field
                if (pendingFieldLen == -1) {
                    pendingValues[pendingIndex++] = null;
                    pendingFieldLen = -1;
                    continue;
                }
                // expand buffer if the upcoming field payload exceeds capacity
                ensureCapacityFor(pendingFieldLen);
                // if payload not fully in buffer yet, wait for next fill
                if (buffer.remaining() < pendingFieldLen) return;

                int startPos = buffer.position();

                // 创建原buffer的副本（共享底层数据，但独立维护position/limit）
                ByteBuffer fieldBuf = buffer.duplicate().order(ByteOrder.BIG_ENDIAN);
                fieldBuf.limit(startPos + pendingFieldLen);
                fieldBuf.position(startPos);

                pendingValues[pendingIndex] =
                        PgCopyUtils.parseBinaryField(
                                fieldBuf, fieldTypes[pendingIndex], EPOCH_DATE, EPOCH_DATETIME);
                buffer.position(startPos + pendingFieldLen);
                pendingIndex++;
                pendingFieldLen = -1;
            }

            // row complete; enqueue and reset state for next row
            queue.add(new SeaTunnelRow(pendingValues));
            pendingFields = -1;
            pendingValues = null;
            pendingIndex = 0;
            pendingFieldLen = -1;
            if (buffer.remaining() < 2) return; // need at least next row header
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
            int startPos = buffer.position();

            ByteBuffer fieldBuf = buffer.duplicate().order(ByteOrder.BIG_ENDIAN);
            fieldBuf.limit(startPos + len);
            fieldBuf.position(startPos);
            values[i] =
                    PgCopyUtils.parseBinaryField(
                            fieldBuf, fieldTypes[i], EPOCH_DATE, EPOCH_DATETIME);
            buffer.position(startPos + len);
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        IOException closeException = null;

        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            closeException = e;
        } finally {
            // 清理资源
            buffer = null;
            queue.clear();
            pendingValues = null;
            pendingFields = -1;
            pendingIndex = 0;
            pendingFieldLen = -1;
        }

        if (closeException != null) {
            throw new IOException("Failed to close PgCopyBinaryReader", closeException);
        }
    }
}
