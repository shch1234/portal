package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 设备匹配服务：基于 device_code 查询 device_base_info
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMatchingService {

    private final DeviceInfoRepository deviceInfoRepository;

    public Optional<DeviceInfoDO> match(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            log.warn("device_code 为空，跳过匹配");
            return Optional.empty();
        }
        DeviceInfoDO device = deviceInfoRepository.findActiveMonitoredByDeviceCode(deviceCode).orElse(null);
        if (device == null) {
            log.warn("设备未匹配: deviceCode={}", deviceCode);
            return Optional.empty();
        }
        return Optional.of(device);
    }
}

