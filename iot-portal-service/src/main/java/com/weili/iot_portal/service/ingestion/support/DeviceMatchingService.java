package com.weili.iot_portal.service.ingestion.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceBaseInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 设备匹配服务：基于 device_code 查询 device_base_info
 */
@Slf4j
@Service
public class DeviceMatchingService {

    @Autowired
    private DeviceBaseInfoMapper deviceBaseInfoMapper;

    public Optional<DeviceBaseInfoDO> match(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            log.warn("device_code 为空，跳过匹配");
            return Optional.empty();
        }
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceBaseInfoDO::getDeviceCode, deviceCode)
                .eq(DeviceBaseInfoDO::getDeleted, 0);
        DeviceBaseInfoDO device = deviceBaseInfoMapper.selectOne(wrapper);
        if (device == null) {
            log.warn("设备未匹配: deviceCode={}", deviceCode);
            return Optional.empty();
        }
        if (!"ACTIVE".equalsIgnoreCase(device.getDeviceStatus())) {
            log.warn("设备状态非 ACTIVE: deviceCode={}, status={}", deviceCode, device.getDeviceStatus());
            return Optional.empty();
        }
        if (Boolean.FALSE.equals(device.getIsMonitored())) {
            log.warn("设备未启用监控: deviceCode={}", deviceCode);
            return Optional.empty();
        }
        return Optional.of(device);
    }
}

