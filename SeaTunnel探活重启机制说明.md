# SeaTunnel 2.3.8 探活与重启机制说明

> **适用版本**: SeaTunnel 2.3.8（hzbank-seatunnel 分支）
> **适用环境**: 内网 CLUSTER 分离部署模式（Master 独立节点 + Worker 独立节点）
> **REST 端口**: 5801（与 Hazelcast 成员端口同端口，由引擎内嵌 TextCommandService 提供）
> **编写日期**: 2026-09-01

---

## 一、探活接口

### 1.1 接口清单

| 接口 | 方法 | 用途 | 能否用于探活 |
|------|:----:|------|:----:|
| `/hazelcast/rest/maps/overview` | GET | 集群概览（节点数/槽位/作业数） | ✅ **主探活接口** |
| `/hazelcast/rest/maps/system-monitoring-information` | GET | 每个存活节点的健康指标（含 `isMaster`/`host`/`port`） | ✅ **节点身份识别 + 假死检测** |
| `/hazelcast/rest/maps/running-jobs` | GET | 正在运行的作业列表 | ❌ 无任务时返回空数组 `[]`，**不能**用于探活 |
| `/hazelcast/rest/maps/submit-job` | POST | 提交作业 | ✅ 辅助验证 master 可用性 |

### 1.2 overview 返回字段（OverviewInfo）

```json
{
  "projectVersion": "2.3.8",
  "gitCommitAbbrev": "",
  "totalSlot": 10,
  "unassignedSlot": 8,
  "runningJobs": 0,
  "finishedJobs": 3,
  "failedJobs": 0,
  "cancelledJobs": 0,
  "workers": 2
}
```

关键字段：`workers`（存活 worker 数）、`totalSlot`（总槽位）、`unassignedSlot`（空闲槽位）、`runningJobs`。

> 注意：调用 worker 节点的 overview 时，请求会转发到 master 节点执行（`RestHttpGetCommandProcessor.java:191` 的 `sendOperationToMasterNode().join()`）。**master 不可用时，worker 上的 overview 也会失败**——这本身即是 master 故障信号。

### 1.3 system-monitoring-information 返回字段（每节点一个对象）

```json
[
  {
    "isMaster": "true",
    "host": "168.188.5.88",
    "port": "5801",
    "processors": "64",
    "physical.memory.total": "503.5G",
    "physical.memory.free": "109.0G",
    "heap.memory.used": "18.3G",
    "heap.memory.used/max": "30.05%",
    "load.process": "10.35%",
    "load.system": "13.88%",
    "thread.count": "450",
    "minor.gc.count": "3400",
    "major.gc.count": "12",
    "operations.pending.invocations.count": "0",
    "connection.active.count": "15",
    "clientEndpoint.count": "3"
  }
]
```

关键字段：`isMaster`（是否 Hazelcast 主节点）、`host`、`port`（节点身份）、`heap.memory.used/max`、`load.process`、`operations.pending.invocations.count`（操作积压，假死征兆）。

### 1.4 探活示例

```bash
# 探活集群（推荐）
curl -s --connect-timeout 5 --max-time 10 http://168.188.5.88:5801/hazelcast/rest/maps/overview

# 获取各节点健康指标与身份
curl -s --connect-timeout 5 --max-time 10 http://168.188.5.88:5801/hazelcast/rest/maps/system-monitoring-information
```

> 必须设置 `--connect-timeout` 与 `--max-time`，防止假死节点导致探活线程长时间挂起。

---

## 二、节点进程挂掉后的探活表现与处理

### 2.1 Worker 进程挂掉

**表现**：

| 探活方式 | 表现 |
|---------|------|
| 探活该 worker 自身 5801 | 连接拒绝/超时，HTTP 不可达 |
| 探活 master 的 overview | **正常**，但 `workers` 数减少、`totalSlot` 减少、`unassignedSlot` 不变 |
| `system-monitoring-information` | 该 worker 的 `host:port` 从返回列表中缺失（心跳判死后约 20-30s） |
| `running-jobs` | 该 worker 上运行的任务变为 FAILED |

**处理**：只重启该 worker 进程。master 及其他 worker 无需任何操作。重启后 worker 自动加入集群（TCP/IP join 配置的成员列表），槽位自动恢复。

### 2.2 Master 进程挂掉

**表现**（与 worker 挂有本质区别）：

| 探活方式 | 表现 |
|---------|------|
| 探活 master 自身 5801 | 连接拒绝/超时 |
| 探活任意 worker 的 overview | **失败/超时**——overview 需转发到 master 执行（`RestHttpGetCommandProcessor.java:191`） |
| `system-monitoring-information` | `isMaster=true` 的节点缺失；若集群只剩 worker，成员列表里全是 lite member |
| `submit-job` | 提交失败/超时 |
| `running-jobs` | 运行中任务全部失败/停止 |

**根因**：worker 是 Hazelcast **lite member**（`SeaTunnelServerStarter.java:106` `setLiteMember(true)`），lite member 不能成为 Hazelcast master。master 挂掉后集群失去主节点，任务调度、作业提交、overview 转发全部不可用——**即使 worker 进程全部存活，集群也是不可用状态**。

**处理**：
1. 只重启 master 进程，**不要重启 worker**——worker 通过 `LiteNodeDropOutTcpIpJoiner` 自动重新加入集群（mastership claim 超时 10s）；
2. master 加入集群后（成员列表恢复，约 10s），通过 `running-jobs` 检查任务状态：此前运行中的任务已失败，需要重新提交；
3. 若配置了作业恢复机制，按恢复流程处理。

### 2.3 进程刚挂的短暂不一致窗口（约 20-30 秒）

进程挂掉后，Hazelcast 依赖心跳机制判死（默认心跳 5s，多次未响应后触发成员移除事件，实际约 20-30s，见 `AbstractResourceManager.java:136` `memberRemoved`）。**进程死后的前 20-30s 内**：

- 5801 端口已不可达（HTTP 探活立即失败）；
- 但 `system-monitoring-information` 的成员列表里还能看到该节点（还未被移除）。

**处理**：探活以"节点自身 HTTP 探活失败"为主判据，立即触发重启，**不要等待成员列表剔除**。成员列表比对仅作为兜底确认（防"端口通但接口不通"的假活，见第三章）。

---

## 三、进程没挂但节点假死的探活表现与处理

### 3.1 场景

JVM 进程未退出，但已无法正常服务：长时间 Full GC、死锁、线程池/队列耗尽、CPU 打满等。

### 3.2 探活表现

| 探活方式 | 表现 |
|---------|------|
| TCP 端口（`nc`/`telnet` 5801） | **可能仍然连通**——socket 层活着，误判风险极高 |
| HTTP 探活（curl 带超时） | 请求超时/无响应——**唯一可靠的直接判据** |
| 从其他节点调 `system-monitoring-information` | 发给假死节点的 `GetClusterHealthMetricsOperation` 超时，该实现无超时兜底（`RestHttpGetCommandProcessor.java:243-251`），会导致：① 该节点从结果中缺失；② 整个接口直接返回 500——这两种都是假死信号 |
| 成员列表 | 若 Hazelcast 心跳线程仍能运行，节点**长期留在成员列表**，但 REST 无响应——典型的"节点活着但 REST 挂了" |

### 3.3 判定与处理

1. **判定**：对节点自身 HTTP 探活，**连续 N 次失败**（建议 3 次 × 超时 10s）即判定不可用。API 层面无法区分"真死"与"假死"，也不需要区分，处理方式相同；
2. **处理**：重启该节点进程（重启是唯一解法）；
3. **防误杀**：连续失败次数阈值可避免单次 GC 抖动（一次 STW 可能达数秒~数十秒）导致误判重启；
4. **辅助信号**：`system-monitoring-information` 结果中节点缺失或接口 500，可作为提前预警（在成员列表剔除前就能发现）。

---

## 四、探活判定逻辑汇总

### 4.1 判定规则矩阵

| 场景 | 该节点 HTTP 探活 | overview（master 视角） | 成员列表比对 | 判定 | 处理 |
|------|:----:|:----:|:----:|:----:|:----:|
| 正常 | ✅ 200 | ✅ 正常 | 完整 | 健康 | 无需操作 |
| worker 进程挂 | ❌ 不可达 | ✅ 正常（workers 减少） | 缺 worker IP | worker 挂 | 重启该 worker |
| master 进程挂 | ❌ 不可达 | ❌ 失败/超时 | 缺 `isMaster=true` 节点 | master 挂 | 重启 master（worker 自动重连） |
| 节点假死 | ❌ 连续超时 | 视假死对象而定 | 可能完整（心跳线程存活） | 假死 | 重启该节点进程 |
| 网络分区（监控端与集群断开） | 全部节点 ❌ | ❌ | 无法获取 | 网络问题 | **不触发任何重启** |

### 4.2 探活流程

```
┌─────────────────────────────────────────────────┐
│ 1. 对每个节点（master 清单 / worker 清单分开）     │
│    自身探活: curl 该节点 5801/overview           │
│    （connect-timeout 5s, max-time 10s）          │
└──────────────────────┬──────────────────────────┘
                       ▼
        ┌──────────────────────────────┐
        │ 是否【全部节点】同时失败？      │──是──▶ 判定网络问题，不触发重启，告警
        └──────────────────────────────┘
                       │ 否
                       ▼
        ┌──────────────────────────────┐
        │ 单节点连续 3 次 HTTP 失败？    │──是──▶ 重启该节点进程
        └──────────────────────────────┘
                       │ 否（单次失败，可能抖动）
                       ▼
        ┌──────────────────────────────┐
        │ 交叉验证: 从存活节点调         │
        │ system-monitoring-information │
        │ 比对静态清单（IP:port）        │
        └──────────────┬───────────────┘
                       ▼
        ┌──────────────────────────────┐
        │ 成员缺失 / isMaster 节点消失？ │──是──▶ 确认节点掉线，重启对应节点
        └──────────────────────────────┘      （master 挂只重启 master，
                       │ 否                      worker 自动重连）
                       ▼
                  节点健康，继续下一轮
```

### 4.3 核心原则

1. **单节点探活看 HTTP 状态**（带超时），不看 TCP 端口连通；
2. **集群健康看成员列表比对**（`system-monitoring-information` vs 静态节点清单），缺失即判掉线；
3. **master 探活额外看 overview 转发**是否成功（worker 上调 overview 失败 = master 不可用）；
4. **防误杀**：全部节点同时失败 → 判网络问题，禁止批量重启；单节点需连续失败才判死；
5. **重启范围最小化**：worker 挂只重启 worker；master 挂只重启 master（worker 自动重连，勿动）。

---

## 五、附录：代码依据

| 依据 | 位置 |
|------|------|
| REST 接口常量定义 | `seatunnel-engine-server/.../rest/RestConstant.java:48-66` |
| overview 返回字段 | `.../resourcemanager/resource/OverviewInfo.java:26-34` |
| running-jobs 读取 `engine_runningJobInfo` map | `.../rest/RestHttpGetCommandProcessor.java:265-280` |
| overview 在 worker 上转发到 master | `.../rest/RestHttpGetCommandProcessor.java:184-195` |
| 节点健康指标渲染（isMaster/host/port 等） | `.../SeaTunnelHealthMonitor.java:202-232` |
| system-monitoring-information 无超时兜底（假死会导致 500） | `.../rest/RestHttpGetCommandProcessor.java:229-263` |
| worker 为 lite member（不能成为 master） | `.../SeaTunnelServerStarter.java:102-114` |
| 成员移除事件触发 worker 资源回收 | `.../resourcemanager/AbstractResourceManager.java:135-142` |
| worker 自动重连/主节点争夺超时 | `.../LiteNodeDropOutTcpIpJoiner.java:57-105` |