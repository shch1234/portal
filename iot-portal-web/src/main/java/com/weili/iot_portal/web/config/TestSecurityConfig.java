package com.weili.iot_portal.web.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 测试用Security配置（临时禁用OAuth2时使用）
 * 
 * 注意：
 * 1. 此配置仅在禁用OAuth2时生效（通过条件注解控制）
 * 2. 允许所有接口匿名访问，仅用于测试Webhook功能
 * 3. 生产环境请勿使用此配置
 * 
 * 使用方法：
 * 在application.properties中设置：test.security.oauth2.disabled=true
 * 
 * @author Custom
 */
@Configuration
@ConditionalOnProperty(
    name = "test.security.oauth2.disabled",
    havingValue = "true",
    matchIfMissing = false
)
public class TestSecurityConfig {

    /**
     * 测试用Security配置
     * 允许所有接口匿名访问，禁用CSRF保护
     * 
     * 注意：
     * - 使用@Order(100)确保在Webhook配置之后执行
     * - 使用securityMatcher排除/webhook/**路径，避免与Webhook配置冲突
     * - Webhook配置使用@Order(1)，优先匹配/webhook/**路径
     */
    @Bean
    @Order(100)  // 在Webhook配置之后执行
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // 排除/webhook/**路径，避免与WebhookSecurityConfig冲突
            .securityMatcher(request -> {
                String path = request.getRequestURI();
                return path != null && !path.startsWith("/webhook/");
            })
            .authorizeHttpRequests(auth -> auth
                // 允许所有接口匿名访问（仅用于测试，不包括/webhook/**）
                .anyRequest().permitAll()
            )
            // 禁用CSRF保护（仅用于测试）
            .csrf(csrf -> csrf.disable());
        
        return http.build();
    }
}

