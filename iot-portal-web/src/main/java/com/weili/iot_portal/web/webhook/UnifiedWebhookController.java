package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.exception.BaseException;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookReceiveService;
import com.weili.iot_portal.service.ingestion.WebhookSecurityService;
import com.weili.iot_portal.service.ingestion.support.WebhookRequestValidator;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@PermitAll
@RestController
public class UnifiedWebhookController {

    @Autowired
    private WebhookReceiveService webhookReceiveService;

    @Autowired
    private WebhookSecurityService webhookSecurityService;

    @Autowired
    private WebhookRequestValidator webhookRequestValidator;

    @Autowired(required = false)
    @Qualifier("webhookAsyncExecutor")
    private Executor webhookAsyncExecutor;

    /**
     * URL 验证：返回 echostr
     */
    @GetMapping("/webhook/{category}/{eventType}")
    public ResponseEntity<String> validateUrl(@PathVariable String category,
                                              @PathVariable String eventType,
                                              @RequestParam("msg_signature") String signature,
                                              @RequestParam("timestamp") String timestamp,
                                              @RequestParam("nonce") String nonce,
                                              @RequestParam("echostr") String echostr) {
        try {
            webhookSecurityService.validateSignature(signature, timestamp, nonce, echostr);
            return ResponseEntity.ok(echostr);
        } catch (ServiceException ex) {
            return ResponseEntity.status(401).body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Webhook URL 验证失败", ex);
            return ResponseEntity.status(500).body("validate failed");
        }
    }

    /**
     * Webhook 接收：BUSINESS / REALTIME
     */
    @PostMapping("/webhook/{category}/{eventType}")
    public ResponseEntity<String> receive(@PathVariable String category,
                                          @PathVariable String eventType,
                                          @RequestBody String rawBody,
                                          @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
                                          @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
                                          @RequestHeader(value = "X-Webhook-Nonce", required = false) String nonce,
                                          @RequestHeader(value = "X-Webhook-Secret", required = false) String headerSecret) {
        long startTime = System.currentTimeMillis();
        try {
            log.debug("[Webhook-接收] ====== 开始接收Webhook请求 ======");
            log.debug("[Webhook-接收] URL路径: category={}, eventType={}", category, eventType);
            log.debug("[Webhook-接收] 请求头: signature={}, timestamp={}, nonce={}, headerSecret={}", 
                signature != null ? signature.substring(0, Math.min(8, signature.length())) + "..." : "null",
                timestamp, nonce, headerSecret != null ? "***" : "null");
            log.debug("[Webhook-接收] 原始请求体大小: {} bytes", rawBody != null ? rawBody.length() : 0);
            
            // 校验请求参数和安全
            webhookRequestValidator.validate(rawBody, signature, timestamp, nonce, headerSecret);
            
            // 解析请求体
            WebhookRequest webhookRequest = JsonUtils.parseObject(rawBody, WebhookRequest.class);
            log.debug("[Webhook-接收] 解析后的请求数据: messageId={}, deviceCode={}, deviceId={}, eventType={}, category={}, timestamp={}, dataTimestamp={}", 
                webhookRequest.getMessageId(), webhookRequest.getDeviceCode(), webhookRequest.getDeviceId(),
                webhookRequest.getEventType(), category, webhookRequest.getTimestamp(), webhookRequest.getDataTimestamp());
            
            if (log.isDebugEnabled()) {
                log.debug("[Webhook-接收] 事件数据: eventData={}", 
                    webhookRequest.getEventData() != null ? webhookRequest.getEventData().toString() : "null");
                log.debug("[Webhook-接收] 遥测数据: telemetryData={}", 
                    webhookRequest.getTelemetryData() != null ? webhookRequest.getTelemetryData().toString() : "null");
            }
            
            // 异步处理业务逻辑，立即返回响应
            if (webhookAsyncExecutor != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        webhookReceiveService.handle(category, eventType, webhookRequest);
                    } catch (Exception e) {
                        log.error("[Webhook-接收] 异步处理失败: category={}, eventType={}, messageId={}",
                                category, eventType, webhookRequest.getMessageId(), e);
                    }
                }, webhookAsyncExecutor);
            } else {
                // 如果没有配置异步线程池，同步处理
                webhookReceiveService.handle(category, eventType, webhookRequest);
            }
            
            long cost = System.currentTimeMillis() - startTime;
            log.debug("[Webhook-接收] ====== Webhook请求接收完成 ====== 耗时: {}ms", cost);
            return ResponseEntity.ok("success");
        } catch (BaseException ex) {
            long cost = System.currentTimeMillis() - startTime;
            log.warn("[Webhook-接收] 业务异常: category={}, eventType={}, error={}, 耗时: {}ms", 
                category, eventType, ex.getMessage(), cost);
            return ResponseEntity.status(401).body(ex.getMessage());
        } catch (Exception ex) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("[Webhook-接收] 处理异常: category={}, eventType={}, 耗时: {}ms", 
                category, eventType, cost, ex);
            return ResponseEntity.status(500).body("process failed");
        }
    }
}

