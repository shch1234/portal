# Webhook签名验证配置说明

**问题**：TB端发送Webhook时，Portal端返回401错误："Webhook签名参数缺失"

**原因**：TB端未发送签名头，或Portal端未配置token

---

## 🔍 问题分析

### Portal端验证逻辑

`WebhookSecurityService.validateSignature()` 方法检查：
1. `signatureToken`（从配置 `webhook.security.token` 读取）
2. `signature`（请求头 `X-Webhook-Signature`）
3. `timestamp`（请求头 `X-Webhook-Timestamp`）
4. `nonce`（请求头 `X-Webhook-Nonce`）

**如果任何一个为空，就会抛出"Webhook签名参数缺失"异常**。

### TB端发送逻辑

`RealtimeWebhookSender.addSignatureHeaders()` 方法会检查：
1. `webhookConfig` 是否为null
2. `signatureService` 是否为null
3. `webhookConfig.getToken()` 是否配置

**如果这些条件不满足，方法会直接return，不会添加签名头**。

---

## 🔧 解决方案

### 方案1：配置Portal端token（推荐）✅

在Portal的配置文件中添加：

**文件**：`application.yml` 或 `application-dev.yml`（或Apollo配置中心）

```yaml
webhook:
  security:
    token: your-webhook-token-123456  # 必须与TB端一致
    timestamp-validity-ms: 300000      # 5分钟有效期
    nonce-ttl-seconds: 300            # 5分钟TTL
```

### 方案2：配置TB端token

确保TB端配置了token：

**文件**：`thingsboard-dev.yml`

```yaml
event-detector:
  webhook:
    token: your-webhook-token-123456  # 必须与Portal端一致
```

### 方案3：临时禁用签名验证（仅用于测试）⚠️

如果暂时无法配置token，可以临时禁用签名验证：

**修改Portal端代码**（仅用于测试）：

在 `WebhookSecurityService.validateSignature()` 方法中添加：

```java
public void validateSignature(String signature, String timestamp, String nonce, String messageBody) {
    // 临时禁用：如果token未配置，跳过验证（仅用于测试）
    if (StringUtils.isBlank(signatureToken)) {
        log.warn("Webhook token未配置，跳过签名验证（仅用于测试）");
        return;
    }
    
    if (StringUtils.isAnyBlank(signatureToken, signature, timestamp, nonce)) {
        throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "Webhook签名参数缺失");
    }
    // ... 其他验证逻辑
}
```

**注意**：此方案仅用于测试，生产环境必须配置token。

---

## ✅ 配置验证

### 1. 检查Portal端配置

```bash
# 检查配置文件中是否有webhook.security.token
grep -r "webhook.security.token" .
```

### 2. 检查TB端配置

```bash
# 检查配置文件中是否有event-detector.webhook.token
grep -r "event-detector.webhook.token" .
```

### 3. 验证token一致性

确保TB端和Portal端的token配置一致：
- TB端：`event-detector.webhook.token`
- Portal端：`webhook.security.token`

---

## 📝 完整配置示例

### Portal端配置

```yaml
webhook:
  security:
    token: your-webhook-token-123456
    timestamp-validity-ms: 300000
    nonce-ttl-seconds: 300
```

### TB端配置

```yaml
event-detector:
  webhook:
    enabled: true
    base-url: http://localhost:8080/webhook
    token: your-webhook-token-123456  # 必须与Portal端一致
```

---

## 🔍 调试步骤

### 1. 检查TB端日志

查看TB端是否添加了签名头：

```
[EventDetector-Webhook] 已添加签名验证头: signature=..., timestamp=..., nonce=...
```

如果没有这条日志，说明：
- `webhookConfig` 为null
- `signatureService` 为null
- `token` 未配置

### 2. 检查Portal端日志

查看Portal端是否收到签名头：

```
[Webhook] [接收] messageId=..., signature=..., timestamp=..., nonce=...
```

### 3. 使用curl测试

```bash
# 测试不带签名（应该返回401）
curl -X POST http://localhost:8080/webhook/realtime/telemetry \
  -H "Content-Type: application/json" \
  -d '{"test":"data"}'

# 测试带签名（需要手动计算签名）
curl -X POST http://localhost:8080/webhook/realtime/telemetry \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: <签名>" \
  -H "X-Webhook-Timestamp: <时间戳>" \
  -H "X-Webhook-Nonce: <随机字符串>" \
  -d '{"test":"data"}'
```

---

## ⚠️ 注意事项

### 1. Token必须一致

- TB端和Portal端的token必须完全一致
- 建议使用环境变量或配置中心管理

### 2. 生产环境

- ✅ 必须配置token
- ✅ 必须启用签名验证
- ❌ 不要禁用签名验证

### 3. 测试环境

- ✅ 可以临时禁用签名验证进行功能测试
- ✅ 测试完成后，恢复签名验证

---

## 🔗 相关文档

- [TB发送Webhook到Portal配置指南](./TB发送Webhook到Portal配置指南.md)
- [TB与Portal对接清单](./TB与Portal对接清单.md)

---

**最后更新**：2025-12-10

