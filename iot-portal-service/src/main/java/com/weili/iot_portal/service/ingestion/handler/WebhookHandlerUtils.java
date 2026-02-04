package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Webhook 事件处理器工具类
 * <p>
 * 提供公共的工具方法，供各个 Handler 使用
 * </p>
 * <p>
 * 优化说明：
 * 1. 统一缓存更新方法，确保所有 Redis 操作都在事务外执行
 * 2. 使用 NOT_SUPPORTED 挂起事务，避免 Redis MULTI 嵌套错误
 * 3. 提供批量 Redis 操作支持，提升性能
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
public class WebhookHandlerUtils {

    @Autowired
    private DeviceIdentityCacheService deviceIdentityCacheService;
    
    @Autowired(required = false)
    private DeviceStateCacheService deviceStateCacheService;

    /**
     * 解析设备身份信息（在事务外执行，避免 Redis 连接泄漏）
     * <p>
     * 优化说明：
     * 1. 使用 NOT_SUPPORTED 挂起事务，避免 Redis 操作绑定到事务
     * 2. 防止 Spring 为 Redis 操作创建专用连接并开启 Redis 事务（multi()）
     * 3. 避免事务未正确提交/回滚时连接泄漏
     * </p>
     * <p>
     * 根据 WebhookRequest 中的 deviceCode 和 deviceId 解析出设备信息
     * </p>
     *
     * @param request Webhook 请求对象
     * @return 设备身份信息，包含 deviceInfoId 和 orgFactoryId
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DeviceIdentity resolveDeviceIdentity(WebhookRequest request) {
        return deviceIdentityCacheService.resolveByDeviceCode(
                request.getDeviceCode(),
                request.getDeviceId(),
                "WebhookHandler");
    }

    /**
     * 统一更新设备状态缓存和心跳（批量操作，事务外执行）
     * <p>
     * 优化说明：
     * 1. 使用 NOT_SUPPORTED 挂起事务，确保 Redis 操作在事务外执行
     * 2. 批量执行 saveState 和 saveHeartbeat，减少网络往返
     * 3. 统一缓存更新逻辑，便于维护
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param state 状态值（数字编码字符串）
     * @param eventTimestamp 事件时间戳（毫秒）
     * @param traceId 追踪ID
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateStateCacheAndHeartbeat(Long factoryId, Long deviceId, String state,
                                             Long eventTimestamp, String traceId) {
        if (deviceStateCacheService == null) {
            log.warn("[WebhookHandlerUtils] DeviceStateCacheService未注入，跳过缓存更新");
            return;
        }
        
        // 使用批量方法：Pipeline 批量执行状态更新和心跳更新
        // 优化：减少网络往返次数（从 2 次减少到 1 次）
        deviceStateCacheService.saveStateAndHeartbeat(factoryId, deviceId, state, 
                eventTimestamp, "TB", traceId);
    }

    /**
     * 统一刷新设备状态缓存 TTL 和心跳（批量操作，事务外执行）
     * <p>
     * 优化说明：
     * 1. 使用 NOT_SUPPORTED 挂起事务，确保 Redis 操作在事务外执行
     * 2. 使用 Pipeline 批量执行，减少网络往返（从 2 次减少到 1 次）
     * 3. 只刷新 TTL，不更新状态值
     * 4. 统一缓存刷新逻辑，便于维护
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param traceId 追踪ID
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshStateCacheAndHeartbeat(Long factoryId, Long deviceId, String traceId) {
        if (deviceStateCacheService == null) {
            log.warn("[WebhookHandlerUtils] DeviceStateCacheService未注入，跳过缓存刷新");
            return;
        }
        
        // 使用批量方法：Pipeline 批量执行刷新 TTL 和心跳更新
        deviceStateCacheService.refreshStateTtlAndHeartbeat(factoryId, deviceId, traceId);
    }
}

