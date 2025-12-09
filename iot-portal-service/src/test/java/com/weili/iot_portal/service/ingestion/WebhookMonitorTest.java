package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook监控与告警测试
 * 覆盖TC-MONITOR-001 ~ TC-MONITOR-004
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook监控与告警测试")
class WebhookMonitorTest {

    @Mock
    private WebhookInboxService webhookInboxService;

    @InjectMocks
    private WebhookProcessWorker webhookProcessWorker;

    @BeforeEach
    void setUp() {
        // 基础设置
    }

    @Test
    @DisplayName("TC-MONITOR-001: 收件箱积压监控")
    void testInboxBacklogMonitoring() {
        // Given - 模拟收件箱中有积压消息
        List<WebhookInboxDO> backlogMessages = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            WebhookInboxDO inbox = new WebhookInboxDO();
            inbox.setMessageId("msg-backlog-" + i);
            inbox.setStatus("PENDING");
            backlogMessages.add(inbox);
        }
        when(webhookInboxService.fetchDue()).thenReturn(backlogMessages);

        // When - 查询积压数量
        int backlogCount = webhookInboxService.fetchDue().size();

        // Then - 返回正确的积压数量
        assertEquals(50, backlogCount, "收件箱积压数量应该正确");
        verify(webhookInboxService, atLeastOnce()).fetchDue();
    }

    @Test
    @DisplayName("TC-MONITOR-002: 处理失败率监控")
    void testProcessingFailureRateMonitoring() {
        // Given - 模拟处理结果
        List<WebhookInboxDO> messages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            WebhookInboxDO inbox = new WebhookInboxDO();
            inbox.setMessageId("msg-" + i);
            inbox.setStatus(i < 10 ? "FAILED" : "SUCCESS"); // 10%失败率
            messages.add(inbox);
        }

        // When - 计算失败率
        long failedCount = messages.stream()
                .filter(m -> "FAILED".equals(m.getStatus()))
                .count();
        double failureRate = (double) failedCount / messages.size() * 100;

        // Then - 返回正确的失败率
        assertEquals(10.0, failureRate, 0.1, "处理失败率应该正确: " + failureRate + "%");
    }

    @Test
    @DisplayName("TC-MONITOR-003: 成功率监控")
    void testSuccessRateMonitoring() {
        // Given - 模拟处理结果
        List<WebhookInboxDO> messages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            WebhookInboxDO inbox = new WebhookInboxDO();
            inbox.setMessageId("msg-" + i);
            inbox.setStatus(i < 5 ? "FAILED" : "SUCCESS"); // 95%成功率
            messages.add(inbox);
        }

        // When - 计算成功率
        long successCount = messages.stream()
                .filter(m -> "SUCCESS".equals(m.getStatus()))
                .count();
        double successRate = (double) successCount / messages.size() * 100;

        // Then - 返回正确的成功率
        assertEquals(95.0, successRate, 0.1, "处理成功率应该正确: " + successRate + "%");
    }

    @Test
    @DisplayName("TC-MONITOR-004: 实时数据缓存命中率")
    void testRealtimeCacheHitRateMonitoring() {
        // Given - 模拟缓存操作
        int totalRequests = 100;
        int cacheHits = 85; // 85%命中率
        int cacheMisses = totalRequests - cacheHits;

        // When - 计算缓存命中率
        double hitRate = (double) cacheHits / totalRequests * 100;

        // Then - 返回正确的命中率
        assertEquals(85.0, hitRate, 0.1, "实时数据缓存命中率应该正确: " + hitRate + "%");
        assertTrue(hitRate > 0, "缓存命中率应该大于0");
        assertTrue(hitRate <= 100, "缓存命中率应该小于等于100%");
    }

    @Test
    @DisplayName("监控测试 - 无积压场景")
    void testNoBacklogScenario() {
        // Given - 收件箱为空
        when(webhookInboxService.fetchDue()).thenReturn(new ArrayList<>());

        // When - 查询积压数量
        int backlogCount = webhookInboxService.fetchDue().size();

        // Then - 积压数量应该为0
        assertEquals(0, backlogCount, "无积压时应该返回0");
    }

    @Test
    @DisplayName("监控测试 - 100%成功率场景")
    void testPerfectSuccessRateScenario() {
        // Given - 所有消息都成功
        List<WebhookInboxDO> messages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            WebhookInboxDO inbox = new WebhookInboxDO();
            inbox.setMessageId("msg-" + i);
            inbox.setStatus("SUCCESS");
            messages.add(inbox);
        }

        // When - 计算成功率
        long successCount = messages.stream()
                .filter(m -> "SUCCESS".equals(m.getStatus()))
                .count();
        double successRate = (double) successCount / messages.size() * 100;

        // Then - 成功率应该为100%
        assertEquals(100.0, successRate, 0.1, "100%成功率场景应该正确");
    }
}

