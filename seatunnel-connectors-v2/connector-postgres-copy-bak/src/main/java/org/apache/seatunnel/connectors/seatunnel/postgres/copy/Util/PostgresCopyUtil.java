package org.apache.seatunnel.connectors.seatunnel.postgres.copy.Util;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.config.PostgresCopyOptions;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
public class PostgresCopyUtil {

    @Getter
    public enum SplitMode {
        RANGE("range"),
        HASH("hash"),
        NONE("none");

        /** -- GETTER -- 枚举 -> 字符串 */
        private final String value;

        SplitMode(String value) {
            this.value = value;
        }

        /** 字符串 -> 枚举（不区分大小写，找不到返回 null） */
        public static SplitMode fromValue(String value) {
            if (value == null) {
                return null;
            }
            for (SplitMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
            return null;
        }

        /** 把所有枚举实例转换成字符串数组 */
        public static String[] toStringArray() {
            return java.util.Arrays.stream(values())
                    .map(SplitMode::getValue)
                    .toArray(String[]::new);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private Connection getConnection(ReadonlyConfig config) throws SQLException {
        String jdbcUrl = config.get(PostgresCopyOptions.JDBC_URL);
        String username = config.get(PostgresCopyOptions.USERNAME);
        String password = config.get(PostgresCopyOptions.PASSWORD);

        try {
            log.info("Connecting to PostgreSQL database: {}", jdbcUrl);
            return DriverManager.getConnection(jdbcUrl, username, password);
        } catch (SQLException e) {
            log.error("Failed to connect to database: {}", jdbcUrl, e);
            throw new RuntimeException("Could not connect to PostgreSQL database", e);
        }

        // return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
                log.debug("Database connection closed successfully");
            } catch (SQLException e) {
                log.warn("Error closing database connection", e);
            }
        }
    }

    //    private List<ColumnInfo> getTableSchema(Connection conn, ReadonlyConfig config) throws
    // SQLException {
    //        String tableName = config.get(PostgresCopyOptions.TABLE);
    //        String schemaName = config.getOptional(PostgresCopyOptions.SCHEMA).orElse("public");
    //
    //        log.info("Fetching schema for table {}.{}", schemaName, tableName);
    //
    //        String sql = "SELECT column_name, data_type, numeric_precision, numeric_scale " +
    //                "FROM information_schema.columns " +
    //                "WHERE table_schema = ? AND table_name = ? " +
    //                "ORDER BY ordinal_position";
    //
    //
    //        List<ColumnInfo> columns = new ArrayList<>();
    //        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    //            stmt.setString(1, schemaName);
    //            stmt.setString(2, tableName);
    //
    //            try (ResultSet rs = stmt.executeQuery()) {
    //                if (!rs.next()) {
    //                    String error = String.format("Table %s.%s not found", schemaName,
    // tableName);
    //                    log.error(error);
    //                    throw new RuntimeException(error);
    //                }
    //
    //                do {
    //                    String columnName = rs.getString("column_name");
    //                    String dataType = rs.getString("data_type");
    //                    Integer precision = (Integer) rs.getObject("numeric_precision");
    //                    Integer scale = (Integer) rs.getObject("numeric_scale");
    //
    //                    log.debug("Found column: {} of type {} (precision: {}, scale: {})",
    //                            columnName, dataType, precision, scale);
    //
    //                    columns.add(new ColumnInfo(columnName, dataType, precision, scale));
    //                } while (rs.next());
    //            }
    //        } catch (SQLException e) {
    //            log.error("Error fetching table schema: {}.{}", schemaName, tableName, e);
    //            throw e;
    //        }
    //
    //        log.info("Successfully retrieved {} columns from {}.{}", columns.size(), schemaName,
    // tableName);
    //        return columns;
    //    }

    //    public static void copyToStream(ReadonlyConfig config, OutputStream out) throws Exception
    // {
    //        String url = config.get(PostgresCopyOptions.JDBC_URL);
    //        String user = config.get(PostgresCopyOptions.USERNAME);
    //        String pass = config.get(PostgresCopyOptions.PASSWORD);
    //
    //        // 构造 COPY SQL
    //        String copySql = buildCopySql(config);
    //
    //        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    //            PGConnection pgConn = conn.unwrap(PGConnection.class);
    //            CopyManager cm = pgConn.getCopyAPI();
    //
    //            try (Writer writer = new OutputStreamWriter(out, "UTF-8")) {
    //                cm.copyOut(copySql, writer);
    //            }
    //        }
    //    }

    //    private static String buildCopySql(ReadonlyConfig config) {
    //        // 如果用户给了自定义 COPY_SQL，直接用
    //        String custom = config.get(PostgresCopyOptions.COPY_SQL);
    //        if (custom != null && !custom.trim().isEmpty()) {
    //            return custom;
    //        }
    //
    //        String table = config.get(PostgresCopyOptions.TABLE);
    //        String cols = config.get(PostgresCopyOptions.COLUMNS);
    //        String where = config.get(PostgresCopyOptions.WHERE);
    //
    //        StringBuilder sb = new StringBuilder();
    //        sb.append("COPY (SELECT ").append(cols).append(" FROM ").append(table);
    //        if (where != null && !where.isEmpty()) {
    //            sb.append(" WHERE ").append(where);
    //        }
    //        sb.append(") TO STDOUT WITH CSV");
    //
    //        if (config.get(PostgresCopyOptions.CSV_HEADER)) {
    //            sb.append(" HEADER");
    //        }
    //
    //        sb.append(" DELIMITER
    // '").append(config.get(PostgresCopyOptions.DELIMITER)).append("'");
    //        sb.append(" QUOTE '").append(config.get(PostgresCopyOptions.QUOTE)).append("'");
    //        sb.append(" ESCAPE '").append(config.get(PostgresCopyOptions.ESCAPE)).append("'");
    //
    //        String nullAs = config.get(PostgresCopyOptions.NULL_AS);
    //        if (nullAs != null && !nullAs.isEmpty()) {
    //            sb.append(" NULL '").append(nullAs).append("'");
    //        }
    //
    //        return sb.toString();
    //    }
}
