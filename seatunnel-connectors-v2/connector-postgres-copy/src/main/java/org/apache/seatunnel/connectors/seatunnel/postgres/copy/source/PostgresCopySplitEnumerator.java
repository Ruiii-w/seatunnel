// PostgresCopySplitEnumerator.java
package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.source.SourceEvent;
import org.apache.seatunnel.api.source.SourceSplitEnumerator;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.Util.PostgresCopyUtil;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.config.PostgresCopyOptions;

import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PostgresCopySplitEnumerator
        implements SourceSplitEnumerator<PostgresCopySourceSplit, Serializable> {
    //    private static final Logger LOG =
    // LoggerFactory.getLogger(PostgresCopySplitEnumerator.class);

    private final Context<PostgresCopySourceSplit> ctx;
    private final ReadonlyConfig conf;
    private final AtomicBoolean assigned = new AtomicBoolean(false);

    private long configParseStartTime;
    private long schemaAnalysisStartTime;
    private long splitPlanStartTime;
    private long totalConfigParseTime;
    private long totalSchemaAnalysisTime;
    private long totalSplitPlanTime;
    private int totalSplits;
    private long totalRecords;

    public PostgresCopySplitEnumerator(Context<PostgresCopySourceSplit> ctx, ReadonlyConfig conf) {
        this.ctx = ctx;
        this.conf = conf;
    }

    public PostgresCopySplitEnumerator(
            Context<PostgresCopySourceSplit> ctx, ReadonlyConfig conf, Object restored) {
        this(ctx, conf);
    }

    @Override
    public void open() {
        log.info("Initializing PostgresCopySplitEnumerator");
        // 用于初始化SourceSplitEnumerator，
        // 可以在这里初始化一些连接器的资源，比如连接数据库，初始化一些状态等
    }

    @Override
    public void run() {
        log.info("Starting split enumeration process");
        if (assigned.compareAndSet(false, true)) {
            List<PostgresCopySourceSplit> splits = planSplits();

            log.info("Planned {} splits for table processing", splits.size());

            // @LOG_MSG
            //            for (int i = 0; i < splits.size(); i++) {
            //                log.debug("Split {}: {}", i, splits.get(i).splitId());
            //            }

            Map<Integer, List<PostgresCopySourceSplit>> buckets = new HashMap<>();
            int p = Math.max(1, ctx.currentParallelism());
            log.info("Current parallelism: {}", p);

            for (int i = 0; i < p; i++) buckets.put(i, new ArrayList<>());
            for (int i = 0; i < splits.size(); i++) buckets.get(i % p).add(splits.get(i));

            buckets.forEach(
                    (subtask, list) -> {
                        log.info("Assigning {} splits to subtask {}", list.size(), subtask);

                        // @LOG_MSG
                        //                        for (PostgresCopySourceSplit split : list) {
                        //                            log.debug("Subtask {} gets split: {}",
                        // subtask, split.splitId());
                        //                        }

                        if (ctx.registeredReaders().contains(subtask)) {
                            ctx.assignSplit(subtask, list);
                            ctx.signalNoMoreSplits(subtask);
                            log.info("Signaled no more splits for subtask {}", subtask);
                        } else {
                            log.warn(
                                    "Subtask {} is not registered, skipping split assignment",
                                    subtask);
                        }
                    });

            log.info("Split assignment completed for all registered readers");
        } else {
            log.info("Splits already assigned, skipping re-assignment");
        }
    }

    private List<PostgresCopySourceSplit> planSplits() {
        log.info("Planning splits for PostgreSQL COPY operation");

        String copySqlOverride = conf.get(PostgresCopyOptions.COPY_SQL);
        if (!copySqlOverride.isEmpty()) {
            log.info("Using custom COPY SQL override");
            return Collections.singletonList(
                    new PostgresCopySourceSplit("copy-0", copySqlOverride));
        }
        final String url = conf.get(PostgresCopyOptions.JDBC_URL);
        final String user = conf.get(PostgresCopyOptions.USERNAME);
        final String pwd = conf.get(PostgresCopyOptions.PASSWORD);
        final String table = conf.get(PostgresCopyOptions.TABLE);
        final String colsRaw = conf.get(PostgresCopyOptions.COLUMNS);
        final String cols = convertJsonArrayToSqlColumns(colsRaw);
        log.debug("Using columns: {}", cols);

        final String mode = conf.get(PostgresCopyOptions.SPLIT_MODE);
        log.info("Split mode: {}", mode);

        final String where = conf.get(PostgresCopyOptions.WHERE);
        if (!where.isEmpty()) {
            log.debug("Using WHERE clause: {}", where);
        }

        if (PostgresCopyUtil.SplitMode.NONE.getValue().equalsIgnoreCase(mode)) {
            log.info("Using NONE split mode - single split");
            String sql = buildCopySql(cols, table, where);
            log.debug("Generated COPY SQL: {}", sql);
            return Collections.singletonList(new PostgresCopySourceSplit("copy-0", sql));
        }

        try (Connection c = DriverManager.getConnection(url, user, pwd)) {
            String splitBy = conf.get(PostgresCopyOptions.SPLIT_BY);
            log.info("Splitting by column: {}", splitBy);
            if (PostgresCopyUtil.SplitMode.RANGE.getValue().equalsIgnoreCase(mode)) {
                { // 划分id区间（只能处理连续id）
                    //                log.info("Using RANGE split mode");
                    //                String splitBy = conf.get(PostgresCopyOptions.SPLIT_BY);
                    //                log.info("Splitting by column: {}", splitBy);
                    //
                    //                int chunk = conf.get(PostgresCopyOptions.SPLIT_SIZE);
                    //                log.info("Split size (chunk): {}", chunk);
                    //
                    //                // 获取 min/max
                    //                long min = 0, max = -1;
                    //                try (Statement st = c.createStatement()) {
                    //                    String base =
                    //                            String.format(
                    //                                    "SELECT MIN(%s), MAX(%s) FROM %s%s",
                    //                                    splitBy,
                    //                                    splitBy,
                    //                                    table,
                    //                                    where.isEmpty() ? "" : "WHERE " +where);
                    //                    log.debug("Executing range query: {}", base);
                    //                    try (ResultSet rs = st.executeQuery(base)) {
                    //                        if (rs.next()) {
                    //                            min = rs.getLong(1);
                    //                            max = rs.getLong(2);
                    //                            log.info("Column {} range: min={}, max={}",
                    // splitBy, min, max);
                    //                        }
                    //                    }
                    //                }
                    //                if (max < min) { // 空表
                    //                    log.warn("Table is empty or range query returned no
                    // results");
                    //                    return Collections.emptyList();
                    //                }
                    //
                    //                long totalRecords = max - min + 1;
                    //                int estimatedSplits = (int) Math.ceil((double)totalRecords /
                    // chunk);
                    //                log.info(
                    //                        "Estimated splits based on range: {} (totalrecords:
                    // {})",
                    //                        estimatedSplits,
                    //                        totalRecords);
                    //
                    //                List<PostgresCopySourceSplit> out = new ArrayList<>();
                    //                long start = min;
                    //                int idx = 0;
                    //                while (start <= max) {
                    //                    long end = Math.min(start + chunk - 1, max);
                    //                    String rangeWhere =
                    //                            where.isEmpty()
                    //                                    ? String.format("%s BETWEEN %d AND%d",
                    // splitBy, start, end)
                    //                                    : "("
                    //                                            + where
                    //                                            + ") AND "
                    //                                            + String.format(
                    //                                                    "%s BETWEEN %d AND%d",
                    // splitBy, start, end);
                    //                    String sql = buildCopySql(cols, table, rangeWhere);
                    //
                    //                    // @LOG_MSG
                    //                    //                    log.debug("Split {}: range{}-{},
                    // SQL: {}", idx, start,
                    //                    // end, sql);
                    //                    out.add(new PostgresCopySourceSplit("range-" +(idx++),
                    // sql));
                    //                    start = end + 1;
                    //                }
                    //                log.info("Created {} range splits", out.size());
                    //                return out;
                }
                {
                    log.info("Using RANGE split mode");

                    int chunk = conf.get(PostgresCopyOptions.SPLIT_SIZE);
                    log.info("Split size (chunk): {}", chunk);

                    // 优化：合并COUNT和MIN/MAX查询
                    long totalRecords = 0;
                    long min = 0, max = -1;
                    try (Statement st = c.createStatement()) {
                        String statsQuery =
                                String.format(
                                        "SELECT COUNT(%s) as total, MIN(%s) as min_val, MAX(%s) as max_val FROM %s %s",
                                        splitBy,
                                        splitBy,
                                        splitBy,
                                        table,
                                        where.isEmpty() ? "" : "WHERE " + where);
                        log.debug("Executing stats query: {}", statsQuery);
                        try (ResultSet rs = st.executeQuery(statsQuery)) {
                            if (rs.next()) {
                                totalRecords = rs.getLong("total");
                                min = rs.getLong("min_val");
                                max = rs.getLong("max_val");
                                log.info(
                                        "Table stats - total: {}, {} range: [{}, {}]",
                                        totalRecords,
                                        splitBy,
                                        min,
                                        max);
                            }
                        }
                    }

                    if (totalRecords == 0 || max < min) {
                        log.warn("Table is empty or query returned no results");
                        return Collections.emptyList();
                    }

                    // 计算每个分片的记录数
                    int numSplits = (int) Math.ceil((double) totalRecords / chunk);
                    //                    long recordsPerSplit = totalRecords / numSplits;
                    long recordsPerSplit = chunk;
                    long remainingRecords = totalRecords % chunk;
                    log.info(
                            "Planning {} splits with approximately {} records each",
                            numSplits,
                            recordsPerSplit);

                    List<PostgresCopySourceSplit> out = new ArrayList<>();
                    long currentMin = min;
                    int idx = 0;

                    // 优化：使用子查询而不是OFFSET来获取分片边界
                    while (idx < numSplits) {
                        // 获取当前分片的上界
                        String boundSql =
                                String.format(
                                        "SELECT %s FROM %s %s WHERE %s >= "
                                                + "(SELECT %s FROM %s %s WHERE %s > %d ORDER BY %s LIMIT 1 OFFSET %d) "
                                                + "ORDER BY %s LIMIT 1",
                                        splitBy,
                                        table,
                                        where.isEmpty() ? "" : "WHERE " + where,
                                        splitBy,
                                        splitBy,
                                        table,
                                        where.isEmpty() ? "" : "WHERE " + where,
                                        splitBy,
                                        currentMin,
                                        splitBy,
                                        recordsPerSplit + (idx < remainingRecords ? 1 : 0) - 2,
                                        splitBy);

                        long currentMax;
                        try (Statement st = c.createStatement();
                                ResultSet rs = st.executeQuery(boundSql)) {
                            currentMax = rs.next() ? rs.getLong(1) : max;
                        }

                        // 构建分片的WHERE条件
                        String rangeWhere =
                                where.isEmpty()
                                        ? String.format(
                                                "%s >= %d AND %s <= %d",
                                                splitBy, currentMin, splitBy, currentMax)
                                        : String.format(
                                                "(%s) AND %s >= %d AND %s <= %d",
                                                where, splitBy, currentMin, splitBy, currentMax);

                        String sql = buildCopySql(cols, table, rangeWhere);
                        out.add(new PostgresCopySourceSplit("range-" + idx, sql));
                        log.debug("Created split {}: range {}-{}", idx, currentMin, currentMax);

                        currentMin = currentMax + 1;
                        idx++;
                    }

                    log.info("Created {} range splits", out.size());
                    return out;
                }
            } else if (PostgresCopyUtil.SplitMode.HASH.getValue().equalsIgnoreCase(mode)) {
                log.info("Using HASH split mode");
                int buckets = conf.get(PostgresCopyOptions.HASH_BUCKETS);
                if (buckets <= 0) buckets = Math.max(1, ctx.currentParallelism());
                log.info("Using {} hash buckets", buckets);

                //                String splitBy = conf.get(PostgresCopyOptions.SPLIT_BY);
                //                log.info("Hashing by column: {}", splitBy);

                List<PostgresCopySourceSplit> out = new ArrayList<>();
                for (int b = 0; b < buckets; b++) {
                    String hashCond =
                            String.format(
                                    "MOD(ABS(HASHTEXT(CAST(%s AS TEXT))), %d) = %d",
                                    splitBy, buckets, b);
                    String combined =
                            where.isEmpty() ? hashCond : "(" + where + ") AND " + hashCond;
                    String sql = buildCopySql(cols, table, combined);
                    log.debug("Hash bucket {}: SQL: {}", b, sql);
                    out.add(new PostgresCopySourceSplit("hash-" + b, sql));
                }
                log.info("Created {} hash splits", out.size());
                return out;
            } else {
                throw new IllegalArgumentException("Unknown split_mode: " + mode);
            }
        } catch (SQLException e) {
            log.error("Plan splits failed", e);
            throw new RuntimeException("Plan splits failed", e);
        }
    }

    //    private List<PostgresCopySourceSplit> planSplits() {
    //        log.info("Planning splits for PostgreSQL COPY operation");
    //
    //        // 配置解析阶段开始（对所有模式通用）
    //        configParseStartTime = System.currentTimeMillis();
    //
    //        String copySqlOverride = conf.get(PostgresCopyOptions.COPY_SQL);
    //        final String url = conf.get(PostgresCopyOptions.JDBC_URL);
    //        final String user = conf.get(PostgresCopyOptions.USERNAME);
    //        final String pwd = conf.get(PostgresCopyOptions.PASSWORD);
    //        final String table = conf.get(PostgresCopyOptions.TABLE);
    //        final String colsRaw = conf.get(PostgresCopyOptions.COLUMNS);
    //        final String cols = convertJsonArrayToSqlColumns(colsRaw);
    //        final String mode = conf.get(PostgresCopyOptions.SPLIT_MODE);
    //        final String where = conf.get(PostgresCopyOptions.WHERE);
    //
    //        // 配置解析阶段结束
    //        totalConfigParseTime = System.currentTimeMillis() - configParseStartTime;
    //        log.info("Configuration parsing completed in {} ms", totalConfigParseTime);
    //
    //        if (PostgresCopyUtil.SplitMode.NONE.getValue().equalsIgnoreCase(mode)) {
    //            // NONE模式不需要额外的统计
    //            log.info("Using NONE split mode - single split");
    //            String sql = buildCopySql(cols, table, where);
    //            return Collections.singletonList(new PostgresCopySourceSplit("copy-0", sql));
    //        }
    //
    //        try (Connection c = DriverManager.getConnection(url, user, pwd)) {
    //            // 表结构分析阶段开始（对RANGE和HASH模式通用）
    //            schemaAnalysisStartTime = System.currentTimeMillis();
    //
    //            String splitBy = conf.get(PostgresCopyOptions.SPLIT_BY);
    //
    //            // 获取总记录数（对RANGE和HASH模式都有用）
    //            try (Statement st = c.createStatement()) {
    //                String countQuery =
    //                        String.format(
    //                                "SELECT COUNT(%s) as total FROM %s %s",
    //                                splitBy, table, where.isEmpty() ? "" : "WHERE " + where);
    //
    //                try (ResultSet rs = st.executeQuery(countQuery)) {
    //                    if (rs.next()) {
    //                        totalRecords = rs.getLong("total");
    //                    }
    //                }
    //            }
    //
    //            // 表结构分析阶段结束
    //            totalSchemaAnalysisTime = System.currentTimeMillis() - schemaAnalysisStartTime;
    //            log.info(
    //                    "Schema analysis completed in {} ms, found {} total records",
    //                    totalSchemaAnalysisTime,
    //                    totalRecords);
    //
    //            // 分片计划生成阶段开始
    //            splitPlanStartTime = System.currentTimeMillis();
    //
    //            List<PostgresCopySourceSplit> splits;
    //            if (PostgresCopyUtil.SplitMode.RANGE.getValue().equalsIgnoreCase(mode)) {
    //                splits = createRangeSplits(c, splitBy, cols, table, where);
    //            } else if (PostgresCopyUtil.SplitMode.HASH.getValue().equalsIgnoreCase(mode)) {
    //                splits = createHashSplits(splitBy, cols, table, where);
    //            } else {
    //                throw new IllegalArgumentException("Unknown split_mode: " + mode);
    //            }
    //
    //            // 分片计划生成阶段结束
    //            totalSplitPlanTime = System.currentTimeMillis() - splitPlanStartTime;
    //            totalSplits = splits.size();
    //
    //            // 输出整体预处理性能统计（对所有模式通用）
    //            log.info("Preprocessing performance statistics:");
    //            log.info("- Configuration parsing: {} ms", totalConfigParseTime);
    //            log.info("- Schema analysis: {} ms", totalSchemaAnalysisTime);
    //            log.info("- Split plan generation: {} ms", totalSplitPlanTime);
    //            log.info(
    //                    "- Total preprocessing time: {} ms",
    //                    totalConfigParseTime + totalSchemaAnalysisTime + totalSplitPlanTime);
    //            log.info("- Total splits created: {}", totalSplits);
    //            //            log.info("- Average time per split: {} ms",
    //            //                    totalSplits > 0 ? (double)(totalSplitPlanTime) / totalSplits
    // : 0);
    //            //            log.info("- Records per split: {}",
    //            //                    totalSplits > 0 ? (double)totalRecords / totalSplits : 0);
    //
    //            return splits;
    //
    //        } catch (SQLException e) {
    //            log.error("Plan splits failed", e);
    //            throw new RuntimeException("Plan splits failed", e);
    //        }
    //    }

    private List<PostgresCopySourceSplit> createRangeSplits(
            Connection c, String splitBy, String cols, String table, String where)
            throws SQLException {

        int chunk = conf.get(PostgresCopyOptions.SPLIT_SIZE);

        // 获取值范围
        try (Statement st = c.createStatement()) {
            String rangeQuery =
                    String.format(
                            "SELECT MIN(%s) as min_val, MAX(%s) as max_val FROM %s %s",
                            splitBy, splitBy, table, where.isEmpty() ? "" : "WHERE " + where);

            try (ResultSet rs = st.executeQuery(rangeQuery)) {
                if (rs.next()) {
                    long min = rs.getLong("min_val");
                    long max = rs.getLong("max_val");
                    log.info("Range split column {} range: [{}, {}]", splitBy, min, max);

                    // 创建范围分片
                    List<PostgresCopySourceSplit> splits = new ArrayList<>();
                    long currentMin = min;
                    int idx = 0;

                    while (currentMin <= max) {
                        long currentMax = Math.min(currentMin + chunk - 1, max);
                        String rangeWhere =
                                String.format(
                                        "%s >= %d AND %s <= %d",
                                        splitBy, currentMin, splitBy, currentMax);
                        if (!where.isEmpty()) {
                            rangeWhere = "(" + where + ") AND " + rangeWhere;
                        }

                        String sql = buildCopySql(cols, table, rangeWhere);
                        log.debug("Created split {}: range {}-{}", idx, currentMin, currentMax);
                        splits.add(new PostgresCopySourceSplit("range-" + idx, sql));
                        currentMin = currentMax + 1;
                        idx++;
                    }

                    return splits;
                }
            }
        }

        return Collections.emptyList();
    }

    private List<PostgresCopySourceSplit> createHashSplits(
            String splitBy, String cols, String table, String where) {

        int buckets = conf.get(PostgresCopyOptions.HASH_BUCKETS);
        if (buckets <= 0) {
            buckets = Math.max(1, ctx.currentParallelism());
        }
        log.info("Creating {} hash buckets for column: {}", buckets, splitBy);

        List<PostgresCopySourceSplit> splits = new ArrayList<>();
        for (int b = 0; b < buckets; b++) {
            String hashCond =
                    String.format(
                            "MOD(ABS(HASHTEXT(CAST(%s AS TEXT))), %d) = %d", splitBy, buckets, b);
            String combined = where.isEmpty() ? hashCond : "(" + where + ") AND " + hashCond;
            String sql = buildCopySql(cols, table, combined);
            log.debug("Hash bucket {}: SQL: {}", b, sql);
            splits.add(new PostgresCopySourceSplit("hash-" + b, sql));
        }

        return splits;
    }

    private String buildCopySql(String cols, String table, String where) {
        String sel =
                String.format("SELECT %s FROM %s", cols, table)
                        + (where == null || where.isEmpty() ? "" : " WHERE " + where);
        // 这里不含 WITH 参数，Reader 里会把 CSV/BINARY/分隔符等拼上
        return "COPY (" + sel + ") TO STDOUT";
    }

    @Override
    public void addSplitsBack(List<PostgresCopySourceSplit> splits, int subtask) {
        log.info("Adding back {} splits to subtask {}", splits.size(), subtask);

        // @LOG_MSG
        //        for (PostgresCopySourceSplit split : splits) {
        //            log.debug("Reassigning split {} to subtask {}", split.splitId(), subtask);
        //        }

        if (!splits.isEmpty()) {
            ctx.assignSplit(subtask, splits);
            ctx.signalNoMoreSplits(subtask);
            log.info("Signaled no more splits for subtask {} after adding back splits", subtask);
        }
    }

    @Override
    public int currentUnassignedSplitSize() {
        int size = 0;
        log.debug("Current unassigned split size: {}", size);
        return size;
    }

    @Override
    public void registerReader(int subtask) {
        log.info("Reader registered for subtask: {}", subtask);
    }

    @Override
    public void handleSplitRequest(int subtaskId) {
        log.debug("Handling split request from subtask: {}", subtaskId);
    }

    @Override
    public Serializable snapshotState(long checkpointId) {
        log.debug("Taking snapshot at checkpoint ID: {}", checkpointId);
        return null;
    }

    @Override
    public void handleSourceEvent(int subtaskId, SourceEvent sourceEvent) {
        log.debug(
                "Handling source event from subtask {}: {}",
                subtaskId,
                sourceEvent.getClass().getSimpleName());
        SourceSplitEnumerator.super.handleSourceEvent(subtaskId, sourceEvent);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        log.info("Checkpoint {} completed successfully", checkpointId);
    }

    @Override
    public void notifyCheckpointAborted(long checkpointId) throws Exception {
        log.warn("Checkpoint {} was aborted", checkpointId);
        SourceSplitEnumerator.super.notifyCheckpointAborted(checkpointId);
    }

    @Override
    public void close() {
        log.info("Closing PostgresCopySplitEnumerator");
    }

    private String convertJsonArrayToSqlColumns(String columnsConfig) {
        if (columnsConfig == null || columnsConfig.trim().isEmpty()) {
            return "*";
        }

        // 如果已经是正常的列列表格式（包含逗号），直接返回
        if (columnsConfig.contains(",") && !columnsConfig.startsWith("[")) {
            return columnsConfig;
        }

        // 处理 JSON 数组格式 ["col1","col2"]
        if (columnsConfig.startsWith("[") && columnsConfig.endsWith("]")) {
            try {
                // 移除方括号并分割
                String withoutBrackets = columnsConfig.substring(1, columnsConfig.length() - 1);
                String[] columnArray = withoutBrackets.split(",");

                // 移除每个列名周围的引号和空格
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < columnArray.length; i++) {
                    String column = columnArray[i].trim();
                    // 移除引号（单引号或双引号）
                    if ((column.startsWith("\"") && column.endsWith("\""))
                            || (column.startsWith("'") && column.endsWith("'"))) {
                        column = column.substring(1, column.length() - 1);
                    }
                    sb.append(column);
                    if (i < columnArray.length - 1) {
                        sb.append(", ");
                    }
                }
                String result = sb.toString();
                log.debug("Converted JSON array {} to SQL columns: {}", columnsConfig, result);
                return result;
            } catch (Exception e) {
                log.warn(
                        "Failed to parse JSON array format for columns: {}, using default '*'",
                        columnsConfig,
                        e);
                // 如果解析失败，返回默认值
                return "*";
            }
        }

        log.debug("Using original columns configuration: {}", columnsConfig);
        // 返回原始值
        return columnsConfig;
    }
}
