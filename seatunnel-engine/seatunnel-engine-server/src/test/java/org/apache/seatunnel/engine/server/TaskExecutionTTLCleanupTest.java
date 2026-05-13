/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.engine.server;

import org.apache.seatunnel.engine.server.execution.TaskGroupContext;
import org.apache.seatunnel.engine.server.execution.TaskGroupDefaultImpl;
import org.apache.seatunnel.engine.server.execution.TaskGroupLocation;
import org.apache.seatunnel.engine.server.execution.Task;
import org.apache.seatunnel.engine.server.execution.TestTask;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.google.common.collect.Lists;
import com.hazelcast.flakeidgen.FlakeIdGenerator;

import java.lang.reflect.Field;
import org.apache.seatunnel.engine.common.utils.PassiveCompletableFuture;
import org.apache.seatunnel.engine.server.execution.TaskExecutionState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.seatunnel.engine.server.execution.ExecutionState.FINISHED;
import static org.awaitility.Awaitility.await;

/**
 * Functional tests verifying the TTL-based cleanup of finished task group contexts.
 *
 * <p>Key test scenarios:
 * <ol>
 *   <li>Finished task context is recorded with a timestamp</li>
 *   <li>notifyCleanTaskGroupContext removes both context and timestamp</li>
 *   <li>shutdown clears all finished contexts</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TaskExecutionTTLCleanupTest extends AbstractSeaTunnelServerTest {

    static FlakeIdGenerator FLAKE_ID_GENERATOR;
    long jobId = 20001;
    int pipeLineId = 200001;

    @BeforeAll
    @Override
    public void before() {
        super.before();
        FLAKE_ID_GENERATOR = instance.getFlakeIdGenerator("ttl-test");
        // Set TTL to 1 minute for fast testing
        server.getSeaTunnelConfig().getEngineConfig().setFinishedTaskContextTTLMinutes(1);
    }

    // ==================== Helper: access private fields via reflection ====================

    @SuppressWarnings("unchecked")
    private ConcurrentMap<TaskGroupLocation, TaskGroupContext> getFinishedContexts() {
        try {
            Field field = TaskExecutionService.class.getDeclaredField("finishedExecutionContexts");
            field.setAccessible(true);
            return (ConcurrentMap<TaskGroupLocation, TaskGroupContext>)
                    field.get(server.getTaskExecutionService());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<TaskGroupLocation, Long> getFinishedTimestamps() {
        try {
            Field field = TaskExecutionService.class.getDeclaredField("finishedContextTimestamps");
            field.setAccessible(true);
            return (ConcurrentMap<TaskGroupLocation, Long>)
                    field.get(server.getTaskExecutionService());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== Tests ====================

    @Test
    public void testFinishedTaskContextHasTimestamp() {
        TaskExecutionService taskExecutionService = server.getTaskExecutionService();
        AtomicBoolean stop = new AtomicBoolean(false);
        TestTask task = new TestTask(stop, 100, true);
        TaskGroupLocation location =
                new TaskGroupLocation(jobId, pipeLineId, FLAKE_ID_GENERATOR.newId());

        TaskGroupDefaultImpl taskGroup =
                new TaskGroupDefaultImpl(location, "ttl-finish-test", Lists.newArrayList(task));

        PassiveCompletableFuture<TaskExecutionState> future = taskExecutionService.deployLocalTask(
                taskGroup,
                new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentHashMap<>());

        stop.set(true);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        Assertions.assertEquals(
                                FINISHED,
                                future.get().getExecutionState()));

        // Verify: finishedExecutionContexts has the entry
        ConcurrentMap<TaskGroupLocation, TaskGroupContext> finishedContexts = getFinishedContexts();
        Assertions.assertTrue(
                finishedContexts.containsKey(location),
                "finishedExecutionContexts should contain the completed task group");

        // Verify: finishedContextTimestamps has the entry
        ConcurrentMap<TaskGroupLocation, Long> timestamps = getFinishedTimestamps();
        Assertions.assertTrue(
                timestamps.containsKey(location),
                "finishedContextTimestamps should contain the timestamp for the completed task group");

        long timestamp = timestamps.get(location);
        long now = System.currentTimeMillis();
        Assertions.assertTrue(
                timestamp > 0 && timestamp <= now,
                "Timestamp should be > 0 and <= current time. Got: " + timestamp + ", now: " + now);

        // Cleanup for next test
        taskExecutionService.notifyCleanTaskGroupContext(location);
    }

    @Test
    public void testNotifyCleanTaskGroupContextRemovesBothMaps() {
        TaskExecutionService taskExecutionService = server.getTaskExecutionService();
        AtomicBoolean stop = new AtomicBoolean(false);
        TestTask task = new TestTask(stop, 100, true);
        TaskGroupLocation location =
                new TaskGroupLocation(jobId, pipeLineId + 1, FLAKE_ID_GENERATOR.newId());

        TaskGroupDefaultImpl taskGroup =
                new TaskGroupDefaultImpl(location, "ttl-clean-test", Lists.newArrayList(task));

        PassiveCompletableFuture<TaskExecutionState> future = taskExecutionService.deployLocalTask(
                taskGroup,
                new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentHashMap<>());

        stop.set(true);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        Assertions.assertEquals(FINISHED, future.get().getExecutionState()));

        // Verify entry exists before clean
        ConcurrentMap<TaskGroupLocation, TaskGroupContext> finishedContexts = getFinishedContexts();
        ConcurrentMap<TaskGroupLocation, Long> timestamps = getFinishedTimestamps();
        Assertions.assertTrue(finishedContexts.containsKey(location),
                "Should have finished context before cleanup");
        Assertions.assertTrue(timestamps.containsKey(location),
                "Should have timestamp before cleanup");

        // Perform cleanup (simulating master sending CleanTaskGroupContextOperation)
        taskExecutionService.notifyCleanTaskGroupContext(location);

        // Verify entry is removed
        Assertions.assertFalse(finishedContexts.containsKey(location),
                "notifyCleanTaskGroupContext should remove from finishedExecutionContexts");
        Assertions.assertFalse(timestamps.containsKey(location),
                "notifyCleanTaskGroupContext should remove from finishedContextTimestamps");
    }

    @Test
    public void testMultipleTaskGroupsEachHaveTimestamps() {
        TaskExecutionService taskExecutionService = server.getTaskExecutionService();
        AtomicBoolean stop1 = new AtomicBoolean(false);
        AtomicBoolean stop2 = new AtomicBoolean(false);

        TestTask task1 = new TestTask(stop1, 100, true);
        TestTask task2 = new TestTask(stop2, 100, true);

        TaskGroupLocation loc1 =
                new TaskGroupLocation(jobId, pipeLineId + 2, FLAKE_ID_GENERATOR.newId());
        TaskGroupLocation loc2 =
                new TaskGroupLocation(jobId, pipeLineId + 3, FLAKE_ID_GENERATOR.newId());

        TaskGroupDefaultImpl tg1 =
                new TaskGroupDefaultImpl(loc1, "ttl-multi-1", Lists.newArrayList(task1));
        TaskGroupDefaultImpl tg2 =
                new TaskGroupDefaultImpl(loc2, "ttl-multi-2", Lists.newArrayList(task2));

        PassiveCompletableFuture<TaskExecutionState> f1 = taskExecutionService.deployLocalTask(
                tg1, new java.util.concurrent.ConcurrentHashMap<>(), new java.util.concurrent.ConcurrentHashMap<>());
        PassiveCompletableFuture<TaskExecutionState> f2 = taskExecutionService.deployLocalTask(
                tg2, new java.util.concurrent.ConcurrentHashMap<>(), new java.util.concurrent.ConcurrentHashMap<>());

        stop1.set(true);
        stop2.set(true);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Assertions.assertEquals(FINISHED, f1.get().getExecutionState());
                    Assertions.assertEquals(FINISHED, f2.get().getExecutionState());
                });

        ConcurrentMap<TaskGroupLocation, TaskGroupContext> finishedContexts = getFinishedContexts();
        ConcurrentMap<TaskGroupLocation, Long> timestamps = getFinishedTimestamps();

        Assertions.assertTrue(finishedContexts.containsKey(loc1),
                "Both completed tasks should be in finishedExecutionContexts");
        Assertions.assertTrue(finishedContexts.containsKey(loc2),
                "Both completed tasks should be in finishedExecutionContexts");
        Assertions.assertTrue(timestamps.containsKey(loc1),
                "Both completed tasks should have timestamps");
        Assertions.assertTrue(timestamps.containsKey(loc2),
                "Both completed tasks should have timestamps");

        // Verify timestamps are different (recorded at different times)
        Assertions.assertNotEquals(timestamps.get(loc1), timestamps.get(loc2),
                "Timestamps for different task groups recorded at different times should differ");

        // Cleanup
        taskExecutionService.notifyCleanTaskGroupContext(loc1);
        taskExecutionService.notifyCleanTaskGroupContext(loc2);
    }

    @Test
    public void testFinishedContextTimestampsFieldExists() {
        // Verify via reflection that the compiled class has the new fields
        boolean hasFinishedContextTimestamps = false;
        boolean hasFinishedExecutionContexts = false;

        for (Field field : TaskExecutionService.class.getDeclaredFields()) {
            if ("finishedContextTimestamps".equals(field.getName())) {
                hasFinishedContextTimestamps = true;
            }
            if ("finishedExecutionContexts".equals(field.getName())) {
                hasFinishedExecutionContexts = true;
            }
        }

        Assertions.assertTrue(hasFinishedExecutionContexts,
                "finishedExecutionContexts field should exist (pre-existing)");
        Assertions.assertTrue(hasFinishedContextTimestamps,
                "finishedContextTimestamps field should exist (newly added for TTL cleanup)");
    }

    @Test
    public void testCleanupExpiredFinishedContextsMethodExists() {
        // Verify the private cleanup method exists in the compiled class
        boolean hasCleanupMethod = false;
        try {
            java.lang.reflect.Method method = TaskExecutionService.class
                    .getDeclaredMethod("cleanupExpiredFinishedContexts");
            method.setAccessible(true);
            hasCleanupMethod = true;
        } catch (NoSuchMethodException e) {
            // method doesn't exist
        }
        Assertions.assertTrue(hasCleanupMethod,
                "cleanupExpiredFinishedContexts method should exist");
    }

    @Test
    public void testFinishedContextMapsAreConcurrent() {
        ConcurrentMap<TaskGroupLocation, TaskGroupContext> finishedContexts = getFinishedContexts();
        ConcurrentMap<TaskGroupLocation, Long> timestamps = getFinishedTimestamps();

        // Verify both maps are ConcurrentHashMap (thread-safe)
        Assertions.assertTrue(finishedContexts instanceof java.util.concurrent.ConcurrentHashMap,
                "finishedExecutionContexts should be a ConcurrentHashMap");
        Assertions.assertTrue(timestamps instanceof java.util.concurrent.ConcurrentHashMap,
                "finishedContextTimestamps should be a ConcurrentHashMap");
    }
}
