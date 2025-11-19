# WebSocket实时推送实现说明

## 📋 概述

已实现通用的WebSocket实时推送组件，支持报警管理、设备管理等模块的实时数据推送需求。

## 🏗️ 架构设计

### 组件层次

```
┌─────────────────────────────────────────┐
│  iot-portal-service (公共服务模块)      │
│  - RealtimePushService (推送服务接口)   │
│  - RealtimePushServiceImpl (推送服务实现)│
│  - RealtimeMessage (消息模型)           │
│  - RealtimeSubscription (订阅模型)      │
└─────────────────────────────────────────┘
              ↑
              │ 使用
              │
┌─────────────────────────────────────────┐
│  iot-portal-web (Web层)                 │
│  - WebSocketConfig (配置类)             │
│  - RealtimeWebSocketHandler (处理器)    │
└─────────────────────────────────────────┘
              ↑
              │ 使用
              │
┌─────────────────────────────────────────┐
│  业务模块 (alarm-mgmt, device-mgmt)     │
│  - AlarmStatisticsServiceImpl           │
│  - DeviceStateService (待实现)          │
└─────────────────────────────────────────┘
```

## 📦 核心组件

### 1. 通用推送服务 (`RealtimePushService`)

**位置**：`iot-portal-service/src/main/java/com/weili/iot_portal/service/realtime/`

**功能**：
- ✅ 订阅管理：管理客户端订阅的主题
- ✅ 消息推送：推送消息到订阅的客户端
- ✅ 主题构建：构建主题键用于分组管理
- ✅ 会话管理：管理WebSocket会话

**核心方法**：
```java
// 订阅
void subscribe(RealtimeSubscription subscription);

// 取消订阅
void unsubscribe(RealtimeSubscription subscription);

// 推送消息
void push(String topic, RealtimeMessage message);

// 推送数据变化
void pushDataChanged(String topic, Object data);

// 构建主题键
String buildTopicKey(String topicPrefix, String... params);
```

### 2. WebSocket处理器 (`RealtimeWebSocketHandler`)

**位置**：`iot-portal-web/src/main/java/com/weili/iot_portal/web/realtime/handler/`

**功能**：
- ✅ 连接管理：管理WebSocket连接的建立和关闭
- ✅ 消息处理：处理客户端发送的消息（订阅、取消订阅、心跳）
- ✅ 消息推送：将服务层的消息推送给客户端
- ✅ 会话存储：维护会话ID到WebSocketSession的映射

### 3. WebSocket配置 (`WebSocketConfig`)

**位置**：`iot-portal-web/src/main/java/com/weili/iot_portal/web/realtime/config/`

**配置**：
- WebSocket端点：`/api/v1/realtime/ws`
- 支持SockJS降级（自动降级到HTTP长轮询）
- 跨域配置（生产环境需配置具体域名）

## 🔧 使用方式

### 1. 业务模块集成

#### 报警管理模块示例

```java
@Service
@RequiredArgsConstructor
public class AlarmStatisticsServiceImpl implements AlarmStatisticsService {
    
    private final RealtimePushService realtimePushService;
    
    @Override
    public CurrentAlarmDeviceCountVO getCurrentAlarmDeviceCount(...) {
        // 1. 查询数据
        CurrentAlarmDeviceCountVO result = ...;
        
        // 2. 推送实时更新
        pushAlarmCountChanged(tenantId, factoryId, workshopId, result);
        
        return result;
    }
    
    private void pushAlarmCountChanged(String tenantId, String factoryId, 
                                      String workshopId, CurrentAlarmDeviceCountVO count) {
        // 构建主题键：alarm.count:tenantId:factoryId:workshopId
        String topicKey = realtimePushService.buildTopicKey(
            "alarm.count", tenantId, factoryId, workshopId);
        // 推送数据变化消息
        realtimePushService.pushDataChanged(topicKey, count);
    }
}
```

#### 设备管理模块示例（未来扩展）

```java
@Service
@RequiredArgsConstructor
public class DeviceStateServiceImpl implements DeviceStateService {
    
    private final RealtimePushService realtimePushService;
    
    public void pushDeviceStateChanged(String tenantId, String factoryId, 
                                      String deviceId, DeviceStateVO state) {
        // 构建主题键：device.state:tenantId:factoryId:deviceId
        String topicKey = realtimePushService.buildTopicKey(
            "device.state", tenantId, factoryId, deviceId);
        // 推送数据变化消息
        realtimePushService.pushDataChanged(topicKey, state);
    }
}
```

### 2. 前端使用

#### JavaScript客户端

```javascript
// 创建WebSocket客户端
const client = new RealtimeWebSocketClient('ws://localhost:8080/api/v1/realtime/ws');

// 订阅报警数量变化
client.subscribe('alarm.count', {
    tenantId: 'xxx',
    factoryId: 'xxx',
    workshopId: 'xxx'
}, (data) => {
    console.log('报警数量变化:', data);
    // 更新UI
    updateAlarmCountUI(data);
});

// 连接
client.connect();
```

#### HTML示例

```html
<!DOCTYPE html>
<html>
<head>
    <title>报警实时推送示例</title>
    <script src="/js/realtime-websocket-example.js"></script>
</head>
<body>
    <div id="alarm-count">0</div>
    
    <script>
        // 初始化报警数量实时推送
        const client = initAlarmCountRealtime('tenant-1', 'factory-1', 'workshop-1');
        
        // 页面卸载时断开连接
        window.addEventListener('beforeunload', () => {
            client.disconnect();
        });
    </script>
</body>
</html>
```

## 📡 主题设计

### 主题命名规范

**格式**：`topicPrefix:param1:param2:...`

**示例**：
- `alarm.count:tenantId:factoryId:workshopId` - 报警数量（按车间）
- `alarm.count:tenantId:factoryId` - 报警数量（按工厂）
- `device.state:tenantId:factoryId:deviceId` - 设备状态
- `device.metrics:tenantId:factoryId:deviceId` - 设备指标

### 支持的主题类型

| 主题前缀 | 说明 | 参数 | 示例 |
|---------|------|------|------|
| `alarm.count` | 报警数量 | tenantId, factoryId, workshopId | `alarm.count:tenant-1:factory-1:workshop-1` |
| `device.state` | 设备状态 | tenantId, factoryId, deviceId | `device.state:tenant-1:factory-1:device-1` |
| `device.metrics` | 设备指标 | tenantId, factoryId, deviceId | `device.metrics:tenant-1:factory-1:device-1` |

## 🔄 消息流程

### 订阅流程

```
1. 前端建立WebSocket连接
   ↓
2. 前端发送订阅消息：{ type: 'SUBSCRIBE', topic: 'alarm.count:...' }
   ↓
3. 后端RealtimeWebSocketHandler接收订阅请求
   ↓
4. 后端RealtimePushService添加订阅
   ↓
5. 后端发送订阅确认：{ type: 'SUBSCRIBED', topic: '...' }
```

### 推送流程

```
1. 业务数据更新（如报警数量变化）
   ↓
2. 业务服务调用 RealtimePushService.pushDataChanged()
   ↓
3. RealtimePushService查找订阅该主题的所有会话
   ↓
4. RealtimePushService推送消息到所有订阅的会话
   ↓
5. 前端接收消息并更新UI
```

## ⚙️ 配置说明

### WebSocket端点

- **开发环境**：`ws://localhost:8080/api/v1/realtime/ws`
- **生产环境**：`wss://your-domain.com/api/v1/realtime/ws`

### SockJS降级

已启用SockJS支持，如果浏览器不支持WebSocket，会自动降级到HTTP长轮询。

### 跨域配置

当前配置允许所有来源（`setAllowedOrigins("*")`），生产环境应配置具体域名：

```java
registry.addHandler(realtimeWebSocketHandler, "/api/v1/realtime/ws")
        .setAllowedOrigins("https://your-domain.com")
        .withSockJS();
```

## 🎯 扩展点

### 1. 添加新的主题类型

只需要在业务服务中调用 `realtimePushService.pushDataChanged()` 即可：

```java
// 推送新的数据类型
String topicKey = realtimePushService.buildTopicKey("new.topic", param1, param2);
realtimePushService.pushDataChanged(topicKey, data);
```

### 2. 添加消息类型

在 `RealtimeMessage` 中添加新的消息类型：

```java
public static RealtimeMessage customMessage(String topic, Object data) {
    return new RealtimeMessage("CUSTOM_TYPE", topic, data);
}
```

### 3. 自定义推送策略

可以扩展 `RealtimePushServiceImpl` 实现：
- 消息队列（Kafka/RabbitMQ）集成
- 消息持久化
- 推送失败重试
- 消息过滤

## 📊 性能优化

### 1. 连接管理

- ✅ 会话存储使用 `ConcurrentHashMap`，支持并发访问
- ✅ 自动清理断开的连接
- ✅ 心跳机制保持连接活跃

### 2. 推送优化

- ✅ 批量推送：同时推送给多个订阅者
- ✅ 失败处理：发送失败自动移除无效订阅
- ✅ 异步推送：推送失败不影响主流程

### 3. 内存管理

- ✅ 连接断开时自动清理订阅
- ✅ 支持大量并发连接（受服务器资源限制）

## 🔒 安全考虑

### 1. 认证授权

当前实现未包含认证逻辑，建议添加：
- WebSocket连接时验证用户身份
- 订阅时验证用户权限
- 推送时验证数据权限

### 2. 消息验证

- 验证消息格式
- 验证主题权限
- 防止消息注入

### 3. 连接限制

- 限制单个用户的连接数
- 限制单个IP的连接数
- 限制消息推送频率

## 🐛 故障处理

### 1. 连接断开

- ✅ 自动重连机制（前端实现）
- ✅ 重连后自动重新订阅
- ✅ 清理断开的连接和订阅

### 2. 推送失败

- ✅ 记录失败日志
- ✅ 自动移除无效订阅
- ✅ 不影响主业务流程

### 3. 消息丢失

当前实现不保证消息可靠性，如需保证：
- 使用消息队列（Kafka/RabbitMQ）
- 实现消息确认机制
- 实现消息持久化

## 📝 后续优化

1. **认证授权**：添加WebSocket连接认证
2. **消息队列**：使用Kafka/RabbitMQ解耦推送
3. **集群支持**：支持多实例部署（使用Redis共享订阅）
4. **监控告警**：添加连接数、推送量等监控指标
5. **消息持久化**：离线消息存储和推送

---

**实现日期**：2025-01-XX  
**版本**：v1.0

