# SeaTunnel 2.3.8 内存问题分析报告

- 报告主题：`finishedExecutionContexts` 持有链导致的内存占用异常
- 分析对象：heap dump `seatunnel.txt` + `seatunnel-2.3.8` 源码
- 分析时间：2026-04-27

## 1. 问题现象

- heap 中 `TaskExecutionService` 是最大保留对象，`Retained Heap` 约 `58,665,480`（`41.70%`）：
  - [seatunnel.txt:L3](file:///root/wjr/seatunnel_dump_file/seatunnel.txt#L3)
- 其中核心来自 `finishedExecutionContexts`，约 `58,640,280`（`41.68%`）：
  - [seatunnel.txt:L4](file:///root/wjr/seatunnel_dump_file/seatunnel.txt#L4)
- 第二大对象是 `com.lmax.disruptor.RingBuffer`，约 `40,574,896`（`28.84%`）：
  - [seatunnel.txt:L438](file:///root/wjr/seatunnel_dump_file/seatunnel.txt#L438)
- 顶层可见多个 `DorisSinkWriter`（每个约 `0.57%`）：
  - [seatunnel.txt:L450-L456](file:///root/wjr/seatunnel_dump_file/seatunnel.txt#L450-L456)

## 2. 对象保留链

从 dump 可见，`finishedExecutionContexts` 持有的对象链路可达 Doris sink 写入核心对象：

`TaskGroupContext -> TaskGroupWithIntermediateBlockingQueue -> TransformSeaTunnelTask -> SinkFlowLifeCycle -> MultiTableSinkWriter -> DorisSinkWriter -> DorisStreamLoad -> RecordStream -> RecordBuffer -> writeQueue`

证据：
- [seatunnel.txt:L12-L19](file:///root/wjr/seatunnel_dump_file/seatunnel.txt#L12-L19)

## 3. 源码执行时序

### 3.1 任务结束时的上下文迁移

任务组完成后，`TaskExecutionService` 会将上下文从 `executionContexts` 移到 `finishedExecutionContexts`：

- [TaskExecutionService:L941-L945](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/TaskExecutionService.java#L941-L945)

### 3.2 清理入口

`finishedExecutionContexts` 的删除入口只有：

- `notifyCleanTaskGroupContext(...)`
- [TaskExecutionService:L525-L527](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/TaskExecutionService.java#L525-L527)

本地没有 TTL/定时兜底清理。

### 3.3 master 侧触发清理

清理依赖 master 远程发送 `CleanTaskGroupContextOperation`：

- [JobMaster:L725-L751](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/master/JobMaster.java#L725-L751)
- [CleanTaskGroupContextOperation:L42-L47](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/task/operation/CleanTaskGroupContextOperation.java#L42-L47)

该流程由 pipeline 收尾调用：

- [SubPlan:L306-L333](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/dag/physical/SubPlan.java#L306-L333)

## 4. 根因分析（详细）

### 4.1 清理链路前置依赖过强

`savePipelineMetricsToHistory()` 里先抓取并落库 metrics，再执行 `cleanTaskGroupContext()`：

- [JobMaster:L663-L673](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/master/JobMaster.java#L663-L673)

若 metrics 过程异常，清理动作可能无法执行。

### 4.2 异常传播导致清理中断

`getCurrJobMetrics()` 中，普通异常会抛 `SeaTunnelEngineException`：

- [JobMaster:L633-L658](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/master/JobMaster.java#L633-L658)

`cleanTaskGroupContext()` 在遍历 taskGroup 时，单点异常会抛 `SeaTunnelException` 并中断本轮：

- [JobMaster:L731-L749](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/master/JobMaster.java#L731-L749)

### 4.3 重试条件覆盖不足

`SubPlan.subPlanDone()` 使用了重试，但重试判定依赖 `ExceptionUtil.isOperationNeedRetryException`：

- [SubPlan:L306-L333](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-server/src/main/java/org/apache/seatunnel/engine/server/dag/physical/SubPlan.java#L306-L333)
- [ExceptionUtil:L149-L154](file:///root/wjr/seatunnel-2.3.8/seatunnel-engine/seatunnel-engine-common/src/main/java/org/apache/seatunnel/engine/common/utils/ExceptionUtil.java#L149-L154)

该判定仅覆盖少数异常（如 `HazelcastInstanceNotActiveException`、`OperationTimeoutException`、`InterruptedException`）。被包装成 `SeaTunnelException/SeaTunnelEngineException` 的错误通常不再命中重试条件。

`RetryUtils` 也明确：不满足 `retryCondition` 时会直接抛出：

- [RetryUtils:L49-L54](file:///root/wjr/seatunnel-2.3.8/seatunnel-common/src/main/java/org/apache/seatunnel/common/utils/RetryUtils.java#L49-L54)

## 5. 为什么会表现为 Doris 占用

- `DorisSinkWriter` 有 `close()`，会 `shutdownNow` 并关闭 stream load：
  - [DorisSinkWriter:L209-L220](file:///root/wjr/seatunnel-2.3.8/seatunnel-connectors-v2/connector-doris/src/main/java/org/apache/seatunnel/connectors/doris/sink/writer/DorisSinkWriter.java#L209-L220)
- 但只要上层 `TaskGroupContext` 未从 `finishedExecutionContexts` 清除，整条对象图依然可达，GC 无法回收。
- `RecordBuffer` 队列与 ByteBuffer 会随对象链一起被长期保活：
  - [RecordBuffer:L39-L57](file:///root/wjr/seatunnel-2.3.8/seatunnel-connectors-v2/connector-doris/src/main/java/org/apache/seatunnel/connectors/doris/sink/writer/RecordBuffer.java#L39-L57)

结论：Doris 对象是“被保留结果”，不是主根因。

## 6. 最终结论

在 SeaTunnel 2.3.8 中，主因是：

- `finishedExecutionContexts` 的回收强依赖 master 回调；
- 回调链路对异常不鲁棒（单点失败可中断清理）；
- 重试条件过窄（包装异常不重试）；
- 本地无兜底回收策略。

因此出现“任务完成但执行上下文长期残留”，最终形成高比例堆占用。

## 7. 修复建议

### P0（优先）

- 将 `cleanTaskGroupContext` 改为“单 taskGroup 失败不影响其他 taskGroup 清理”，记录失败列表并汇总告警。
- 将 metrics 存档与 context 清理解耦，保证清理逻辑进入 `finally` 或独立补偿路径。
- 在 `TaskExecutionService` 增加兜底回收（如完成时间 TTL + 定时扫描）。

### P1（增强）

- 扩展重试判定为 root-cause 级别，避免包装异常失去可重试语义。
- 增加运行指标：
  - `finishedExecutionContexts.size`
  - 最大驻留时长
  - 每分钟新增/清理数量
  - 清理失败计数

## 8. 附注

- `RingBuffer` 占比高（`28.84%`）说明可能存在吞吐背压/消费滞后，应结合业务流量和线程状态继续排查。
- 但本次内存异常的主导问题仍是 `finishedExecutionContexts` 未清理。
