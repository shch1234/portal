package com.weili.iot_portal.service.support;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.repository.devicebase.DeviceBaseInfoRepository;
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

    private final DeviceBaseInfoRepository deviceBaseInfoRepository;
    private final DeviceFactoryCacheService deviceFactoryCacheService;

    /**
     * 验证设备是否存在且属于指定工厂
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @throws ServiceException 如果设备不存在或不属于指定工厂
     */
    public void ensureDeviceBelongsToFactory(String tenantId, String factoryId, String deviceId) {
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息，请先选择工厂");
        }
        
        String actualFactoryId = resolveFactoryId(tenantId, deviceId);

        if (!factoryId.equals(actualFactoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), 
                "设备不属于当前选择的工厂，无权访问");
        }
    }

    /**
     * 解析设备所属工厂（带缓存）
     */
    public String resolveFactoryId(String tenantId, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备ID不能为空");
        }

        String cached = deviceFactoryCacheService.get(deviceId);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }

        DeviceBaseInfoDO device = deviceBaseInfoRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不存在"));

        if (StringUtils.isBlank(device.getFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备未关联工厂");
        }

        deviceFactoryCacheService.cache(device.getId(), device.getFactoryId());
        return device.getFactoryId();
    }

    /**
     * 刷新设备-工厂关系缓存
     */
    public void refreshFactoryCache(String deviceId, String factoryId) {
        deviceFactoryCacheService.cache(deviceId, factoryId);
    }

    public void evictFactoryCache(String deviceId) {
        deviceFactoryCacheService.evict(deviceId);
    }
}

