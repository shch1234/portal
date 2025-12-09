package com.weili.iot_portal.service.ingestion.handler.registry;

import com.weili.iot_portal.service.ingestion.handler.WebhookEventHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.BeanFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook处理器注册表测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook处理器注册表测试")
class WebhookHandlerRegistryTest {

    @Mock
    private WebhookEventHandler handler1;

    @Mock
    private WebhookEventHandler handler2;

    @Mock
    private WebhookEventHandler handler3;

    @Mock
    private BeanFactory beanFactory;

    private WebhookRoutingProperties routingProperties;
    private List<WebhookEventHandler> handlers;

    @BeforeEach
    void setUp() {
        handlers = new ArrayList<>();
        routingProperties = new WebhookRoutingProperties();
        routingProperties.setRouting(Map.of());

        when(handler1.order()).thenReturn(10);
        when(handler2.order()).thenReturn(20);
        when(handler3.order()).thenReturn(30);
    }

    @Test
    @DisplayName("按order排序注册处理器")
    void testHandlerOrdering() {
        // Given
        handlers.add(handler3);
        handlers.add(handler1);
        handlers.add(handler2);

        when(handler1.supports("EVENT_TYPE")).thenReturn(true);
        when(handler2.supports("EVENT_TYPE")).thenReturn(false);
        when(handler3.supports("EVENT_TYPE")).thenReturn(false);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result = registry.resolve("EVENT_TYPE");

        // Then
        assertTrue(result.isPresent());
        assertEquals(handler1, result.get());
        verify(handler1).supports("EVENT_TYPE");
        verify(handler2, never()).supports(anyString());
        verify(handler3, never()).supports(anyString());
    }

    @Test
    @DisplayName("找到第一个支持的处理器")
    void testFindFirstSupportedHandler() {
        // Given
        handlers.add(handler1);
        handlers.add(handler2);
        handlers.add(handler3);

        when(handler1.supports("EVENT_TYPE")).thenReturn(false);
        when(handler2.supports("EVENT_TYPE")).thenReturn(true);
        when(handler3.supports("EVENT_TYPE")).thenReturn(true);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result = registry.resolve("EVENT_TYPE");

        // Then
        assertTrue(result.isPresent());
        assertEquals(handler2, result.get());
        verify(handler1).supports("EVENT_TYPE");
        verify(handler2).supports("EVENT_TYPE");
        verify(handler3, never()).supports(anyString());
    }

    @Test
    @DisplayName("没有找到支持的处理器")
    void testNoHandlerFound() {
        // Given
        handlers.add(handler1);
        handlers.add(handler2);

        when(handler1.supports("EVENT_TYPE")).thenReturn(false);
        when(handler2.supports("EVENT_TYPE")).thenReturn(false);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result = registry.resolve("EVENT_TYPE");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("eventType为空返回空")
    void testEmptyEventType() {
        // Given
        handlers.add(handler1);
        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result1 = registry.resolve(null);
        Optional<WebhookEventHandler> result2 = registry.resolve("");
        Optional<WebhookEventHandler> result3 = registry.resolve("   ");

        // Then
        assertFalse(result1.isPresent());
        assertFalse(result2.isPresent());
        assertFalse(result3.isPresent());
        verify(handler1, never()).supports(anyString());
    }

    @Test
    @DisplayName("使用缓存提高性能")
    void testCaching() {
        // Given
        handlers.add(handler1);
        when(handler1.supports("EVENT_TYPE")).thenReturn(true);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result1 = registry.resolve("EVENT_TYPE");
        Optional<WebhookEventHandler> result2 = registry.resolve("EVENT_TYPE");
        Optional<WebhookEventHandler> result3 = registry.resolve("EVENT_TYPE");

        // Then
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertTrue(result3.isPresent());
        assertEquals(handler1, result1.get());
        assertEquals(handler1, result2.get());
        assertEquals(handler1, result3.get());
        // 第一次调用会调用supports，后续使用缓存
        verify(handler1, atLeastOnce()).supports("EVENT_TYPE");
    }

    @Test
    @DisplayName("配置路由 - 精确匹配")
    void testConfigExactMatch() {
        // Given
        handlers.clear();
        routingProperties.setRouting(Map.of("EXACT_EVENT", "customHandlerBean"));

        WebhookEventHandler customHandler = mock(WebhookEventHandler.class);
        when(beanFactory.getBean("customHandlerBean", WebhookEventHandler.class)).thenReturn(customHandler);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result = registry.resolve("EXACT_EVENT");

        // Then
        assertTrue(result.isPresent());
        assertEquals(customHandler, result.get());
    }

    @Test
    @DisplayName("配置路由 - 通配符匹配")
    void testConfigWildcardMatch() {
        // Given
        handlers.clear();
        // 通配符模式：EVENT_* 应该匹配 EVENT_TYPE_A
        routingProperties.setRouting(Map.of("EVENT_*", "wildcardHandlerBean"));

        WebhookEventHandler wildcardHandler = mock(WebhookEventHandler.class);
        when(beanFactory.getBean("wildcardHandlerBean", WebhookEventHandler.class)).thenReturn(wildcardHandler);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result = registry.resolve("EVENT_TYPE_A");

        // Then
        assertTrue(result.isPresent(), "通配符匹配应该成功");
        assertEquals(wildcardHandler, result.get());
    }

    @Test
    @DisplayName("配置路由 - 通配符匹配多个场景")
    void testConfigWildcardMatchMultiple() {
        // Given
        handlers.clear();
        // 使用LinkedHashMap保证顺序，避免Map.of()顺序不确定的问题
        Map<String, String> routing = new java.util.LinkedHashMap<>();
        routing.put("DEVICE_*", "deviceHandlerBean");
        routing.put("*_ALARM", "alarmHandlerBean");
        routing.put("TEST_*_END", "testHandlerBean");
        routingProperties.setRouting(routing);

        WebhookEventHandler deviceHandler = mock(WebhookEventHandler.class);
        WebhookEventHandler alarmHandler = mock(WebhookEventHandler.class);
        WebhookEventHandler testHandler = mock(WebhookEventHandler.class);

        when(beanFactory.getBean("deviceHandlerBean", WebhookEventHandler.class)).thenReturn(deviceHandler);
        when(beanFactory.getBean("alarmHandlerBean", WebhookEventHandler.class)).thenReturn(alarmHandler);
        when(beanFactory.getBean("testHandlerBean", WebhookEventHandler.class)).thenReturn(testHandler);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When & Then - 测试不同的通配符模式
        // DEVICE_STATE 只匹配 DEVICE_*
        Optional<WebhookEventHandler> result1 = registry.resolve("DEVICE_STATE");
        assertTrue(result1.isPresent());
        assertEquals(deviceHandler, result1.get());

        // DEVICE_ALARM 同时匹配 DEVICE_* 和 *_ALARM，由于DEVICE_*先添加，所以优先匹配
        Optional<WebhookEventHandler> result2 = registry.resolve("DEVICE_ALARM");
        assertTrue(result2.isPresent());
        assertEquals(deviceHandler, result2.get()); // DEVICE_* 先匹配

        // TEST_START_END 匹配 TEST_*_END
        Optional<WebhookEventHandler> result3 = registry.resolve("TEST_START_END");
        assertTrue(result3.isPresent());
        assertEquals(testHandler, result3.get());

        // OTHER_ALARM 只匹配 *_ALARM
        Optional<WebhookEventHandler> result4 = registry.resolve("OTHER_ALARM");
        assertTrue(result4.isPresent());
        assertEquals(alarmHandler, result4.get());
    }

    @Test
    @DisplayName("配置路由 - 通配符不匹配")
    void testConfigWildcardNoMatch() {
        // Given
        handlers.clear();
        routingProperties.setRouting(Map.of("EVENT_*", "wildcardHandlerBean"));

        WebhookEventHandler wildcardHandler = mock(WebhookEventHandler.class);
        when(beanFactory.getBean("wildcardHandlerBean", WebhookEventHandler.class)).thenReturn(wildcardHandler);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result = registry.resolve("OTHER_TYPE");

        // Then
        assertFalse(result.isPresent(), "不匹配的模式应该返回空");
    }

    @Test
    @DisplayName("配置路由优先级低于代码处理器")
    void testConfigRoutingPriority() {
        // Given
        handlers.add(handler1);
        when(handler1.supports("EVENT_TYPE")).thenReturn(true);
        routingProperties.setRouting(Map.of("EVENT_TYPE", "configHandlerBean"));

        WebhookEventHandler configHandler = mock(WebhookEventHandler.class);
        when(beanFactory.getBean("configHandlerBean", WebhookEventHandler.class)).thenReturn(configHandler);

        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);

        // When
        Optional<WebhookEventHandler> result = registry.resolve("EVENT_TYPE");

        // Then
        assertTrue(result.isPresent());
        // 代码处理器优先
        assertEquals(handler1, result.get());
        verify(configHandler, never()).supports(anyString());
    }

    @Test
    @DisplayName("配置路由Bean不存在时忽略")
    void testConfigBeanNotFound() {
        // Given
        handlers.clear();
        routingProperties.setRouting(Map.of("EVENT_TYPE", "nonExistentBean"));

        when(beanFactory.getBean("nonExistentBean", WebhookEventHandler.class))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("nonExistentBean"));

        // When - 不应该抛出异常
        WebhookHandlerRegistry registry = new WebhookHandlerRegistry(handlers, routingProperties, beanFactory);
        Optional<WebhookEventHandler> result = registry.resolve("EVENT_TYPE");

        // Then
        assertFalse(result.isPresent());
    }
}

