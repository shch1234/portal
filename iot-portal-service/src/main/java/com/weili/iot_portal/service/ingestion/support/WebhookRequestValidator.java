package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.service.ingestion.WebhookSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Webhook 请求校验器
 * 负责校验 Webhook 请求的参数、格式和安全
 */
@Slf4j
@Component
public class WebhookRequestValidator {

    @Autowired
    private WebhookSecurityService securityService;

    /**
     * 完整校验 Webhook 请求
     * 
     * @param rawBody 原始请求体
     * @param signature 签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param headerSecret Header密钥
     */
    public void validate(String rawBody, String signature, String timestamp, 
                        String nonce, String headerSecret) {
        log.debug("[Webhook-校验] ====== 开始校验Webhook请求 ======");
        
        // 1. 基础参数校验
        if (StringUtils.isAnyBlank(signature, timestamp, nonce)) {
            log.warn("[Webhook-校验] 缺少签名参数: signature={}, timestamp={}, nonce={}", 
                signature != null, timestamp != null, nonce != null);
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_SIGNATURE_PARAMS_MISSING);
        }
        
        // 2. 请求体校验
        if (StringUtils.isBlank(rawBody) || "invalid".equals(rawBody)) {
            log.warn("[Webhook-校验] 无效的请求体");
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_SIGNATURE_PARAMS_MISSING, "无效的请求体");
        }
        log.debug("[Webhook-校验] 请求体校验通过，大小: {} bytes", rawBody.length());
        
        // 3. Header 密钥校验
        log.debug("[Webhook-校验] Header密钥校验");
        securityService.validate(headerSecret);
        
        // 4. 签名验证（包含时间戳和nonce校验）
        log.debug("[Webhook-校验] 签名验证: signature={}, timestamp={}, nonce={}", 
            signature.length() > 8 ? signature.substring(0, 8) + "..." : signature, timestamp, nonce);
        securityService.validateSignature(signature, timestamp, nonce, rawBody);
        
        log.debug("[Webhook-校验] ====== Webhook请求校验通过 ======");
    }
}

