package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.webhook.WebhookReceiveService;
import com.weili.iot_portal.service.webhook.WebhookSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class UnifiedWebhookController {

    @Autowired
    private WebhookReceiveService webhookReceiveService;

    @Autowired
    private WebhookSecurityService webhookSecurityService;

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
        try {
            if (StringUtils.isAnyBlank(signature, timestamp, nonce)) {
                return ResponseEntity.status(400).body("缺少签名参数");
            }
            if (StringUtils.isBlank(rawBody) || "invalid".equals(rawBody)) {
                return ResponseEntity.status(400).body("无效的请求体");
            }
            WebhookRequest webhookRequest = JsonUtils.parseObject(rawBody, WebhookRequest.class);
            webhookReceiveService.handle(category, eventType, rawBody, webhookRequest,
                    headerSecret, signature, timestamp, nonce);
            return ResponseEntity.ok("success");
        } catch (ServiceException ex) {
            return ResponseEntity.status(401).body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Webhook 处理异常", ex);
            return ResponseEntity.status(500).body("process failed");
        }
    }
}

