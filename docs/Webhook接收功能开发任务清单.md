# Portal端Webhook接收功能开发任务清单

**文档版本**：v1.1  
**创建日期**：2025-12-05  
**最后更新**：2025-12-05

---

## 📋 文档说明

本文档详细列出了Portal端Webhook接收功能的所有开发任务，按照开发顺序组织，包含技术实现要点和验收标准。

**重要约束**：
- ⚠️ **不修改 `iot-business` 目录**：该目录仅作参考，所有代码已分布在其他模块中
- ⚠️ **表名不带前缀**：系统本身就是Portal，表名直接使用 `webhook_inbox`、`webhook_fail_log` 等
- ⚠️ **使用Redis缓存**：幂等性检查、实时数据缓存均使用Redis
- ⚠️ **使用XXL-Job**：所有定时任务使用XXL-Job调度
- ⚠️ **配置集中到 Apollo**：Portal 侧所有配置（含 webhook 安全参数、Redis、XXL-Job appName/执行器、路由策略、超时/重试等）统一放到 Apollo 管理，代码中不硬编码

---

## 🎯 总体目标

实现Portal端Webhook接收功能，支持：
- ✅ 统一Webhook接收接口（`/webhook/{category}/{eventType}`）
- ✅ 快速ACK机制（立即返回200）
- ✅ 幂等性保障（基于messageId，使用Redis）
- ✅ 异步处理机制（收件箱 + Worker）
- ✅ 可靠性保障（重试、对账、补偿）
- ✅ 业务数据处理（设备状态、报警、产量等）

---

## 📊 开发阶段划分

| 阶段 | 任务 | 预计工时 | 优先级 |
|------|------|----------|--------|
| **阶段1** | 数据库设计与创建 | 4小时 | P0 |
| **阶段2** | 基础服务层实现 | 8小时 | P0 |
| **阶段3** | Webhook接收Controller | 6小时 | P0 |
| **阶段4** | 异步处理Worker | 12小时 | P0 |
| **阶段5** | 业务处理服务 | 16小时 | P1 |
| **阶段6** | 定时任务（恢复、对账） | 8小时 | P1 |
| **阶段7** | 监控告警 | 4小时 | P2 |
| **阶段8** | 测试与优化 | 8小时 | P0 |

**总计**：约66小时（约8-9个工作日）

---

## 📁 Portal目录结构说明

Portal项目采用分层架构，代码组织如下：

```
iot-portal-dal/          # 数据访问层
  ├── dal/dataobject/    # 实体类（DO）
  ├── dal/mapper/        # MyBatis Mapper接口
  └── dal/repository/    # Repository接口和实现

iot-portal-domain/       # 领域模型层
  ├── domain/            # VO、DTO、Request等
  └── domain/request/    # 请求对象

iot-portal-service/      # 业务服务层
  ├── service/           # Service接口
  └── service/impl/      # Service实现

iot-portal-web/          # Web控制层
  └── web/               # Controller

iot-portal-task/         # 定时任务模块（XXL-Job）
```

**Webhook相关代码应放在**：
- **DO实体**：`iot-portal-dal/src/main/java/com/weili/iot_portal/dal/dataobject/ingestion/`
- **Mapper**：`iot-portal-dal/src/main/java/com/weili/iot_portal/dal/mapper/ingestion/`
- **Service**：`iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/`
- **Controller**：`iot-portal-web/src/main/java/com/weili/iot_portal/web/webhook/`
- **DTO/VO**：`iot-portal-domain/src/main/java/com/weili/iot_portal/domain/ingestion/`
- **定时任务**：`iot-portal-task/src/main/java/com/weili/iot_portal/task/`

---

## 阶段1：数据库设计与创建

### 任务1.1：设计收件箱表（webhook_inbox）

**任务描述**：设计并创建Webhook收件箱表，用于存储待处理的Webhook消息。

**表结构要点**：
- 表名：`webhook_inbox`（不带portal前缀）
- 核心字段：`message_id`（唯一索引，幂等性）、`status`、`webhook_category`、`payload`（JSON）
- 状态字段：`status`（PENDING/PROCESSING/SUCCESS/FAILED）
- 重试字段：`process_count`、`next_retry_time`、`last_error`
- 时间字段：`received_time`、`processed_time`、`created_time`、`updated_time`

**索引设计**：
- 主键：`id`
- 唯一索引：`message_id`（幂等性保障）
- 联合索引：`(status, next_retry_time)`（Worker扫描优化）
- 普通索引：`tenant_id`、`device_id`、`event_type`、`received_time`

**验收标准**：
- ✅ 表结构创建成功
- ✅ 所有索引创建成功
- ✅ 字段注释完整
- ✅ 支持JSON类型（MySQL 5.7+）

**文件位置**：
- SQL文件：`database/ddl/webhook_inbox.sql`

---

### 任务1.2：设计失败日志表（webhook_fail_log）

**任务描述**：设计并创建Webhook失败日志表，用于记录处理失败的消息。

**表结构要点**：
- 表名：`webhook_fail_log`（不带portal前缀）
- 核心字段：`message_id`、`error_type`、`error_message`、`error_stack`
- 错误分类：`error_type`（VALIDATION_ERROR/BUSINESS_ERROR/SYSTEM_ERROR）
- 恢复标记：`recovered`、`recovered_time`、`need_manual`
- 重试计数：`retry_count`

**索引设计**：
- 主键：`id`
- 普通索引：`message_id`、`(recovered, created_time)`、`(need_manual, created_time)`、`(tenant_id, device_id)`

**验收标准**：
- ✅ 表结构创建成功
- ✅ 所有索引创建成功
- ✅ 支持错误类型分类查询

**文件位置**：
- SQL文件：`database/ddl/webhook_fail_log.sql`

---

### 任务1.3：创建数据库迁移脚本

**任务描述**：创建数据库迁移脚本，包含表创建和初始数据。

**实现要点**：
- 创建SQL文件：`database/ddl/webhook_tables.sql`（包含所有Webhook相关表）
- 脚本支持幂等性（可重复执行）
- 包含完整的表创建和索引创建语句

**验收标准**：
- ✅ SQL脚本可执行
- ✅ 脚本支持幂等性（可重复执行）
- ✅ 包含回滚脚本（可选）

---

## 阶段2：基础服务层实现

### 任务2.1：优化WebhookIdempotentService（使用Redis）

**任务描述**：将现有的内存实现改为Redis实现，支持分布式环境。

**当前实现**：使用`ConcurrentHashMap`（内存实现，位于`iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/support/WebhookIdempotentService.java`）

**实现要点**：
- 使用`RedisTemplate`替代`ConcurrentHashMap`
- Redis Key格式：`webhook:idempotent:{messageId}`
- 使用`setIfAbsent`实现原子性检查
- 支持分布式环境，多实例部署时幂等性检查仍然有效

**验收标准**：
- ✅ 使用Redis替代内存实现
- ✅ 支持分布式环境
- ✅ TTL设置为24小时
- ✅ 性能测试通过（QPS > 1000）

**文件位置**：
- `iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/support/WebhookIdempotentService.java`

---

### 任务2.2：创建WebhookInboxService（收件箱服务）

**任务描述**：创建收件箱服务，负责收件箱表的CRUD操作。

**实现要点**：

1. **创建DO实体类**：
   - 表名：`webhook_inbox`
   - 字段对应表结构，`payload`字段存储JSON字符串
   - 使用MyBatis Plus的`@TableName`注解

2. **创建Mapper接口**：
   - 继承`BaseMapper<WebhookInboxDO>`
   - 自定义方法：`selectPendingMessages`（查询待处理消息，用于Worker扫描）
   - 自定义方法：`updateStatus`（更新处理状态）

3. **创建Service接口和实现**：
   - `saveInbox`：写入收件箱（接收Webhook时调用）
   - `getPendingMessages`：查询待处理消息（Worker扫描调用）
   - `markProcessing`：标记为处理中
   - `markSuccess`：标记为成功
   - `markFailed`：标记为失败（记录错误信息和下次重试时间）

**验收标准**：
- ✅ Entity、Mapper、Service创建完成
- ✅ 支持批量查询待处理消息
- ✅ 支持状态更新
- ✅ 单元测试通过

**文件位置**：
- DO：`iot-portal-dal/src/main/java/com/weili/iot_portal/dal/dataobject/ingestion/WebhookInboxDO.java`
- Mapper：`iot-portal-dal/src/main/java/com/weili/iot_portal/dal/mapper/ingestion/WebhookInboxMapper.java`
- Service接口：`iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/WebhookInboxService.java`
- Service实现：`iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/impl/WebhookInboxServiceImpl.java`

---

### 任务2.3：创建WebhookFailLogService（失败日志服务）

**任务描述**：创建失败日志服务，负责失败日志表的CRUD操作。

**实现要点**：

1. **创建DO实体类**：
   - 表名：`webhook_fail_log`
   - 字段对应表结构，包含错误类型、错误信息、错误堆栈等

2. **创建Service接口和实现**：
   - `saveFailLog`：记录失败日志（处理失败时调用）
   - `getUnrecoveredLogs`：查询待恢复的失败日志（恢复任务调用）
   - `markRecovered`：标记为已恢复

**验收标准**：
- ✅ DO、Service创建完成
- ✅ 支持错误分类记录
- ✅ 支持恢复标记

**文件位置**：
- DO：`iot-portal-dal/src/main/java/com/weili/iot_portal/dal/dataobject/ingestion/WebhookFailLogDO.java`
- Service接口：`iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/WebhookFailLogService.java`
- Service实现：`iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/impl/WebhookFailLogServiceImpl.java`

---

### 任务2.4：创建WebhookEventDTO（事件DTO）

**任务描述**：创建统一的Webhook事件DTO，用于数据传输。

**实现要点**：
- 字段包含：`messageId`、`tenantId`、`deviceId`、`deviceCode`、`deviceName`、`deviceType`、`eventType`、`timestamp`、`dataTimestamp`、`webhookCategory`、`eventData`、`telemetryData`、`metadata`、`transactionInfo`
- 使用`@Data`注解（Lombok）
- 支持JSON序列化/反序列化
- 关键字段添加校验注解（`@NotNull`、`@NotBlank`等）

**验收标准**：
- ✅ DTO字段完整
- ✅ 支持JSON序列化/反序列化
- ✅ 字段校验注解完整

**文件位置**：
- `iot-portal-domain/src/main/java/com/weili/iot_portal/domain/ingestion/dto/WebhookEventDTO.java`

---

## 阶段3：Webhook接收Controller

### 任务3.1：创建统一Webhook接收Controller

**任务描述**：创建统一的Webhook接收接口，支持`/webhook/{category}/{eventType}`路径。

**实现要点**：

1. **接口路径**：`POST /webhook/{category}/{eventType}`
   - 示例：`/webhook/business/workpiece-start`
   - 示例：`/webhook/realtime/state`

2. **处理流程**：
   - 安全验证：验证`X-Webhook-Secret`请求头（使用`WebhookSecurityService`）
   - 参数校验：校验`messageId`、`tenantId`、`category`等必填字段
   - 幂等性检查：使用`WebhookIdempotentService.tryConsume()`检查（Redis实现）
   - 设置分类：将`category`和`eventType`设置到DTO中
   - 写入收件箱：调用`WebhookInboxService.saveInbox()`写入数据库
   - 快速ACK：立即返回200 OK（即使写入失败也返回200，避免TB重试）

3. **异常处理**：
   - 参数校验失败：返回400错误
   - 安全验证失败：返回401错误
   - 幂等性检查失败：返回200（重复消息）
   - 写入收件箱失败：记录日志，返回200（通过对账任务补偿）

**验收标准**：
- ✅ 接口路径正确
- ✅ 快速ACK（响应时间 < 100ms）
- ✅ 幂等性检查生效
- ✅ 安全验证生效
- ✅ 参数校验完整
- ✅ 异常处理完善

**文件位置**：
- `iot-portal-web/src/main/java/com/weili/iot_portal/web/webhook/UnifiedWebhookController.java`

---

### 任务3.2：创建Webhook事件处理器接口

**任务描述**：创建事件处理器接口，用于不同事件类型的业务处理。

**实现要点**：
- 定义`WebhookEventHandler`接口
- 方法：`handle(WebhookEventDTO event)`（处理事件）
- 方法：`supports(String eventType)`（判断是否支持该事件类型）
- 方法：`getOrder()`（返回优先级，数字越小优先级越高）

**验收标准**：
- ✅ 接口定义清晰
- ✅ 支持事件类型匹配
- ✅ 支持优先级排序

**文件位置**：
- `iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/handler/WebhookEventHandler.java`

---

## 阶段4：异步处理Worker

### 任务4.1：创建WebhookProcessingWorker（处理Worker）

**任务描述**：创建异步处理Worker，使用XXL-Job定时扫描收件箱，处理待处理消息。

**实现要点**：

1. **Worker功能**：
   - 定时扫描收件箱表，查询`status=PENDING`或`status=FAILED`且`next_retry_time <= now()`的记录
   - 批量处理（每批100条）
   - 解析JSON payload为`WebhookEventDTO`
   - 根据`eventType`查找对应的`WebhookEventHandler`
   - 调用Handler处理事件
   - 更新处理状态

2. **失败处理**：
   - 重试机制：最大重试5次，指数退避（2^retryCount * 60秒）
   - 超过最大重试次数：写入`webhook_fail_log`表，标记`need_manual`
   - 错误分类：VALIDATION_ERROR、BUSINESS_ERROR、SYSTEM_ERROR

3. **XXL-Job配置**：
   - Job名称：`webhookProcessingJob`
   - 执行频率：每5秒执行一次
   - 执行策略：单机串行（避免重复处理）

**验收标准**：
- ✅ Worker定时执行
- ✅ 批量处理消息
- ✅ 支持重试机制
- ✅ 失败处理完善
- ✅ 性能测试通过（处理速度 > 100条/秒）

**文件位置**：
- `iot-portal-task/src/main/java/com/weili/iot_portal/task/webhook/WebhookProcessingJob.java`

---

## 阶段5：业务处理服务

### 任务5.1：创建设备状态事件处理器

**任务描述**：实现设备状态变更事件的业务处理。

**实现要点**：
- 实现`WebhookEventHandler`接口
- `supports`方法：支持`DEVICE_STATE_CHANGE`、`STATE_CHANGE`事件类型
- `handle`方法：
  - 解析`eventData`获取`stateCode`、`stateName`
  - 根据`deviceCode`查询设备（使用现有的设备查询服务）
  - 调用设备状态服务保存状态
- 异常处理：设备不存在时抛出`BusinessException`（标记`need_manual=true`）

**验收标准**：
- ✅ 支持设备状态变更事件
- ✅ 正确保存设备状态
- ✅ 异常处理完善

**文件位置**：
- `iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/handler/DeviceStateEventHandler.java`

---

### 任务5.2：创建报警事件处理器

**任务描述**：实现报警事件的业务处理。

**实现要点**：
- 实现`WebhookEventHandler`接口
- `supports`方法：支持包含`ALARM`或`ALERT`的事件类型
- `handle`方法：
  - 解析`eventData`获取`alarmCode`、`alarmText`、`alarmLevel`、`isActive`
  - 根据`deviceCode`查询设备
  - 根据`isActive`判断是报警开始还是结束，调用对应的报警服务方法
- 异常处理：设备不存在时抛出`BusinessException`

**验收标准**：
- ✅ 支持报警开始/结束事件
- ✅ 正确保存报警记录
- ✅ 支持报警级别分类

**文件位置**：
- `iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/handler/AlarmEventHandler.java`

---

### 任务5.3：创建工件事件处理器

**任务描述**：实现工件开始/结束事件的业务处理。

**实现要点**：
- 实现`WebhookEventHandler`接口
- `supports`方法：支持`WORKPIECE_START`、`WORKPIECE_END`事件类型
- `handle`方法：
  - 解析`eventData`获取`workpieceNo`、`orderNo`等
  - 根据`deviceCode`查询设备
  - 根据事件类型调用产量服务的`startWorkpiece`或`endWorkpiece`方法
- 异常处理：设备不存在时抛出`BusinessException`

**验收标准**：
- ✅ 支持工件开始/结束事件
- ✅ 正确保存产量记录
- ✅ 支持产量和合格率统计

**文件位置**：
- `iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/handler/WorkpieceEventHandler.java`

---

### 任务5.4：创建实时数据事件处理器

**任务描述**：实现实时数据事件的业务处理（Redis缓存 + WebSocket推送）。

**实现要点**：
- 实现`WebhookEventHandler`接口
- `supports`方法：支持`REALTIME_STATE`、`REALTIME_TELEMETRY`、`TELEMETRY_UPDATE`等事件类型
- `handle`方法：
  - Redis缓存：Key格式`realtime:device:{deviceId}`，TTL=60秒
  - WebSocket推送：调用现有的`RealtimeWebSocketHandler`推送实时数据
- 优先级：设置为最低优先级（`getOrder()`返回1000）

**验收标准**：
- ✅ 实时数据缓存到Redis
- ✅ WebSocket推送功能正常
- ✅ 性能测试通过（延迟 < 100ms）

**文件位置**：
- `iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/handler/RealtimeDataEventHandler.java`

---

## 阶段6：定时任务（恢复、对账）

### 任务6.1：创建WebhookRecoveryJob（恢复任务）

**任务描述**：创建恢复任务，使用XXL-Job定时扫描失败日志，重新处理失败的消息。

**实现要点**：

1. **任务功能**：
   - 查询`webhook_fail_log`表中`recovered=false`的记录
   - 批量处理（每批50条）
   - 解析JSON payload为`WebhookEventDTO`
   - 重新写入`webhook_inbox`表
   - 标记`recovered=true`

2. **XXL-Job配置**：
   - Job名称：`webhookRecoveryJob`
   - 执行频率：每10分钟执行一次
   - 执行策略：单机串行

3. **注意事项**：
   - 只恢复`need_manual=false`的记录（业务校验失败的需要人工处理）
   - 恢复失败时记录日志，不中断任务

**验收标准**：
- ✅ 定时任务正常执行
- ✅ 失败消息成功恢复
- ✅ 恢复后标记正确

**文件位置**：
- `iot-portal-task/src/main/java/com/weili/iot_portal/task/webhook/WebhookRecoveryJob.java`

---

### 任务6.2：创建WebhookReconciliationJob（对账任务）

**任务描述**：创建对账任务，使用XXL-Job定时对比TB和Portal的数据，发现差异并补偿。

**实现要点**：

1. **任务功能**：
   - 获取对账时间范围（前一天）
   - 调用TB REST API查询`webhook_buffer`表的SUCCESS记录
   - 查询Portal的`webhook_inbox`表的SUCCESS记录
   - 对比`messageId`，找出TB有但Portal没有的消息
   - 调用TB Backlog API获取缺失消息的完整数据
   - 重新写入`webhook_inbox`表

2. **XXL-Job配置**：
   - Job名称：`webhookReconciliationJob`
   - 执行频率：每天凌晨2点执行
   - 执行策略：单机串行

3. **TB API调用**：
   - 需要创建TB REST API客户端（`ThingsBoardClient`）
   - 调用Backlog API：`/api/webhook/backlog`

**验收标准**：
- ✅ 定时任务正常执行
- ✅ 数据对比准确
- ✅ 差异补偿成功

**文件位置**：
- `iot-portal-task/src/main/java/com/weili/iot_portal/task/webhook/WebhookReconciliationJob.java`

---

## 阶段7：监控告警

### 任务7.1：创建Webhook监控指标

**任务描述**：创建监控指标服务，用于监控Webhook处理情况。

**实现要点**：
- 提供方法：`getInboxBacklogCount()`（收件箱积压数量）
- 提供方法：`getFailedMessageCount()`（失败消息数量）
- 提供方法：`getSuccessRate()`（处理成功率，统计最近1小时）
- 通过调用`WebhookInboxService`和`WebhookFailLogService`的统计方法实现

**验收标准**：
- ✅ 监控指标准确
- ✅ 支持实时查询

**文件位置**：
- `iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/metrics/WebhookMetrics.java`

---

### 任务7.2：创建告警任务

**任务描述**：创建告警任务，使用XXL-Job定时监控积压和失败率，超过阈值时发送告警。

**实现要点**：

1. **告警检查**：
   - 检查收件箱积压：阈值200条
   - 检查处理失败率：阈值5%
   - 超过阈值时调用告警服务发送通知（钉钉/邮件等）

2. **XXL-Job配置**：
   - Job名称：`webhookAlertJob`
   - 执行频率：每10分钟执行一次
   - 执行策略：单机串行

3. **告警内容**：
   - 收件箱积压告警：包含积压数量和阈值
   - 失败率告警：包含失败率和阈值

**验收标准**：
- ✅ 告警任务正常执行
- ✅ 告警阈值配置正确
- ✅ 告警通知成功发送

**文件位置**：
- `iot-portal-task/src/main/java/com/weili/iot_portal/task/webhook/WebhookAlertJob.java`

---

## 阶段8：测试与优化

### 任务8.1：单元测试

**任务描述**：为所有Service和Handler编写单元测试。

**测试覆盖**：
- ✅ WebhookInboxService测试
- ✅ WebhookFailLogService测试
- ✅ WebhookProcessingWorker测试
- ✅ 各EventHandler测试

**文件位置**：
- `iot-portal-service/src/test/java/com/weili/iot_portal/service/ingestion/`

---

### 任务8.2：集成测试

**任务描述**：编写集成测试，测试完整的Webhook接收和处理流程。

**测试场景**：
- ✅ Webhook接收接口测试
- ✅ 幂等性测试
- ✅ 异步处理测试
- ✅ 失败重试测试
- ✅ 恢复任务测试

**文件位置**：
- `iot-portal-service/src/test/java/com/weili/iot_portal/service/ingestion/integration/`

---

### 任务8.3：性能测试

**任务描述**：进行性能测试，确保系统满足性能要求。

**测试指标**：
- ✅ Webhook接收接口响应时间 < 100ms
- ✅ Worker处理速度 > 100条/秒
- ✅ 幂等性检查QPS > 1000
- ✅ 数据库查询性能优化

---

### 任务8.4：文档编写

**任务描述**：编写开发文档和使用文档。

**文档内容**：
- ✅ API接口文档
- ✅ 部署文档
- ✅ 运维文档
- ✅ 故障排查文档

**文件位置**：
- `docs/webhook/`

---

## 📝 开发注意事项

### 1. 目录结构约束
- ⚠️ **不修改 `iot-business` 目录**：该目录仅作参考，所有代码已分布在其他模块中
- ✅ **代码位置**：
  - DO实体：`iot-portal-dal/dal/dataobject/ingestion/`
  - Mapper：`iot-portal-dal/dal/mapper/ingestion/`
  - Service：`iot-portal-service/service/ingestion/`
  - Controller：`iot-portal-web/web/webhook/`
  - DTO/VO：`iot-portal-domain/domain/ingestion/`
  - 定时任务：`iot-portal-task/`（使用XXL-Job）

### 2. 表名规范
- ⚠️ **表名不带前缀**：系统本身就是Portal，表名直接使用 `webhook_inbox`、`webhook_fail_log` 等
- ✅ **命名规范**：使用下划线命名，小写字母

### 3. 技术选型
- ✅ **缓存**：使用Redis（幂等性检查、实时数据缓存）
- ✅ **定时任务**：使用XXL-Job（所有定时任务）
- ✅ **数据库**：MySQL（收件箱表、失败日志表）

### 4. 性能优化
- 使用批量查询减少数据库交互
- 使用Redis缓存提升查询性能
- 异步处理避免阻塞主流程
- 合理设置批量处理大小

### 5. 可靠性保障
- 快速ACK避免TB超时
- 幂等性检查防止重复处理（Redis实现）
- 失败重试机制保障数据不丢失
- 对账任务发现并补偿缺失数据

### 6. 异常处理
- 所有异常都要记录日志
- 业务异常和系统异常区分处理
- 需要人工处理的异常标记`need_manual=true`

### 7. 监控告警
- 实时监控收件箱积压
- 监控处理成功率
- 超过阈值及时告警（XXL-Job定时检查）

---

## ✅ 验收标准总结

### 功能验收
- ✅ Webhook接收接口正常
- ✅ 快速ACK机制生效
- ✅ 幂等性检查生效
- ✅ 异步处理正常
- ✅ 业务数据处理正确
- ✅ 失败重试机制正常
- ✅ 恢复任务正常
- ✅ 对账任务正常

### 性能验收
- ✅ Webhook接收响应时间 < 100ms
- ✅ Worker处理速度 > 100条/秒
- ✅ 幂等性检查QPS > 1000
- ✅ 系统稳定运行

### 可靠性验收
- ✅ 网络故障时数据不丢失
- ✅ 服务重启后数据可恢复
- ✅ 对账发现差异可自动补偿

---

## 📚 参考文档

- [TB与Portal交互架构](../../thingsboard/event-detector/docs/design/TB与Portal交互架构.md)
- [架构设计与实现总结](../../thingsboard/event-detector/docs/design/架构设计与实现总结.md)
- [Webhook配置和使用指南](../../thingsboard/event-detector/Webhook配置和使用指南.md)
- [Weili-IoT-Portal方案设计文档](../Weili-IoT-Portal方案设计文档.md)

---

**文档版本**：v1.1  
**最后更新**：2025-12-05

**版本更新记录**：
- v1.1（2025-12-05）：按照项目约束修改，去掉portal前缀，简化代码示例，明确使用Redis和XXL-Job
- v1.0（2025-12-05）：初始版本

