package com.weili.iot_portal.web.security.config;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.weili.iot_portal.service.system.IAuthLoginService;
import com.weili.iot_portal.web.security.context.TransmittableThreadLocalSecurityContextHolderStrategy;
import com.weili.iot_portal.web.security.handle.TokenAuthenticationFilter;
import com.weili.iot_portal.web.security.handle.AccessDeniedHandlerImpl;
import com.weili.iot_portal.web.security.handle.AuthenticationEntryPointImpl;
import com.weili.iot_portal.web.security.handle.GlobalExceptionHandler;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spring Security 统一配置类，包含安全配置、跨域配置和相关组件配置
 */
@AutoConfiguration
@AutoConfigureOrder(-1)
@EnableMethodSecurity(securedEnabled = true)
public class SecurityAutoConfiguration {

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 认证失败处理类 Bean
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new AuthenticationEntryPointImpl();
    }

    /**
     * 权限不够处理器 Bean
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new AccessDeniedHandlerImpl();
    }

    /**
     * 创建并配置Token认证过滤器Bean
     *
     * @param globalExceptionHandler 全局异常处理器，用于处理认证过程中出现的异常
     * @param authLoginService       认证登录服务，提供用户认证相关业务逻辑
     * @return 配置好的TokenAuthenticationFilter实例
     */
    @Bean
    public TokenAuthenticationFilter authenticationTokenFilter(GlobalExceptionHandler globalExceptionHandler,
                                                               IAuthLoginService authLoginService) {
        return new TokenAuthenticationFilter(globalExceptionHandler, authLoginService);
    }

    /**
     * 声明调用 {@link SecurityContextHolder#setStrategyName(String)} 方法，
     */
    @Bean
    public MethodInvokingFactoryBean securityContextHolderMethodInvokingFactoryBean() {
        MethodInvokingFactoryBean methodInvokingFactoryBean = new MethodInvokingFactoryBean();
        methodInvokingFactoryBean.setTargetClass(SecurityContextHolder.class);
        methodInvokingFactoryBean.setTargetMethod("setStrategyName");
        methodInvokingFactoryBean.setArguments(TransmittableThreadLocalSecurityContextHolderStrategy.class.getName());
        return methodInvokingFactoryBean;
    }

    /**
     * 由于 Spring Security 创建 AuthenticationManager 对象时，没声明 @Bean 注解，导致无法被注入
     * 通过覆写父类的该方法，添加 @Bean 注解，解决该问题
     */
    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 创建 CorsFilter Bean，解决跨域问题
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterBean() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*"); // 设置访问源地址
        config.addAllowedHeader("*"); // 设置访问源请求头
        config.addAllowedMethod("*"); // 设置访问源请求方法
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 对接口配置跨域设置
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Integer.MIN_VALUE);
        return bean;
    }

    /**
     * 配置Spring Security的安全过滤器链。
     *
     * <p>该方法用于构建HTTP安全配置，包括跨域支持、CSRF禁用、会话管理策略、请求授权规则等。
     * 同时集成了自定义的认证入口点和拒绝访问处理器，并添加了基于Token的身份验证过滤器。</p>
     *
     * @param httpSecurity HTTP安全配置对象，由Spring自动注入
     * @return 构建完成的安全过滤器链
     * @throws Exception 在配置过程中可能抛出异常
     */
    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // 登出
        httpSecurity
                // 开启跨域
                .cors(Customizer.withDefaults())
                // CSRF 禁用，因为不使用 Session
                .csrf(AbstractHttpConfigurer::disable)
                // 基于 token 机制，所以不需要 Session
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(c -> c.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                // 一堆自定义的 Spring Security 处理器
                .exceptionHandling(c -> c.authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));
        // 获得 @PermitAll 带来的 URL 列表，免登录
        Multimap<HttpMethod, String> permitAllUrls = getPermitAllUrlsFromAnnotations();
        httpSecurity
                // ①：全局共享规则
                .authorizeHttpRequests(c -> c
                        // 1.1 静态资源，可匿名访问
                        .requestMatchers(HttpMethod.GET, "/*.html", "/*.html", "/*.css", "/*.js").permitAll()
                        // 1.2 健康检查端口
                        .requestMatchers("/actuator").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // 1.3 设置 @PermitAll 无需认证
                        .requestMatchers(HttpMethod.GET, permitAllUrls.get(HttpMethod.GET).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.POST, permitAllUrls.get(HttpMethod.POST).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.PUT, permitAllUrls.get(HttpMethod.PUT).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.DELETE, permitAllUrls.get(HttpMethod.DELETE).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.HEAD, permitAllUrls.get(HttpMethod.HEAD).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.PATCH, permitAllUrls.get(HttpMethod.PATCH).toArray(new String[0])).permitAll()
                )
                .authorizeHttpRequests(c -> c.anyRequest().authenticated());

        // 添加 Token Filter
        httpSecurity.addFilterBefore(authenticationTokenFilter(globalExceptionHandler(), null), UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    private Multimap<HttpMethod, String> getPermitAllUrlsFromAnnotations() {
        Multimap<HttpMethod, String> result = HashMultimap.create();
        // 获得接口对应的 HandlerMethod 集合
        RequestMappingHandlerMapping requestMappingHandlerMapping = (RequestMappingHandlerMapping)
                applicationContext.getBean("requestMappingHandlerMapping");
        Map<RequestMappingInfo, HandlerMethod> handlerMethodMap = requestMappingHandlerMapping.getHandlerMethods();
        // 获得有 @PermitAll 注解的接口
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethodMap.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            if (!handlerMethod.hasMethodAnnotation(PermitAll.class)) {
                continue;
            }
            Set<String> urls = new HashSet<>();
            //noinspection ConstantConditions
            if (entry.getKey().getPathPatternsCondition() != null) {
                urls.addAll(com.weili.basic.common.util.CollectionUtils.convertList(
                        entry.getKey().getPathPatternsCondition().getPatterns(),
                        PathPattern::getPatternString
                ));
            }
            if (urls.isEmpty()) {
                continue;
            }

            // 特殊：使用 @RequestMapping 注解，并且未写 method 属性，此时认为都需要免登录
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            if (CollectionUtils.isEmpty(methods)) {
                result.putAll(HttpMethod.GET, urls);
                result.putAll(HttpMethod.POST, urls);
                result.putAll(HttpMethod.PUT, urls);
                result.putAll(HttpMethod.DELETE, urls);
                result.putAll(HttpMethod.HEAD, urls);
                result.putAll(HttpMethod.PATCH, urls);
                continue;
            }
            // 根据请求方法，添加到 result 结果
            entry.getKey().getMethodsCondition().getMethods().forEach(requestMethod -> {
                switch (requestMethod) {
                    case GET -> result.putAll(HttpMethod.GET, urls);
                    case POST -> result.putAll(HttpMethod.POST, urls);
                    case PUT -> result.putAll(HttpMethod.PUT, urls);
                    case DELETE -> result.putAll(HttpMethod.DELETE, urls);
                    case HEAD -> result.putAll(HttpMethod.HEAD, urls);
                    case PATCH -> result.putAll(HttpMethod.PATCH, urls);
                }
            });
        }
        return result;
    }
}
