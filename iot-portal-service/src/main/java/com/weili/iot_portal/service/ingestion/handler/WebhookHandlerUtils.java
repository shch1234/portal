package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
     * 解析设备身份信息
     * <p>
     * 根据 WebhookRequest 中的 deviceCode 和 deviceId 解析出设备信息
     * </p>
     *
     * @param request Webhook 请求对象
     * @return 设备身份信息，包含 deviceInfoId 和 orgFactoryId
     */
    public DeviceIdentity resolveDeviceIdentity(WebhookRequest request) {
        return deviceIdentityCacheService.resolveByDeviceCode(
                request.getDeviceCode(),
                request.getDeviceId(),
                "WebhookHandler");
    }
}

