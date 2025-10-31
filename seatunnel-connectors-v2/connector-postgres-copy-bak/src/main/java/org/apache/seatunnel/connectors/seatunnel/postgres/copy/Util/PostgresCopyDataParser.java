package org.apache.seatunnel.connectors.seatunnel.postgres.copy.Util;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PostgresCopyDataParser {

    private final SeaTunnelRowType rowType;
    private final String delimiter;
    private final String nullAs;

    // 添加字段类型缓存
    private final SeaTunnelDataType<?>[] fieldTypes;
    // 添加布尔值缓存
    private static final Map<String, Boolean> BOOLEAN_CACHE = new ConcurrentHashMap<>();

    static {
        // 预缓存常见布尔值
        BOOLEAN_CACHE.put("true", true);
        BOOLEAN_CACHE.put("false", false);
        BOOLEAN_CACHE.put("1", true);
        BOOLEAN_CACHE.put("0", false);
        BOOLEAN_CACHE.put("t", true);
        BOOLEAN_CACHE.put("f", false);
        BOOLEAN_CACHE.put("yes", true);
        BOOLEAN_CACHE.put("no", false);
    }

    // 添加字段分割正则模式
    private final Pattern delimiterPattern;

    public PostgresCopyDataParser(SeaTunnelRowType rowType, String delimiter, String nullAs) {
        this.rowType = rowType;
        this.delimiter = delimiter;
        this.nullAs = nullAs;
        // 初始化字段类型缓存
        this.fieldTypes = rowType.getFieldTypes();
        // 编译分隔符正则
        this.delimiterPattern = Pattern.compile(delimiter, Pattern.LITERAL);
    }

    public SeaTunnelRow createRowFromData(Object data, boolean isBinary) {
        if (rowType.getFieldNames().length == 1) {
            // 单字段行结构：直接包装数据
            return new SeaTunnelRow(new Object[] {data});
        } else {
            // 多字段行结构：需要解析数据
            if (isBinary) {
                return parseBinaryRow((byte[]) data);
            } else {
                return parseCsvRow((String) data);
            }
        }
    }

    public SeaTunnelRow parseCsvRow(String csvLine) {
        // 使用预编译正则分割字段
        String[] fields = delimiterPattern.split(csvLine, -1);
        Object[] rowData = new Object[fields.length];

        // 批量处理字段
        for (int i = 0; i < fields.length && i < fieldTypes.length; i++) {
            rowData[i] = convertCsvField(fields[i], fieldTypes[i]);
        }

        // 处理字段数不匹配情况
        if (rowData.length < fieldTypes.length) {
            Object[] extendedData = new Object[fieldTypes.length];
            System.arraycopy(rowData, 0, extendedData, 0, rowData.length);
            return new SeaTunnelRow(extendedData);
        }

        return new SeaTunnelRow(rowData);
    }

    private Object convertCsvField(String fieldValue, SeaTunnelDataType<?> fieldType) {
        if (fieldValue == null || fieldValue.equals(nullAs) || fieldValue.isEmpty()) {
            return null;
        }

        try {
            // 使用 switch 优化类型判断
            if (fieldType.equals(BasicType.STRING_TYPE)) {
                return fieldValue;
            } else if (fieldType.equals(BasicType.INT_TYPE)) {
                return Integer.parseInt(fieldValue);
            } else if (fieldType.equals(BasicType.LONG_TYPE)) {
                return Long.parseLong(fieldValue);
            } else if (fieldType.equals(BasicType.FLOAT_TYPE)) {
                return Float.parseFloat(fieldValue);
            } else if (fieldType.equals(BasicType.DOUBLE_TYPE)) {
                return Double.parseDouble(fieldValue);
            } else if (fieldType.equals(BasicType.BOOLEAN_TYPE)) {
                // 使用布尔值缓存
                return parseBoolean(fieldValue.toLowerCase());
            } else if (fieldType.equals(PrimitiveByteArrayType.INSTANCE)) {
                return fieldValue.getBytes(StandardCharsets.UTF_8);
            } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TYPE)) {
                return LocalDate.parse(fieldValue);
            } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TIME_TYPE)) {
                return LocalDateTime.parse(fieldValue);
            }
            return fieldValue;
        } catch (Exception e) {
            return fieldValue;
        }
    }

    private Boolean parseBoolean(String value) {
        // 使用缓存的布尔值
        return BOOLEAN_CACHE.getOrDefault(value.toLowerCase(), null);
    }

    // 优化二进制解析
    public SeaTunnelRow parseBinaryRow(byte[] binaryData) {
        if (binaryData == null || binaryData.length < 19) {
            throw new RuntimeException("Invalid binary data: too short");
        }

        try {
            // 使用 ByteBuffer 替代 DataInputStream
            ByteBuffer buffer = ByteBuffer.wrap(binaryData);

            // 跳过签名和头部信息
            buffer.position(11); // 跳过签名
            buffer.getInt(); // 跳过标志
            buffer.getInt(); // 跳过扩展头

            short numFields = buffer.getShort();
            Object[] rowData = new Object[numFields];

            // 批量读取字段
            for (int i = 0; i < numFields && i < fieldTypes.length; i++) {
                int fieldLength = buffer.getInt();
                if (fieldLength == -1) {
                    rowData[i] = null;
                    continue;
                }

                byte[] fieldData = new byte[fieldLength];
                buffer.get(fieldData);
                rowData[i] = parseBinaryField(fieldData, fieldTypes[i]);
            }

            return new SeaTunnelRow(rowData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse binary row: " + e.getMessage(), e);
        }
    }

    // 优化字节转换
    private Object parseBinaryField(byte[] fieldData, SeaTunnelDataType<?> fieldType) {
        try {
            if (fieldType.equals(BasicType.INT_TYPE)) {
                return ByteBuffer.wrap(fieldData).getInt();
            } else if (fieldType.equals(BasicType.LONG_TYPE)) {
                return ByteBuffer.wrap(fieldData).getLong();
            } else if (fieldType.equals(BasicType.FLOAT_TYPE)) {
                return ByteBuffer.wrap(fieldData).getFloat();
            } else if (fieldType.equals(BasicType.DOUBLE_TYPE)) {
                return ByteBuffer.wrap(fieldData).getDouble();
            } else if (fieldType.equals(BasicType.BOOLEAN_TYPE)) {
                return fieldData[0] != 0;
            } else if (fieldType.equals(BasicType.STRING_TYPE)) {
                return new String(fieldData, StandardCharsets.UTF_8);
            } else if (fieldType.equals(PrimitiveByteArrayType.INSTANCE)) {
                return fieldData;
            } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TYPE)) {
                return parseBinaryDate(fieldData);
            } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TIME_TYPE)) {
                return parseBinaryTimestamp(fieldData);
            }
            return new String(fieldData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse binary field: " + e.getMessage(), e);
        }
    }
    //    public SeaTunnelRow parseCsvRow(String csvLine) {
    //        String[] fields = csvLine.split(delimiter, -1); // 保留空字段
    //        Object[] rowData = new Object[fields.length];
    //
    //        for (int i = 0; i < fields.length; i++) {
    //            if (i < rowType.getFieldTypes().length) {
    //                rowData[i] = convertCsvField(fields[i], rowType.getFieldType(i));
    //            } else {
    //                // 如果字段数多于定义的类型，多余的字段作为字符串处理
    //                rowData[i] = convertCsvField(fields[i], BasicType.STRING_TYPE);
    //            }
    //        }
    //
    //        // 如果字段数少于定义的类型，剩余字段设为null
    //        if (rowData.length < rowType.getFieldTypes().length) {
    //            Object[] extendedData = new Object[rowType.getFieldTypes().length];
    //            System.arraycopy(rowData, 0, extendedData, 0, rowData.length);
    //            for (int i = rowData.length; i < extendedData.length; i++) {
    //                extendedData[i] = null;
    //            }
    //            return new SeaTunnelRow(extendedData);
    //        }
    //
    //        return new SeaTunnelRow(rowData);
    //    }

    //    private Object convertCsvField(String fieldValue, SeaTunnelDataType<?> fieldType) {
    //        if (fieldValue == null || fieldValue.equals(nullAs) || fieldValue.isEmpty()) {
    //            return null;
    //        }
    //
    //        try {
    //            if (fieldType.equals(BasicType.STRING_TYPE)) {
    //                return fieldValue;
    //            } else if (fieldType.equals(BasicType.INT_TYPE)) {
    //                return Integer.parseInt(fieldValue);
    //            } else if (fieldType.equals(BasicType.LONG_TYPE)) {
    //                return Long.parseLong(fieldValue);
    //            } else if (fieldType.equals(BasicType.FLOAT_TYPE)) {
    //                return Float.parseFloat(fieldValue);
    //            } else if (fieldType.equals(BasicType.DOUBLE_TYPE)) {
    //                return Double.parseDouble(fieldValue);
    //            } else if (fieldType.equals(BasicType.BOOLEAN_TYPE)) {
    //                return parseBoolean(fieldValue);
    //            } else if (fieldType.equals(PrimitiveByteArrayType.INSTANCE)) {
    //                return fieldValue.getBytes(StandardCharsets.UTF_8);
    //            } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TYPE)) {
    //                return LocalDate.parse(fieldValue);
    //            } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TIME_TYPE)) {
    //                return LocalDateTime.parse(fieldValue);
    //            } else {
    //                return fieldValue; // 默认转为字符串
    //            }
    //        } catch (Exception e) {
    //            // 解析失败时返回原始字符串
    //            return fieldValue;
    //        }
    //    }

    //    private Boolean parseBoolean(String value) {
    //        if (value.equalsIgnoreCase("true")
    //                || value.equals("1")
    //                || value.equalsIgnoreCase("t")
    //                || value.equalsIgnoreCase("yes")) {
    //            return true;
    //        } else if (value.equalsIgnoreCase("false")
    //                || value.equals("0")
    //                || value.equalsIgnoreCase("f")
    //                || value.equalsIgnoreCase("no")) {
    //            return false;
    //        }
    //        throw new IllegalArgumentException("Invalid boolean value: " + value);
    //    }

    //    public SeaTunnelRow parseBinaryRow(byte[] binaryData) {
    //        if (binaryData == null || binaryData.length < 19) { // 最小长度检查：11(签名) + 4(标志) + 4(扩展头)
    //            throw new RuntimeException("Invalid binary data: too short");
    //        }
    //
    //        try (ByteArrayInputStream bais = new ByteArrayInputStream(binaryData);
    //             DataInputStream dis = new DataInputStream(bais)) {
    //
    //            // 验证签名
    //            byte[] signature = new byte[11];
    //            dis.readFully(signature);
    //            // 可以添加签名验证逻辑
    //
    //            // 读取头部信息
    //            int flags = dis.readInt();
    //            int headerExtension = dis.readInt();
    //
    //            // 读取并验证字段数量
    //            short numFields = dis.readShort();
    //            if (numFields <= 0 || numFields > rowType.getFieldTypes().length) {
    //                throw new RuntimeException("Invalid number of fields: " + numFields);
    //            }
    //
    //            Object[] rowData = new Object[numFields];
    //
    //            // 读取字段数据
    //            for (int i = 0; i < numFields; i++) {
    //                int fieldLength = dis.readInt();
    //                if (fieldLength == -1) {
    //                    rowData[i] = null;
    //                    continue;
    //                }
    //
    //                // 验证字段长度
    //                if (fieldLength < 0 || fieldLength > binaryData.length - dis.available()) {
    //                    throw new RuntimeException("Invalid field length: " + fieldLength);
    //                }
    //
    //                byte[] fieldData = new byte[fieldLength];
    //                dis.readFully(fieldData);
    //
    //                try {
    //                    if (i < rowType.getFieldTypes().length) {
    //                        rowData[i] = parseBinaryField(fieldData, rowType.getFieldType(i));
    //                    } else {
    //                        rowData[i] = fieldData;
    //                    }
    //                } catch (Exception e) {
    //                    throw new RuntimeException("Failed to parse field " + i + ": " +
    // e.getMessage(), e);
    //                }
    //            }
    //
    //            return new SeaTunnelRow(rowData);
    //
    //        } catch (IOException e) {
    //            throw new RuntimeException("Failed to parse PostgreSQL binary row: " +
    // e.getMessage(), e);
    //        }
    //    }

    //    private Object parseBinaryField(byte[] fieldData, SeaTunnelDataType<?> fieldType) {
    //        try {
    //            if (fieldType.equals(BasicType.INT_TYPE)) {
    //                return bytesToInt(fieldData);
    //            } else if (fieldType.equals(BasicType.LONG_TYPE)) {
    //                return bytesToLong(fieldData);
    //            } else if (fieldType.equals(BasicType.FLOAT_TYPE)) {
    //                return bytesToFloat(fieldData);
    //            } else if (fieldType.equals(BasicType.DOUBLE_TYPE)) {
    //                return bytesToDouble(fieldData);
    //            } else if (fieldType.equals(BasicType.BOOLEAN_TYPE)) {
    //                return fieldData[0] != 0;
    //            } else if (fieldType.equals(BasicType.STRING_TYPE)) {
    //                return new String(fieldData, StandardCharsets.UTF_8);
    //            } else if (fieldType.equals(PrimitiveByteArrayType.INSTANCE)) {
    //                return fieldData;
    //            } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TYPE)) {
    //                return parseBinaryDate(fieldData);
    //            } else if (fieldType.equals(LocalTimeType.LOCAL_DATE_TIME_TYPE)) {
    //                return parseBinaryTimestamp(fieldData);
    //            } else {
    //                return new String(fieldData, StandardCharsets.UTF_8);
    //            }
    //        } catch (Exception e) {
    //            throw new RuntimeException("Failed to parse binary field: " + e.getMessage(), e);
    //        }
    //    }

    // 字节转换辅助方法
    private int bytesToInt(byte[] bytes) {
        if (bytes.length < 4) {
            throw new IllegalArgumentException("Not enough bytes for integer");
        }
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private long bytesToLong(byte[] bytes) {
        if (bytes.length < 8) {
            throw new IllegalArgumentException("Not enough bytes for long");
        }
        return ((long) (bytes[0] & 0xFF) << 56)
                | ((long) (bytes[1] & 0xFF) << 48)
                | ((long) (bytes[2] & 0xFF) << 40)
                | ((long) (bytes[3] & 0xFF) << 32)
                | ((long) (bytes[4] & 0xFF) << 24)
                | ((long) (bytes[5] & 0xFF) << 16)
                | ((long) (bytes[6] & 0xFF) << 8)
                | ((long) (bytes[7] & 0xFF));
    }

    private float bytesToFloat(byte[] bytes) {
        int intBits = bytesToInt(bytes);
        return Float.intBitsToFloat(intBits);
    }

    private double bytesToDouble(byte[] bytes) {
        long longBits = bytesToLong(bytes);
        return Double.longBitsToDouble(longBits);
    }

    private LocalDate parseBinaryDate(byte[] bytes) {
        int days = bytesToInt(bytes);
        return LocalDate.of(2000, 1, 1).plusDays(days);
    }

    private LocalDateTime parseBinaryTimestamp(byte[] bytes) {
        long microseconds = bytesToLong(bytes);
        // PostgreSQL 时间戳是从 2000-01-01 开始的微秒偏移
        long seconds = microseconds / 1000000;
        long nanos = (microseconds % 1000000) * 1000;

        return LocalDateTime.of(2000, 1, 1, 0, 0, 0).plusSeconds(seconds).plusNanos(nanos);
    }

    // 快速创建单字段行
    public static SeaTunnelRow createSimpleRow(Object data) {
        return new SeaTunnelRow(new Object[] {data});
    }

    // 批量处理方法
    public SeaTunnelRow[] parseBatch(byte[][] binaryData, boolean isBinary) {
        SeaTunnelRow[] rows = new SeaTunnelRow[binaryData.length];
        for (int i = 0; i < binaryData.length; i++) {
            rows[i] = createRowFromData(binaryData[i], isBinary);
        }
        return rows;
    }

    public SeaTunnelRow[] parseBatch(String[] csvLines, boolean isBinary) {
        SeaTunnelRow[] rows = new SeaTunnelRow[csvLines.length];
        for (int i = 0; i < csvLines.length; i++) {
            rows[i] = createRowFromData(csvLines[i], isBinary);
        }
        return rows;
    }
}
