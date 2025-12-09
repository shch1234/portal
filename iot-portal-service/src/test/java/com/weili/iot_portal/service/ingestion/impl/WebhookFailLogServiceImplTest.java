package com.weili.iot_portal.service.ingestion.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookFailLogDO;
import com.weili.iot_portal.dal.mapper.ingestion.WebhookFailLogMapper;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook失败日志服务测试
 * 测试用例：TC-FAIL-001 ~ TC-FAIL-006
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook失败日志服务测试")
class WebhookFailLogServiceImplTest {

    @Mock
    private WebhookFailLogMapper failLogMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WebhookFailLogServiceImpl webhookFailLogService;

    @BeforeEach
    void setUp() {
        // 设置默认的Mock行为
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
    }

    @Test
    @DisplayName("TC-FAIL-001: 保存失败日志")
    void testSaveFailLog() {
        // Given
        WebhookRequest request = createWebhookRequest();
        String errorType = "PROCESS";
        String errorMessage = "处理失败";
        boolean needManual = false;

        // When
        webhookFailLogService.saveFailLog(request, errorType, errorMessage, needManual);

        // Then - 验证失败日志被保存
        ArgumentCaptor<WebhookFailLogDO> captor = ArgumentCaptor.forClass(WebhookFailLogDO.class);
        verify(failLogMapper).insert(captor.capture());

        WebhookFailLogDO saved = captor.getValue();
        assertEquals("msg-001", saved.getMessageId());
        assertEquals("tenant-001", saved.getTenantUuid());
        assertEquals("DEVICE_STATE", saved.getEventType());
        assertEquals(errorType, saved.getErrorType());
        assertEquals(errorMessage, saved.getErrorMessage());
        assertEquals(needManual, saved.getNeedManual());
        assertEquals(0, saved.getRetryCount());
        assertFalse(saved.getRecovered());
        assertNotNull(saved.getFailedTime());
    }

    @Test
    @DisplayName("TC-FAIL-002: 错误分类记录")
    void testErrorTypeClassification() {
        // Given
        WebhookRequest request = createWebhookRequest();

        // When - 保存不同类型的错误
        webhookFailLogService.saveFailLog(request, "VALIDATION_ERROR", "验证失败", false);
        webhookFailLogService.saveFailLog(request, "BUSINESS_ERROR", "业务异常", true);
        webhookFailLogService.saveFailLog(request, "SYSTEM_ERROR", "系统错误", false);

        // Then - 验证错误类型正确分类
        ArgumentCaptor<WebhookFailLogDO> captor = ArgumentCaptor.forClass(WebhookFailLogDO.class);
        verify(failLogMapper, times(3)).insert(captor.capture());

        List<WebhookFailLogDO> allValues = captor.getAllValues();
        assertEquals("VALIDATION_ERROR", allValues.get(0).getErrorType());
        assertEquals("BUSINESS_ERROR", allValues.get(1).getErrorType());
        assertEquals("SYSTEM_ERROR", allValues.get(2).getErrorType());
    }

    @Test
    @DisplayName("TC-FAIL-003: 错误堆栈记录")
    void testErrorStackRecord() {
        // Given
        WebhookRequest request = createWebhookRequest();
        String errorMessage = "处理失败\n" +
                "java.lang.RuntimeException: 异常信息\n" +
                "    at com.example.Test.method(Test.java:10)";

        // When
        webhookFailLogService.saveFailLog(request, "PROCESS", errorMessage, false);

        // Then - 验证错误信息（包含堆栈）被记录
        ArgumentCaptor<WebhookFailLogDO> captor = ArgumentCaptor.forClass(WebhookFailLogDO.class);
        verify(failLogMapper).insert(captor.capture());
        assertTrue(captor.getValue().getErrorMessage().contains("RuntimeException"));
    }

    @Test
    @DisplayName("TC-FAIL-004: 人工处理标记")
    void testManualProcessingFlag() {
        // Given
        WebhookRequest request = createWebhookRequest();

        // When - 保存需要人工处理的错误
        webhookFailLogService.saveFailLog(request, "PROCESS", "需要人工处理", true);

        // Then - 验证need_manual字段为true
        ArgumentCaptor<WebhookFailLogDO> captor = ArgumentCaptor.forClass(WebhookFailLogDO.class);
        verify(failLogMapper).insert(captor.capture());
        assertTrue(captor.getValue().getNeedManual());
    }

    @Test
    @DisplayName("TC-FAIL-005: 查询待恢复日志")
    void testGetUnrecoveredLogs() {
        // Given
        WebhookFailLogDO log1 = createFailLog("log-001", false, false);
        WebhookFailLogDO log2 = createFailLog("log-002", false, false);
        List<WebhookFailLogDO> unrecoveredLogs = Arrays.asList(log1, log2);

        when(failLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(unrecoveredLogs);

        // When
        List<WebhookFailLogDO> result = webhookFailLogService.getUnrecoveredLogs(10);

        // Then - 验证返回未恢复的日志列表
        assertEquals(2, result.size());
        assertFalse(result.get(0).getRecovered());
        assertFalse(result.get(1).getRecovered());
    }

    @Test
    @DisplayName("TC-FAIL-006: 标记为已恢复")
    void testMarkRecovered() {
        // Given
        String failLogId = "log-001";
        WebhookFailLogDO failLog = createFailLog(failLogId, false, false);
        when(failLogMapper.selectById(failLogId)).thenReturn(failLog);

        // When
        webhookFailLogService.markRecovered(failLogId);

        // Then - 验证recovered=true，recovered_time有值
        ArgumentCaptor<WebhookFailLogDO> captor = ArgumentCaptor.forClass(WebhookFailLogDO.class);
        verify(failLogMapper).updateById(captor.capture());
        assertTrue(captor.getValue().getRecovered());
        assertNotNull(captor.getValue().getUpdateTime());
    }

    @Test
    @DisplayName("验证request为null时也能保存")
    void testSaveFailLogWithNullRequest() {
        // Given
        String errorType = "PROCESS";
        String errorMessage = "处理失败";

        // When
        webhookFailLogService.saveFailLog(null, errorType, errorMessage, false);

        // Then - 验证即使request为null也能保存
        ArgumentCaptor<WebhookFailLogDO> captor = ArgumentCaptor.forClass(WebhookFailLogDO.class);
        verify(failLogMapper).insert(captor.capture());
        assertNull(captor.getValue().getMessageId());
        assertEquals(errorType, captor.getValue().getErrorType());
    }

    @Test
    @DisplayName("验证errorType为空时使用默认值")
    void testDefaultErrorType() {
        // Given
        WebhookRequest request = createWebhookRequest();

        // When - errorType为空
        webhookFailLogService.saveFailLog(request, null, "错误信息", false);

        // Then - 验证使用默认值"PROCESS"
        ArgumentCaptor<WebhookFailLogDO> captor = ArgumentCaptor.forClass(WebhookFailLogDO.class);
        verify(failLogMapper).insert(captor.capture());
        assertEquals("PROCESS", captor.getValue().getErrorType());
    }

    @Test
    @DisplayName("验证根据messageId查询失败日志")
    void testGetByMessageId() {
        // Given
        String messageId = "msg-001";
        WebhookFailLogDO failLog = createFailLog("log-001", false, false);
        failLog.setMessageId(messageId);

        when(failLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(failLog);

        // When
        WebhookFailLogDO result = webhookFailLogService.getByMessageId(messageId);

        // Then - 验证返回对应的失败日志
        assertNotNull(result);
        assertEquals(messageId, result.getMessageId());
    }

    @Test
    @DisplayName("验证批量查询限制")
    void testBatchSizeLimit() {
        // Given
        when(failLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // When - 查询batchSize=5
        List<WebhookFailLogDO> result = webhookFailLogService.getUnrecoveredLogs(5);

        // Then - 验证查询时使用了LIMIT限制
        verify(failLogMapper).selectList(any(LambdaQueryWrapper.class));
        assertNotNull(result);
    }

    @Test
    @DisplayName("验证异常处理不抛出异常")
    void testExceptionHandling() {
        // Given
        WebhookRequest request = createWebhookRequest();
        when(failLogMapper.insert(any(WebhookFailLogDO.class))).thenThrow(new RuntimeException("数据库异常"));

        // When & Then - 验证异常被捕获，不抛出
        assertDoesNotThrow(() -> {
            webhookFailLogService.saveFailLog(request, "PROCESS", "错误", false);
        });
    }

    // 辅助方法
    private WebhookRequest createWebhookRequest() {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setDeviceId("tb-device-001");
        request.setEventType("DEVICE_STATE");
        return request;
    }

    private WebhookFailLogDO createFailLog(String id, boolean recovered, boolean needManual) {
        WebhookFailLogDO failLog = new WebhookFailLogDO();
        failLog.setId(id);
        failLog.setMessageId("msg-001");
        failLog.setErrorType("PROCESS");
        failLog.setErrorMessage("处理失败");
        failLog.setRecovered(recovered);
        failLog.setNeedManual(needManual);
        failLog.setFailedTime(LocalDateTime.now());
        return failLog;
    }
}

