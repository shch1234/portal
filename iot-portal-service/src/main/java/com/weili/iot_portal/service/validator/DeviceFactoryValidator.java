package com.weili.iot_portal.service.validator;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
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
     * @throws ServiceException 如果设备不存在或不属于指定工厂
     */
    public void ensureDeviceBelongsToFactory(String factoryId, String deviceId) {
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息，请先选择工厂");
        }
        
        String actualFactoryId = resolveFactoryId(deviceId);

        if (!factoryId.equals(actualFactoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), 
                "设备不属于当前选择的工厂，无权访问");
        }
    }

    /**
     * 解析设备所属工厂（带缓存）
     */
    public String resolveFactoryId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备ID不能为空");
        }

        String cached = deviceFactoryCacheService.get(deviceId);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }

        DeviceInfoDO device = deviceInfoRepository.findById(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不存在"));

        if (StringUtils.isBlank(device.getOrgFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备未关联工厂");
        }

        deviceFactoryCacheService.cache(device.getId(), device.getOrgFactoryId());
        return device.getOrgFactoryId();
    }
}

