package com.weili.iot_portal.service.shift;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.service.cache.DeviceFactoryCacheService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 设备工厂验证工具类
 */
@Component
@RequiredArgsConstructor
public class DeviceFactoryValidator {

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceFactoryCacheService deviceFactoryCacheService;

    /**
     * 验证设备是否存在且属于指定工厂
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @throws IotPortalException 如果设备不存在或不属于指定工厂
     */
    public void ensureDeviceBelongsToFactory(Long factoryId, Long deviceId) {
        if (factoryId==null) {
            throw new IotPortalException(IotPortalErrorCode.FACTORY_INFO_NOT_FOUND);
        }
        
        Long actualFactoryId = resolveFactoryId(deviceId);
        if (!factoryId.equals(actualFactoryId)) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_NOT_BELONG_TO_FACTORY);
        }
    }

    /**
     * 解析设备所属工厂（带缓存）
     */
    public Long resolveFactoryId(Long deviceId) {
        if (deviceId==null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }

        String cached = deviceFactoryCacheService.get(deviceId);
        if (StringUtils.isNotBlank(cached)) {
            return Long.parseLong(cached);
        }

        DeviceInfoDO device = deviceInfoRepository.findById(deviceId)
                .orElseThrow(() -> new IotPortalException(IotPortalErrorCode.DEVICE_NOT_FOUND));

        if (device.getOrgFactoryId()==null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_NOT_ASSOCIATED_FACTORY);
        }

        deviceFactoryCacheService.cache(device.getId(), device.getOrgFactoryId());
        return device.getOrgFactoryId();
    }
}

