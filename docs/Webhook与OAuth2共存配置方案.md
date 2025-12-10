# Webhook与OAuth2共存配置方案

**场景**：TB需要向Portal推送数据，但Portal本身有OAuth2认证保护

**问题**：如何在保持OAuth2认证的同时，允许Webhook接口匿名访问？

---

## 📋 行业通用做法

### 方案1：独立的SecurityFilterChain（推荐）✅

**原理**：为Webhook路径创建独立的`SecurityFilterChain`，使用`@Order(1)`确保优先执行。

**优点**：
- ✅ 不影响现有OAuth2配置
- ✅ 配置清晰，职责分离
- ✅ 易于维护和扩展

**实现**：

```java
@Configuration
@ConditionalOnWebApplication
public class WebhookSecurityConfig {
    
    @Bean
    @Order(1)  // 最高优先级
    public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/webhook/**")  // 只匹配Webhook路径
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()  // 允许匿名访问
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/webhook/**")  // 禁用CSRF
            );
        
        return http.build();
    }
}
```

**工作原理**：
1. Spring Security按`@Order`顺序匹配`SecurityFilterChain`
2. `@Order(1)`的配置优先执行
3. `securityMatcher("/webhook/**")`精确匹配Webhook路径
4. 匹配成功后，配置`permitAll()`允许匿名访问
5. 其他路径继续由OAuth2配置处理

---

### 方案2：在现有Security配置中添加（备选）

如果无法创建独立的配置类，可以在现有的OAuth2 Security配置中添加：

```java
@Configuration
@EnableWebSecurity
public class OAuth2SecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Webhook接口允许匿名访问（优先匹配）
                .requestMatchers("/webhook/**").permitAll()
                // 其他接口需要OAuth2认证
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> {
                // OAuth2配置
            })
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/webhook/**")
            );
        
        return http.build();
    }
}
```

---

## 🔒 安全机制

虽然Webhook接口允许匿名访问，但通过以下机制保障安全：

### 1. 签名验证

**请求头**：`X-Webhook-Signature`

**算法**：`SHA-256(token + timestamp + nonce + messageBody)`

**验证位置**：`UnifiedWebhookController.receive()`方法中

### 2. 时间戳验证

**请求头**：`X-Webhook-Timestamp`

**有效期**：5分钟（可配置）

**作用**：防止重放攻击

### 3. nonce验证

**请求头**：`X-Webhook-Nonce`

**存储**：Redis（TTL：5分钟）

**作用**：防止重复请求

### 4. 设备匹配

**验证位置**：`WebhookReceiveService.handle()`

**验证内容**：
- 设备编号（`deviceCode`）必须在`device_info`表中存在
- 设备状态必须为`ACTIVE`
- 设备必须启用监控（`is_monitored = 1`）

---

## ✅ 配置验证

### 1. 检查配置是否生效

启动Portal服务后，检查日志：

```
[WebhookSecurityConfig] Webhook SecurityFilterChain已创建
```

### 2. 测试Webhook接口

```bash
# 测试（应该返回400而不是401）
curl -X POST http://localhost:8080/webhook/realtime/telemetry \
  -H "Content-Type: application/json" \
  -d '{"test":"data"}'
```

**预期结果**：
- ✅ 返回400（缺少签名参数）- 说明已通过Spring Security
- ❌ 返回401（未认证）- 说明配置未生效

### 3. 测试OAuth2接口

```bash
# 测试其他接口（应该返回401，需要认证）
curl -X GET http://localhost:8080/api/device/list
```

**预期结果**：
- ✅ 返回401（未认证）- 说明OAuth2配置正常

---

## ⚠️ 注意事项

### 1. 配置顺序

- Webhook配置必须使用`@Order(1)`（最高优先级）
- OAuth2配置使用默认优先级或`@Order(100)`

### 2. 路径匹配

- Webhook路径：`/webhook/**`
- 确保Controller路径与配置一致

### 3. CSRF保护

- Webhook接口必须禁用CSRF
- 其他接口保持CSRF保护

### 4. 条件注解

- 使用`@ConditionalOnWebApplication`避免在非Web环境加载
- 避免与现有配置冲突

---

## 🔧 故障排查

### 问题1：启动失败，OAuth2配置错误

**错误信息**：
```
The Issuer "..." provided in the configuration metadata did not match the requested issuer "..."
```

**原因**：OAuth2配置的Issuer地址不匹配（与Webhook配置无关）

**解决**：检查OAuth2配置文件中的Issuer地址

### 问题2：Webhook接口仍然返回401

**原因**：SecurityFilterChain顺序不正确

**解决**：
1. 确保Webhook配置使用`@Order(1)`
2. 确保使用`securityMatcher("/webhook/**")`精确匹配

### 问题3：OAuth2接口无法访问

**原因**：Webhook配置影响了所有路径

**解决**：
1. 确保使用`securityMatcher("/webhook/**")`限制匹配范围
2. 不要使用`anyRequest().permitAll()`在Webhook配置中

---

## 📚 参考文档

- [Spring Security Multiple SecurityFilterChain](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html#multiple-securityfilterchain)
- [TB发送Webhook到Portal配置指南](./TB发送Webhook到Portal配置指南.md)
- [TB与Portal对接清单](./TB与Portal对接清单.md)

---

**最后更新**：2025-12-10

