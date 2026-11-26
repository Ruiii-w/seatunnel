import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IMap 直连压测 — 模拟 CheckpointCoordinator.updateStatus() 的持续 IMap 写入
 * 
 * 用法: java -cp "lib/*:starter/*" IMapStressTest [threads] [valueSizeBytes] [intervalMs]
 *       默认: threads=50, valueSize=1048576(1MB), intervalMs=100(10次/秒/线程)
 */
public class IMapStressTest {

    private static final String IMAP_NAME = "engine_runningJobState";
    private static final AtomicLong writeCount = new AtomicLong(0);
    private static final AtomicLong errorCount = new AtomicLong(0);
    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        int valueSize = args.length > 1 ? Integer.parseInt(args[1]) : 1048576; // 1MB
        int intervalMs = args.length > 2 ? Integer.parseInt(args[2]) : 100;

        System.out.println("=== IMap Stress Test ===");
        System.out.println("IMap: " + IMAP_NAME);
        System.out.println("Threads: " + threads);
        System.out.println("Value size: " + (valueSize / 1024) + "KB");
        System.out.println("Interval: " + intervalMs + "ms");
        System.out.println("Press Ctrl+C to stop\n");

        // Connect to Hazelcast
        ClientConfig clientConfig = ClientConfig.load();
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        IMap<String, byte[]> map = client.getMap(IMAP_NAME);
        
        System.out.println("Connected to Hazelcast. Starting stress...\n");

        Random rand = new Random();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(threads);

        // Create byte[] template (1MB of random data)
        byte[] template = new byte[valueSize];
        rand.nextBytes(template);

        long startTime = System.currentTimeMillis();

        // Start writer threads
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.scheduleAtFixedRate(() -> {
                try {
                    String key = "stress-" + threadId + "-" + writeCount.incrementAndGet();
                    byte[] value = template.clone(); // each write gets its own copy
                    map.set(key, value);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    if (errorCount.get() < 5) {
                        System.err.println("Error: " + e.getMessage());
                    }
                }
            }, 0, intervalMs, TimeUnit.MILLISECONDS);
        }

        // Status printer
        executor.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            long writes = writeCount.get();
            long errors = errorCount.get();
            int mapSize = map.size();
            double rate = elapsed > 0 ? writes / (double) elapsed : 0;
            long dataMB = (writes * (long) valueSize) / (1024 * 1024);
            
            System.out.printf("t=%ds | writes=%d (%.0f/s) | mapSize=%d | data=%dMB | errors=%d%n",
                    elapsed, writes, rate, mapSize, dataMB, errors);
        }, 5, 5, TimeUnit.SECONDS);

        // Run for 10 minutes or until OOM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            executor.shutdown();
            System.out.println("\n=== Final Stats ===");
            System.out.println("Total writes: " + writeCount.get());
            System.out.println("Total errors: " + errorCount.get());
            client.shutdown();
        }));

        Thread.sleep(TimeUnit.MINUTES.toMillis(10));
        System.exit(0);
    }
}
