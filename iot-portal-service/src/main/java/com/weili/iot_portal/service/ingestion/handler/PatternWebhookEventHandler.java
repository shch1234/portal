package com.weili.iot_portal.service.ingestion.handler;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 简单的通配符 Handler 基类，支持 patterns 中的 * 通配（转正则）
 */
@Order(0)
public abstract class PatternWebhookEventHandler implements WebhookEventHandler {

    private final List<Pattern> compiledPatterns;

    protected PatternWebhookEventHandler(List<String> patterns) {
        this.compiledPatterns = patterns.stream()
                .filter(StringUtils::isNotBlank)
                .map(PatternWebhookEventHandler::wildcardToRegex)
                .map(Pattern::compile)
                .toList();
    }

    @Override
    public boolean supports(String eventType) {
        if (StringUtils.isBlank(eventType)) {
            return false;
        }
        return compiledPatterns.stream().anyMatch(p -> p.matcher(eventType).matches());
    }

    private static String wildcardToRegex(String pattern) {
        // 将 * 转为 .*
        String escaped = Pattern.quote(pattern).replace("\\*", ".*");
        return "^" + escaped + "$";
    }
}

