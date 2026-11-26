package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.copy;

import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import java.io.Closeable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Common utility functions for COPY readers. */
public final class PgCopyUtils {

    private PgCopyUtils() {}

    public static void closeQuietly(Object obj) {
        if (obj == null) {
            return;
        }
        try {
            if (obj instanceof Closeable) {
                ((Closeable) obj).close();
            } else if (obj instanceof AutoCloseable) {
                ((AutoCloseable) obj).close();
            }
        } catch (Exception ignored) {
        }
    }

    public static Object parseBinaryField(
            byte[] bytes,
            SeaTunnelDataType<?> type,
            LocalDate epochDate,
            LocalDateTime epochDateTime) {

        if (bytes == null) {
            return null;
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

        try {
            switch (type.getSqlType()) {
                case STRING:
                    return new String(bytes);
                case BOOLEAN:
                    return buf.get() != 0;
                case TINYINT:
                    return buf.get();
                case SMALLINT:
                    return buf.getShort();
                case INT:
                    return buf.getInt();
                case BIGINT:
                    return buf.getLong();
                case FLOAT:
                    return buf.getFloat();
                case DOUBLE:
                    return buf.getDouble();
                case DECIMAL:
                    return PgNumericDecoder.decode(buf);
                case DATE:
                    return epochDate.plusDays(buf.getInt());
                case TIME:
                    return java.time.LocalTime.ofNanoOfDay(buf.getLong() * 1000L);
                case TIMESTAMP:
                    return epochDateTime.plusNanos(buf.getLong() * 1000L);
                case BYTES:
                    return bytes;
                default:
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.UNSUPPORTED_DATA_TYPE,
                            "Unsupported binary type: " + type);
            }
        } catch (Exception e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.UNSUPPORTED_DATA_TYPE,
                    "Failed to parse binary field for type: " + type,
                    e);
        }
    }

    public static Object parseBinaryField(
            ByteBuffer buf,
            SeaTunnelDataType<?> type,
            LocalDate epochDate,
            LocalDateTime epochDateTime) {
        try {
            switch (type.getSqlType()) {
                case STRING:
                    if (buf.hasArray()) {
                        return new String(buf.array(), buf.position(), buf.remaining());
                    } else {
                        return java.nio.charset.StandardCharsets.UTF_8
                                .decode(buf.slice())
                                .toString();
                    }
                case BOOLEAN:
                    return buf.get() != 0;
                case TINYINT:
                    return buf.get();
                case SMALLINT:
                    return buf.getShort();
                case INT:
                    return buf.getInt();
                case BIGINT:
                    return buf.getLong();
                case FLOAT:
                    return buf.getFloat();
                case DOUBLE:
                    return buf.getDouble();
                case DECIMAL:
                    return PgNumericDecoder.decode(buf.slice());
                case DATE:
                    return epochDate.plusDays(buf.getInt());
                case TIME:
                    return java.time.LocalTime.ofNanoOfDay(buf.getLong() * 1000L);
                case TIMESTAMP:
                    return epochDateTime.plusNanos(buf.getLong() * 1000L);
                case BYTES:
                    {
                        byte[] out = new byte[buf.remaining()];
                        buf.get(out);
                        return out;
                    }
                default:
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.UNSUPPORTED_DATA_TYPE,
                            "Unsupported binary type: " + type);
            }
        } catch (Exception e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.UNSUPPORTED_DATA_TYPE,
                    "Failed to parse binary field for type: " + type,
                    e);
        }
    }

    public static Object parseValue(String raw, SeaTunnelDataType<?> type) {
        if (raw == null || "\\N".equals(raw)) {
            return null;
        }

        try {
            switch (type.getSqlType()) {
                case STRING:
                    return raw;
                case BOOLEAN:
                    if ("t".equalsIgnoreCase(raw)
                            || "true".equalsIgnoreCase(raw)
                            || "y".equalsIgnoreCase(raw)
                            || "yes".equalsIgnoreCase(raw)
                            || "1".equals(raw)) {
                        return true;
                    }
                    if ("f".equalsIgnoreCase(raw)
                            || "false".equalsIgnoreCase(raw)
                            || "n".equalsIgnoreCase(raw)
                            || "no".equalsIgnoreCase(raw)
                            || "0".equals(raw)) {
                        return false;
                    }
                    return Boolean.valueOf(raw);
                case TINYINT:
                    return Byte.valueOf(raw);
                case SMALLINT:
                    return Short.valueOf(raw);
                case INT:
                    return Integer.valueOf(raw);
                case BIGINT:
                    return Long.valueOf(raw);
                case FLOAT:
                    return Float.valueOf(raw);
                case DOUBLE:
                    return Double.valueOf(raw);
                case DECIMAL:
                    return new BigDecimal(raw);
                case DATE:
                    return Date.valueOf(raw).toLocalDate();
                case TIME:
                    return Time.valueOf(raw).toLocalTime();
                case TIMESTAMP:
                    return parseTimestamp(raw);
                case BYTES:
                    if (raw.length() >= 3
                            && raw.charAt(0) == '\\'
                            && raw.charAt(1) == '\\'
                            && (raw.charAt(2) == 'x' || raw.charAt(2) == 'X')) {
                        return Hex.decodeHex(raw.substring(3));
                    }
                    if (raw.length() >= 2
                            && raw.charAt(0) == '\\'
                            && (raw.charAt(1) == 'x' || raw.charAt(1) == 'X')) {
                        return Hex.decodeHex(raw.substring(2));
                    }
                    return Base64.decodeBase64(raw);
                default:
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.UNSUPPORTED_DATA_TYPE,
                            "Unsupported CSV type: " + type);
            }
        } catch (Exception e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.UNSUPPORTED_DATA_TYPE,
                    "Failed to parse CSV field for type: " + type,
                    e);
        }
    }

    /**
     * PostgreSQL COPY TO CSV renders timestamp with time zone as e.g. {@code 2023-05-07
     * 06:30:00+00}, which {@link Timestamp#valueOf(String)} cannot parse because of the trailing
     * offset. Fall back to ISO offset parsing in that case.
     */
    private static LocalDateTime parseTimestamp(String raw) {
        try {
            return Timestamp.valueOf(raw).toLocalDateTime();
        } catch (IllegalArgumentException e) {
            String normalized = normalizeOffset(raw.trim().replace(' ', 'T'));
            try {
                return OffsetDateTime.parse(normalized).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.parse(normalized);
                } catch (DateTimeParseException ignoredAgain) {
                    throw e;
                }
            }
        }
    }

    /**
     * Java 8's ISO offset parser only accepts offsets with a colon (e.g. {@code +08:00}).
     * PostgreSQL renders {@code +08} / {@code +0000}, so add the missing colon before parsing.
     */
    private static String normalizeOffset(String value) {
        int len = value.length();
        if (len >= 5 && isOffsetSign(value.charAt(len - 5)) && isDigit(value, len - 4, len)) {
            return value.substring(0, len - 2) + ":" + value.substring(len - 2);
        }
        if (len >= 3 && isOffsetSign(value.charAt(len - 3)) && isDigit(value, len - 2, len)) {
            return value + ":00";
        }
        return value;
    }

    private static boolean isOffsetSign(char c) {
        return c == '+' || c == '-';
    }

    private static boolean isDigit(String value, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
