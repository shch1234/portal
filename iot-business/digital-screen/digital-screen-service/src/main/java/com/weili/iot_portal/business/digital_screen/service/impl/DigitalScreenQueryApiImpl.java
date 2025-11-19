package com.weili.iot_portal.business.digital_screen.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.business.alarm_mgmt.api.AlarmRankingApi;
import com.weili.iot_portal.business.common.api.device.DeviceBaseDataApi;
import com.weili.iot_portal.business.common.domain.model.device.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_mgmt.api.DeviceStateService;
import com.weili.iot_portal.business.digital_screen.api.DigitalScreenQueryApi;
import com.weili.iot_portal.business.digital_screen.domain.model.AlarmRankingVO;
import com.weili.iot_portal.business.digital_screen.domain.model.FactoryLayoutVO;
import com.weili.iot_portal.business.digital_screen.domain.model.FactoryMetricsVO;
import com.weili.iot_portal.business.digital_screen.domain.model.FactoryStatusSummaryVO;
import com.weili.iot_portal.business.digital_screen.service.assembler.DigitalScreenAssembler;
import com.weili.iot_portal.business.efficiency_mgmt.api.FactoryMetricsApi;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数字大屏查询实现
 */
@Service
@RequiredArgsConstructor
public class DigitalScreenQueryApiImpl implements DigitalScreenQueryApi {

    private final DeviceBaseDataApi deviceBaseDataApi;
    private final DeviceStateService deviceStateService;
    private final AlarmRankingApi alarmRankingApi;
    private final FactoryMetricsApi factoryMetricsApi;

    @Override
    public FactoryLayoutVO getFactoryLayout(String tenantId, String factoryId) {
        validateParams(tenantId, factoryId);

        List<DeviceBaseInfoVO> devices = deviceBaseDataApi.getDevicesByFactory(tenantId, factoryId, null);
        Map<String, DeviceStateService.DeviceStatusVO> statusMap = loadDeviceStatus(tenantId, factoryId, devices);

        String factoryName = CollectionUtils.isNotEmpty(devices)
                ? devices.get(0).getFactoryName()
                : null;

        return DigitalScreenAssembler.toFactoryLayout(factoryId, factoryName, devices, statusMap);
    }

    @Override
    public FactoryStatusSummaryVO getFactoryStatusSummary(String tenantId, String factoryId) {
        validateParams(tenantId, factoryId);

        List<DeviceBaseInfoVO> devices = deviceBaseDataApi.getDevicesByFactory(tenantId, factoryId, null);
        Map<String, DeviceStateService.DeviceStatusVO> statusMap = loadDeviceStatus(tenantId, factoryId, devices);

        String factoryName = CollectionUtils.isNotEmpty(devices)
                ? devices.get(0).getFactoryName()
                : null;

        return DigitalScreenAssembler.toStatusSummary(factoryId, factoryName, devices, statusMap);
    }

    @Override
    public List<AlarmRankingVO> getAlarmDurationRanking(String tenantId, String factoryId, Integer limit) {
        validateParams(tenantId, factoryId);
        return DigitalScreenAssembler.toAlarmRanking(
                alarmRankingApi.getTopActiveAlarms(tenantId, factoryId, limit));
    }

    @Override
    public FactoryMetricsVO getFactoryMetrics(String tenantId, String factoryId, Integer days) {
        validateParams(tenantId, factoryId);
        return DigitalScreenAssembler.toFactoryMetrics(
                factoryMetricsApi.getCurrentFactoryMetrics(tenantId, factoryId, days));
    }

    private Map<String, DeviceStateService.DeviceStatusVO> loadDeviceStatus(String tenantId,
                                                                           String factoryId,
                                                                           List<DeviceBaseInfoVO> devices) {
        if (CollectionUtils.isEmpty(devices)) {
            return Map.of();
        }
        List<String> deviceIds = devices.stream()
                .map(DeviceBaseInfoVO::getId)
                .collect(Collectors.toList());
        return deviceStateService.batchGetStatus(tenantId, factoryId, deviceIds);
    }

    private void validateParams(String tenantId, String factoryId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "未获取到租户信息");
        }
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到厂区信息");
        }
    }
}


