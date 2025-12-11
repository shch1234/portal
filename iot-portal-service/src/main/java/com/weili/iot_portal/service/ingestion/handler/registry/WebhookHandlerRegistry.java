package com.weili.iot_portal.service.ingestion.handler.registry;

import com.weili.iot_portal.service.ingestion.handler.WebhookEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Webhook Handler 注册表：按 supports 顺序匹配
 */
@Slf4j
@Component
public class WebhookHandlerRegistry {

    private final List<WebhookEventHandler> codeHandlers;
    private final List<PatternHandler> configExactHandlers;
    private final List<PatternHandler> configWildcardHandlers;
    private final Map<String, Optional<WebhookEventHandler>> matchCache = new ConcurrentHashMap<>();

    @Autowired
    public WebhookHandlerRegistry(List<WebhookEventHandler> handlers,
                                  WebhookRoutingProperties routingProperties,
                                  BeanFactory beanFactory) {
        // 代码注册的 handler，按 order 排序
        this.codeHandlers = handlers.stream()
                .sorted(Comparator.comparingInt(WebhookEventHandler::order))
                .toList();

        // 配置路由（精确 / 通配）
        List<PatternHandler> exact = new ArrayList<>();
        List<PatternHandler> wildcard = new ArrayList<>();
        for (Map.Entry<String, String> entry : routingProperties.getRouting().entrySet()) {
            String pattern = entry.getKey();
            String beanName = entry.getValue();
            if (StringUtils.isAnyBlank(pattern, beanName)) {
                continue;
            }
            try {
                WebhookEventHandler handler = beanFactory.getBean(beanName, WebhookEventHandler.class);
                if (pattern.contains("*")) {
                    wildcard.add(new PatternHandler(patternToRegex(pattern), handler));
                } else {
                    exact.add(new PatternHandler(Pattern.compile("^" + Pattern.quote(pattern) + "$"), handler));
                }
            } catch (BeansException ex) {
                log.warn("Webhook routing config bean not found: pattern={}, beanName={}", pattern, beanName);
            }
        }
        this.configExactHandlers = exact;
        this.configWildcardHandlers = wildcard;

        log.info("Webhook handlers registered (code): {}", this.codeHandlers.stream()
                .map(h -> h.getClass().getSimpleName())
                .toList());
        log.info("Webhook handlers registered (config exact): {}", this.configExactHandlers.stream()
                .map(p -> p.handler.getClass().getSimpleName())
                .toList());
        log.info("Webhook handlers registered (config wildcard): {}", this.configWildcardHandlers.stream()
                .map(p -> p.handler.getClass().getSimpleName())
                .toList());
    }

    public Optional<WebhookEventHandler> resolve(String eventType) {
        if (StringUtils.isBlank(eventType)) {
            log.debug("[Webhook-Registry] eventType为空，返回空");
            return Optional.empty();
        }
        
        log.debug("[Webhook-Registry] 解析Handler: eventType={}", eventType);
        Optional<WebhookEventHandler> result = matchCache.computeIfAbsent(eventType, this::doResolve);
        
        if (result.isPresent()) {
            log.debug("[Webhook-Registry] 找到Handler: eventType={}, handlerClass={}", 
                eventType, result.get().getClass().getSimpleName());
        } else {
            log.debug("[Webhook-Registry] 未找到Handler: eventType={}", eventType);
        }
        
        return result;
    }

    private Optional<WebhookEventHandler> doResolve(String eventType) {
        log.debug("[Webhook-Registry] 开始匹配Handler: eventType={}", eventType);
        
        // 1) 代码 handler（包含精确/通配，按 order）
        log.debug("[Webhook-Registry] [步骤1] 检查代码注册的Handler: count={}", codeHandlers.size());
        for (WebhookEventHandler handler : codeHandlers) {
            boolean supports = handler.supports(eventType);
            log.debug("[Webhook-Registry] [步骤1] 检查Handler: handlerClass={}, supports={}", 
                handler.getClass().getSimpleName(), supports);
            if (supports) {
                log.debug("[Webhook-Registry] [步骤1] 匹配成功: eventType={}, handlerClass={}", 
                    eventType, handler.getClass().getSimpleName());
                return Optional.of(handler);
            }
        }
        
        // 2) 配置精确
        log.debug("[Webhook-Registry] [步骤2] 检查配置精确匹配: count={}", configExactHandlers.size());
        for (PatternHandler ph : configExactHandlers) {
            boolean matches = ph.pattern.matcher(eventType).matches();
            log.debug("[Webhook-Registry] [步骤2] 检查精确模式: pattern={}, matches={}, handlerClass={}", 
                ph.pattern.pattern(), matches, ph.handler.getClass().getSimpleName());
            if (matches) {
                log.debug("[Webhook-Registry] [步骤2] 精确匹配成功: eventType={}, handlerClass={}", 
                    eventType, ph.handler.getClass().getSimpleName());
                return Optional.of(ph.handler);
            }
        }
        
        // 3) 配置通配
        log.debug("[Webhook-Registry] [步骤3] 检查配置通配匹配: count={}", configWildcardHandlers.size());
        for (PatternHandler ph : configWildcardHandlers) {
            boolean matches = ph.pattern.matcher(eventType).matches();
            log.debug("[Webhook-Registry] [步骤3] 检查通配模式: pattern={}, matches={}, handlerClass={}", 
                ph.pattern.pattern(), matches, ph.handler.getClass().getSimpleName());
            if (matches) {
                log.debug("[Webhook-Registry] [步骤3] 通配匹配成功: eventType={}, handlerClass={}", 
                    eventType, ph.handler.getClass().getSimpleName());
                return Optional.of(ph.handler);
            }
        }
        
        log.debug("[Webhook-Registry] 未找到匹配的Handler: eventType={}", eventType);
        return Optional.empty();
    }

    private Pattern patternToRegex(String pattern) {
        // 将通配符模式转换为正则表达式
        // 例如：EVENT_* -> ^EVENT_.*$
        // 需要转义正则特殊字符（除了*），然后将*替换为.*
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                // 转义正则特殊字符
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile("^" + regex + "$");
    }

    private record PatternHandler(Pattern pattern, WebhookEventHandler handler) {
    }
}

