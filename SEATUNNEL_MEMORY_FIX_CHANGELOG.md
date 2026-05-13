# SeaTunnel 内存泄漏修复 — 变更与升级说明

> **版本**: 基于 SeaTunnel 2.3.8  
> **日期**: 2026-04-28  
> **分支**: hzbank-seatunnel  

---

## 一、问题背景

在生产环境中发现 SeaTunnel Engine 存在内存泄漏：任务完成后，`TaskExecutionService` 中已完成的任务组上下文 (`finishedExecutionContexts`) 无法被可靠清理，导致 `DorisSinkWriter`、`RecordBuffer` 等大量对象长期驻留内存，最终引发 OOM。

根本原因有三：

| 缺陷 | 位置 | 影响 |
|------|------|------|
| ① 缺少本地 TTL 清理 | `TaskExecutionService` | Worker 侧无自主清理能力，完全依赖 Master 指令 |
| ② cleanup 链脆弱 | `JobMaster.savePipelineMetricsToHistory` | metrics 采集失败 → 异常中断 → 后续 cleanup 全部跳过 |
| ③ 异常重试逻辑缺失 | `ExceptionUtil.isOperationNeedRetryException` | `SeaTunnelException`/`SeaTunnelEngineException` 包装的可重试异常（如 `OperationTimeoutException`）不被识别，cleanup 失败后无法重试 |

---

## 二、代码修改清单

### 2.1 修改文件概览

| 文件 | 模块 | 变更类型 |
|------|------|---------|
| `ServerConfigOptions.java` | seatunnel-engine-common | 新增配置项 |
| `EngineConfig.java` | seatunnel-engine-common | 新增字段 + setter/getter |
| `ExceptionUtil.java` | seatunnel-engine-common | 修改方法体 |
| `TaskExecutionService.java` | seatunnel-engine-server | 新增字段 + 方法 |
| `JobMaster.java` | seatunnel-engine-server | 重写两个方法 |

### 2.2 逐文件变更详情

---

#### 变更 ① — `ServerConfigOptions.java`

**路径**: `seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/config/server/ServerConfigOptions.java`

**新增**：在 `HISTORY_JOB_EXPIRE_MINUTES` 之后添加配置常量

```java
public static final Option<Integer> FINISHED_TASK_CONTEXT_TTL_MINUTES =
        Options.key("finished-task-context-ttl-minutes")
                .intType()
                .defaultValue(30)
                .withDescription(
                        "The TTL (in minutes) for finished task group contexts. "
                                + "Expired contexts will be forcibly cleaned by the worker's local "
                                + "scheduled task. Set to 0 to disable. Default: 30 minutes.");
```

---

#### 变更 ② — `EngineConfig.java`

**路径**: `seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/config/EngineConfig.java`

**新增**：
- 字段：`private int finishedTaskContextTTLMinutes = ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES.defaultValue();`
- Setter：`setFinishedTaskContextTTLMinutes(int)` — 允许 >= 0，负数抛出 `IllegalArgumentException`
- Getter：`getFinishedTaskContextTTLMinutes()`

```java
private int finishedTaskContextTTLMinutes =
        ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES.defaultValue();

public void setFinishedTaskContextTTLMinutes(int finishedTaskContextTTLMinutes) {
    if (finishedTaskContextTTLMinutes < 0) {
        throw new IllegalArgumentException(
                ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES + " must be >= 0");
    }
    this.finishedTaskContextTTLMinutes = finishedTaskContextTTLMinutes;
}
```

---

#### 变更 ③ — `ExceptionUtil.java`

**路径**: `seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/utils/ExceptionUtil.java`

**新增 import**：`org.apache.seatunnel.common.utils.SeaTunnelException`

**修改** `isOperationNeedRetryException()` 方法：在原有三个 retry 条件之后，新增对 `SeaTunnelException` 和 `SeaTunnelEngineException` 的 cause 解包逻辑。

```java
public static boolean isOperationNeedRetryException(@NonNull Throwable e) {
    Throwable exception = ExceptionUtils.getRootException(e);
    if (exception instanceof HazelcastInstanceNotActiveException
            || exception instanceof InterruptedException
            || exception instanceof OperationTimeoutException) {
        return true;
    }
    // +++ 新增 +++
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

> 注意：部分调用点（如 `getCurrJobMetrics` L657）使用 `SeaTunnelEngineException(String message)` 构造函数丢失了 cause，此时 `isOperationNeedRetryException` 返回 false，行为与修改前一致，不会引入误判。

---

#### 变更 ④ — `TaskExecutionService.java`

**路径**: `seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/TaskExecutionService.java`

**A. 新增字段**（L136）：

```java
private final ConcurrentMap<TaskGroupLocation, Long> finishedContextTimestamps =
        new ConcurrentHashMap<>();
```

**B. 注册定时清理任务**（`start()` 方法中，L175）：

```java
scheduledExecutorService.scheduleAtFixedRate(
        this::cleanupExpiredFinishedContexts, 1, 1, TimeUnit.MINUTES);
```

**C. 新增清理方法**（L537 之后）：

```java
private void cleanupExpiredFinishedContexts() {
    if (!isRunning) return;
    int ttlMinutes = seaTunnelConfig.getEngineConfig().getFinishedTaskContextTTLMinutes();
    if (ttlMinutes <= 0) return;
    long ttlMillis = TimeUnit.MINUTES.toMillis(ttlMinutes);
    long now = System.currentTimeMillis();
    List<TaskGroupLocation> expired = new ArrayList<>();
    finishedContextTimestamps.forEach((loc, ts) -> {
        if (now - ts > ttlMillis) expired.add(loc);
    });
    for (TaskGroupLocation loc : expired) {
        TaskGroupContext removed = finishedExecutionContexts.remove(loc);
        Long ts = finishedContextTimestamps.remove(loc);
        if (removed != null && ts != null) {
            logger.warning("TTL expired: forcibly cleaned " + loc + " (age: "
                + TimeUnit.MILLISECONDS.toMinutes(now - ts) + " min)");
        }
    }
}
```

**D. `taskDone()` 记录时间戳**（L1000）：

```java
finishedExecutionContexts.put(taskGroupLocation, removed);
finishedContextTimestamps.put(taskGroupLocation, System.currentTimeMillis());  // +++
```

**E. `notifyCleanTaskGroupContext()` 同步清理时间戳**（L537）：

```java
public void notifyCleanTaskGroupContext(TaskGroupLocation taskGroupLocation) {
    finishedExecutionContexts.remove(taskGroupLocation);
    finishedContextTimestamps.remove(taskGroupLocation);  // +++
}
```

**F. `shutdown()` 兜底清理**（L192）：

```java
public void shutdown() {
    // ...
    finishedExecutionContexts.clear();         // +++
    finishedContextTimestamps.clear();         // +++
    scheduledExecutorService.shutdown();
}
```

---

#### 变更 ⑤ — `JobMaster.java`

**路径**: `seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/master/JobMaster.java`

**A. `savePipelineMetricsToHistory()` 重构为 try-finally**：

- 原逻辑：metrics 采集 → 存储 → `cleanTaskGroupContext`。若 metrics 采集异常，整个方法终止，cleanup 被跳过。
- 新逻辑：metrics 采集在 try 块中执行，失败时仅记 warning；cleanup 在 finally 块中**总是执行**。

```java
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
        LOGGER.warning("Failed to save pipeline metrics for " + pipelineLocation
                + ", but will still attempt to clean TaskGroupContext. Error: "
                + ExceptionUtils.getMessage(e));
    } finally {
        this.cleanTaskGroupContext(pipelineLocation);
    }
}
```

**B. `cleanTaskGroupContext()` 单 taskGroup 失败不中断循环**：

- 原逻辑：forEach 中任何一个 taskGroup 清理异常 → `throw new SeaTunnelException` → 循环终止，后续 taskGroup 和 `subPlanDone()` 中后续步骤全部跳过。
- 新逻辑：单个失败仅记 warning 并收集到 `failedTaskGroups` 列表，循环继续；所有 taskGroup 处理完毕后统一输出汇总 warning。

```java
List<TaskGroupLocation> failedTaskGroups = new ArrayList<>();
slotProfileMap.forEach((taskGroupLocation, slotProfile) -> {
    try {
        // ... 原有清理逻辑 ...
    } catch (Exception e) {
        LOGGER.warning(taskGroupLocation + " clean TaskGroupContext failed: "
                + ExceptionUtils.getMessage(e) + ". Will be handled by local TTL cleanup.");
        failedTaskGroups.add(taskGroupLocation);
    }
});
if (!failedTaskGroups.isEmpty()) {
    LOGGER.warning("Pipeline " + pipelineLocation + ": "
            + failedTaskGroups.size() + "/" + slotProfileMap.size()
            + " taskGroups failed to clean via master. "
            + "They will be cleaned by local TTL on worker nodes.");
}
```

**还移除了不再需要的 import**：`org.apache.seatunnel.common.utils.SeaTunnelException`

---

## 三、升级部署清单

### 3.1 变更说明

本次升级在 `seatunnel-frame` 内涉及的文件（其余文件不动）：

```
$SEATUNNEL_HOME/                          # 例如 /home/seatunnel/
├── starter/
│   └── seatunnel-starter.jar            ← 替换（含 engine-common + engine-server 全部变更）
├── connectors/
│   └── connector-jdbc-2.3.8.jar         ← 替换（JDBC COPY 修复）
└── config/
    └── seatunnel.yaml                   ← 新增一行 TTL 配置
```

> engine 相关模块（engine-common、engine-server）的 class 文件在构建时合并打入 `seatunnel-starter.jar`，不单独生成 JAR。

### 3.2 物料

开发提供以下物料，由运维分发到集群各节点：

| 物料 | 说明 |
|------|------|
| `apache-seatunnel-2.3.8-memory-fix.tar.gz` | 完整构建包，内含更新后的 `starter/seatunnel-starter.jar` 和 `connectors/connector-jdbc-2.3.8.jar` |

### 3.3 部署步骤

以下 6 步在集群 **每个节点** 上执行。物料已放置在各节点 `/tmp/` 下。

**Step 1 — 停服**

```bash
$SEATUNNEL_HOME/bin/stop-seatunnel-cluster.sh
ps aux | grep '[s]eatunnel' | awk '{print $2}' | xargs -r kill -15
```

**Step 2 — 解压物料并备份**

```bash
mkdir -p /tmp/seatunnel-upgrade && cd /tmp/seatunnel-upgrade
tar xzf /tmp/apache-seatunnel-2.3.8-memory-fix.tar.gz

D=$(date +%Y%m%d)
cp $SEATUNNEL_HOME/starter/seatunnel-starter.jar           $SEATUNNEL_HOME/starter/seatunnel-starter.jar.bak.$D
cp $SEATUNNEL_HOME/connectors/connector-jdbc-2.3.8.jar     $SEATUNNEL_HOME/connectors/connector-jdbc-2.3.8.jar.bak.$D
cp $SEATUNNEL_HOME/config/seatunnel.yaml                   $SEATUNNEL_HOME/config/seatunnel.yaml.bak.$D
```

**Step 3 — 替换 JAR**

```bash
cp /tmp/seatunnel-upgrade/apache-seatunnel-2.3.8/starter/seatunnel-starter.jar \
   $SEATUNNEL_HOME/starter/seatunnel-starter.jar

cp /tmp/seatunnel-upgrade/apache-seatunnel-2.3.8/connectors/connector-jdbc-2.3.8.jar \
   $SEATUNNEL_HOME/connectors/connector-jdbc-2.3.8.jar
```

**Step 4 — 更新配置**

在 `$SEATUNNEL_HOME/config/seatunnel.yaml` 的 `seatunnel.engine:` 块末尾新增一行：

```yaml
    finished-task-context-ttl-minutes: 30
```

**Step 5 — 启动**

```bash
$SEATUNNEL_HOME/bin/seatunnel-cluster.sh -d
sleep 10
tail -30 $SEATUNNEL_HOME/logs/seatunnel-engine-server.log
```

**Step 6 — 验证**

```bash
# 确认进程在运行
ps aux | grep '[s]eatunnel'

# 确认新 JAR 已生效（有输出即正常）
jar tf $SEATUNNEL_HOME/starter/seatunnel-starter.jar | grep -c 'TaskExecutionService.class'
jar tf $SEATUNNEL_HOME/connectors/connector-jdbc-2.3.8.jar | grep -c 'PgCopyBinaryReader.class'

# 确认 TTL 配置已写入
grep 'finished-task-context-ttl-minutes' $SEATUNNEL_HOME/config/seatunnel.yaml
```

### 3.4 多节点顺序

按 **Worker → Master备 → Master主** 顺序逐台执行上述 6 步，每台启动完成后再操作下一台。如无需不停服，可全部停服后统一操作再全部启动。

---

## 四、新增配置项

### 4.1 配置变更 (`seatunnel.yaml`)

**操作**：编辑 `$SEATUNNEL_HOME/config/seatunnel.yaml`，在 `seatunnel.engine:` 块末尾新增一行：

```yaml
    finished-task-context-ttl-minutes: 30
```

> 该配置项默认值为 30 分钟。设为 0 可禁用。Worker 每分钟自动清理超过 TTL 的已完成任务上下文，释放内存。

### 4.2 行为说明

```
                        ┌──────────────────────┐
                        │   TaskGroup 完成       │
                        │ (taskDone 被调用)     │
                        └──────────┬───────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  记录到 finishedContext-    │
                    │  Timestamps (当前时间)      │
                    └──────────────┬──────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
    ┌─────────▼─────────┐  ┌──────▼───────┐  ┌─────────▼─────────┐
    │ Master 正常清理    │  │ Master 清理   │  │ Worker TTL 兜底   │
    │ send CleanTask-   │  │ 部分失败      │  │ cleanupExpired-   │
    │ GroupContext-     │  │              │  │ FinishedContexts  │
    │ Operation         │  │ 记 warning   │  │ (每 1 分钟)       │
    └─────────┬─────────┘  └──────┬───────┘  └─────────┬─────────┘
              │                    │                    │
              └────────────────────┼────────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  从两个 Map 中移除           │
                    │  释放 TaskGroupContext       │
                    │  内存被 GC 回收              │
                    └─────────────────────────────┘
```

正常流程：Master → Worker (立即清理)  
兜底流程：Worker TTL (延迟清理，最坏情况延迟 = TTL 分钟数)

---

## 五、升级后验证

### 5.1 深度验证（可选）

部署 Step 6 已做基本校验。如需进一步确认，执行以下命令：

```bash
# 验证 4 个关键 engine 类均在新 JAR 中（每条应有输出）
jar tf $SEATUNNEL_HOME/starter/seatunnel-starter.jar | grep -E 'EngineConfig\.class|TaskExecutionService\.class|ExceptionUtil\.class|JobMaster\.class'

# 验证 TTL 方法签名
javap -p -classpath $SEATUNNEL_HOME/starter/seatunnel-starter.jar org.apache.seatunnel.engine.common.config.EngineConfig | grep finishedTaskContext
# 预期输出：getFinishedTaskContextTTLMinutes、setFinishedTaskContextTTLMinutes
```

### 5.2 监控指标

部署后关注以下日志关键字：

| 日志内容 | 含义 |
|---------|------|
| `TTL expired: forcibly cleaned` | 发生了兜底清理，Master cleanup 链可能存在故障 |
| `Failed to save pipeline metrics` | metrics 采集失败，但 cleanup 未被阻断 |
| `failed to clean via master. They will be cleaned by local TTL` | 部分 taskGroup 清理失败，由 TTL 兜底 |

---

## 六、兼容性与回滚

### 6.1 兼容性

- **向前兼容**：新 JAR 可替换旧版直接运行，无需修改 `seatunnel.yaml`。TTL 默认 30 分钟，配置文件缺失时自动使用默认值。
- **向后兼容**：由于只新增字段和方法且不改变现有 API，已编译的其他 JAR（connector 等）不受影响。
- **connector-jdbc 兼容**：变更仅涉及 COPY 读取的空队列边界处理和 split 枚举器告警优化，不影响已有 Source/Sink 行为。
- **配置兼容**：新配置项 `finished-task-context-ttl-minutes` 使用 `Options.key()` 标准 API，旧版解析 `seatunnel.yaml` 时会自动忽略未知 key，不会报错或启动失败。

### 6.2 回滚步骤

前提：确认升级当天的日期，如 `20260428`，替换下面命令中的 `<升级日期>`。

以下 4 步在集群 **每个节点** 上执行。

**Step 1 — 停服**

```bash
$SEATUNNEL_HOME/bin/stop-seatunnel-cluster.sh
ps aux | grep '[s]eatunnel' | awk '{print $2}' | xargs -r kill -15
```

**Step 2 — 恢复备份**

```bash
cp $SEATUNNEL_HOME/starter/seatunnel-starter.jar.bak.<升级日期>    $SEATUNNEL_HOME/starter/seatunnel-starter.jar
cp $SEATUNNEL_HOME/connectors/connector-jdbc-2.3.8.jar.bak.<升级日期>  $SEATUNNEL_HOME/connectors/connector-jdbc-2.3.8.jar
sed -i '/finished-task-context-ttl-minutes/s/^/# [回滚] /' $SEATUNNEL_HOME/config/seatunnel.yaml
```

**Step 3 — 启动**

```bash
$SEATUNNEL_HOME/bin/seatunnel-cluster.sh -d
sleep 10
tail -30 $SEATUNNEL_HOME/logs/seatunnel-engine-server.log
```

**Step 4 — 验证**

```bash
# 确认进程运行
ps aux | grep '[s]eatunnel'

# 确认旧版已恢复（此命令无输出即为正常，旧版无 TTL 方法）
javap -p -classpath $SEATUNNEL_HOME/starter/seatunnel-starter.jar \
  org.apache.seatunnel.engine.common.config.EngineConfig \
  | grep finishedTaskContext

# 确认无 TTL 清理日志（回滚后不应出现）
grep -c 'TTL expired' $SEATUNNEL_HOME/logs/seatunnel-engine-server.log
```

---

## 七、附录

### 7.1 完整变更文件清单

| # | 源文件路径 | 模块 | 变更行数 |
|---|-----------|------|---------|
| 1 | `seatunnel-engine/seatunnel-engine-common/src/main/java/.../config/server/ServerConfigOptions.java` | engine-common | +10 |
| 2 | `seatunnel-engine/seatunnel-engine-common/src/main/java/.../config/EngineConfig.java` | engine-common | +11 |
| 3 | `seatunnel-engine/seatunnel-engine-common/src/main/java/.../utils/ExceptionUtil.java` | engine-common | +23 / -1 |
| 4 | `seatunnel-engine/seatunnel-engine-server/src/main/java/.../server/TaskExecutionService.java` | engine-server | +62 |
| 5 | `seatunnel-engine/seatunnel-engine-server/src/main/java/.../server/master/JobMaster.java` | engine-server | +48 / -16 |
| 6 | `seatunnel-connectors-v2/connector-jdbc/src/main/java/.../copy/PgCopyBinaryReader.java` | connector-jdbc | +12 / -1 |
| 7 | `seatunnel-connectors-v2/connector-jdbc/src/main/java/.../source/JdbcSourceSplitEnumerator.java` | connector-jdbc | 告警优化 |

**总计**: ~+166 行 / ~-18 行，净增约 148 行代码（不含 connector 行数优化）。

### 7.2 物料 → 部署 对照

| tar.gz 内路径 | 部署到 frame 的路径 |
|-------------|-------------------|
| `starter/seatunnel-starter.jar` | `$SEATUNNEL_HOME/starter/seatunnel-starter.jar` |
| `connectors/connector-jdbc-2.3.8.jar` | `$SEATUNNEL_HOME/connectors/connector-jdbc-2.3.8.jar` |

> engine-common / engine-server 不生成独立 JAR，已合并打入 `seatunnel-starter.jar`。tar.gz 内其余文件（`bin/`、`lib/`、其他 `connectors/` 等）不覆盖 frame 现有文件。
