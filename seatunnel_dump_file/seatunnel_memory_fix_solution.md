# SeaTunnel 2.3.8 `finishedExecutionContexts` 内存泄漏修复方案

> 基于 heap dump `seatunnel.txt` 及 `seatunnel-2.3.8` 源码分析  
> 分析日期：2026-04-28

---

## 1. 问题现象

从 heap dump 提取的关键数据：

| 对象 | Retained Heap | 占比 |
|---|---|---|
| `TaskExecutionService` | 58,665,480 B | 41.70% |
| `finishedExecutionContexts` (ConcurrentHashMap) | 58,640,280 B | 41.68% |
| `com.lmax.disruptor.RingBuffer` | 40,574,896 B | 28.84% |

`finishedExecutionContexts` 中的保留对象链路：

```
TaskGroupContext
  → TaskGroupWithIntermediateBlockingQueue
    → TransformSeaTunnelTask
      → SinkFlowLifeCycle
        → MultiTableSinkWriter
          → DorisSinkWriter
            → DorisStreamLoad
              → RecordStream
                → RecordBuffer
                  → writeQueue (ArrayBlockingQueue, 其中包含预分配的 ByteBuffer)
```

堆顶独立出现 **7 个以上** `DorisSinkWriter` 实例（每个约 800 KB retained），说明多个已完成 pipeline 的上下文全部未被清理。

---

## 2. 根因分析

### 2.1 清理链路全景

```
JobMaster.updateTaskExecutionState()                  [TaskExecutionService 通知 master]
  → SubPlan.updatePipelineState()
    → SubPlan.subPlanDone(pipelineStatus)
      → RetryUtils.retryWithException(() -> {
            jobMaster.savePipelineMetricsToHistory();     ① 获取并落库 metrics
            jobMaster.removeMetricsContext();             ② 移除 metrics 上下文
            notifyCheckpointManagerPipelineEnd();         ③ 通知 checkpoint 结束
            jobMaster.releasePipelineResource();          ④ 释放 pipeline 资源
        })
          ↓
        savePipelineMetricsToHistory()
          → getCurrJobMetrics(taskGroupLocationSlotProfileMap)
              → 向各 worker 发送 GetTaskGroupMetricsOperation
              → 异常处理：HazelcastInstanceNotActiveException → warn 日志吞掉
              → 其他异常：throw new SeaTunnelEngineException()            ⚠
          → cleanTaskGroupContext(pipelineLocation)
              → forEach(taskGroup):
                  → 向 worker 发送 CleanTaskGroupContextOperation
                  → HazelcastInstanceNotActiveException → warn 吞掉
                  → 其他异常：throw new SeaTunnelException()              ⚠ 致命!
                              → forEach 中断，后续 taskGroup 不再清理

CleanTaskGroupContextOperation.runInternal()
  → TaskExecutionService.notifyCleanTaskGroupContext(location)
      → finishedExecutionContexts.remove(location)          唯一删除入口!
```

### 2.2 三条致命缺陷

#### 缺陷一：`finishedExecutionContexts` 无本地兜底清理

**代码位置**：`TaskExecutionService.java` L133-L134, L525-L527

```java
// 定义：无容量限制、无 TTL、无定时扫描
private final ConcurrentMap<TaskGroupLocation, TaskGroupContext> finishedExecutionContexts =
        new ConcurrentHashMap<>();

// 唯一的删除入口，只能由 master 远程触发
public void notifyCleanTaskGroupContext(TaskGroupLocation taskGroupLocation) {
    finishedExecutionContexts.remove(taskGroupLocation);
}
```

- 该 Map 没有任何本地自保护机制（无 TTL、无定时扫描、无容量上限）
- 一旦 master 清理链路断裂，已完成的任务上下文永久驻留
- TaskGroupContext 也没有 `close()` 方法，即使想主动关闭也无从下手

#### 缺陷二：Master 侧清理对异常不鲁棒

**代码位置**：`JobMaster.java` L663-L673, L725-L751

```java
// 问题 A：metrics 异常阻止清理
public void savePipelineMetricsToHistory(PipelineLocation pipelineLocation) {
    List<RawJobMetrics> currJobMetrics =
        this.getCurrJobMetrics(Collections.singletonList(pipelineLocation));  // 异常 → 中止
    // ...存储 metrics...
    this.cleanTaskGroupContext(pipelineLocation);  // 永远到不了这里
}

// 问题 B：单点失败中断全量清理
private void cleanTaskGroupContext(PipelineLocation pipelineLocation) {
    slotProfileMap.forEach((taskGroupLocation, slotProfile) -> {
        try {
            // ...发送清理操作...
        } catch (HazelcastInstanceNotActiveException e) {
            LOGGER.warning(...);  // 此异常被吞掉，继续
        } catch (Exception e) {
            throw new SeaTunnelException(e.getMessage());  // 中断全部 forEach！
        }
    });
}
```

如果 pipeline 有 10 个 taskGroup，第 3 个清理失败，后面 7 个都不会被清理。

**更严重的是：`cleanTaskGroupContext` 的异常不止中断自身，还会一直向上传播，连锁跳过 `subPlanDone()` 中后续的所有关键收尾操作。**

```java
// SubPlan.java L306-L320 (简化)
private void subPlanDone(PipelineStatus pipelineStatus) {
    try {
        RetryUtils.retryWithException(() -> {
            jobMaster.savePipelineMetricsToHistory(...);   // 内调 cleanTaskGroupContext
            //       ↑ 如果这里 throw SeaTunnelException，下面三行全部跳过！
            jobMaster.removeMetricsContext(...);            // ① IMAP metrics 残留
            notifyCheckpointManagerPipelineEnd(...);         // ② checkpoint manager 未收到结束通知
            jobMaster.releasePipelineResource(this);         // ③ slot 等资源未释放
            return null;
        }, ...);
    } catch (Exception e) {
        log.warn("...");  // 一条日志完事，什么都不做
    }
}
```

一个 taskGroup 清理时的网络超时或序列化异常，会导致：

| 被跳过的操作 | 后果 |
|---|---|
| 剩余 taskGroup 的 `CleanTaskGroupContextOperation` | **本次 dump 的直接根因**：上下文永久驻留，RecordBuffer 中的 ByteBuffer（每个约 800KB）不被释放 |
| `removeMetricsContext()` | IMAP 中 metrics 上下文残留，`runningJobMetricsIMap` 中的过期 entries 不会被清理 |
| `notifyCheckpointManagerPipelineEnd()` | Checkpoint manager 不知道 pipeline 已结束，可能影响 checkpoint 的清理与统计 |
| `releasePipelineResource()` | Slot 资源可能未及时归还，影响后续 job 调度 |

**一个 taskGroup 清理的网络抖动，通过一次 `throw`，毁掉了整个 pipeline 的全部收尾流程。** 这正是 heap dump 中同时出现 7 个以上 `DorisSinkWriter` 实例的原因——多个 pipeline 都因为不同的 taskGroup 清理异常而导致全量收尾中断。

#### 缺陷三：重试条件过窄

**代码位置**：`ExceptionUtil.java` L149-L154, `RetryUtils.java` L49-L54

```java
public static boolean isOperationNeedRetryException(@NonNull Throwable e) {
    Throwable exception = ExceptionUtils.getRootException(e);
    return exception instanceof HazelcastInstanceNotActiveException     // 节点离线
            || exception instanceof InterruptedException               // 被中断
            || exception instanceof OperationTimeoutException;         // 超时
    // SeaTunnelException、SeaTunnelEngineException 都不在列表中！
}
```

```java
// RetryUtils.java L49-L54
if (retryCondition != null && !retryCondition.canRetry(e)) {
    if (retryMaterial.shouldThrowException()) {
        throw e;  // 不满足重试条件，直接抛出
    }
}
```

`getCurrJobMetrics` 把普通 `Exception` 包装成 `SeaTunnelEngineException`，`cleanTaskGroupContext` 包装成 `SeaTunnelException`——都不命中重试条件，直接终止整个 `subPlanDone` 流程。

```java
// SubPlan.java L306-L333 结局
private void subPlanDone(PipelineStatus pipelineStatus) {
    try {
        RetryUtils.retryWithException(() -> {
            jobMaster.savePipelineMetricsToHistory(getPipelineLocation());
            // ...
        }, new RetryUtils.RetryMaterial(...));
    } catch (Exception e) {
        log.warn("The cleaning operation ... is not completed, ...");
        // 只打一条日志，finishedExecutionContexts 中的对象永远留下来
    }
}
```

### 2.3 为什么表现为 Doris 对象占用

这是**症状而非根因**：

- `DorisSinkWriter.close()` 会调 `shutdownNow` 和 `dorisStreamLoad.close()`，本身有清理能力
- 但只要 `TaskGroupContext` 还被 `finishedExecutionContexts` 引用，整条对象图就从 GC Root 可达
- `RecordBuffer` 中的 `writeQueue`（`ArrayBlockingQueue`）预分配了 `ByteBuffer`，这是堆空间的主要占用者

---

## 3. 修复方案

### 3.1 修改总览

| 序号 | 文件 | 修改内容 | 优先级 |
|---|---|---|---|
| ① | `ServerConfigOptions.java` | 新增 `FINISHED_TASK_CONTEXT_TTL_MINUTES` 配置项 | P0 |
| ② | `EngineConfig.java` | 新增对应字段和 setter | P0 |
| ③ | `TaskExecutionService.java` | **核心**：增加 TTL 兜底定时清理 | **P0** |
| ④ | `JobMaster.java` | `cleanTaskGroupContext` 单点容错 + metrics 与清理解耦 | **P0** |
| ⑤ | `ExceptionUtil.java` | 扩展重试判定，解包包装异常 | P1 |

---

### 3.2 修改详情

#### 修改①：`ServerConfigOptions.java` — 新增配置项

**文件**：
```
seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/config/server/ServerConfigOptions.java
```

**位置**：L135 的 `HISTORY_JOB_EXPIRE_MINUTES` 定义之后，插入新配置项。

**变更**：

```java
// ===== 在 L139 的 ");" 之后插入 =====

public static final Option<Integer> FINISHED_TASK_CONTEXT_TTL_MINUTES =
        Options.key("finished-task-context-ttl-minutes")
                .intType()
                .defaultValue(30)
                .withDescription(
                        "The TTL (in minutes) for finished task group contexts in "
                                + "TaskExecutionService. After this time, finished contexts "
                                + "will be cleaned up locally even if master cleanup fails. "
                                + "Set to 0 to disable TTL-based cleanup.");
```

**说明**：
- 默认值 30 分钟：正常清理链路在秒级完成，30 分钟保留期给 metrics 采集等留足安全窗口
- 用户可在 `seatunnel.yaml` 中通过 `engine.finished-task-context-ttl-minutes` 调整
- 设为 0 则禁用此特性，完全保持旧行为

**完整上下文**（修改后 L135-L149）：

```java
    public static final Option<Integer> HISTORY_JOB_EXPIRE_MINUTES =
            Options.key("history-job-expire-minutes")
                    .intType()
                    .defaultValue(1440)
                    .withDescription("The expire time of history jobs.time unit minute");

    public static final Option<Integer> FINISHED_TASK_CONTEXT_TTL_MINUTES =
            Options.key("finished-task-context-ttl-minutes")
                    .intType()
                    .defaultValue(30)
                    .withDescription(
                            "The TTL (in minutes) for finished task group contexts in "
                                    + "TaskExecutionService. After this time, finished contexts "
                                    + "will be cleaned up locally even if master cleanup fails. "
                                    + "Set to 0 to disable TTL-based cleanup.");
```

---

#### 修改②：`EngineConfig.java` — 读取新配置

**文件**：
```
seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/config/EngineConfig.java
```

**变更点 2a**：在 L66 `historyJobExpireMinutes` 字段之后（或附近）新增字段。

```java
// ===== 在 L66 之后插入 =====

private int finishedTaskContextTTLMinutes =
        ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES.defaultValue();
```

**变更点 2b**：在 L113 `setHistoryJobExpireMinutes` 方法之后新增 setter。

```java
// ===== 在 L113 后面（类结束 `}` 之前）插入 =====

public void setFinishedTaskContextTTLMinutes(int finishedTaskContextTTLMinutes) {
    checkPositive(
            finishedTaskContextTTLMinutes,
            ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES + " must be >= 0");
    this.finishedTaskContextTTLMinutes = finishedTaskContextTTLMinutes;
}
```

> 注意：`checkPositive` 校验了值 `>= 0`，允许设为 0 来关闭 TTL 清理。

---

#### 修改③：`TaskExecutionService.java` — 核心 TTL 兜底清理

**文件**：
```
seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/TaskExecutionService.java
```

**变更点 3a**：新增字段（L134 之后）

```java
// 现有代码 L133-L134:
private final ConcurrentMap<TaskGroupLocation, TaskGroupContext> finishedExecutionContexts =
        new ConcurrentHashMap<>();

// ===== 新增：记录每个 finished context 的完成时间戳 =====
private final ConcurrentMap<TaskGroupLocation, Long> finishedContextTimestamps =
        new ConcurrentHashMap<>();
```

**变更点 3b**：修改 `taskDone` 方法（L941-L945）

```java
// 原代码 L941-L944:
if (completionLatch.decrementAndGet() == 0) {
    recycleClassLoader(taskGroupLocation);
    finishedExecutionContexts.put(
            taskGroupLocation, executionContexts.remove(taskGroupLocation));

// ===== 修改为：同步记录时间戳 =====
if (completionLatch.decrementAndGet() == 0) {
    recycleClassLoader(taskGroupLocation);
    TaskGroupContext removed = executionContexts.remove(taskGroupLocation);
    finishedExecutionContexts.put(taskGroupLocation, removed);
    finishedContextTimestamps.put(taskGroupLocation, System.currentTimeMillis());
```

**变更点 3c**：修改 `notifyCleanTaskGroupContext`（L525-L527）

```java
// 原代码:
public void notifyCleanTaskGroupContext(TaskGroupLocation taskGroupLocation) {
    finishedExecutionContexts.remove(taskGroupLocation);
}

// ===== 修改为：同步清理时间戳 =====
public void notifyCleanTaskGroupContext(TaskGroupLocation taskGroupLocation) {
    finishedExecutionContexts.remove(taskGroupLocation);
    finishedContextTimestamps.remove(taskGroupLocation);
}
```

**变更点 3d**：新增 TTL 扫描方法（插入在 `notifyCleanTaskGroupContext` 方法之后）

```java
// ===== 在 L527 之后插入新方法 =====

/**
 * Periodically scan {@link #finishedExecutionContexts} and remove entries
 * that have exceeded the configured TTL. This is a safeguard for cases where
 * the master-side cleanup chain fails (e.g., network issues, exceptions
 * breaking the forEach loop in {@code JobMaster.cleanTaskGroupContext}).
 *
 * <p>This runs on {@link #scheduledExecutorService} at a fixed rate of 1 minute.
 */
private void cleanupExpiredFinishedContexts() {
    if (!isRunning) {
        return;
    }
    int ttlMinutes = seaTunnelConfig.getEngineConfig().getFinishedTaskContextTTLMinutes();
    if (ttlMinutes <= 0) {
        return;  // TTL-based cleanup is disabled
    }
    long ttlMillis = TimeUnit.MINUTES.toMillis(ttlMinutes);
    long now = System.currentTimeMillis();
    List<TaskGroupLocation> expired = new ArrayList<>();

    finishedContextTimestamps.forEach((location, timestamp) -> {
        if (now - timestamp > ttlMillis) {
            expired.add(location);
        }
    });

    for (TaskGroupLocation location : expired) {
        TaskGroupContext removed = finishedExecutionContexts.remove(location);
        Long timestamp = finishedContextTimestamps.remove(location);
        if (removed != null && timestamp != null) {
            long ageMinutes = TimeUnit.MILLISECONDS.toMinutes(now - timestamp);
            logger.warning(
                    String.format(
                            "TTL expired: forcibly cleaned finished task group context %s "
                                    + "(age: %d minutes, TTL: %d minutes, jobId: %d, pipelineId: %d). "
                                    + "This indicates master-side cleanup chain failure.",
                            location,
                            ageMinutes,
                            ttlMinutes,
                            location.getJobId(),
                            location.getPipelineId()));
        }
    }
}
```

**变更点 3e**：在构造函数中注册定时扫描任务（L166-L170 之后）

```java
// 原代码 L166-L170:
scheduledExecutorService.scheduleAtFixedRate(
        this::updateMetricsContextInImap,
        0,
        seaTunnelConfig.getEngineConfig().getJobMetricsBackupInterval(),
        TimeUnit.SECONDS);

// ===== 在 L170 后追加 =====
// 注册 TTL 兜底清理任务：每分钟扫描一次
scheduledExecutorService.scheduleAtFixedRate(
        this::cleanupExpiredFinishedContexts,
        1,   // initial delay: 1 minute
        1,   // period: every 1 minute
        TimeUnit.MINUTES);
```

**变更点 3f**：增强 `shutdown` 方法（L182-L186）

```java
// 原代码:
public void shutdown() {
    isRunning = false;
    executorService.shutdownNow();
    scheduledExecutorService.shutdown();
}

// ===== 修改为：最后全量清空 =====
public void shutdown() {
    isRunning = false;
    executorService.shutdownNow();
    // 最后兜底：清空所有已完成的任务上下文，释放堆内存
    finishedExecutionContexts.clear();
    finishedContextTimestamps.clear();
    scheduledExecutorService.shutdown();
}
```

---

#### 修改④：`JobMaster.java` — Master 侧清理鲁棒性

**文件**：
```
seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/master/JobMaster.java
```

**变更点 4a**：`savePipelineMetricsToHistory` — metrics 与清理解耦（修改 L663-L673）

```java
// 原代码 L663-L673:
public void savePipelineMetricsToHistory(PipelineLocation pipelineLocation) {
    List<RawJobMetrics> currJobMetrics =
            this.getCurrJobMetrics(Collections.singletonList(pipelineLocation));
    JobMetrics jobMetrics = JobMetricsUtil.toJobMetrics(currJobMetrics);
    long jobId = this.getJobImmutableInformation().getJobId();
    synchronized (this) {
        jobHistoryService.storeFinishedPipelineMetrics(jobId, jobMetrics);
    }
    // Clean TaskGroupContext for TaskExecutionServer
    this.cleanTaskGroupContext(pipelineLocation);
}

// ===== 修改为：解耦，保证清理一定执行 =====
public void savePipelineMetricsToHistory(PipelineLocation pipelineLocation) {
    try {
        List<RawJobMetrics> currJobMetrics =
                this.getCurrJobMetrics(Collections.singletonList(pipelineLocation));
        JobMetrics jobMetrics = JobMetricsUtil.toJobMetrics(currJobMetrics);
        long jobId = this.getJobImmutableInformation().getJobId();
        synchronized (this) {
            jobHistoryService.storeFinishedPipelineMetrics(jobId, jobMetrics);
        }
    } catch (Exception e) {
        LOGGER.warning(
                String.format(
                        "Failed to save pipeline metrics for %s, "
                                + "but will still attempt to clean TaskGroupContext. Error: %s",
                        pipelineLocation, ExceptionUtils.getMessage(e)));
    } finally {
        // Clean TaskGroupContext for TaskExecutionServer
        // Always execute regardless of metrics success/failure
        this.cleanTaskGroupContext(pipelineLocation);
    }
}
```

**变更点 4b**：`cleanTaskGroupContext` — 单 taskGroup 失败不影响其他（修改 L725-L751）

```java
// 原代码 L725-L751:
private void cleanTaskGroupContext(PipelineLocation pipelineLocation) {
    Map<TaskGroupLocation, SlotProfile> slotProfileMap =
            ownedSlotProfilesIMap.get(pipelineLocation);
    if (slotProfileMap == null) {
        return;
    }
    slotProfileMap.forEach(
            (taskGroupLocation, slotProfile) -> {
                try {
                    if (nodeEngine.getClusterService().getMember(slotProfile.getWorker())
                            != null) {
                        NodeEngineUtil.sendOperationToMemberNode(
                                        nodeEngine,
                                        new CleanTaskGroupContextOperation(taskGroupLocation),
                                        slotProfile.getWorker())
                                .get();
                    }
                } catch (HazelcastInstanceNotActiveException e) {
                    LOGGER.warning(
                            String.format(
                                    "%s clean TaskGroupContext with exception: %s.",
                                    taskGroupLocation, ExceptionUtils.getMessage(e)));
                } catch (Exception e) {
                    throw new SeaTunnelException(e.getMessage());
                }
            });
}

// ===== 修改为：单点失败不中断，收集失败列表统一告警 =====
private void cleanTaskGroupContext(PipelineLocation pipelineLocation) {
    Map<TaskGroupLocation, SlotProfile> slotProfileMap =
            ownedSlotProfilesIMap.get(pipelineLocation);
    if (slotProfileMap == null) {
        return;
    }
    List<TaskGroupLocation> failedTaskGroups = new ArrayList<>();

    slotProfileMap.forEach(
            (taskGroupLocation, slotProfile) -> {
                try {
                    if (nodeEngine.getClusterService().getMember(slotProfile.getWorker())
                            != null) {
                        NodeEngineUtil.sendOperationToMemberNode(
                                        nodeEngine,
                                        new CleanTaskGroupContextOperation(taskGroupLocation),
                                        slotProfile.getWorker())
                                .get();
                    }
                } catch (HazelcastInstanceNotActiveException e) {
                    LOGGER.warning(
                            String.format(
                                    "%s clean TaskGroupContext with exception: %s.",
                                    taskGroupLocation, ExceptionUtils.getMessage(e)));
                } catch (Exception e) {
                    // 记录失败但继续处理下一个 taskGroup，不再抛出中断 forEach
                    LOGGER.warning(
                            String.format(
                                    "%s clean TaskGroupContext failed: %s. "
                                            + "Will be handled by local TTL cleanup on worker.",
                                    taskGroupLocation, ExceptionUtils.getMessage(e)));
                    failedTaskGroups.add(taskGroupLocation);
                }
            });

    if (!failedTaskGroups.isEmpty()) {
        LOGGER.warning(
                String.format(
                        "Pipeline %s: %d/%d taskGroups failed to clean via master. "
                                + "They will be cleaned by local TTL (configured as %d min) "
                                + "on worker nodes. Failed taskGroups: %s",
                        pipelineLocation,
                        failedTaskGroups.size(),
                        slotProfileMap.size(),
                        engineConfig.getFinishedTaskContextTTLMinutes(),
                        failedTaskGroups));
    }
}
```

**为什么 `warn + continue` 是安全的**：

`CleanTaskGroupContextOperation.runInternal()` 在 worker 侧的**全部逻辑**仅做内存释放：

```java
// CleanTaskGroupContextOperation.java L42-L46
public void runInternal() {
    SeaTunnelServer service = getService();
    service.getTaskExecutionService().notifyCleanTaskGroupContext(taskGroupLocation);
}

// TaskExecutionService.java L525-L527
public void notifyCleanTaskGroupContext(TaskGroupLocation taskGroupLocation) {
    finishedExecutionContexts.remove(taskGroupLocation);  // 只移除引用，无业务逻辑
}
```

这个操作**没有业务状态变更、没有数据写入、没有幂等性要求**。Task group 的工作在此之前已经全部完成。清理的本质是释放堆内存引用，让 GC 可以回收对象图。

对比原代码 `throw` 的行为：

| | 原代码（throw） | 修改后（warn + continue） |
|---|---|---|
| 失败的 taskGroup | 不清理 **+** 更糟：后续所有 taskGroup 也不清理 | 推迟到 worker TTL 兜底（最多 30 分钟） |
| 其余 taskGroup | 全部被 forEach 中断丢弃 | 正常即时清理 |
| `removeMetricsContext()` | 被跳过 | 正常执行 |
| `notifyCheckpointManagerPipelineEnd()` | 被跳过 | 正常执行 |
| `releasePipelineResource()` | 被跳过 | 正常执行 |

原代码的 `throw` 是把一个 taskGroup 的清理异常**放大**成了整个 pipeline 收尾流程的灾难性失败。修改为 `warn + continue` 不仅安全，而且修复了原逻辑的连锁破坏。

> **已验证**：`JobMaster` 已有 `private final EngineConfig engineConfig` 字段（L146），无需新增引用。TTL 值通过 `this.engineConfig.getFinishedTaskContextTTLMinutes()` 直接获取。

---

#### 修改⑤：`ExceptionUtil.java` — 扩展重试判定（P1 增强）

**文件**：
```
seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/utils/ExceptionUtil.java
```

**位置**：L149-L154

```java
// 原代码:
public static boolean isOperationNeedRetryException(@NonNull Throwable e) {
    Throwable exception = ExceptionUtils.getRootException(e);
    return exception instanceof HazelcastInstanceNotActiveException
            || exception instanceof InterruptedException
            || exception instanceof OperationTimeoutException;
}

// ===== 修改为：解包 SeaTunnelException / SeaTunnelEngineException，检查根因 =====
public static boolean isOperationNeedRetryException(@NonNull Throwable e) {
    Throwable exception = ExceptionUtils.getRootException(e);
    if (exception instanceof HazelcastInstanceNotActiveException
            || exception instanceof InterruptedException
            || exception instanceof OperationTimeoutException) {
        return true;
    }
    // Unwrap SeaTunnelException and SeaTunnelEngineException to
    // examine the root cause for retry eligibility. These wrapper exceptions
    // are thrown by JobMaster.getCurrJobMetrics() and cleanTaskGroupContext()
    // and were previously not retried, causing cleanup chain failures.
    if (exception instanceof SeaTunnelException
            || exception instanceof SeaTunnelEngineException) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            return isOperationNeedRetryException(cause);
        }
    }
    return false;
}
```

---

## 4. 防御层次总览

```
第一层：Master 正常清理
  ├─ savePipelineMetricsToHistory 的 finally 保证清理调用
  ├─ cleanTaskGroupContext 单点容错（记录失败继续）
  └─ RetryUtils 重试（扩展了重试判定范围）
       ↓ 网络/节点故障仍可能失败

第二层：Worker 本地 TTL 扫描（30 分钟兜底）
  └─ TaskExecutionService.cleanupExpiredFinishedContexts()
       每分钟扫描，超时即清理
       ↓ 极端情况

第三层：JVM 关闭时的全量清空
  └─ TaskExecutionService.shutdown()
       finishedExecutionContexts.clear()
```

---

## 5. 存储变动评估

### 新增数据结构开销

| 结构 | 估算 |
|---|---|
| `finishedContextTimestamps` | 每个 entry 约 48 bytes（Long + map Node），假设 100 个并发 pipeline × 平均 5 个 taskGroup = 500 entries ≈ 24 KB |
| 定时任务 | 1 个 `ScheduledFuture`，每分钟执行，CPU 开销极低（仅遍历时间戳 map） |

总内存开销远小于泄漏的 58 MB。

### 行为变化

- **正常路径**：无影响。Master 正常清理时，`notifyCleanTaskGroupContext` 同步移除时间戳，TTL 扫描不到。
- **异常路径**：Master 清理失败后，最多等待 30 分钟（可配置），Worker 本地 TTL 触发兜底清理并打 warn 日志。
- **关闭路径**：`shutdown()` 全量清空，与旧行为一致但更强。

### 不兼容变更

无。所有修改都是增量性质的：
- TTL 默认 30 分钟，用户可通过配置调整或设为 0 关闭
- Master 侧容错修改保留了原有清理逻辑，仅把 `throw` 改为 `warn + 继续`
- 重试判定扩展是向后兼容的（只增加了新条件）

---

## 6. 建议的配置项

```yaml
# seatunnel.yaml
seatunnel:
  engine:
    # 已完成的 TaskGroup 上下文在 worker 本地的最大保留时间（分钟）
    # 超过此时间的未清理上下文将被强制回收
    # 默认为 30，设为 0 表示禁用 TTL 清理
    finished-task-context-ttl-minutes: 30
```

---

## 7. 建议的监控告警

建议在 `TaskExecutionService.provideDynamicMetrics()` 中增加以下指标（可选）：

```java
// finishedExecutionContexts 当前大小
context.collect(copy.withMetric("finishedExecutionContexts.size"),
        finishedExecutionContexts.size());

// 最大驻留时间
long maxAgeMinutes = finishedContextTimestamps.isEmpty() ? 0
        : TimeUnit.MILLISECONDS.toMinutes(
                System.currentTimeMillis()
                        - Collections.min(finishedContextTimestamps.values()));
context.collect(copy.withMetric("finishedExecutionContexts.maxAgeMinutes"),
        maxAgeMinutes);
```

告警规则建议：
- `finishedExecutionContexts.size > 0` 持续时间超过 5 分钟 → warning
- `finishedExecutionContexts.maxAgeMinutes > 10` → critical（正常清理链路已断裂）

---

## 8. 附录：涉及的完整文件清单

| 文件路径 | 修改行号 | 修改类型 |
|---|---|---|
| `seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/config/server/ServerConfigOptions.java` | L139 后 | 新增配置项 |
| `seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/config/EngineConfig.java` | L66 后, L113 后 | 新增字段 + setter |
| `seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/TaskExecutionService.java` | L134 后, L525, L941, L182 | 新增字段 + TTL 扫描 + 修改 4 处 |
| `seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/master/JobMaster.java` | L663-L673, L725-L751 | 重写 2 个方法 |
| `seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/utils/ExceptionUtil.java` | L149-L154 | 扩展重试判定 |
