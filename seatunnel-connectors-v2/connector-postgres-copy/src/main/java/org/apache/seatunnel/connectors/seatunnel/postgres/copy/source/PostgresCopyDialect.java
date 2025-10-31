package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PostgresCopyDialect {

    /** 从指定列采样数据 */
    public Object[] sampleDataFromColumn(
            Connection connection,
            TablePath tablePath,
            String columnName,
            SeaTunnelDataType dataType,
            double samplingRate)
            throws SQLException {

        String sampleQuery = buildSampleQuery(tablePath, columnName, samplingRate);
        log.info("Executing sample query: {}", sampleQuery);

        List<Object> samples = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sampleQuery);
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                Object value = resultSet.getObject(1);
                if (value != null) {
                    samples.add(value);
                }
            }
        }

        log.info("Collected {} samples from column {}", samples.size(), columnName);
        return samples.toArray();
    }

    /** 构建采样查询SQL */
    private String buildSampleQuery(TablePath tablePath, String columnName, double samplingRate) {
        String fullTableName = getFullTableName(tablePath);

        // PostgreSQL使用TABLESAMPLE SYSTEM进行采样
        // 采样率转换为百分比
        double samplePercent = samplingRate * 100;

        return String.format(
                "SELECT %s FROM %s TABLESAMPLE SYSTEM (%.2f) WHERE %s IS NOT NULL ORDER BY %s",
                quoteIdentifier(columnName),
                fullTableName,
                samplePercent,
                quoteIdentifier(columnName),
                quoteIdentifier(columnName));
    }

    /** 查询列的最小值和最大值 */
    public Object[] queryMinMax(Connection connection, TablePath tablePath, String columnName)
            throws SQLException {
        String fullTableName = getFullTableName(tablePath);
        String query =
                String.format(
                        "SELECT MIN(%s), MAX(%s) FROM %s WHERE %s IS NOT NULL",
                        quoteIdentifier(columnName),
                        quoteIdentifier(columnName),
                        fullTableName,
                        quoteIdentifier(columnName));
        log.info("Dialect query sql for min and max identifier: {}", query);
        try (PreparedStatement statement = connection.prepareStatement(query);
                ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return new Object[] {resultSet.getObject(1), resultSet.getObject(2)};
            }
        } catch (SQLException e) {
            log.error("Failed to execute query: {}, error: {}", query, e.getMessage(), e);
            throw new RuntimeException("Database query failed", e);
        } catch (Exception e) {
            log.error(
                    "Unexpected error while executing query: {}, error: {}",
                    query,
                    e.getMessage(),
                    e);
            throw new RuntimeException("Unexpected error during query execution", e);
        }
        return new Object[] {null, null};
    }

    /** 查询近似行数 */
    public long queryApproximateRowCount(Connection connection, TablePath tablePath)
            throws SQLException {
        String fullTableName = getFullTableName(tablePath);

        // 首先尝试从统计信息获取
        String statsQuery =
                String.format(
                        "SELECT n_tup_ins - n_tup_del AS approximate_row_count "
                                + "FROM pg_stat_user_tables "
                                + "WHERE schemaname = '%s' AND relname = '%s'",
                        tablePath.getSchemaName(), tablePath.getTableName());

        try (PreparedStatement statement = connection.prepareStatement(statsQuery);
                ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                long count = resultSet.getLong(1);
                if (count > 0) {
                    return count;
                }
            }
        }

        // 如果统计信息不可用，使用COUNT查询
        String countQuery = String.format("SELECT COUNT(*) FROM %s", fullTableName);
        try (PreparedStatement statement = connection.prepareStatement(countQuery);
                ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
        }

        return 0L;
    }

    private String getFullTableName(TablePath tablePath) {
        if (tablePath.getSchemaName() != null) {
            return quoteIdentifier(tablePath.getSchemaName())
                    + "."
                    + quoteIdentifier(tablePath.getTableName());
        }
        return quoteIdentifier(tablePath.getTableName());
    }

    private String quoteIdentifier(String identifier) {
        //        return "\"" + identifier + "\"";
        return identifier;
    }
}
