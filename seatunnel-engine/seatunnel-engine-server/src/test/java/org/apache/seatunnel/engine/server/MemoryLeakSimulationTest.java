package org.apache.seatunnel.engine.server;

import org.apache.seatunnel.engine.common.config.ConfigProvider;
import org.apache.seatunnel.engine.common.config.EngineConfig;
import org.apache.seatunnel.engine.common.config.SeaTunnelConfig;
import org.apache.seatunnel.engine.common.config.server.CheckpointConfig;
import org.apache.seatunnel.engine.common.runtime.ExecutionMode;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.config.Config;

import lombok.extern.slf4j.Slf4j;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Memory leak simulation with production-matching conditions.
 * Runs 20 concurrent streaming jobs with 5s checkpoint interval, 50 generic threads,
 * to reproduce the IMap write pressure → RingBuffer backlog → Old Gen inflation pattern.
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MemoryLeakSimulationTest
        extends AbstractSeaTunnelServerTest<MemoryLeakSimulationTest> {

    private static final int CONCURRENT_JOB_COUNT = 15;
    private static final long CHECKPOINT_INTERVAL_MS = 5000;
    private static final int MONITOR_INTERVAL_SECONDS = 4;
    private static final int JOB_RUN_DURATION_SECONDS = 60;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private ExecutorService jobExecutor;

    @BeforeAll
    @Override
    public void before() {
        Config hazelcastConfig = Config.loadFromString(getHazelcastConfig());
        hazelcastConfig.setClusterName(
                TestUtils.getClusterName("MemLeakSim_" + getClass().getName()));
        SeaTunnelConfig seaTunnelConfig = loadSeaTunnelConfigWithCheckpoint();
        seaTunnelConfig.setHazelcastConfig(hazelcastConfig);
        seaTunnelConfig.getEngineConfig().setMode(ExecutionMode.LOCAL);

        long maxMemMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        log.info("============================================");
        log.info("  Memory Leak Simulation Test");
        log.info("  JVM MaxHeap: {} MB ({} GB)", maxMemMB, maxMemMB / 1024);
        log.info("  G1GC: {}", hasG1GC());
        log.info("  Concurrent Jobs: {}", CONCURRENT_JOB_COUNT);
        log.info("  Checkpoint Interval: {} ms", CHECKPOINT_INTERVAL_MS);
        log.info("  Run Duration: {} s", JOB_RUN_DURATION_SECONDS);
        log.info("============================================");

        instance = SeaTunnelServerStarter.createHazelcastInstance(seaTunnelConfig);
        nodeEngine = instance.node.nodeEngine;
        server = nodeEngine.getService(SeaTunnelServer.SERVICE_NAME);
        LOGGER = nodeEngine.getLogger(MemoryLeakSimulationTest.class);
        jobExecutor = Executors.newFixedThreadPool(CONCURRENT_JOB_COUNT);
    }

    @Override
    protected String getHazelcastConfig() {
        return "hazelcast:\n"
                + "  cluster-name: seatunnel\n"
                + "  network:\n"
                + "    rest-api:\n"
                + "      enabled: true\n"
                + "      endpoint-groups:\n"
                + "        CLUSTER_WRITE:\n"
                + "          enabled: true\n"
                + "        DATA:\n"
                + "          enabled: true\n"
                + "    join:\n"
                + "      tcp-ip:\n"
                + "        enabled: true\n"
                + "        member-list:\n"
                + "          - localhost\n"
                + "    port:\n"
                + "      auto-increment: true\n"
                + "      port-count: 100\n"
                + "      port: 5801\n"
                + "  properties:\n"
                + "    hazelcast.invocation.max.retry.count: 200\n"
                + "    hazelcast.tcp.join.port.try.count: 30\n"
                + "    hazelcast.invocation.retry.pause.millis: 2000\n"
                + "    hazelcast.slow.operation.detector.stacktrace.logging.enabled: true\n"
                + "    hazelcast.logging.type: log4j2\n"
                + "    hazelcast.operation.generic.thread.count: 50\n";
    }

    private SeaTunnelConfig loadSeaTunnelConfigWithCheckpoint() {
        SeaTunnelConfig config = ConfigProvider.locateAndGetSeaTunnelConfig();
        EngineConfig engineConfig = config.getEngineConfig();
        CheckpointConfig cp = engineConfig.getCheckpointConfig();
        cp.setCheckpointInterval(CHECKPOINT_INTERVAL_MS);
        cp.setCheckpointTimeout(CHECKPOINT_INTERVAL_MS * 6);
        cp.setCheckpointEnable(true);
        engineConfig.setHistoryJobExpireMinutes(1);
        return config;
    }

    private static boolean hasG1GC() {
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (g.getName().contains("G1")) return true;
        }
        return false;
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    public void testMemoryLeakSimulation() throws Exception {
        log.info("Starting simulation...");
        Thread monitorThread = startMemoryMonitor();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_JOB_COUNT; i++) {
            final long jobId = System.currentTimeMillis() + i;
            final int idx = i;
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try {
                    log.info("[J-{}] Submit streaming job {}", idx, jobId);
                    // Use streaming config with continuous data + checkpoint
                    startJob(jobId, "stress_fake_to_console.conf", false);
                    log.info("[J-{}] Job {} finished", idx, jobId);
                } catch (Exception e) {
                    log.warn("[J-{}] Job {} error: {}", idx, jobId, e.getMessage());
                }
            }, jobExecutor);
            futures.add(f);
        }

        // Let jobs run and generate memory pressure
        log.info("Running {} streaming jobs for {}s...",
                CONCURRENT_JOB_COUNT, JOB_RUN_DURATION_SECONDS);
        Thread.sleep(TimeUnit.SECONDS.toMillis(JOB_RUN_DURATION_SECONDS));

        // Cancel jobs (streaming jobs run indefinitely)
        jobExecutor.shutdownNow();
        log.info("Cancelling all jobs...");

        running.set(false);
        monitorThread.join(5000);

        // Wait for GC to settle
        log.info("Waiting for GC...");
        Thread.sleep(15000);
        System.gc();
        Thread.sleep(5000);

        printFinalMemoryReport();
        log.info("Simulation completed");
    }

    private Thread startMemoryMonitor() {
        Thread t = new Thread(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            List<GarbageCollectorMXBean> gcBeans =
                    ManagementFactory.getGarbageCollectorMXBeans();

            log.info("| Time     | HeapUsed(MB) | HeapMax(MB) | UsedPct | YGC | FGC |");

            long prevYGC = 0, prevFGC = 0;
            for (GarbageCollectorMXBean g : gcBeans) {
                long count = g.getCollectionCount();
                String name = g.getName();
                if (name.contains("Young") || name.contains("G1 Young")) prevYGC = count;
                if (name.contains("Old") || name.contains("G1 Old")
                        || name.contains("G1 Mixed")) prevFGC += count;
            }

            while (running.get()) {
                try { Thread.sleep(TimeUnit.SECONDS.toMillis(MONITOR_INTERVAL_SECONDS)); }
                catch (InterruptedException e) { break; }

                MemoryUsage heap = memBean.getHeapMemoryUsage();
                long usedMB = heap.getUsed() / 1048576;
                long maxMB = heap.getMax() / 1048576;
                String pctStr = String.format(Locale.US, "%.1f",
                        100.0 * heap.getUsed() / heap.getMax());

                long curYGC = 0, curFGC = 0;
                for (GarbageCollectorMXBean g : gcBeans) {
                    long count = g.getCollectionCount();
                    String name = g.getName();
                    if (name.contains("Young") || name.contains("G1 Young"))
                        curYGC = count - prevYGC;
                    if (name.contains("Old") || name.contains("G1 Old")
                            || name.contains("G1 Mixed"))
                        curFGC = count - prevFGC;
                }

                double pct = 100.0 * heap.getUsed() / heap.getMax();
                log.info("| {} | {} | {} | {}% | {} | {} |",
                        sdf.format(new Date()), usedMB, maxMB, pctStr, curYGC, curFGC);

                if (pct > 80) log.warn("MEMORY WARNING: {}% heap used", pctStr);
                if (pct > 90) log.error("MEMORY CRITICAL: {}% - OOM imminent!", pctStr);

                for (GarbageCollectorMXBean g : gcBeans) {
                    long count = g.getCollectionCount();
                    String name = g.getName();
                    if (name.contains("Young") || name.contains("G1 Young")) prevYGC = count;
                    if (name.contains("Old") || name.contains("G1 Old")
                            || name.contains("G1 Mixed")) prevFGC = count;
                }
            }
        });
        t.setDaemon(true);
        t.setName("mem-monitor");
        t.start();
        return t;
    }

    private void printFinalMemoryReport() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

        log.info("");
        log.info("================ FINAL MEMORY REPORT =================");
        log.info("Heap Used:      {} MB / {} MB ({}%)",
                heap.getUsed() / 1048576, heap.getMax() / 1048576,
                String.format(Locale.US, "%.1f", 100.0 * heap.getUsed() / heap.getMax()));
        log.info("Heap Committed: {} MB", heap.getCommitted() / 1048576);
        log.info("NonHeap Used:   {} MB", nonHeap.getUsed() / 1048576);
        log.info("Pending Finalization: {}", memBean.getObjectPendingFinalizationCount());
        log.info("");
        log.info("--- GC Statistics ---");
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
            log.info("  {}: {} collections, {} ms",
                    g.getName(), g.getCollectionCount(), g.getCollectionTime());
        }
        log.info("=====================================================");
    }

    @AfterAll
    @Override
    public void after() {
        running.set(false);
        if (jobExecutor != null) {
            jobExecutor.shutdownNow();
            try { jobExecutor.awaitTermination(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        try {
            if (server != null) server.shutdown(true);
            if (instance != null) instance.shutdown();
        } catch (Exception e) {
            log.warn("Shutdown: {}", e.getMessage());
        }
    }
}
