// PostgresCopySourceReader.java
package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.source.Boundedness;
import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.source.SourceEvent;
import org.apache.seatunnel.api.source.SourceReader;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.Util.PgBinaryCopyParser;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.Util.PostgresCopyDataParser;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.config.PostgresCopyOptions;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class PostgresCopySourceReader
        implements SourceReader<SeaTunnelRow, PostgresCopySourceSplit> {

    // 批处理配置
    private static final int BATCH_SIZE = 100000; // 每批处理的行数
    private static final int BATCH_TIMEOUT_MS = 100; // 批处理超时时间（毫秒）
    private static final int MAX_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    // 单个任务的统计信息类
    private class TaskStatistics {
        String split_id = "";
        long initTime = 0;
        long batchTime = 0;
        long outputTime = 0;
        long bytes = 0;
        long rows = 0;
        long batches = 0;

        TaskStatistics() {}

        TaskStatistics(String _id) {
            this.split_id = _id;
        }

        void outputTaskStatistics() {
            //            log.info("Task Statistics for split: {}", split_id);
            //            log.info("  Init time: {} ms", initTime);
            //            log.info("  batch time: {} ms", batchTime);
            //            log.info("  Output time: {} ms", outputTime);
            //            log.info("  Total bytes: {}", bytes);
            //            log.info("  Total rows: {}", rows);
            //            log.info("  Total batches: {}", batches);
            //            log.info("  Average batch size: {}", batches > 0 ? rows / batches : 0);

            long totalTimeMs = batchTime + outputTime;
            if (totalTimeMs > 0) {
                double rowsPerSec = rows * 1000.0 / totalTimeMs;
                double mbPerSec = (bytes / (1024.0 * 1024.0)) * 1000.0 / totalTimeMs;
                log.info(
                        "  Processing rate: {} rows/s, {} MB/s",
                        String.format("%.2f", rowsPerSec),
                        String.format("%.2f", mbPerSec));
            }
        }
    }

    private class GlobalStatistics {
        AtomicLong totalInitTime = new AtomicLong(0);
        AtomicLong totalbatchTime = new AtomicLong(0);
        AtomicLong totalOutputTime = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicLong totalRows = new AtomicLong(0);
        AtomicLong totalBatches = new AtomicLong(0);

        synchronized void addTaskStatistics(TaskStatistics stats) {
            totalInitTime.addAndGet(stats.initTime);
            totalbatchTime.addAndGet(stats.batchTime);
            totalOutputTime.addAndGet(stats.outputTime);
            totalBytes.addAndGet(stats.bytes);
            totalRows.addAndGet(stats.rows);
            totalBatches.addAndGet(stats.batches);
        }

        void outputGlobalStatistics() {
            log.info("Global Statistics:");
            log.info("  Total init time: {} ms", totalInitTime.get());
            log.info("  Total batch time: {} ms", totalbatchTime.get());
            log.info("  Total output time: {} ms", totalOutputTime.get());
            log.info("  Total bytes processed: {}", totalBytes.get());
            log.info("  Total rows processed: {}", totalRows.get());
            log.info("  Total batches processed: {}", totalBatches.get());
            log.info(
                    "  Average batch size: {}",
                    totalBatches.get() > 0 ? totalRows.get() / totalBatches.get() : 0);
            log.info(
                    "  Average batch time: {} ms",
                    totalBatches.get() > 0 ? totalbatchTime.get() / totalBatches.get() : 0);

            long totalTimeMs = totalbatchTime.get() + totalOutputTime.get();
            if (totalTimeMs > 0) {
                double rowsPerSec = totalRows.get() * 1000.0 / totalTimeMs;
                double mbPerSec = (totalBytes.get() / (1024.0 * 1024.0)) * 1000.0 / totalTimeMs;
                log.info(
                        "  Global processing rate: {} rows/s, {} MB/s",
                        String.format("%.2f", rowsPerSec),
                        String.format("%.2f", mbPerSec));
            }
        }
    }

    /** 简易 List 对象池（代替 commons-pool2） */
    private static class SimpleListPool {
        private final Queue<List<SeaTunnelRow>> pool = new ConcurrentLinkedQueue<>();
        private final int batchSize;

        SimpleListPool(int batchSize) {
            this.batchSize = batchSize;
        }

        List<SeaTunnelRow> borrowObject() {
            List<SeaTunnelRow> list = pool.poll();
            return (list != null) ? list : new ArrayList<>(batchSize);
        }

        void returnObject(List<SeaTunnelRow> list) {
            list.clear();
            pool.offer(list);
        }

        void close() {
            pool.clear();
        }
    }

    private final Context ctx;
    private final ReadonlyConfig conf;
    private final Queue<PostgresCopySourceSplit> splits = new ConcurrentLinkedQueue<>();
    private final Queue<PostgresCopySourceSplit> pendingSplits = new ConcurrentLinkedQueue<>();
    private final Set<PostgresCopySourceSplit> runningSplits =
            Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService executor;
    private final SeaTunnelRowType rowType;
    private final PostgresCopyDataParser parser;
    private final GlobalStatistics globalStats = new GlobalStatistics();
    // 使用自定义池
    private final SimpleListPool batchPool;

    public PostgresCopySourceReader(Context ctx, ReadonlyConfig conf, SeaTunnelRowType rowType) {
        this.ctx = ctx;
        this.conf = conf;
        this.rowType = rowType;
        int parallelism = Math.min(MAX_THREAD_POOL_SIZE, Math.max(1, pendingSplits.size()));
        this.executor = Executors.newFixedThreadPool(parallelism);

        String delimiter = conf.get(PostgresCopyOptions.DELIMITER);
        String nullAs = conf.get(PostgresCopyOptions.NULL_AS);
        parser = new PostgresCopyDataParser(rowType, delimiter, nullAs);

        // 初始化批处理对象池
        this.batchPool = new SimpleListPool(BATCH_SIZE);
    }

    @Override
    public void open() throws Exception {
        // 初始化操作
    }

    @Override
    public void pollNext(Collector<SeaTunnelRow> output) throws Exception {
        // 将新的 split 加入 pending
        PostgresCopySourceSplit split;
        while ((split = splits.poll()) != null) {
            pendingSplits.add(split);
        }

        // 提交 pendingSplits 到线程池
        Iterator<PostgresCopySourceSplit> it = pendingSplits.iterator();
        while (it.hasNext()) {
            PostgresCopySourceSplit s = it.next();
            runningSplits.add(s);
            it.remove();
            executor.submit(
                    () -> {
                        try {
                            readSplit(s, output);
                        } catch (Exception e) {
                            log.error("Error processing split: {}", s.splitId(), e);
                            throw new RuntimeException(e);
                        } finally {
                            runningSplits.remove(s);
                            if (pendingSplits.isEmpty()
                                    && runningSplits.isEmpty()
                                    && Boundedness.BOUNDED.equals(ctx.getBoundedness())) {
                                ctx.signalNoMoreElement();
                            }
                        }
                    });
        }

        //        if (pendingSplits.isEmpty() && runningSplits.isEmpty() && splits.isEmpty()) {
        //            globalStats.outputGlobalStatistics();
        //        }

        // 如果没有正在运行的 split，则稍作等待
        //        if (runningSplits.isEmpty()) {
        //            Thread.sleep(100);
        //        }
    }

    public void handleNoMoreSplits() {
        if (splits.isEmpty() && Boundedness.BOUNDED.equals(ctx.getBoundedness())) {
            ctx.signalNoMoreElement();
        }
    }

    @Override
    public void handleSourceEvent(SourceEvent sourceEvent) {
        SourceReader.super.handleSourceEvent(sourceEvent);
    }

    @Override
    public void addSplits(List<PostgresCopySourceSplit> splits) {
        this.splits.addAll(splits);
    }

    private void readSplit(PostgresCopySourceSplit split, Collector<SeaTunnelRow> output) {
        TaskStatistics taskStats = new TaskStatistics(split.splitId());
        long stageStartTime = System.currentTimeMillis();

        final String url = conf.get(PostgresCopyOptions.JDBC_URL);
        final String user = conf.get(PostgresCopyOptions.USERNAME);
        final String pwd = conf.get(PostgresCopyOptions.PASSWORD);
        final boolean binary = conf.get(PostgresCopyOptions.BINARY);

        final String delimiter = conf.get(PostgresCopyOptions.DELIMITER);
        final String nullAs = conf.get(PostgresCopyOptions.NULL_AS);
        final String quote = conf.get(PostgresCopyOptions.QUOTE);
        final String escape = conf.get(PostgresCopyOptions.ESCAPE);
        final boolean header = conf.get(PostgresCopyOptions.CSV_HEADER);

        String copy = split.getSql();
        StringBuilder with = new StringBuilder(" WITH (");
        if (binary) {
            with.append("FORMAT BINARY");
        } else {
            with.append("FORMAT CSV");
            if (header) with.append(", HEADER true");
            if (delimiter != null) with.append(", DELIMITER '").append(delimiter).append("'");
            if (!nullAs.isEmpty()) with.append(", NULL '").append(nullAs).append("'");
            if (quote != null) with.append(", QUOTE '").append(quote).append("'");
            if (escape != null) with.append(", ESCAPE '").append(escape).append("'");
        }
        with.append(")");
        if (!copy.toUpperCase(Locale.ROOT).contains("WITH")) {
            copy = copy + with;
        }

        log.info("Task SQL for split {} : {}", split.splitId(), copy);
        try (Connection raw = DriverManager.getConnection(url, user, pwd)) {
            // 记录初始化阶段耗时
            taskStats.initTime = System.currentTimeMillis() - stageStartTime;
            stageStartTime = System.currentTimeMillis();

            PGConnection pg = raw.unwrap(PGConnection.class);
            CopyManager cm = new CopyManager((BaseConnection) pg);

            log.info("Starting COPY command execution for split: {}", split.splitId());

            if (binary) {
                log.info(
                        "Starting COPY command execution for split: {} with binary",
                        split.splitId());
                processBinaryData(cm, copy, output, taskStats, stageStartTime);
            } else {
                log.info("Starting COPY command execution for split: {} with csv", split.splitId());
                processTextData(cm, copy, header, output, taskStats, stageStartTime);
            }

            log.info("Finishing COPY command execution for split: {}", split.splitId());

            taskStats.outputTaskStatistics();
            globalStats.addTaskStatistics(taskStats);

            // 检查是否所有任务都已完成

        } catch (Exception e) {
            log.error("Error processing split: {}", split.splitId(), e);
            throw new RuntimeException(e);
        }
    }

    private void processBinaryData(
            CopyManager cm,
            String copy,
            Collector<SeaTunnelRow> output,
            TaskStatistics taskStats,
            long startTime)
            throws Exception {
        //        log.info("Start processing binary data");
        org.postgresql.copy.CopyOut co = cm.copyOut(copy);

        // 定义字段类型映射（你需要根据实际表 schema 来传）
        SeaTunnelDataType<?>[] fieldTypes = rowType.getFieldTypes();
        //        log.info("Initialized field types, total fields: {}", fieldTypes.length);
        PgBinaryCopyParser parser = new PgBinaryCopyParser(fieldTypes);

        List<SeaTunnelRow> batch = batchPool.borrowObject();
        log.info("Initialized batch processing, batch size: {}", BATCH_SIZE);
        long batchStartTime = System.currentTimeMillis();

        byte[] expected =
                new byte[] {'P', 'G', 'C', 'O', 'P', 'Y', '\n', (byte) 0xFF, '\r', '\n', 0};

        byte[] buf;
        ByteBuffer buffer = null;
        boolean headerParsed = false;
        try {
            while ((buf = co.readFromCopy()) != null) {
                //                log.debug("Read buffer chunk of size: {} bytes", buf.length);
                taskStats.bytes += buf.length;

                if (buffer == null) {
                    buffer = ByteBuffer.wrap(buf);
                    //                    log.debug("Created new buffer with size: {}", buf.length);
                } else {
                    ByteBuffer newBuf = ByteBuffer.allocate(buffer.remaining() + buf.length);
                    newBuf.put(buffer);
                    newBuf.put(buf);
                    newBuf.flip();
                    buffer = newBuf;
                    //                    log.debug("Extended buffer to size: {}",
                    // buffer.capacity());
                }
                buffer.order(ByteOrder.BIG_ENDIAN);

                // 解析头部（只解析一次）
                if (!headerParsed && buffer.remaining() >= 19) {
//                    log.info("Parsing binary format header");
                    byte[] signature = new byte[11];
                    buffer.get(signature);
                    //                    String sig = new String(signature,
                    // StandardCharsets.US_ASCII);
                    //                    if (!"PGCOPY\n\377\r\n\0".equals(sig)) {
                    //                        throw new RuntimeException("Invalid COPY BINARY
                    // signature: " + sig);
                    //                    }
                    for (int i = 0; i < expected.length; i++) {
                        if (signature[i] != expected[i]) {
                            log.error(
                                    "Invalid signature at position {}: expected={}, got={}",
                                    i,
                                    expected[i] & 0xFF,
                                    signature[i] & 0xFF);
                            throw new RuntimeException(
                                    "Invalid COPY BINARY signature, mismatch at index "
                                            + i
                                            + " expected="
                                            + (expected[i] & 0xFF)
                                            + " got="
                                            + (signature[i] & 0xFF));
                        }
                    }
                    int flags = buffer.getInt();
                    //                    log.debug("Binary format flags: {}", flags);
                    //                    buffer.getInt(); // flags
                    int headerExtLen = buffer.getInt();
                    //                    log.debug("Header extension length: {}", headerExtLen);
                    if (headerExtLen > 0) {
                        buffer.position(buffer.position() + headerExtLen);
                    }
                    headerParsed = true;
                    //                                        log.info("Successfully parsed binary
                    // format header");
                }

                //                log.info("Starting parsing binary format in rows (buffer.mark()
                // check)");

                while (buffer.remaining() >= 2) {
                    short numFields = -999;
                    try {
                        numFields = buffer.getShort();
                        //                        log.info(
                        //                                "[{}] before mark: numFields={}, pos={},
                        // limit={}, remaining={}",
                        //                                Thread.currentThread().getName(),
                        //                                numFields,
                        //                                buffer.position(),
                        //                                buffer.limit(),
                        //                                buffer.remaining());
                        // 保护性 try-catch 专门包裹 mark()，以确保任何异常都被记录
                        try {
                            buffer.mark();
                            //                            log.info(
                            //                                    "[{}] after mark (immediate):
                            // pos={}, limit={}, remaining={}, mark={}",
                            //                                    Thread.currentThread().getName(),
                            //                                    buffer.position(),
                            //                                    buffer.limit(),
                            //                                    buffer.remaining(),
                            //                                    getBufferMark(buffer));
                        } catch (Throwable tMark) {
                            log.error(
                                    "[{}] Exception during buffer.mark(): pos={}, limit={}, remaining={}",
                                    Thread.currentThread().getName(),
                                    buffer.position(),
                                    buffer.limit(),
                                    buffer.remaining(),
                                    tMark);
                            throw tMark;
                        }
                    } catch (Throwable t) {
                        // 捕获任何在 getShort() / mark() 之前抛出的异常，并打印堆栈
                        log.error(
                                "[{}] Exception reading numFields or marking buffer: pos={}, limit={}, remaining={}",
                                Thread.currentThread().getName(),
                                buffer != null ? buffer.position() : -1,
                                buffer != null ? buffer.limit() : -1,
                                buffer != null ? buffer.remaining() : -1,
                                t);
                        throw t;
                    }
                    //                    buffer.mark();
                    //                    short numFields = buffer.getShort();
                    if (numFields == -1) {
                        log.info("Reached end of data marker (-1)");
                        // trailer，结束
                        outputBatch(batch, output, taskStats);
                        return;
                    }

                    //                    log.info("Processing row with {} fields", numFields);
                    Object[] rowData = new Object[numFields];
                    boolean enough = true;

                    for (int i = 0; i < numFields; i++) {
                        if (buffer.remaining() < 4) {
                            //                            log.debug(
                            //                                    "Buffer doesn't have enough data
                            // for field length at position {}",
                            //                                    i);
                            enough = false;
                            break;
                        }
                        int len = buffer.getInt();
                        if (len == -1) {
                            //                            log.debug("Field {} is NULL", i);
                            rowData[i] = null;
                            continue;
                        }
                        if (buffer.remaining() < len) {
                            //                            log.debug(
                            //                                    "Buffer doesn't have enough data
                            // for field {} content (need {} bytes)",
                            //                                    i,
                            //                                    len);
                            enough = false;
                            break;
                        }
                        byte[] fieldBytes = new byte[len];
                        buffer.get(fieldBytes);
                        rowData[i] = parser.parseBinaryField(fieldBytes, fieldTypes[i]);
                        //                        log.debug("Successfully parsed field {} with
                        // length {}", i, len);
                    }

                    if (!enough) {
                        //                        log.debug(
                        //                                "Insufficient data in buffer, resetting
                        // position and waiting for more data");
                        buffer.reset();
                        byte[] remain = new byte[buffer.remaining()];
                        buffer.get(remain);
                        buffer = ByteBuffer.wrap(remain);
                        buffer.order(ByteOrder.BIG_ENDIAN);
                        break; // 等待更多数据拼接进来
                        //                        break;
                    }

                    SeaTunnelRow row = new SeaTunnelRow(rowData);
                    batch.add(row);
                    taskStats.rows++;

                    if (batch.size() >= BATCH_SIZE) {
                        long batchEndTime = System.currentTimeMillis();
                        taskStats.batchTime += batchEndTime - batchStartTime;
//                        log.info("Batch full ({} rows), outputting batch", batch.size());
                        outputBatch(batch, output, taskStats);
                        batch = batchPool.borrowObject();
                        batchStartTime = System.currentTimeMillis();
                    }
                }

                if (buffer != null && buffer.hasRemaining()) {
                    byte[] remain = new byte[buffer.remaining()];
                    buffer.get(remain);
                    buffer = ByteBuffer.wrap(remain);
                    buffer.order(ByteOrder.BIG_ENDIAN);
                    //                    log.debug("Preserved {} bytes for next iteration",
                    // remain.length);
                } else {
                    buffer = null;
                }
            }

            // flush 最后不足一批的
            if (!batch.isEmpty()) {
                long batchEndTime = System.currentTimeMillis();
                taskStats.batchTime += batchEndTime - batchStartTime;
                //                log.info("Processing final batch with {} rows", batch.size());
                outputBatch(batch, output, taskStats);
            }
            log.info(
                    "Completed binary data processing. Total rows: {}, Total bytes:{}",
                    taskStats.rows,
                    taskStats.bytes);
        } catch (Exception e) {
            if (e instanceof java.nio.BufferUnderflowException) {
                log.error(
                        "BufferUnderflowException caught: buffer可能未读取完整，position={}, limit={}",
                        buffer != null ? buffer.position() : -1,
                        buffer != null ? buffer.limit() : -1,
                        e);
            } else {
                log.error("Exception during binary data processing:", e);
            }
            throw e; // 保持原行为
            //            log.error("Exception during binary data processing:", e);
            //            throw e;
        } finally {
            if (!batch.isEmpty()) {
                batchPool.returnObject(batch);
                log.debug("Returned batch to pool");
            }
        }
    }

    //    private void processBinaryData(
    //            CopyManager cm,
    //            String copy,
    //            Collector<SeaTunnelRow> output,
    //            TaskStatistics taskStats,
    //            long startTime)
    //            throws Exception {
    //        List<SeaTunnelRow> batch = batchPool.borrowObject();
    //        long batchStartTime = System.currentTimeMillis();
    //        org.postgresql.copy.CopyOut co = cm.copyOut(copy);
    //        try {
    //            byte[] buf;
    //            while ((buf = co.readFromCopy()) != null) {
    //                //                taskStats.bytes += buf.length;
    //
    //                SeaTunnelRow row = parser.createRowFromData(buf, true);
    //                batch.add(row);
    //
    //                //                taskStats.rows++;
    //
    //                // 检查是否达到批处理大小
    //                if (batch.size() >= BATCH_SIZE) {
    //                    long batchEndTime = System.currentTimeMillis();
    //                    taskStats.batchTime += batchEndTime - batchStartTime;
    //
    //                    outputBatch(batch, output, taskStats);
    //                    batch = batchPool.borrowObject();
    //                    batchStartTime = System.currentTimeMillis();
    //                }
    //            }
    //
    //            // 输出剩余的批次
    //            if (!batch.isEmpty()) {
    //                long batchEndTime = System.currentTimeMillis();
    //                taskStats.batchTime += batchEndTime - batchStartTime;
    //                outputBatch(batch, output, taskStats);
    //            }
    //        } catch (Exception e) {
    //            throw new RuntimeException(e);
    //        } finally {
    //            if (!batch.isEmpty()) {
    //                batchPool.returnObject(batch);
    //            }
    //        }
    //    }

    private void processTextData(
            CopyManager cm,
            String copy,
            boolean header,
            Collector<SeaTunnelRow> output,
            TaskStatistics taskStats,
            long startTime)
            throws Exception {
        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream(pos, 1 << 20);

        List<SeaTunnelRow> batch = batchPool.borrowObject();
        long batchStartTime = System.currentTimeMillis();

        Thread copyThread =
                new Thread(
                        () -> {
                            try (OutputStream os = pos) {
                                cm.copyOut(copy, os);
                            } catch (Exception e) {
                                throw new RuntimeException("Copy operation failed", e);
                            }
                        });
        copyThread.start();

        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(pis, StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            boolean first = header;

            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }

                taskStats.bytes += line.getBytes(StandardCharsets.UTF_8).length;

                SeaTunnelRow row = parser.createRowFromData(line, false);
                batch.add(row);

                taskStats.rows++;

                // 检查是否达到批处理大小或超时
                if (batch.size() >= BATCH_SIZE) {

                    long batchEndTime = System.currentTimeMillis();
                    taskStats.batchTime += batchEndTime - batchStartTime;

                    outputBatch(batch, output, taskStats);
                    batch = batchPool.borrowObject();
                    batchStartTime = System.currentTimeMillis();
                }
            }

            // 输出剩余的批次
            if (!batch.isEmpty()) {
                long batchEndTime = System.currentTimeMillis();
                taskStats.batchTime += batchEndTime - batchStartTime;
                outputBatch(batch, output, taskStats);
            }
        } finally {
            //            if (!batch.isEmpty()) {
            //                batchPool.returnObject(batch);
            //            }
            try {
                copyThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Copy thread interrupted", e);
            }
        }
    }

    private void outputBatch(
            List<SeaTunnelRow> batch, Collector<SeaTunnelRow> output, TaskStatistics taskStats)
            throws Exception {
        if (batch.isEmpty()) {
            return;
        }

        long outputStartTime = System.currentTimeMillis();

        for (SeaTunnelRow row : batch) {
            output.collect(row);
        }

        long outputTime = System.currentTimeMillis() - outputStartTime;
        taskStats.outputTime += outputTime;
        taskStats.batches++;

        batch.clear();
        batchPool.returnObject(batch);
    }

    @Override
    public List<PostgresCopySourceSplit> snapshotState(long checkpointId) {
        return Collections.emptyList();
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {}

    @Override
    public void notifyCheckpointAborted(long checkpointId) {}

    @Override
    public void close() throws IOException {
        try {
            executor.shutdown();
            batchPool.close();
        } catch (Exception e) {
            throw new IOException("Error closing resources", e);
        }
    }

    // debug only - reflectively read Buffer.mark field
    private int getBufferMark(ByteBuffer buffer) {
        try {
            java.lang.reflect.Field f = java.nio.Buffer.class.getDeclaredField("mark");
            f.setAccessible(true);
            return f.getInt(buffer);
        } catch (Throwable e) {
            return Integer.MIN_VALUE;
        }
    }
}
