package com.weili.iot_portal.service.ingestion.impl;

import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookFailLogDO;
import com.weili.iot_portal.dal.repository.ingestion.WebhookFailLogRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Webhook失败日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookFailLogServiceImpl implements WebhookFailLogService {

    private final WebhookFailLogRepository failLogRepository;

    @Override
    public void saveFailLog(WebhookRequest request, String errorType, String errorMessage, boolean needManual) {
        try {
            WebhookFailLogDO fail = new WebhookFailLogDO();
            
            // 设置基础信息
            if (request != null) {
                fail.setMessageId(request.getMessageId());
                fail.setTbDeviceId(request.getDeviceId());
                fail.setDeviceCode(request.getDeviceCode());
                fail.setEventType(request.getEventType());
                // 转换payload
                try {
                    fail.setPayload(JsonUtils.toJSONObject(request));
                } catch (Exception ex) {
                    log.warn("转换WebhookRequest为JSONObject失败，跳过payload设置", ex);
                }
            }
            
            // 设置错误信息
            fail.setErrorType(StringUtils.isNotBlank(errorType) ? errorType : "PROCESS");
            fail.setErrorMessage(errorMessage);
            fail.setNeedManual(needManual);
            fail.setRetryCount(0);
            fail.setRecovered(false);
            fail.setFailedTime(LocalDateTime.now());
            
            failLogRepository.insert(fail);
            log.debug("记录Webhook失败日志成功: messageId={}, errorType={}, needManual={}", 
                    fail.getMessageId(), errorType, needManual);
        } catch (Exception ex) {
            log.error("记录Webhook失败日志异常: messageId={}, errorType={}", 
                    request != null ? request.getMessageId() : null, errorType, ex);
            // 不抛出异常，避免影响主流程
        }
    }

    @Override
    public List<WebhookFailLogDO> getUnrecoveredLogs(int batchSize) {
        if (batchSize <= 0) {
            return Collections.emptyList();
        }
        
        try {
            return failLogRepository.findUnrecovered(batchSize);
        } catch (Exception ex) {
            log.error("查询待恢复的失败日志异常", ex);
            return Collections.emptyList();
        }
    }

    @Override
    public void markRecovered(String failLogId) {
        if (StringUtils.isBlank(failLogId)) {
            log.warn("标记恢复失败：failLogId为空");
            return;
        }
        
        try {
            WebhookFailLogDO fail = failLogRepository.findById(failLogId).orElse(null);
            if (fail == null) {
                log.warn("标记恢复失败：找不到失败日志，failLogId={}", failLogId);
                return;
            }
            
            fail.setRecovered(true);
            failLogRepository.update(fail);
            
            log.debug("标记失败日志为已恢复成功: failLogId={}, messageId={}", failLogId, fail.getMessageId());
        } catch (Exception ex) {
            log.error("标记失败日志为已恢复异常: failLogId={}", failLogId, ex);
            // 不抛出异常，避免影响恢复任务
        }
    }

    @Override
    public WebhookFailLogDO getByMessageId(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return null;
        }
        
        try {
            return failLogRepository.findLatestByMessageId(messageId).orElse(null);
        } catch (Exception ex) {
            log.error("根据messageId查询失败日志异常: messageId={}", messageId, ex);
            return null;
        }
    }
}

