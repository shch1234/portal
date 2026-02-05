package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.exception.BaseException;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookReceiveService;
import com.weili.iot_portal.service.ingestion.WebhookSecurityService;
import com.weili.iot_portal.service.ingestion.support.WebhookRateLimiter;
import com.weili.iot_portal.service.ingestion.support.WebhookRequestValidator;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
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
    private WebhookRateLimiter webhookRateLimiter;

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
                                          @RequestHeader(value = "X-Webhook-Secret", required = false) String headerSecret,
                                          HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            log.debug("[Webhook-接收] ====== 开始接收Webhook请求 ======");
            log.debug("[Webhook-接收] URL路径: category={}, eventType={}", category, eventType);
            log.debug("[Webhook-接收] 请求头: signature={}, timestamp={}, nonce={}, headerSecret={}", 
                signature != null ? signature.substring(0, Math.min(8, signature.length())) + "..." : "null",
                timestamp, nonce, headerSecret != null ? "***" : "null");
            log.debug("[Webhook-接收] 原始请求体大小: {} bytes", rawBody != null ? rawBody.length() : 0);
            
            // 限流检查（在参数校验之前，快速失败）
            if (webhookRateLimiter != null && !webhookRateLimiter.tryAcquire()) {
                log.warn("[Webhook-接收] 请求被限流: category={}, eventType={}", category, eventType);
                return ResponseEntity.status(429).body("Too Many Requests");
            }
            
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
            // 优化：使用 "fire and forget" 模式，符合行业最佳实践（GitHub、Stripe、AWS 等）
            // 即使客户端断开连接，异步处理也会继续执行，不影响业务逻辑
            if (webhookAsyncExecutor != null) {
                // 获取当前线程的 MDC 上下文，以便在异步执行时传递
                Map<String, String> mdcContext = MDC.getCopyOfContextMap();
                
                // 使用 whenComplete 确保 CompletableFuture 完成，避免资源泄露
                // ⚠️ 重要：不持有 Future 引用，让 GC 自动回收
                CompletableFuture.runAsync(() -> {
                    // 在异步线程中恢复 MDC 上下文
                    if (mdcContext != null) {
                        MDC.setContextMap(mdcContext);
                    }
                    try {
                        webhookReceiveService.handle(category, eventType, webhookRequest);
                    } catch (Exception e) {
                        // 异常已被捕获，记录日志但不传播
                        // ⚠️ 不重新抛出异常，避免触发异常处理器
                        log.error("[Webhook-接收] 异步处理失败: category={}, eventType={}, messageId={}",
                                category, eventType, webhookRequest.getMessageId(), e);
                    } finally {
                        // 清除 MDC，避免线程复用导致设备编号污染
                        MDC.clear();
                    }
                }, webhookAsyncExecutor)
                .whenComplete((result, throwable) -> {
                    // 资源泄漏防护：确保 CompletableFuture 完成
                    // 即使发生未捕获的异常，也要确保 Future 完成，避免内存泄漏
                    if (throwable != null) {
                        // 这里不应该有异常，因为 runAsync 中的异常已被捕获
                        // 但如果 Spring 框架或其他地方抛出异常，这里可以捕获
                        log.warn("[Webhook-接收] CompletableFuture 异常（不应该发生）: category={}, eventType={}, messageId={}, error={}",
                                category, eventType, webhookRequest.getMessageId(), throwable.getMessage());
                    }
                    // Future 完成，可以被 GC 回收
                });
            } else {
                // 如果没有配置异步线程池，同步处理
                webhookReceiveService.handle(category, eventType, webhookRequest);
            }
            
            long cost = System.currentTimeMillis() - startTime;
            log.debug("[Webhook-接收] ====== Webhook请求接收完成 ====== 耗时: {}ms", cost);
            
            // 返回响应前检查客户端是否断开连接
            if (isClientDisconnected(request)) {
                if (log.isDebugEnabled()) {
                    log.debug("[Webhook-接收] 客户端已断开连接，跳过响应返回");
                }
                return null; // 返回 null，Spring 会跳过响应写入
            }
            return ResponseEntity.ok("success");
        } catch (BaseException ex) {
            long cost = System.currentTimeMillis() - startTime;
            log.warn("[Webhook-接收] 业务异常: category={}, eventType={}, error={}, 耗时: {}ms", 
                category, eventType, ex.getMessage(), cost);
            
            // 返回响应前检查客户端是否断开连接
            if (isClientDisconnected(request)) {
                if (log.isDebugEnabled()) {
                    log.debug("[Webhook-接收] 客户端已断开连接，跳过业务异常响应返回");
                }
                return null;
            }
            return ResponseEntity.status(401).body(ex.getMessage());
        } catch (Exception ex) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("[Webhook-接收] 处理异常: category={}, eventType={}, 耗时: {}ms", 
                category, eventType, cost, ex);
            
            // 返回响应前检查客户端是否断开连接
            if (isClientDisconnected(request)) {
                if (log.isDebugEnabled()) {
                    log.debug("[Webhook-接收] 客户端已断开连接，跳过系统异常响应返回");
                }
                return null;
            }
            return ResponseEntity.status(500).body("process failed");
        }
    }

    /**
     * 检查客户端是否已断开连接
     * <p>
     * 通过检查 HttpServletResponse 是否已提交来判断。
     * 如果响应已提交，通常意味着客户端已接收到部分或全部响应，或者已断开连接。
     * </p>
     * @param request HttpServletRequest
     * @return true 如果客户端已断开连接或响应已提交
     */
    private boolean isClientDisconnected(HttpServletRequest request) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletResponse response = attributes.getResponse();
                if (response != null) {
                    return response.isCommitted();
                }
            }
        } catch (Exception e) {
            // 检查失败，假设连接正常
            if (log.isDebugEnabled()) {
                log.debug("[Webhook-接收] 无法检查客户端连接状态: {}", e.getMessage());
            }
        }
        return false;
    }
}

