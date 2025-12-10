package com.weili.iot_portal.web.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Webhook安全配置
 * 配置Webhook接口允许匿名访问（使用签名验证而非Spring Security认证）
 * 
 * 行业通用做法：
 * 1. 为Webhook路径创建独立的SecurityFilterChain，优先级最高
 * 2. 使用签名验证（X-Webhook-Signature）替代传统认证
 * 3. 使用条件注解避免与现有配置冲突
 * 4. 只处理/webhook/**路径，不影响其他接口
 * 
 * 注意：
 * - 不使用@EnableWebSecurity，避免与现有OAuth2配置冲突
 * - 使用@Order(1)确保优先执行，匹配到/webhook/**后不再执行其他配置
 * - 使用@ConditionalOnWebApplication确保只在Web应用时生效
 * 
 * @author Custom
 */
@Configuration
@ConditionalOnWebApplication
public class WebhookSecurityConfig {

    /**
     * 配置Webhook接口允许匿名访问
     * 
     * 工作原理：
     * 1. Spring Security会按@Order顺序匹配SecurityFilterChain
     * 2. 使用securityMatcher("/webhook/**")精确匹配Webhook路径
     * 3. 匹配成功后，配置permitAll()允许匿名访问
     * 4. 禁用CSRF保护（Webhook使用签名验证）
     * 5. 其他路径继续由OAuth2 Security配置处理
     * 
     * 安全机制：
     * - 签名验证：X-Webhook-Signature（在Controller中验证）
     * - 时间戳验证：防止重放攻击
     * - nonce验证：防止重复请求
     * - 设备匹配：只处理已注册设备的数据
     */
    @Bean
    @Order(1)  // 最高优先级，确保Webhook路径优先匹配
    public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // 只匹配/webhook/**路径
            .securityMatcher("/webhook/**")
            // 允许匿名访问（不使用Spring Security认证）
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            // 禁用CSRF保护（Webhook使用签名验证，不需要CSRF token）
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/webhook/**")
            );
        
        return http.build();
    }
}

