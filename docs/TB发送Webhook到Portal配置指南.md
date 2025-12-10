# TB发送Webhook到Portal配置指南

**文档版本**：v1.0  
**创建日期**：2025-12-05  
**最后更新**：2025-12-05

---

## 📋 概述

本文档基于实际代码实现，详细说明TB端如何配置和发送Webhook数据到Portal端。

---

## 1. Portal端接口路径（已确认）

### 1.1 实际接口路径

根据 `UnifiedWebhookController.java` 的实现，Portal端提供的接口路径为：

```
POST /webhook/{category}/{eventType}
GET  /webhook/{category}/{eventType}  (URL验证接口)
```

**路径参数说明**：
- `category`：数据分类，支持 `business` 或 `realtime`（小写）
- `eventType`：事件类型，例如：
  - `workpiece-start`（工件开始）
  - `workpiece-end`（工件结束）
  - `alarm`（报警）
  - `state`（状态）
  - `telemetry`（遥测）

### 1.2 完整URL示例

```
http://localhost:8080/webhook/business/workpiece-start
http://localhost:8080/webhook/business/workpiece-end
http://localhost:8080/webhook/business/alarm
http://localhost:8080/webhook/realtime/state
http://localhost:8080/webhook/realtime/telemetry
```

---

## 2. TB端配置

### 2.1 配置文件位置

在TB的 `application.yml` 或 `application-webhook.yml` 中添加以下配置：

```yaml
event-detector:
  webhook:
    # 启用Webhook发送
    enabled: true
    
    # Portal基础URL（注意：不要包含 /api 前缀）
    # 正确示例：http://localhost:8080/webhook
    # 错误示例：http://localhost:8080/api/webhook
    base-url: http://localhost:8080/webhook
    
    # 业务数据路径（默认：/business）
    business-path: /business
    
    # 实时状态数据路径（默认：/realtime/state）
    realtime-state-path: /realtime/state
    
    # 实时遥测数据路径（默认：/realtime/telemetry）
    realtime-telemetry-path: /realtime/telemetry
    
    # Webhook Token（用于签名验证，必须与Portal端一致）
    token: your-webhook-token
    
    # 可选：简单密钥验证（如果未使用签名验证）
    secret: your-webhook-secret
    
    # 请求超时时间（毫秒，默认：5000）
    timeout-ms: 5000
    
    # 重试次数（默认：3）
    retry-count: 3
    
    # 重试间隔（毫秒，默认：1000）
    retry-interval-ms: 1000
```

### 2.2 关键配置说明

#### base-url 配置要点

根据 `WebhookConfig.java` 和 `WebhookRequestBuilder.java` 的实现：

1. **base-url 应该包含 `/webhook` 路径**：
   ```yaml
   base-url: http://localhost:8080/webhook
   ```

2. **URL构建逻辑**：
   - 业务数据：`baseUrl + businessPath + mapEventTypeToPath(eventType)`
   - 实时状态：`baseUrl + realtimeStatePath`
   - 实时遥测：`baseUrl + realtimeTelemetryPath`

3. **事件类型到路径的映射**（在 `WebhookConfig.java` 中定义）：
   ```java
   WORKPIECE_START → /workpiece-start
   WORKPIECE_END → /workpiece-end
   MACHINE_TEMPERATURE_ALERT → /alarm
   PRODUCTION_LINE_STATUS_CHANGE → /status-change
   其他 → /event
   ```

4. **最终URL示例**：
   - 业务数据（WORKPIECE_START）：
     ```
     http://localhost:8080/webhook/business/workpiece-start
     ```
   - 实时状态：
     ```
     http://localhost:8080/webhook/realtime/state
     ```
   - 实时遥测：
     ```
     http://localhost:8080/webhook/realtime/telemetry
     ```

---

## 3. 数据格式要求

### 3.1 必需字段

根据 `WebhookEventData.java` 和 `WebhookEventFormatter.java` 的实现，以下字段是必需的：

1. **`deviceCode`**：设备编号（必需）
   - 从遥测数据中提取，支持字段名：`deviceCode`、`devicecode`、`device_code`
   - 优先从 `telemetry.getData()` 中获取，其次从 `telemetry.getMetadata()` 中获取
   - **如果 `deviceCode` 为空，TB端会跳过发送**

2. **`messageId`**：消息唯一ID（UUID格式，用于幂等性检查）

3. **`eventType`**：事件类型

4. **`timestamp`**：事件时间戳（毫秒）

### 3.2 完整数据格式

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "deviceId": "789e0123-e45b-67c8-d901-234567890abc",
  "deviceCode": "M001",
  "eventType": "WORKPIECE_START",
  "timestamp": 1704067200000,
  "dataTimestamp": 1704067200000,
  "webhookCategory": "BUSINESS",
  "eventData": {
    "workpieceNo": "WP001",
    "orderNo": "ORD001",
    "programName": "PROG001"
  },
  "telemetryData": {
    "workpieceNo": "WP001",
    "spindleSpeed": 1500,
    "temperature": 75.5
  },
  "metadata": {
    "source": "RULE_ENGINE",
    "version": "1.0"
  }
}
```

---

## 4. 请求头要求

### 4.1 签名验证头（必需）

根据 `WebhookSender.java` 的实现，TB端会自动添加以下请求头：

```
X-Webhook-Signature: <签名>
X-Webhook-Timestamp: <时间戳（毫秒）>
X-Webhook-Nonce: <随机字符串>
X-Webhook-Message-Id: <消息ID>
Content-Type: application/json
```

### 4.2 签名算法

根据 `WebhookSignatureService` 的实现（如果启用）：

1. **参数排序**（字典序）：
   ```
   [token, timestamp, nonce, messageBody]
   ```

2. **拼接字符串**：
   ```
   str = token + timestamp + nonce + messageBody
   ```

3. **SHA-256加密**：
   ```
   signature = SHA256(str)
   ```

### 4.3 简单密钥验证（可选）

如果未配置 `token`，可以使用简单的密钥验证：

```
X-Webhook-Secret: your-webhook-secret
```

---

## 5. 完整配置示例

### 5.1 TB端配置（application.yml）

```yaml
event-detector:
  webhook:
    enabled: true
    base-url: http://localhost:8080/webhook
    business-path: /business
    realtime-state-path: /realtime/state
    realtime-telemetry-path: /realtime/telemetry
    token: your-webhook-token-123456
    timeout-ms: 5000
    retry-count: 3
    retry-interval-ms: 1000
```

### 5.2 Portal端配置（application.yml）

```yaml
webhook:
  security:
    token: your-webhook-token-123456  # 必须与TB端一致
    timestamp-validity-ms: 300000     # 5分钟有效期
```

---

## 6. 发送流程

### 6.1 自动发送（推荐）

TB端已自动集成Webhook发送功能，当检测到事件时会自动发送：

1. **事件检测**：`DirectTelemetryEventService` 检测到事件
2. **数据格式化**：`WebhookEventFormatter` 格式化事件数据
3. **提取deviceCode**：从遥测数据中提取设备编号
4. **构建URL**：`WebhookRequestBuilder` 根据事件类型构建URL
5. **发送请求**：`WebhookSender` 发送HTTP POST请求
6. **添加签名**：自动添加签名验证头
7. **重试机制**：失败时自动重试（可配置）

### 6.2 代码调用示例（已集成，无需手动调用）

```java
// 在 DirectTelemetryEventService 中已自动集成
webhookSender.sendEventWithRetryAsync(
    detectionResult, 
    telemetry, 
    "BUSINESS"  // 或 "REALTIME"
);
```

---

## 7. 事件类型与URL映射

### 7.1 业务数据（BUSINESS）

| 事件类型 | URL路径 | 示例 |
|---------|---------|------|
| `WORKPIECE_START` | `/webhook/business/workpiece-start` | `http://localhost:8080/webhook/business/workpiece-start` |
| `WORKPIECE_END` | `/webhook/business/workpiece-end` | `http://localhost:8080/webhook/business/workpiece-end` |
| `MACHINE_TEMPERATURE_ALERT` | `/webhook/business/alarm` | `http://localhost:8080/webhook/business/alarm` |
| `PRODUCTION_LINE_STATUS_CHANGE` | `/webhook/business/status-change` | `http://localhost:8080/webhook/business/status-change` |
| 其他 | `/webhook/business/event` | `http://localhost:8080/webhook/business/event` |

### 7.2 实时数据（REALTIME）

| 数据类型 | URL路径 | 示例 |
|---------|---------|------|
| 状态数据（eventType以`STATUS`开头） | `/webhook/realtime/state` | `http://localhost:8080/webhook/realtime/state` |
| 遥测数据（其他） | `/webhook/realtime/telemetry` | `http://localhost:8080/webhook/realtime/telemetry` |

---

## 8. 验证步骤

### 8.1 配置验证

1. **检查TB端配置**：
   ```yaml
   event-detector.webhook.enabled: true
   event-detector.webhook.base-url: http://localhost:8080/webhook
   event-detector.webhook.token: your-webhook-token
   ```

2. **检查Portal端配置**：
   ```yaml
   webhook.security.token: your-webhook-token  # 必须与TB端一致
   ```

3. **确认设备数据包含deviceCode**：
   - 遥测数据中必须包含 `deviceCode`、`devicecode` 或 `device_code` 字段
   - 如果设备数据中没有这些字段，TB端会跳过发送

### 8.2 测试发送

1. **发送测试事件**：
   - 在TB端触发一个事件（例如：工件开始）
   - 检查TB端日志，确认Webhook已发送

2. **检查Portal端日志**：
   - 查看Portal端是否收到Webhook请求
   - 检查设备匹配是否成功

3. **验证数据**：
   - 检查Portal端数据库（`webhook_inbox` 表）是否收到数据
   - 检查Redis缓存（REALTIME数据）是否更新

---

## 9. 常见问题

### 9.1 URL路径不匹配

**问题**：Portal端返回404错误

**原因**：
- `base-url` 配置错误（多加了 `/api` 前缀）
- 事件类型映射错误

**解决**：
- 确认 `base-url` 为：`http://localhost:8080/webhook`（不包含 `/api`）
- 检查事件类型是否正确映射到URL路径

### 9.2 签名验证失败

**问题**：Portal端返回401错误

**原因**：
- TB端和Portal端的 `token` 不一致
- 时间戳过期（超过5分钟）

**解决**：
- 确认TB端和Portal端的 `token` 配置一致
- 检查系统时间是否同步

### 9.3 deviceCode为空

**问题**：TB端日志显示"设备编号为空，跳过发送"

**原因**：
- 遥测数据中未包含 `deviceCode` 字段

**解决**：
- 确保设备发送的遥测数据中包含 `deviceCode`、`devicecode` 或 `device_code` 字段
- 或者在metadata中添加这些字段

### 9.4 设备未匹配

**问题**：Portal端收到数据但未处理

**原因**：
- Portal端的 `device_info` 表中没有对应的设备
- 设备状态非ACTIVE
- 设备未启用监控（`is_monitored != 1`）

**解决**：
- 检查Portal端 `device_info` 表，确认设备存在且状态正常
- 确认设备的 `device_code` 与TB端发送的 `deviceCode` 一致

---

## 10. 参考文档

- [TB与Portal对接清单](./TB与Portal对接清单.md)
- [Webhook配置和使用指南](../thingsboard/event-detector/Webhook配置和使用指南.md)
- [Portal端Webhook接收功能开发任务清单](./Webhook接收功能开发任务清单.md)

---

**文档版本**：v1.0  
**最后更新**：2025-12-05

