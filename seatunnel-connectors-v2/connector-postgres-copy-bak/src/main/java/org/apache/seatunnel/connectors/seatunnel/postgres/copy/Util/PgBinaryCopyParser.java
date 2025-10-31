package org.apache.seatunnel.connectors.seatunnel.postgres.copy.Util;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;

import org.postgresql.copy.CopyOut;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PgBinaryCopyParser {
    private final SeaTunnelDataType<?>[] fieldTypes;

    public PgBinaryCopyParser(SeaTunnelDataType<?>[] fieldTypes) {
        this.fieldTypes = fieldTypes;
    }

    /** 解析整个 COPY BINARY 流，输出多行数据 */
    public List<SeaTunnelRow> parseBinaryStream(CopyOut co) throws IOException, SQLException {
        List<SeaTunnelRow> rows = new ArrayList<>();

        byte[] buf;
        ByteBuffer buffer = null;
        boolean headerParsed = false;

        while ((buf = co.readFromCopy()) != null) {
            if (buffer == null) {
                buffer = ByteBuffer.wrap(buf);
            } else {
                // 拼接数据块（因为一块 buf 可能被切断）
                ByteBuffer newBuf = ByteBuffer.allocate(buffer.remaining() + buf.length);
                newBuf.put(buffer);
                newBuf.put(buf);
                newBuf.flip();
                buffer = newBuf;
            }
            buffer.order(ByteOrder.BIG_ENDIAN);

            // 解析头部（只需要一次）
            if (!headerParsed && buffer.remaining() >= 19) {
                byte[] signature = new byte[11];
                buffer.get(signature);
                String sig = new String(signature, StandardCharsets.US_ASCII);
                if (!"PGCOPY\n\377\r\n\0".equals(sig)) {
                    throw new RuntimeException("Invalid COPY BINARY signature: " + sig);
                }
                buffer.getInt(); // flags
                int headerExtLen = buffer.getInt();
                if (headerExtLen > 0) {
                    buffer.position(buffer.position() + headerExtLen);
                }
                headerParsed = true;
            }

            // 尝试解析多行
            while (buffer.remaining() >= 2) {
                buffer.mark();
                short numFields = buffer.getShort();
                if (numFields == -1) {
                    // 文件尾
                    return rows;
                }

                if (numFields != fieldTypes.length) {
                    throw new RuntimeException(
                            "Column count mismatch: expected "
                                    + fieldTypes.length
                                    + " got "
                                    + numFields);
                }

                Object[] rowData = new Object[numFields];
                boolean enough = true;

                for (int i = 0; i < numFields; i++) {
                    if (buffer.remaining() < 4) {
                        enough = false;
                        break;
                    }
                    int len = buffer.getInt();
                    if (len == -1) {
                        rowData[i] = null;
                        continue;
                    }
                    if (buffer.remaining() < len) {
                        enough = false;
                        break;
                    }
                    byte[] fieldBytes = new byte[len];
                    buffer.get(fieldBytes);
                    rowData[i] = parseBinaryField(fieldBytes, fieldTypes[i]);
                }

                if (!enough) {
                    // 数据不完整，回退到行开头，等待下一块 buf
                    buffer.reset();
                    break;
                }

                rows.add(new SeaTunnelRow(rowData));
            }

            // 把未读完的部分保留，等待下次拼接
            if (buffer.hasRemaining()) {
                byte[] remain = new byte[buffer.remaining()];
                buffer.get(remain);
                buffer = ByteBuffer.wrap(remain);
                buffer.order(ByteOrder.BIG_ENDIAN);
            } else {
                buffer = null;
            }
        }

        return rows;
    }

    /** 字段解析 */
    public Object parseBinaryField(byte[] fieldData, SeaTunnelDataType<?> fieldType) {
        ByteBuffer buf = ByteBuffer.wrap(fieldData).order(ByteOrder.BIG_ENDIAN);
        if (fieldType.equals(BasicType.INT_TYPE)) {
            return buf.getInt();
        } else if (fieldType.equals(BasicType.LONG_TYPE)) {
            return buf.getLong();
        } else if (fieldType.equals(BasicType.FLOAT_TYPE)) {
            return buf.getFloat();
        } else if (fieldType.equals(BasicType.DOUBLE_TYPE)) {
            return buf.getDouble();
        } else if (fieldType.equals(BasicType.BOOLEAN_TYPE)) {
            return fieldData[0] != 0;
        } else if (fieldType.equals(BasicType.STRING_TYPE)) {
            return new String(fieldData, StandardCharsets.UTF_8);
        } else if (fieldType.equals(PrimitiveByteArrayType.INSTANCE)) {
            return fieldData;
        } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TYPE)) {
            return parseBinaryDate(buf.getInt());
        } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TIME_TYPE)) {
            return parseBinaryTimestamp(buf.getLong());
        } else {
            return new String(fieldData, StandardCharsets.UTF_8);
        }
    }

    /** PG Date: int32 = days since 2000-01-01 */
    private LocalDate parseBinaryDate(int pgDays) {
        LocalDate base = LocalDate.of(2000, 1, 1);
        return base.plusDays(pgDays);
    }

    /** PG Timestamp: int64 = microseconds since 2000-01-01 */
    private LocalDateTime parseBinaryTimestamp(long pgMicros) {
        LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
        return base.plusNanos(pgMicros * 1000);
    }
}
