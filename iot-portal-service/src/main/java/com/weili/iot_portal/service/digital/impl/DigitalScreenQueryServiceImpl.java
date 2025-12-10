package com.weili.iot_portal.service.digital.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.api.alarm.AlarmRankingApi;
import com.weili.iot_portal.api.device.DeviceBaseDataApi;
import com.weili.iot_portal.api.device.DeviceStateApi;
import com.weili.iot_portal.api.device.FactoryMetricApi;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoVO;
import com.weili.iot_portal.domain.devicemng.DeviceStatusVO;
import com.weili.iot_portal.domain.digital.AlarmRankingVO;
import com.weili.iot_portal.domain.digital.FactoryLayoutVO;
import com.weili.iot_portal.domain.digital.FactoryMetricsVO;
import com.weili.iot_portal.domain.digital.FactoryStatusSummaryVO;
import com.weili.iot_portal.service.assembler.DigitalScreenAssembler;
import com.weili.iot_portal.service.digital.DigitalScreenQueryApi;
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
public class DigitalScreenQueryServiceImpl implements DigitalScreenQueryApi {

    private final DeviceBaseDataApi deviceBaseDataApi;
    private final DeviceStateApi deviceStateApi;
    private final AlarmRankingApi alarmRankingApi;
    private final FactoryMetricApi factoryMetricsApi;

    @Override
    public FactoryLayoutVO getFactoryLayout(String factoryId) {
        validateParams(factoryId);

        List<DeviceBaseInfoVO> devices = deviceBaseDataApi.getDevicesByFactory(factoryId, null);
        Map<String, DeviceStatusVO> statusMap = loadDeviceStatus(factoryId, devices);

        String factoryName = CollectionUtils.isNotEmpty(devices)
                ? devices.get(0).getFactoryName()
                : null;

        return DigitalScreenAssembler.toFactoryLayout(factoryId, factoryName, devices, statusMap);
    }

    @Override
    public FactoryStatusSummaryVO getFactoryStatusSummary(String factoryId) {
        validateParams(factoryId);

        List<DeviceBaseInfoVO> devices = deviceBaseDataApi.getDevicesByFactory(factoryId, null);
        Map<String, DeviceStatusVO> statusMap = loadDeviceStatus(factoryId, devices);

        String factoryName = CollectionUtils.isNotEmpty(devices)
                ? devices.get(0).getFactoryName()
                : null;

        return DigitalScreenAssembler.toStatusSummary(factoryId, factoryName, devices, statusMap);
    }

    @Override
    public List<AlarmRankingVO> getAlarmDurationRanking(String factoryId, Integer limit) {
        validateParams(factoryId);
        return DigitalScreenAssembler.toAlarmRanking(
                alarmRankingApi.getTopActiveAlarms(factoryId, limit));
    }

    @Override
    public FactoryMetricsVO getFactoryMetrics(String factoryId, Integer days) {
        validateParams(factoryId);
        return BeanUtils.toBean(factoryMetricsApi.getCurrentFactoryMetrics(factoryId, days),  FactoryMetricsVO.class);
    }

    private Map<String, DeviceStatusVO> loadDeviceStatus(
                                                                           String factoryId,
                                                                           List<DeviceBaseInfoVO> devices) {
        if (CollectionUtils.isEmpty(devices)) {
            return Map.of();
        }
        List<String> deviceIds = devices.stream()
                .map(DeviceBaseInfoVO::getId)
                .collect(Collectors.toList());
        return deviceStateApi.batchGetStatus(factoryId, deviceIds);
    }

    private void validateParams(String factoryId) {
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到厂区信息");
        }
    }
}


