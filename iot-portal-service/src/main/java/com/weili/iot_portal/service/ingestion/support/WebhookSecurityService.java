package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;
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

    private static final String NONCE_PREFIX = "webhook:nonce:";

    public void validate(String providedSecret) {
        if (StringUtils.isBlank(globalSecret)) {
            return;
        }
        if (!StringUtils.equals(globalSecret, providedSecret)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "Webhook密钥验证失败");
        }
    }

    /**
     * 校验签名 + 时间戳 + nonce
     */
    public void validateSignature(String signature, String timestamp, String nonce, String messageBody) {
        if (StringUtils.isAnyBlank(signatureToken, signature, timestamp, nonce)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "Webhook签名参数缺失");
        }
        if (!verifyTimestamp(timestamp)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "Webhook请求已过期");
        }
        if (!verifyNonce(nonce)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "Webhook重复请求");
        }
        if (!verifySignatureInternal(signature, timestamp, nonce, messageBody)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "Webhook签名验证失败");
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
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(nonceTtlSeconds));
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
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "签名算法不可用");
        }
    }
}

