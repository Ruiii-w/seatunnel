package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.config.PostgresCopySourceConfig;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.math.BigDecimal.ROUND_CEILING;

@Slf4j
public class PostgresCopyChunkSplitter {

    private final PostgresCopySourceConfig config;
    private final PostgresCopyDialect dialect;

    public PostgresCopyChunkSplitter(PostgresCopySourceConfig config) {
        this.config = config;
        this.dialect = new PostgresCopyDialect();
    }

    /**
     * 创建分片
     */
    public Collection<PostgresCopySourceSplit> createSplits(
            PostgresCopySourceTable table, SeaTunnelRowType splitKey) throws Exception {

        if (splitKey == null || splitKey.getFieldNames().length == 0) {
            // 没有分片键，返回单个分片
            String sql = buildBaseCopySQL(table);
            return Collections.singletonList(
                    new PostgresCopySourceSplit("split-0", sql)
            );
        }

        String splitColumnName = splitKey.getFieldNames()[0];
        SeaTunnelDataType splitColumnType = splitKey.getFieldType(0);

        List<ChunkRange> chunks = splitTableIntoChunks(table, splitColumnName, splitColumnType);

        List<PostgresCopySourceSplit> splits = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkRange chunk = chunks.get(i);
            String sql = buildChunkCopySQL(table, splitColumnName, chunk);
            splits.add(new PostgresCopySourceSplit(
                    "split-" + i,
                    sql,
                    chunk.getChunkStart(),
                    chunk.getChunkEnd(),
                    splitColumnName
            ));
        }

        log.info("Created {} splits for table {}", splits.size(), table.getTablePath());
        return splits;
    }

    /**
     * 将表分割成块
     */
    private List<ChunkRange> splitTableIntoChunks(
            PostgresCopySourceTable table, String splitColumnName, SeaTunnelDataType splitColumnType)
            throws Exception {

        try (Connection connection = createConnection(table)) {
            Object[] minMax = dialect.queryMinMax(connection, table.getTablePath(), splitColumnName);
            Object min = minMax[0];
            Object max = minMax[1];

            if (min == null || max == null) {
                log.warn("Cannot find min/max values for column {}, using single split", splitColumnName);
                return Collections.singletonList(ChunkRange.all());
            }

            long approximateRowCnt = dialect.queryApproximateRowCount(connection, table.getTablePath());
            log.info("Approximate row count: {} for table {}", approximateRowCnt, table.getTablePath());

            int chunkSize = config.getSplitSize();

            // 计算分布因子
            double distributionFactor = calculateDistributionFactor(
                    table.getTablePath(), min, max, approximateRowCnt);

            log.info("Distribution factor: {} for table {}", distributionFactor, table.getTablePath());

            // 估算分片数量
            int estimatedSplitCount = (int) Math.ceil((double) approximateRowCnt / chunkSize);

            // 判断是否使用采样分片
            if (distributionFactor >= config.getSplitEvenDistributionFactorUpperBound() ||
                    distributionFactor <= config.getSplitEvenDistributionFactorLowerBound() ||
                    estimatedSplitCount > config.getSplitSampleShardingThreshold()) {

                log.info("Using sampling sharding strategy for table {}", table.getTablePath());
                return efficientShardingThroughSampling(connection, table, splitColumnName,
                        splitColumnType, approximateRowCnt, estimatedSplitCount);
            } else {
                log.info("Using even distribution strategy for table {}", table.getTablePath());
                return evenlyColumnSplitChunks(table, splitColumnName, min, max, chunkSize);
            }
        }
    }

    /**
     * 通过采样进行高效分片
     */
    private List<ChunkRange> efficientShardingThroughSampling(
            Connection connection,
            PostgresCopySourceTable table,
            String splitColumnName,
            SeaTunnelDataType splitColumnType,
            long approximateRowCnt,
            int shardCount) throws SQLException {

        double samplingRate = 1.0 / config.getSplitInverseSamplingRate();

        Object[] sampleData = dialect.sampleDataFromColumn(
                connection, table.getTablePath(), splitColumnName, splitColumnType, samplingRate);

        if (sampleData.length == 0) {
            log.warn("No sample data collected, using single split");
            return Collections.singletonList(ChunkRange.all());
        }

        // 对采样数据排序
        Arrays.sort(sampleData, this::objectCompare);

        return efficientShardingThroughSampling(table.getTablePath(), sampleData, approximateRowCnt, shardCount);
    }

    /**
     * 基于采样数据创建分片
     */
    public static List<ChunkRange> efficientShardingThroughSampling(
            TablePath tablePath, Object[] sampleData, long approximateRowCnt, int shardCount) {

        log.info("Efficient sharding through sampling for table: {}, sampleData size: {}, shardCount: {}",
                tablePath, sampleData.length, shardCount);

        if (sampleData.length == 0) {
            return Collections.singletonList(ChunkRange.all());
        }

        int samplesPerShard = Math.max(1, sampleData.length / shardCount);
        List<ChunkRange> chunks = new ArrayList<>();

        Object chunkStart = null;
        for (int i = 0; i < sampleData.length; i += samplesPerShard) {
            Object chunkEnd = (i + samplesPerShard < sampleData.length) ?
                    sampleData[i + samplesPerShard - 1] : null;

            // 避免重复的边界值
            if (chunkEnd != null && Objects.equals(chunkStart, chunkEnd)) {
                continue;
            }

            chunks.add(ChunkRange.of(chunkStart, chunkEnd));
            chunkStart = chunkEnd;
        }

        log.info("Created {} chunks through sampling for table {}", chunks.size(), tablePath);
        return chunks;
    }

    /**
     * 均匀分片
     */
    private List<ChunkRange> evenlyColumnSplitChunks(
            PostgresCopySourceTable table, String splitColumnName, Object min, Object max, int chunkSize)
            throws Exception {

        log.info("Evenly splitting column {} from {} to {} with chunk size {}",
                splitColumnName, min, max, chunkSize);

        List<ChunkRange> chunks = new ArrayList<>();

        if (min instanceof Number && max instanceof Number) {
            return splitNumericColumn(min, max, chunkSize);
        } else {
            // 对于非数值类型，使用单个分片
            chunks.add(ChunkRange.of(min, max));
        }

        return chunks;
    }

    /**
     * 分割数值列
     */
    private List<ChunkRange> splitNumericColumn(Object min, Object max, int chunkSize) {
        List<ChunkRange> chunks = new ArrayList<>();

        BigDecimal minVal = new BigDecimal(min.toString());
        BigDecimal maxVal = new BigDecimal(max.toString());
        BigDecimal chunkSizeDecimal = new BigDecimal(chunkSize);

        BigDecimal totalRange = maxVal.subtract(minVal);
        int numChunks = totalRange.divide(chunkSizeDecimal, ROUND_CEILING).intValue();

        if (numChunks <= 1) {
            chunks.add(ChunkRange.of(min, max));
            return chunks;
        }

        BigDecimal stepSize = totalRange.divide(new BigDecimal(numChunks), ROUND_CEILING);

        BigDecimal chunkStart = minVal;
        for (int i = 0; i < numChunks; i++) {
            BigDecimal chunkEnd = (i == numChunks - 1) ? maxVal : chunkStart.add(stepSize);
            chunks.add(ChunkRange.of(chunkStart, chunkEnd));
            chunkStart = chunkEnd;
        }

        return chunks;
    }

    /**
     * 计算分布因子
     */
    private double calculateDistributionFactor(
            TablePath tablePath, Object min, Object max, long approximateRowCnt) {

        if (!(min instanceof Number) || !(max instanceof Number)) {
            return 1.0; // 非数值类型默认分布因子
        }

        try {
            BigDecimal minVal = new BigDecimal(min.toString());
            BigDecimal maxVal = new BigDecimal(max.toString());
            BigDecimal range = maxVal.subtract(minVal);

            if (range.compareTo(BigDecimal.ZERO) == 0) {
                return 0.0;
            }

            BigDecimal avgGap = range.divide(new BigDecimal(approximateRowCnt), 10, ROUND_CEILING);
            return range.divide(avgGap, 10, ROUND_CEILING).doubleValue() / approximateRowCnt;

        } catch (Exception e) {
            log.warn("Failed to calculate distribution factor for table {}: {}", tablePath, e.getMessage());
            return 1.0;
        }
    }

    /**
     * 构建基础COPY SQL
     */
    private String buildBaseCopySQL(PostgresCopySourceTable table) {
        if (table.getQuery() != null) {
            return String.format("COPY (%s) TO STDOUT WITH CSV HEADER", table.getQuery());
        }

        String fullTableName = getFullTableName(table.getTablePath());
        return String.format("COPY %s TO STDOUT WITH CSV HEADER", fullTableName);
    }

    /**
     * 构建分片COPY SQL
     */
    private String buildChunkCopySQL(PostgresCopySourceTable table, String splitColumnName, ChunkRange chunk) {
        String baseQuery;

        if (table.getQuery() != null) {
            baseQuery = table.getQuery();
        } else {
            String fullTableName = getFullTableName(table.getTablePath());
            baseQuery = String.format("SELECT * FROM %s", fullTableName);
        }

        // 添加分片条件
        String whereClause = buildWhereClause(baseQuery, splitColumnName, chunk);
        String finalQuery = addWhereClause(baseQuery, whereClause);

        return String.format("COPY (%s) TO STDOUT WITH CSV HEADER", finalQuery);
    }

    /**
     * 构建WHERE子句
     */
    private String buildWhereClause(String baseQuery, String splitColumnName, ChunkRange chunk) {
        List<String> conditions = new ArrayList<>();

        if (chunk.getChunkStart() != null) {
            conditions.add(String.format("\"%s\" >= %s", splitColumnName, formatValue(chunk.getChunkStart())));
        }

        if (chunk.getChunkEnd() != null) {
            conditions.add(String.format("\"%s\" < %s", splitColumnName, formatValue(chunk.getChunkEnd())));
        }

        return String.join(" AND ", conditions);
    }

    /**
     * 添加WHERE子句到查询中
     */
    private String addWhereClause(String baseQuery, String whereClause) {
        if (whereClause.isEmpty()) {
            return baseQuery;
        }

        String upperQuery = baseQuery.toUpperCase();
        if (upperQuery.contains(" WHERE ")) {
            return baseQuery + " AND " + whereClause;
        } else {
            return baseQuery + " WHERE " + whereClause;
        }
    }

    /**
     * 格式化值
     */
    private String formatValue(Object value) {
        if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        }
        return value.toString();
    }

    private String getFullTableName(TablePath tablePath) {
        if (tablePath.getSchemaName() != null) {
            return "\"" + tablePath.getSchemaName() + "\".\"" + tablePath.getTableName() + "\"";
        }
        return "\"" + tablePath.getTableName() + "\"";
    }

    private Connection createConnection(PostgresCopySourceTable table) throws SQLException {
        return DriverManager.getConnection(table.getJdbcUrl(), table.getUsername(), table.getPassword());
    }

    private int objectCompare(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return 0;
        if (obj1 == null) return -1;
        if (obj2 == null) return 1;

        if (obj1 instanceof Comparable && obj2 instanceof Comparable) {
            return ((Comparable) obj1).compareTo(obj2);
        }

        return obj1.toString().compareTo(obj2.toString());
    }
}