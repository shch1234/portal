# Webhook 事件处理与分发说明

## 1. 目标
- 适配 TB 侧不断增加/变化的 `eventType`
- 支持精确匹配与通配（如 `alarm.*`）
- 可插拔扩展，不写大 switch/enum
- 可与 Apollo 配置结合（后续可扩展路由表）

## 2. 组件概览
- `WebhookEventHandler`：处理接口，定义 `supports(eventType)` / `handle(...)` / `order()`
- `PatternWebhookEventHandler`：基类，支持 `*` 通配（转正则），便于快速实现
- `WebhookHandlerRegistry`：启动时收集所有 Handler（按 `order` 升序），运行时按 `supports` 依序匹配
- `LoggingFallbackWebhookHandler`：兜底 Handler，未匹配时抛异常并记录告警
- `WebhookProcessWorker`：从收件箱取消息 → 解析 payload → 通过 registry 选 Handler → 调用处理 → 标记成功/失败
- `WebhookInboxService`：收件箱管理、重试/指数退避、批量拉取
- `WebhookInboxJob`：XXL-Job 定时任务，调度 Worker 批处理

## 3. 工作流
```
WebhookInboxJob (XXL-Job cron)
    └─ WebhookProcessWorker.processBatch()
        ├─ inboxService.fetchDue() 批量拉取 PENDING/FAILED 且到期可重试的记录
        ├─ markProcessing
        ├─ 反序列化 payload -> WebhookRequest，并补全 inbox 中的基础字段
        ├─ registry.resolve(eventType) 找到匹配 Handler（按 order、supports）
        │   └─ 找不到时由 LoggingFallbackWebhookHandler 抛异常
        ├─ handler.handle(...)
        ├─ markSuccess
        └─ 异常 → markFailed（记录 last_error、next_retry_time 指数退避）
```

## 4. Handler 编写规范
```java
@Component
public class WorkpieceStartHandler extends PatternWebhookEventHandler {
    public WorkpieceStartHandler() {
        super(List.of("biz.workpiece.start")); // 精确匹配，可用 "biz.workpiece.*"
    }

    @Override
    public void handle(WebhookInboxDO inbox, WebhookRequest req) {
        // TODO: 业务处理，如入库、调用下游服务
    }

    @Override
    public int order() {
        return 100; // 优先级，数值越小越先匹配
    }
}
```

要点：
- 精确/通配皆可，用 `PatternWebhookEventHandler` 传入 pattern 列表（支持 `*`）
- `order` 控制优先级；默认 0，兜底 Handler 为 9999
- 抛出异常即可触发收件箱失败重试；成功不抛异常

## 5. 配置与扩展
- 路由配置化（建议）：扩展 registry 读取 Apollo 路由表，支持精确/通配，优先级可设为：代码精确 > 配置精确 > 代码通配 > 配置通配 > 兜底。键示例：`webhook.routing.alarm.*=alarmHandler`，`webhook.routing.biz.workpiece.start=workpieceStartHandler`
- 路由结果缓存：对匹配结果做本地缓存（如 Caffeine），降低 regex 开销
- 收件箱重试参数（Apollo 管理）：
  - `webhook.inbox.batch-size`（默认 100）
  - `webhook.inbox.max-retry-count`（默认 5）
  - `webhook.inbox.retry-interval-base-seconds`（默认 60，指数退避）
- 安全参数同之前：`webhook.security.token`、`webhook.security.timestamp-validity-ms`、`webhook.security.nonce-ttl-seconds`、`portal.webhook.secret` 等

## 6. 示例：新增一个通配报警 Handler
```java
@Component
public class AlarmHandler extends PatternWebhookEventHandler {
    public AlarmHandler() {
        super(List.of("alarm.*"));
    }

    @Override
    public void handle(WebhookInboxDO inbox, WebhookRequest req) {
        // 示例：记录日志或推送告警
        // alarmService.handle(req.getEventData());
    }

    @Override
    public int order() {
        return 50; // 在通用处理器之前命中
    }
}
```

## 7. 未匹配事件的处理
- 建议：未匹配事件直接标记 FAILED，写入 `webhook_fail_log`，触发告警，避免无效重试；可按需落一份原始 payload 便于后补 Handler
- 监控：统计未匹配的 eventType、失败率，触发报警；及时补充 Handler 或配置路由

## 9. 可观测与校验补强
- 指标：按 eventType 的成功/失败/未匹配数、处理耗时、重试次数分布、积压量
- 日志：记录命中 Handler 名、路由来源（代码/配置/兜底）、失败原因
- 校验：为核心事件增加 schema/必填校验，校验失败直接标记不可重试，减少重试风暴


## 8. 测试建议
- 单元测试：注册两个 Handler，验证 resolve 优先级、通配命中、未匹配兜底
- 集成测试：写入 inbox 多条 eventType（匹配/不匹配），跑一次 Job，验证 SUCCESS / FAILED / 重试时间

