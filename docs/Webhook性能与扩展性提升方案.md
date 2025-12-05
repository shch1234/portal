# Webhook 性能与扩展性提升方案

## 目标
- 支撑 TB → Portal Webhook 的高并发、低延迟接入
- 保障业务（BUSINESS）与实时（REALTIME）链路的吞吐与稳定
- 快速 ACK、异步处理、可横向扩容

## 1. 接口与入口层
- **快速 ACK**：Controller 仅做验签/幂等/设备匹配，BUSINESS 入收件箱，REALTIME 入 Redis；禁止入口同步重业务。
- **线程池/连接池**：调优 Web 容器（Tomcat/Undertow）工作线程、队列长度；配置 HTTP keep-alive、超时。
- **请求大小与序列化**：限制 body 大小；复用 ObjectMapper；可启用 GZIP。
- **限流与熔断**：在网关/入口按租户或全局限流；对异常流量触发熔断/告警。
- **安全校验开销**：验签逻辑轻量（SHA-256）；nonce/幂等用 Redis SETNX，确保 O(1)。

## 2. 数据路径优化
- **BUSINESS**：收件箱表 `webhook_inbox` 已建索引（message_id、status+next_retry、device_code）；避免跨表事务，单表写入。
- **REALTIME**：直接写 Redis 缓存，TTL 可配置（默认 30 分钟）；必要时启用 Redis Pipeline 批写。
- **幂等**：Redis SETNX，24h TTL；避免大 map 占用内存。
- **设备匹配**：`device_base_info` 索引 device_code；如高频可加本地/Redis 缓存。

## 3. Worker 与调度
- **批处理**：`webhook.inbox.batch-size`（默认 100）可调大；批量扫描 PENDING/FAILED + 到期重试。
- **重试指数退避**：`retry-interval-base-seconds`（默认 60），最大重试 5 次；避免重试风暴。
- **无效重试治理**：未匹配事件 `markFailedNoRetry` 直接终止重试；业务校验失败（不可重试）与临时故障（可重试）区分。
- **XXL-Job**：配置合适 cron 与路由策略（如一致性 hash/轮询）；多实例部署提升并行度。

## 4. 横向扩容
- **无状态入口**：Web 层可多实例 LB；Redis/MySQL 连接池随实例扩容调整。
- **数据层容量**：Redis 监控内存与 QPS，必要时分片；MySQL 调优 buffer pool，考虑分库分表（按租户/时间）以支撑高写入。
- **Worker 扩展**：多实例执行 `webhookInboxJob`，确保路由策略避免同一条记录被多实例并发处理（依赖唯一更新 + 状态字段）。

## 5. 监控与告警
- 指标（已集成 Micrometer hooks，可扩展）：  
  - QPS/入口耗时  
  - 收件箱积压量（PENDING/FAILED）、重试次数分布  
  - 处理成功/失败率，未匹配事件数  
  - REALTIME Redis 写入耗时/失败  
  - 设备匹配失败率、幂等拒绝数
- 告警：积压超阈值、未匹配激增、失败率升高、Redis/MySQL RT & 错误率。

## 6. 流控与降级
- **租户/设备级限流**：防止单租户/设备刷爆；可在网关或入口 AOP 实现。
- **实时数据采样/TTL**：超高频实时事件可采样或降低 TTL。
- **降级路径**：当收件箱积压严重时，可暂时只接收 REALTIME（不入业务链），或降低 BUSINESS 重试上限。

## 7. 测试与压测
- **压测场景**：高 QPS 单租户、多租户混合；大包体与小包体；高失败/高重试场景。
- **容量预估**：测得单实例极限 QPS、RT 分布、CPU/Mem/IO，占比后推算需要的实例数与连接池大小。
- **基准指标**：入口 P99、收件箱入库 RT、Worker 处理吞吐、Redis 写入 RT。

## 8. 可进一步的优化选项（按需）
- 使用 Caffeine 缓存 eventType→Handler 路由结果（当前有 ConcurrentHashMap，可设 TTL/size 控制）。
- Handler 内对外调用增加超时/重试/隔离（Hystrix/Resilience4j）防止级联。
- 分级存储：冷数据归档，热数据保留短期，减轻主表压力。

