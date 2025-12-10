# Webhook接口Spring Security配置说明

**问题**：Portal端Webhook接口被Spring Security拦截，返回401未认证错误

**错误信息**：
```
访问受保护资源 [/webhook/realtime/telemetry]未认证
org.springframework.security.authentication.InsufficientAuthenticationException: Full authentication is required to access this resource
```

---

## 🔧 解决方案

### 方案1：使用独立的SecurityFilterChain（推荐）✅

已创建配置类：`iot-portal-web/src/main/java/com/weili/iot_portal/web/config/WebhookSecurityConfig.java`

**配置说明**：
- 使用 `@Order(1)` 确保此配置优先执行
- 配置 `/webhook/**` 路径允许匿名访问
- 禁用CSRF保护（Webhook使用签名验证）

**工作原理**：
1. Spring Security会按顺序匹配SecurityFilterChain
2. 使用 `securityMatcher("/webhook/**")` 匹配Webhook路径
3. 配置 `permitAll()` 允许匿名访问
4. Webhook接口使用签名验证（`X-Webhook-Signature`）而非Spring Security认证

---

### 方案2：在现有Security配置中添加（如果已有Security配置）

如果Portal端已有Security配置类，可以在其中添加：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 允许Webhook接口匿名访问
                .requestMatchers("/webhook/**").permitAll()
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf
                // 禁用Webhook接口的CSRF保护
                .ignoringRequestMatchers("/webhook/**")
            );
        
        return http.build();
    }
}
```

---

## ✅ 验证步骤

### 1. 重启Portal服务

重启Portal服务使配置生效。

### 2. 测试Webhook接口

使用curl测试：

```bash
# 测试Webhook接口（应该返回400而不是401）
curl -X POST http://localhost:8080/webhook/realtime/telemetry \
  -H "Content-Type: application/json" \
  -d '{"test":"data"}'
```

**预期结果**：
- ✅ 返回400（缺少签名参数）- 说明已通过Spring Security认证检查
- ❌ 返回401（未认证）- 说明配置未生效

### 3. 检查日志

查看Portal端日志，应该看到：
```
[Webhook] [接收] messageId=..., deviceCode=..., eventType=...
```

而不是：
```
访问受保护资源 [/webhook/...]未认证
```

---

## 📝 配置要点

### 1. 路径匹配

确保配置的路径与Controller路径一致：
- Controller路径：`/webhook/{category}/{eventType}`
- Security配置：`/webhook/**`（匹配所有子路径）

### 2. CSRF保护

Webhook接口需要禁用CSRF保护，因为：
- Webhook使用签名验证（`X-Webhook-Signature`）
- CSRF token不适用于外部系统调用

### 3. 优先级

如果存在多个SecurityFilterChain：
- 使用 `@Order` 注解控制执行顺序
- Webhook配置应该优先执行（`@Order(1)`）

---

## ⚠️ 注意事项

### 1. 安全考虑

虽然Webhook接口允许匿名访问，但：
- ✅ 使用签名验证（`X-Webhook-Signature`）确保请求来源
- ✅ 使用时间戳验证防止重放攻击
- ✅ 使用nonce验证防止重复请求
- ✅ 设备匹配确保只有已注册设备的数据被处理

### 2. 其他接口不受影响

此配置只影响 `/webhook/**` 路径：
- 其他接口仍然需要Spring Security认证
- 不影响现有的安全机制

---

## 🔗 相关文档

- [TB发送Webhook到Portal配置指南](./TB发送Webhook到Portal配置指南.md)
- [TB与Portal对接清单](./TB与Portal对接清单.md)
- [Webhook接收功能开发任务清单](./Webhook接收功能开发任务清单.md)

---

**最后更新**：2025-12-10

