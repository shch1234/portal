package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook 时间戳工具类
 * 统一处理时间戳单位转换
 */
@Slf4j
public class WebhookTimestampUtils {

    /**
     * 统一转换时间戳：ThingsBoard 发送的是毫秒级时间戳，统一转换为秒级
     * 判断规则：如果时间戳大于 10^12（2001-09-09 的毫秒级时间戳），则认为是毫秒，需要转换
     * 
     * @param request Webhook请求对象
     */
    public static void normalizeTimestamp(WebhookRequest request) {
        if (request == null) {
            return;
        }
        
        if (request.getTimestamp() != null) {
            Long original = request.getTimestamp();
            if (original > 1_000_000_000_000L) {
                // 毫秒级时间戳，转换为秒
                request.setTimestamp(original / 1000);
                log.debug("[Webhook-Timestamp] 时间戳从毫秒转换为秒: timestamp={} -> {}", original, request.getTimestamp());
            }
        }
        
        if (request.getDataTimestamp() != null) {
            Long original = request.getDataTimestamp();
            if (original > 1_000_000_000_000L) {
                // 毫秒级时间戳，转换为秒
                request.setDataTimestamp(original / 1000);
                log.debug("[Webhook-Timestamp] 数据时间戳从毫秒转换为秒: dataTimestamp={} -> {}", original, request.getDataTimestamp());
            }
        }
    }
}

