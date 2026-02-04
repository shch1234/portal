package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import org.apache.commons.lang3.StringUtils;
import com.weili.iot_portal.service.cache.SafeRedisOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;

/**
 * Webhook 安全校验
 */
@Component
public class WebhookSecurityService {

    @Value("${portal.webhook.secret:}")
    private String globalSecret;

    @Value("${webhook.security.token:}")
    private String signatureToken;

    @Value("${webhook.security.timestamp-validity-ms:300000}")
    private long timestampValidityMs;

    @Value("${webhook.security.nonce-ttl-seconds:300}")
    private long nonceTtlSeconds;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private SafeRedisOperations safeRedisOperations;

    private static final String NONCE_PREFIX = "webhook:nonce:";

    public void validate(String providedSecret) {
        if (StringUtils.isBlank(globalSecret)) {
            return;
        }
        if (!StringUtils.equals(globalSecret, providedSecret)) {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_SECRET_VALIDATION_FAILED);
        }
    }

    /**
     * 校验签名 + 时间戳 + nonce
     * 注意：调用此方法前，调用方应确保 signature、timestamp、nonce 参数不为空
     * 
     * @param signature 签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param messageBody 消息体
     */
    public void validateSignature(String signature, String timestamp, String nonce, String messageBody) {
        // 检查配置的签名token是否存在
        if (StringUtils.isBlank(signatureToken)) {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_SIGNATURE_PARAMS_MISSING);
        }
        
        // 验证时间戳有效性
        if (!verifyTimestamp(timestamp)) {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_REQUEST_EXPIRED);
        }
        
        // 验证nonce防重放
        if (!verifyNonce(nonce)) {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_DUPLICATE_REQUEST);
        }
        
        // 验证签名
        if (!verifySignatureInternal(signature, timestamp, nonce, messageBody)) {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_SIGNATURE_VALIDATION_FAILED);
        }
    }

    private boolean verifyTimestamp(String timestamp) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis();
            return Math.abs(currentTime - requestTime) <= timestampValidityMs;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean verifyNonce(String nonce) {
        String key = NONCE_PREFIX + nonce;
        // 使用工具类统一处理 Redis 操作
        // 注意：nonce 验证是安全相关的，失败时必须抛出异常
        Boolean success = safeRedisOperations.safeExecute(
            () -> redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(nonceTtlSeconds)),
            null
        );
        
        if (success == null) {
            // Redis 操作失败：为了安全，拒绝请求（避免重放攻击）
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_SIGNATURE_VALIDATION_FAILED, 
                    "Nonce验证失败: Redis操作超时或连接失败");
        }
        
        return Boolean.TRUE.equals(success);
    }

    private boolean verifySignatureInternal(String signature, String timestamp, String nonce, String messageBody) {
        String[] arr = {signatureToken, timestamp, nonce, messageBody == null ? "" : messageBody};
        Arrays.sort(arr);
        String joined = String.join("", arr);
        String calculated = sha256(joined);
        return StringUtils.equals(calculated, signature);
    }

    private String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_SIGNATURE_ALGORITHM_UNAVAILABLE);
        }
    }
}

