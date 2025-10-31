package org.apache.seatunnel.connectors.seatunnel.postgres.copy.Util;

import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.source.PostgresCopySourceSplit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PostgresSplitOptimizer {

//    private static final Logger log = LoggerFactory.getLogger(PostgresSplitOptimizer.class);


    /**
     * 优化的分片生成方法
     */
    public List<PostgresCopySourceSplit> generateOptimizedSplits(
            Connection connection,
            String table,
            String splitBy,
            String where,
            String cols,
            long min,
            long max,
            long totalRecords,
            int numSplits) throws SQLException {

        // 输入验证
        if (numSplits <= 0) {
            throw new IllegalArgumentException("numSplits must be positive");
        }

        List<PostgresCopySourceSplit> splits = new ArrayList<>();

        // 预计算基础参数
        SplitCalculationParams params = preCalculateParams(totalRecords, numSplits);
        String baseWhereClause = buildBaseWhereClause(where);
        String baseTableClause = buildBaseTableClause(table, baseWhereClause);

        log.info("Generating {} splits for table {}, total records: {}",
                numSplits, table, totalRecords);

        // 批量预计算分片边界（推荐方案）
        if (numSplits > 1 && totalRecords > params.recordsPerSplit) {
            splits = generateSplitsWithPrecomputedBoundaries(
                    connection, table, splitBy, where, cols, min, max,
                    baseWhereClause, baseTableClause, params);
        } else {
            // 单个分片的简单情况
            splits = generateSingleSplit(table, where, cols);
        }

        log.info("Generated {} splits successfully", splits.size());
        return splits;
    }

    /**
     * 预计算分片参数
     */
    private SplitCalculationParams preCalculateParams(long totalRecords, int numSplits) {
        return new SplitCalculationParams(totalRecords, numSplits);
    }

    /**
     * 构建基础WHERE子句
     */
    private String buildBaseWhereClause(String where) {
        return where.isEmpty() ? "" : "WHERE " + where;
    }

    /**
     * 构建基础表子句
     */
    private String buildBaseTableClause(String table, String baseWhereClause) {
        return table + (baseWhereClause.isEmpty() ? "" : " " + baseWhereClause);
    }

    /**
     * 使用预计算边界生成分片（推荐方案）
     */
    private List<PostgresCopySourceSplit> generateSplitsWithPrecomputedBoundaries(
            Connection connection,
            String table,
            String splitBy,
            String where,
            String cols,
            long min,
            long max,
            String baseWhereClause,
            String baseTableClause,
            SplitCalculationParams params) throws SQLException {

        List<PostgresCopySourceSplit> splits = new ArrayList<>();
        List<Long> boundaries = precomputeAllBoundaries(
                connection, table, splitBy, baseWhereClause, min, max, params);

        long currentMin = min;
        for (int idx = 0; idx < boundaries.size(); idx++) {
            long currentMax = boundaries.get(idx);

            String rangeWhere = buildRangeWhere(where, splitBy, currentMin, currentMax);
            String sql = buildCopySql(cols, table, rangeWhere);

            splits.add(new PostgresCopySourceSplit("range-" + idx, sql));
            log.debug("Created split {}: range {}-{}", idx, currentMin, currentMax);

            currentMin = currentMax + 1;
        }

        return splits;
    }

    /**
     * 预计算所有分片边界
     */
    private List<Long> precomputeAllBoundaries(
            Connection connection,
            String table,
            String splitBy,
            String baseWhereClause,
            long min,
            long max,
            SplitCalculationParams params) throws SQLException {

        List<Long> boundaries = new ArrayList<>();

        // 构建边界查询SQL
        String boundariesSql = buildBoundariesSql(table, splitBy, baseWhereClause, params);

        try (PreparedStatement stmt = connection.prepareStatement(boundariesSql)) {
            // 设置分片大小参数
            stmt.setLong(1, min);
            stmt.setInt(2, params.recordsPerSplit);
            if (params.remainingRecords > 0) {
                stmt.setInt(3, params.remainingRecords);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    boundaries.add(rs.getLong(1));
                }
            }
        }

        // 确保包含最大值作为最后一个边界
        if (boundaries.isEmpty() || boundaries.get(boundaries.size() - 1) < max) {
            boundaries.add(max);
        }

        return boundaries;
    }

    /**
     * 构建边界查询SQL
     */
    private String buildBoundariesSql(String table, String splitBy, String baseWhereClause,
                                      SplitCalculationParams params) {

        String whereCondition = baseWhereClause.isEmpty() ?
                "" : baseWhereClause + " AND ";

        return "WITH numbered_data AS (" +
                "  SELECT " + splitBy + ", " +
                "  ROW_NUMBER() OVER (ORDER BY " + splitBy + ") as rn " +
                "  FROM " + table + " " +
                "  WHERE " + whereCondition + splitBy + " >= ?" +
                ") " +
                "SELECT " + splitBy + " " +
                "FROM numbered_data " +
                "WHERE rn % ? = 0 " +
                "ORDER BY " + splitBy;
    }

    /**
     * 构建范围WHERE条件
     */
    private String buildRangeWhere(String where, String splitBy, long min, long max) {
        String rangeCondition = String.format("%s >= %d AND %s <= %d", splitBy, min, splitBy, max);
        return where.isEmpty() ? rangeCondition :
                String.format("(%s) AND %s", where, rangeCondition);
    }

    /**
     * 构建COPY SQL（保持与原方法一致）
     */
    private String buildCopySql(String cols, String table, String rangeWhere) {
        return String.format("COPY (SELECT %s FROM %s WHERE %s) TO STDOUT",
                cols, table, rangeWhere);
    }

    /**
     * 生成单个分片
     */
    private List<PostgresCopySourceSplit> generateSingleSplit(String table, String where, String cols) {
        List<PostgresCopySourceSplit> splits = new ArrayList<>();
        String whereClause = where.isEmpty() ? "" : "WHERE " + where;
        String sql = buildCopySql(cols, table, whereClause);
        splits.add(new PostgresCopySourceSplit("range-0", sql));
        log.debug("Created single split for entire range");
        return splits;
    }

    /**
     * 备选方案：使用原始方法的优化版本（如果预计算边界方案不适用）
     */
    private List<PostgresCopySourceSplit> generateSplitsSequentialOptimized(
            Connection connection,
            String table,
            String splitBy,
            String where,
            String cols,
            long min,
            long max,
            String baseWhereClause,
            String baseTableClause,
            SplitCalculationParams params) throws SQLException {

        List<PostgresCopySourceSplit> splits = new ArrayList<>();
        long currentMin = min;
        int idx = 0;

        // 预编译边界查询SQL
        String boundSqlTemplate = buildBoundSqlTemplate(splitBy, baseTableClause);

        try (PreparedStatement boundStmt = connection.prepareStatement(boundSqlTemplate)) {
            while (idx < params.numSplits && currentMin <= max) {
                // 计算当前分片的偏移量
                int offset = calculateCurrentOffset(idx, params);

                // 设置参数并执行查询
                boundStmt.setLong(1, currentMin);
                boundStmt.setInt(2, offset);

                long currentMax;
                try (ResultSet rs = boundStmt.executeQuery()) {
                    currentMax = rs.next() ? rs.getLong(1) : max;
                }

                // 构建分片
                String rangeWhere = buildRangeWhere(where, splitBy, currentMin, currentMax);
                String sql = buildCopySql(cols, table, rangeWhere);
                splits.add(new PostgresCopySourceSplit("range-" + idx, sql));

                log.debug("Created split {}: range {}-{}", idx, currentMin, currentMax);
                currentMin = currentMax + 1;
                idx++;
            }
        }

        return splits;
    }

    /**
     * 构建边界查询SQL模板
     */
    private String buildBoundSqlTemplate(String splitBy, String baseTableClause) {
        return String.format(
                "SELECT %s FROM %s WHERE %s >= " +
                        "(SELECT %s FROM %s WHERE %s > ? ORDER BY %s LIMIT 1 OFFSET ?) " +
                        "ORDER BY %s LIMIT 1",
                splitBy, baseTableClause, splitBy,
                splitBy, baseTableClause, splitBy, splitBy,
                splitBy);
    }

    /**
     * 计算当前分片偏移量
     */
    private int calculateCurrentOffset(int idx, SplitCalculationParams params) {
        return params.recordsPerSplit + (idx < params.remainingRecords ? 1 : 0) - 2;
    }

    /**
     * 分片计算参数容器类
     */
    private static class SplitCalculationParams {
        int recordsPerSplit;
        int remainingRecords;
        int numSplits;

        SplitCalculationParams(long totalRecords, int numSplits) {
            this.numSplits = numSplits;
            this.recordsPerSplit = (int) (totalRecords / numSplits);
            this.remainingRecords = (int) (totalRecords % numSplits);
        }
    }
}
