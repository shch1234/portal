# TB与Portal Webhook对接清单

**文档版本**：v1.0  
**创建日期**：2025-12-05  
**最后更新**：2025-12-05

---

## 📋 文档说明

本文档详细描述 ThingsBoard（TB）与 Weili-IoT-Portal（Portal）之间的 Webhook 对接方案，包括安全机制、数据格式、设备匹配规则和实现要点。

---

## 1. 对接概述

### 1.1 系统角色

| 系统 | 角色 | 职责 |
|------|------|------|
| **ThingsBoard** | 发送方 | 事件检测、Webhook推送、缓冲重试、Backlog API |
| **Portal** | 接收方 | Webhook接收、安全验证、设备匹配、业务处理、数据缓存 |

### 1.2 数据分类

| 数据类型 | 特点 | 处理策略 |
|----------|------|----------|
| **BUSINESS（业务数据）** | 必须可靠送达 | 缓冲表、重试机制、收件箱、异步处理 |
| **REALTIME（实时数据）** | 可丢失、低延迟 | 快速ACK、Redis缓存、WebSocket推送 |

### 1.3 核心要求

1. ✅ **安全验证**：签名验证、时间戳验证、防重放攻击
2. ✅ **设备匹配**：必须携带`device_code`，匹配`device_info`表后才处理
3. ✅ **幂等性保障**：基于`messageId`，使用Redis实现
4. ✅ **快速ACK**：立即返回200，异步处理
5. ✅ **可靠性保障**：收件箱、重试、对账、补偿

---

## 2. 安全机制

### 2.1 签名算法

#### 2.1.1 算法说明

TB端和Portal端使用相同的签名算法，确保请求的完整性和真实性。

**签名算法**：`SHA-256(token + timestamp + nonce + messageBody)`

**参数说明**：
- `token`：共享密钥（TB和Portal配置一致）
- `timestamp`：时间戳（毫秒，Unix时间戳）
- `nonce`：随机字符串（每次请求唯一）
- `messageBody`：请求体内容（JSON字符串，未加密）

#### 2.1.2 签名生成流程（TB端）

```
1. 准备参数：
   - token = "your-webhook-token"
   - timestamp = System.currentTimeMillis()  // 例如：1660460256546
   - nonce = generateRandomString(12)  // 例如："9S4R2clVti27"
   - messageBody = JSON.stringify(eventData)  // 例如：'{"messageId":"xxx","deviceCode":"M001",...}'

2. 参数排序（字典序）：
   arr = [token, timestamp, nonce, messageBody]
   arr.sort()  // 按字典序排序

3. 拼接字符串：
   str = arr[0] + arr[1] + arr[2] + arr[3]

4. SHA-256加密：
   signature = SHA256(str)

5. 添加到请求头：
   X-Webhook-Signature: signature
   X-Webhook-Timestamp: timestamp
   X-Webhook-Nonce: nonce
```

#### 2.1.3 签名验证流程（Portal端）

```
1. 提取请求参数：
   - signature = request.getHeader("X-Webhook-Signature")
   - timestamp = request.getHeader("X-Webhook-Timestamp")
   - nonce = request.getHeader("X-Webhook-Nonce")
   - messageBody = getRequestBody(request)  // 原始请求体

2. 验证时间戳（防重放攻击）：
   currentTime = System.currentTimeMillis()
   timeDiff = |currentTime - Long.parseLong(timestamp)|
   if (timeDiff > 300000) {  // 5分钟有效期
       return 401 "请求已过期"
   }

3. 验证nonce（防重放攻击）：
   nonceKey = "webhook:nonce:" + nonce
   if (redis.exists(nonceKey)) {
       return 401 "重复请求"
   }
   redis.setex(nonceKey, 300, "1")  // 5分钟TTL

4. 计算签名：
   arr = [token, timestamp, nonce, messageBody]
   arr.sort()  // 按字典序排序
   str = arr[0] + arr[1] + arr[2] + arr[3]
   calculatedSignature = SHA256(str)

5. 验证签名：
   if (calculatedSignature.equals(signature)) {
       return true  // 验证通过
   } else {
       return false  // 验证失败
   }
```

#### 2.1.4 签名算法伪代码

```java
/**
 * 生成签名
 * @param token 共享密钥
 * @param timestamp 时间戳（毫秒）
 * @param nonce 随机字符串
 * @param messageBody 消息体（JSON字符串）
 * @return 签名字符串（十六进制）
 */
function generateSignature(token, timestamp, nonce, messageBody) {
    // 1. 参数数组
    arr = [token, timestamp, nonce, messageBody]
    
    // 2. 按字典序排序
    arr.sort()
    
    // 3. 拼接字符串
    str = ""
    for (item in arr) {
        str += item
    }
    
    // 4. SHA-256加密
    hash = SHA256(str.getBytes("UTF-8"))
    
    // 5. 转换为十六进制字符串
    hexString = ""
    for (byte in hash) {
        hex = Integer.toHexString(0xff & byte)
        if (hex.length() == 1) {
            hexString += "0"
        }
        hexString += hex
    }
    
    return hexString
}

/**
 * 验证签名
 * @param signature 待验证的签名
 * @param token 共享密钥
 * @param timestamp 时间戳
 * @param nonce 随机字符串
 * @param messageBody 消息体
 * @return 是否验证通过
 */
function verifySignature(signature, token, timestamp, nonce, messageBody) {
    // 1. 计算签名
    calculatedSignature = generateSignature(token, timestamp, nonce, messageBody)
    
    // 2. 比较签名（常量时间比较，防止时序攻击）
    return constantTimeEquals(calculatedSignature, signature)
}
```

### 2.2 URL验证接口（可选）

#### 2.2.1 接口说明

用于TB端验证Portal的Webhook URL是否可用，通常在配置Webhook时调用。

**请求方式**：`GET`  
**请求路径**：`/webhook/{category}/{eventType}`  
**请求参数（URL参数）**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `msg_signature` | String | TB生成的签名 |
| `timestamp` | String | 时间戳（毫秒） |
| `nonce` | String | 随机字符串 |
| `echostr` | String | 加密的验证字符串（URL编码） |

**响应**：返回解密后的`echostr`（明文字符串）

#### 2.2.2 验证流程

```
1. 提取URL参数：
   - msg_signature = request.getParameter("msg_signature")
   - timestamp = request.getParameter("timestamp")
   - nonce = request.getParameter("nonce")
   - echostr = URLDecoder.decode(request.getParameter("echostr"))

2. 验证时间戳：
   if (|currentTime - timestamp| > 300000) {
       return 401 "请求已过期"
   }

3. 验证签名：
   signature = generateSignature(token, timestamp, nonce, echostr)
   if (signature != msg_signature) {
       return 401 "签名验证失败"
   }

4. 解密echostr（如果使用加密）：
   plainEchostr = decrypt(echostr, encodingAesKey)

5. 返回明文echostr：
   return 200 plainEchostr
```

### 2.3 加解密流程（可选）

#### 2.3.1 加密场景

如果TB端配置了`encodingAesKey`，则消息体需要加密传输。

**加密算法**：AES-256-CBC（参考微信企业号加密方式）

#### 2.3.2 加密流程（TB端）

```
1. 准备明文消息：
   plainText = JSON.stringify(eventData)

2. 生成随机IV（16字节）：
   iv = generateRandomBytes(16)

3. AES加密：
   cipherText = AES256_CBC_Encrypt(plainText, encodingAesKey, iv)

4. Base64编码：
   encryptedData = Base64.encode(cipherText)

5. 添加到请求体：
   {
     "msg_encrypt": encryptedData,
     "iv": Base64.encode(iv)
   }
```

#### 2.3.3 解密流程（Portal端）

```
1. 提取加密数据：
   msg_encrypt = requestBody.get("msg_encrypt")
   iv = Base64.decode(requestBody.get("iv"))

2. Base64解码：
   cipherText = Base64.decode(msg_encrypt)

3. AES解密：
   plainText = AES256_CBC_Decrypt(cipherText, encodingAesKey, iv)

4. JSON解析：
   eventData = JSON.parse(plainText)
```

#### 2.3.4 加解密伪代码

```java
/**
 * AES-256-CBC加密
 * @param plainText 明文
 * @param key 密钥（Base64编码）
 * @param iv 初始向量（16字节）
 * @return 密文（Base64编码）
 */
function encrypt(plainText, key, iv) {
    // 1. Base64解码密钥
    keyBytes = Base64.decode(key)
    
    // 2. 创建密钥规范
    secretKey = new SecretKeySpec(keyBytes, "AES")
    
    // 3. 创建Cipher
    cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    ivSpec = new IvParameterSpec(iv)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
    
    // 4. 加密
    encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"))
    
    // 5. Base64编码
    return Base64.encode(encryptedBytes)
}

/**
 * AES-256-CBC解密
 * @param cipherText 密文（Base64编码）
 * @param key 密钥（Base64编码）
 * @param iv 初始向量（16字节）
 * @return 明文
 */
function decrypt(cipherText, key, iv) {
    // 1. Base64解码密文
    encryptedBytes = Base64.decode(cipherText)
    
    // 2. Base64解码密钥
    keyBytes = Base64.decode(key)
    
    // 3. 创建密钥规范
    secretKey = new SecretKeySpec(keyBytes, "AES")
    
    // 4. 创建Cipher
    cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    ivSpec = new IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    
    // 5. 解密
    decryptedBytes = cipher.doFinal(encryptedBytes)
    
    // 6. 返回明文
    return new String(decryptedBytes, "UTF-8")
}
```

---

## 3. 数据格式

### 3.1 Webhook请求格式

#### 3.1.1 请求头

| 请求头 | 说明 | 是否必需 |
|--------|------|----------|
| `X-Webhook-Signature` | 消息签名 | ✅ 是 |
| `X-Webhook-Timestamp` | 时间戳（毫秒） | ✅ 是 |
| `X-Webhook-Nonce` | 随机字符串 | ✅ 是 |
| `X-Webhook-Message-Id` | 消息ID（用于幂等性） | ✅ 是 |
| `Content-Type` | `application/json` | ✅ 是 |

#### 3.1.2 请求体（统一事件格式）

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
  },
  "transactionInfo": {
    "transactionId": "txn-001",
    "transactionType": "WORKPIECE_PROCESSING"
  }
}
```

**字段说明**：

| 字段 | 类型 | 说明 | 是否必需 |
|------|------|------|----------|
| `messageId` | String | 消息唯一ID（UUID） | ✅ 是 |
| `tenantId` | String | 租户ID（UUID） | ✅ 是 |
| `deviceId` | String | TB设备ID（UUID） | ✅ 是 |
| `deviceCode` | String | 设备编号（威力编号） | ✅ **是** |
| `eventType` | String | 事件类型 | ✅ 是 |
| `timestamp` | Long | 事件时间戳（毫秒） | ✅ 是 |
| `dataTimestamp` | Long | 数据时间戳（毫秒） | ✅ 是 |
| `webhookCategory` | String | Webhook分类（BUSINESS/REALTIME） | ✅ 是 |
| `eventData` | Object | 事件数据（业务相关） | ❌ 否 |
| `telemetryData` | Object | 遥测数据 | ❌ 否 |
| `metadata` | Object | 元数据 | ❌ 否 |
| `transactionInfo` | Object | 事务信息（可选） | ❌ 否 |

**重要约束**：
- ✅ **`deviceCode`字段必须存在且非空**，TB端发送前必须校验
- ✅ Portal端收到数据后，必须先验证`deviceCode`是否在`device_info`表中存在

### 3.2 接口路径规范

```
/webhook/{category}/{eventType}

示例：
/webhook/business/workpiece-start
/webhook/business/workpiece-end
/webhook/business/production
/webhook/business/alarm
/webhook/realtime/state
/webhook/realtime/telemetry
```

---

## 4. 设备匹配规则

### 4.1 核心要求

**TB端要求**：
- ✅ 所有Webhook请求（BUSINESS和REALTIME）必须携带`device_code`字段
- ✅ TB端发送前必须校验`device_code`非空，否则跳过发送

**Portal端要求**：
- ✅ 收到Webhook后，首先提取`device_code`
- ✅ 查询`device_info`表，匹配`device_code`
- ✅ **只有匹配成功的数据才进行处理**，未匹配的数据直接返回200（快速ACK），不进入后续流程

### 4.2 设备匹配流程

```
1. 接收Webhook请求：
   POST /webhook/{category}/{eventType}

2. 安全验证：
   - 验证签名
   - 验证时间戳
   - 验证nonce
   - 幂等性检查（基于messageId）

3. 提取device_code：
   deviceCode = requestBody.get("deviceCode")
   if (deviceCode == null || deviceCode.isEmpty()) {
       log.warn("device_code为空，跳过处理: messageId={}", messageId)
       return 200 "success"  // 快速ACK，但不处理
   }

4. 查询device_info表：
   SELECT id, device_code, device_name, tenant_uuid, tb_device_id, 
          device_status, is_monitored, deleted
   FROM device_info
   WHERE device_code = ? AND deleted = 0

5. 设备匹配判断：
   if (deviceInfo == null) {
       log.warn("设备未匹配，跳过处理: deviceCode={}, messageId={}", 
                deviceCode, messageId)
       return 200 "success"  // 快速ACK，但不处理
   }

   if (deviceInfo.getDeviceStatus() != "ACTIVE") {
       log.warn("设备状态非ACTIVE，跳过处理: deviceCode={}, status={}", 
                deviceCode, deviceInfo.getDeviceStatus())
       return 200 "success"  // 快速ACK，但不处理
   }

   if (deviceInfo.getIsMonitored() != 1) {
       log.warn("设备未启用监控，跳过处理: deviceCode={}", deviceCode)
       return 200 "success"  // 快速ACK，但不处理
   }

6. 设备匹配成功，继续处理：
   - 写入收件箱（BUSINESS）或Redis缓存（REALTIME）
   - 异步处理业务逻辑
```

### 4.3 设备匹配伪代码

```java
/**
 * 设备匹配服务
 */
@Service
public class DeviceMatchingService {
    
    @Autowired
    private DeviceInfoMapper deviceInfoMapper;
    
    /**
     * 匹配设备
     * @param deviceCode 设备编号
     * @return 设备信息，如果未匹配则返回null
     */
    public DeviceInfo matchDevice(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            log.warn("device_code为空");
            return null;
        }
        
        // 查询设备信息
        DeviceInfo deviceInfo = deviceInfoMapper.selectByDeviceCode(deviceCode);
        
        if (deviceInfo == null) {
            log.warn("设备未找到: deviceCode={}", deviceCode);
            return null;
        }
        
        // 检查设备状态
        if (!"ACTIVE".equals(deviceInfo.getDeviceStatus())) {
            log.warn("设备状态非ACTIVE: deviceCode={}, status={}", 
                     deviceCode, deviceInfo.getDeviceStatus());
            return null;
        }
        
        // 检查是否启用监控
        if (deviceInfo.getIsMonitored() != 1) {
            log.warn("设备未启用监控: deviceCode={}", deviceCode);
            return null;
        }
        
        return deviceInfo;
    }
}

/**
 * Webhook接收Controller
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {
    
    @Autowired
    private DeviceMatchingService deviceMatchingService;
    
    @PostMapping("/{category}/{eventType}")
    public ResponseEntity<String> receiveWebhook(
            @PathVariable String category,
            @PathVariable String eventType,
            HttpServletRequest request) {
        
        // 1. 安全验证（签名、时间戳、nonce、幂等性）
        if (!validateSecurity(request)) {
            return ResponseEntity.status(401).body("验证失败");
        }
        
        // 2. 解析请求体
        WebhookEventData eventData = parseRequestBody(request);
        
        // 3. 提取device_code
        String deviceCode = eventData.getDeviceCode();
        if (StringUtils.isBlank(deviceCode)) {
            log.warn("device_code为空，跳过处理: messageId={}", 
                     eventData.getMessageId());
            return ResponseEntity.ok("success");  // 快速ACK
        }
        
        // 4. 设备匹配
        DeviceInfo deviceInfo = deviceMatchingService.matchDevice(deviceCode);
        if (deviceInfo == null) {
            log.warn("设备未匹配，跳过处理: deviceCode={}, messageId={}", 
                     deviceCode, eventData.getMessageId());
            return ResponseEntity.ok("success");  // 快速ACK
        }
        
        // 5. 设备匹配成功，继续处理
        if ("BUSINESS".equalsIgnoreCase(category)) {
            // 业务数据：写入收件箱
            webhookInboxService.save(eventData, deviceInfo);
        } else if ("REALTIME".equalsIgnoreCase(category)) {
            // 实时数据：写入Redis缓存
            realtimeDataService.cache(eventData, deviceInfo);
        }
        
        // 6. 快速ACK
        return ResponseEntity.ok("success");
    }
}
```

### 4.4 Redis缓存结构（REALTIME数据）

**Key格式**：
```
realtime:device:{deviceCode}:{eventType}
```

**Value格式**：
```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceCode": "M001",
  "eventType": "STATE",
  "timestamp": 1704067200000,
  "data": {
    "state": "RUNNING",
    "spindleSpeed": 1500,
    "temperature": 75.5
  }
}
```

**TTL**：30分钟（可配置）

---

## 5. Portal端处理流程

### 5.1 完整处理流程

```
TB发送Webhook请求
    │
    ▼
Portal Controller接收
    │
    ├─ 1. 安全验证
    │   ├─ 验证签名（SHA-256）
    │   ├─ 验证时间戳（±5分钟）
    │   ├─ 验证nonce（Redis去重，5分钟TTL）
    │   └─ 幂等性检查（Redis，基于messageId，24小时TTL）
    │
    ├─ 2. 提取device_code
    │   └─ 如果为空，直接返回200（快速ACK）
    │
    ├─ 3. 设备匹配
    │   ├─ 查询device_info表（device_code）
    │   ├─ 检查设备状态（ACTIVE）
    │   ├─ 检查是否启用监控（is_monitored = 1）
    │   └─ 如果未匹配，直接返回200（快速ACK）
    │
    ├─ 4. 设备匹配成功，继续处理
    │   │
    │   ├─ BUSINESS数据：
    │   │   ├─ 写入webhook_inbox表（status=PENDING）
    │   │   └─ 快速返回200（快速ACK）
    │   │
    │   └─ REALTIME数据：
    │       ├─ 写入Redis缓存（realtime:device:{deviceCode}:{eventType}）
    │       ├─ WebSocket推送（可选）
    │       └─ 快速返回200（快速ACK）
    │
    ▼
异步Worker处理（仅BUSINESS数据）
    │
    ├─ 扫描webhook_inbox表（status=PENDING）
    ├─ 按eventType分发到业务Handler
    ├─ 处理成功：标记status=SUCCESS
    └─ 处理失败：标记status=FAILED，写入webhook_fail_log表
```

### 5.2 幂等性保障

**实现方式**：基于`messageId`，使用Redis实现

**Key格式**：
```
webhook:idempotent:{messageId}
```

**Value**：`"1"`（固定值）

**TTL**：24小时

**伪代码**：
```java
public boolean tryConsume(String messageId) {
    if (StringUtils.isBlank(messageId)) {
        return true;  // 允许处理（兼容无messageId的情况）
    }
    
    String key = "webhook:idempotent:" + messageId;
    Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(key, "1", Duration.ofSeconds(86400));  // 24小时
    
    return Boolean.TRUE.equals(success);
}
```

### 5.3 收件箱处理（BUSINESS数据）

**表结构**：`webhook_inbox`

**处理流程**：
```
1. Worker扫描：
   SELECT * FROM webhook_inbox
   WHERE status IN ('PENDING', 'FAILED')
     AND next_retry_time <= NOW()
   ORDER BY received_time ASC
   LIMIT 100

2. 批量处理：
   for (record in records) {
       try {
           // 按eventType分发到业务Handler
           eventHandler.handle(record);
           
           // 标记成功
           record.status = 'SUCCESS';
           record.processed_time = NOW();
       } catch (Exception e) {
           // 标记失败
           record.status = 'FAILED';
           record.process_count++;
           record.next_retry_time = calculateNextRetryTime(record.process_count);
           record.last_error = e.getMessage();
           
           // 写入失败日志
           webhookFailLogService.save(record, e);
       }
   }

3. 更新数据库
```

### 5.4 Redis缓存处理（REALTIME数据）

**缓存Key**：
```
realtime:device:{deviceCode}:{eventType}
```

**缓存Value**：
```json
{
  "messageId": "xxx",
  "deviceCode": "M001",
  "eventType": "STATE",
  "timestamp": 1704067200000,
  "data": { ... }
}
```

**TTL**：30分钟

**处理流程**：
```
1. 设备匹配成功后，直接写入Redis：
   redisTemplate.opsForValue().set(
       "realtime:device:" + deviceCode + ":" + eventType,
       jsonData,
       Duration.ofMinutes(30)
   )

2. WebSocket推送（可选）：
   webSocketService.pushToClients(deviceCode, eventType, data)

3. 快速返回200
```

---

## 6. 配置要求

### 6.1 TB端配置

**application.yml**：
```yaml
event-detector:
  webhook:
    enabled: true
    base-url: "https://portal.example.com"
    token: "${WEBHOOK_TOKEN}"
    encoding-aes-key: "${WEBHOOK_ENCODING_AES_KEY}"  # 可选
    timeout-ms: 5000
    retry-count: 5
    retry-interval-ms: 2000
```

### 6.2 Portal端配置

**application.yml**：
```yaml
webhook:
  security:
    # 验证Token（必须与TB端一致）
    token: "${WEBHOOK_TOKEN}"
    # 时间戳有效期（毫秒，默认5分钟）
    timestamp-validity-ms: 300000
    # 加密密钥（可选，如果TB端使用加密）
    encoding-aes-key: "${WEBHOOK_ENCODING_AES_KEY}"
  
  device:
    # 设备匹配缓存（Redis）
    cache-enabled: true
    cache-ttl-seconds: 3600  # 1小时
  
  realtime:
    # 实时数据缓存TTL（秒）
    cache-ttl-seconds: 1800  # 30分钟
  
  inbox:
    # Worker批量大小
    batch-size: 100
    # Worker扫描间隔（秒）
    scan-interval-seconds: 10
    # 最大重试次数
    max-retry-count: 5
    # 重试间隔（秒，指数退避）
    retry-interval-base-seconds: 60
```

---

## 7. 错误处理

### 7.1 错误码定义

| HTTP状态码 | 说明 | 处理方式 |
|------------|------|----------|
| `200` | 成功 | TB端标记SUCCESS |
| `400` | 请求参数错误 | TB端记录错误，不重试 |
| `401` | 验证失败（签名/时间戳） | TB端记录错误，不重试 |
| `500` | 服务器错误 | TB端重试（指数退避） |

### 7.2 Portal端错误处理

**安全验证失败**：
- 返回401，记录日志
- 不进入后续处理流程

**设备未匹配**：
- 返回200（快速ACK）
- 记录警告日志
- 不进入后续处理流程

**业务处理失败**：
- 返回200（快速ACK）
- 写入`webhook_inbox`表（status=FAILED）
- 写入`webhook_fail_log`表
- 异步Worker重试

---

## 8. 监控指标

### 8.1 Portal端监控指标

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| Webhook接收成功率 | 接收成功数 / 总接收数 | < 99% |
| 设备匹配率 | 匹配成功数 / 总接收数 | < 95% |
| 收件箱积压数 | `webhook_inbox`表中PENDING数量 | > 200 |
| 处理失败率 | 失败数 / 总处理数 | > 5% |
| 实时数据缓存命中率 | 缓存命中数 / 总查询数 | < 90% |

### 8.2 日志要求

**关键日志点**：
1. Webhook接收（INFO）
2. 安全验证失败（WARN）
3. 设备未匹配（WARN）
4. 业务处理成功（INFO）
5. 业务处理失败（ERROR）

**日志格式**：
```
[Webhook] [接收] messageId={}, deviceCode={}, eventType={}, category={}
[Webhook] [验证失败] reason={}, messageId={}
[Webhook] [设备未匹配] deviceCode={}, messageId={}
[Webhook] [处理成功] messageId={}, eventType={}
[Webhook] [处理失败] messageId={}, error={}
```

---

## 9. 测试验证

### 9.1 单元测试

1. **签名算法测试**：
   - 测试签名生成和验证
   - 测试时间戳验证
   - 测试nonce去重

2. **设备匹配测试**：
   - 测试设备存在且状态正常
   - 测试设备不存在
   - 测试设备状态非ACTIVE
   - 测试设备未启用监控

3. **幂等性测试**：
   - 测试重复消息处理
   - 测试Redis TTL过期

### 9.2 集成测试

1. **端到端测试**：
   - TB发送 → Portal接收 → 设备匹配 → 业务处理

2. **性能测试**：
   - 并发接收测试
   - 批量处理测试
   - Redis缓存性能测试

3. **可靠性测试**：
   - 网络异常测试
   - 数据库异常测试
   - Redis异常测试

---

## 10. 实施检查清单

### 10.1 TB端检查项

- [ ] 配置`device_code`字段提取逻辑
- [ ] 发送前校验`device_code`非空
- [ ] 配置签名算法（SHA-256）
- [ ] 配置Token（与Portal一致）
- [ ] 配置Webhook URL
- [ ] 测试URL验证接口
- [ ] 测试消息发送接口

### 10.2 Portal端检查项

- [ ] 实现签名验证服务
- [ ] 实现时间戳验证
- [ ] 实现nonce去重（Redis）
- [ ] 实现幂等性检查（Redis）
- [ ] 实现设备匹配服务
- [ ] 实现Webhook接收Controller
- [ ] 实现收件箱表（`webhook_inbox`）
- [ ] 实现失败日志表（`webhook_fail_log`）
- [ ] 实现异步Worker
- [ ] 实现Redis缓存（REALTIME数据）
- [ ] 配置监控告警
- [ ] 编写单元测试
- [ ] 编写集成测试

---

## 11. 目录规划与隔离（TB ↔ Portal）

### 11.1 Portal 侧（保持与业务代码相对独立、集中）
- **Controller（统一入口）**：`iot-portal-web/src/main/java/com/weili/iot_portal/web/webhook/`
  - 统一路由 `/webhook/{category}/{eventType}`，含 URL 验证与消息接收。
- **Service（安全 + 幂等 + 分发）**：`iot-portal-service/src/main/java/com/weili/iot_portal/service/ingestion/`
  - `security/`：签名、时间戳、nonce 校验；可选 AES 解密。
  - `support/`：`WebhookIdempotentService`（Redis）、`WebhookSecurityService`。
  - `dispatcher/`：按 `eventType` 分发；`DeviceMatchingService` 负责 `device_code`→`device_info`。
  - `realtime/`：实时数据 Redis 缓存 + WebSocket 推送。
  - `business/`：业务事件入库、领域服务调用。
- **Domain DTO/VO**：`iot-portal-domain/src/main/java/com/weili/iot_portal/domain/ingestion/`
  - 事件 DTO、验签请求对象、解密后明文对象。
- **DAL（收件箱/失败日志/设备基础）**：`iot-portal-dal/src/main/java/com/weili/iot_portal/dal/`
  - `dataobject/ingestion/`：`WebhookInboxDO`、`WebhookFailLogDO`。
  - `mapper/ingestion/`：收件箱、失败日志 Mapper。
  - 设备表 `device_info` 已在现有 schema，下沉共用。
- **XXL-Job 定时任务**：`iot-portal-task/src/main/java/com/weili/iot_portal/task/webhook/`
  - 恢复任务、对账任务、积压/失败告警。
- **配置与常量**：`iot-portal-service/src/main/resources/application-*.yml` 下的 `webhook.*`；公用常量放 `ingestion/constants/`。
- **严格不改动目录**：`iot-business` 仅作参考，保持只读。

### 11.2 ThingsBoard 侧（event-detector 模块内聚）
- **Webhook 发送与安全**：`event-detector-components/src/main/java/com/custom/thingsboard/event/components/service/webhook/`
  - `WebhookSender`、`RealtimeWebhookSender`、`WebhookRequestBuilder`。
  - `security/`：`WebhookSignatureService`、`WebhookUrlValidator`。
  - `buffer/`：`WebhookBufferService` 配置与实现。
- **配置与默认值**：`event-detector-components/src/main/resources/` 下 `application*.yml`（token、encodingAesKey、重试、超时）。
- **文档与方案**：`event-detector/docs/design/`（交互架构、方案说明）；`Webhook配置和使用指南.md`。

### 11.3 隔离原则
- 入口、校验、幂等、分发、缓存、定时任务分别有独立包；通过接口解耦，避免与业务模块混杂。
- 设备匹配服务只依赖 `device_info` 与公共 DAL，不侵入业务子域。
- 配置集中在 `webhook.*` 命名空间，避免与其他业务配置冲突。
- TB 与 Portal 的对接逻辑仅在上述路径变更，避免跨模块散落。

---

## 11. 参考文档

- [TB与Portal交互架构](../thingsboard/event-detector/docs/design/TB与Portal交互架构.md)
- [Portal端Webhook接收功能开发任务清单](./Webhook接收功能开发任务清单.md)
- [Webhook配置和使用指南](../thingsboard/event-detector/Webhook配置和使用指南.md)

---

**文档版本**：v1.0  
**最后更新**：2025-12-05

