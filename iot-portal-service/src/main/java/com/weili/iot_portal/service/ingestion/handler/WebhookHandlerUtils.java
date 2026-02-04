package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
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
 *
 * @author system
 */
@Slf4j
@Component
public class WebhookHandlerUtils {

    @Autowired
    private DeviceIdentityCacheService deviceIdentityCacheService;

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
}

