package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceMetricsShiftDO;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceMetricsShiftRepository;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceMetricHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceMetricHistoryReq;
import com.weili.iot_portal.business.device_mgmt.service.DeviceMetricsService;
import com.weili.iot_portal.business.device_mgmt.service.assembler.DeviceMetricsAssembler;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备指标服务实现
 */
@Service
@RequiredArgsConstructor
public class DeviceMetricsServiceImpl implements DeviceMetricsService {

    private final DeviceMetricsShiftRepository deviceMetricsShiftRepository;
    private final DeviceFactoryValidator deviceFactoryValidator;

    @Override
    public DeviceMetricHistoryVO getCurrentMetrics(String tenantId, String factoryId, String deviceId,
                                                  List<String> metricCodes) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        DeviceMetricsShiftDO latest = deviceMetricsShiftRepository.selectLatestFinalized(tenantId, deviceId)
                .orElse(null);
        return DeviceMetricsAssembler.toSingle(latest, metricCodes);
    }

    @Override
    public DeviceMetricHistoryVO getShiftMetrics(String tenantId, String factoryId, DeviceMetricHistoryReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, request.getDeviceId());
        PageResult<DeviceMetricsShiftDO> pageResult = deviceMetricsShiftRepository.selectPage(
                tenantId,
                request.getDeviceId(),
                request.getStartTs(),
                request.getEndTs(),
                request.getPageNo(),
                request.getPageSize()
        );
        return DeviceMetricsAssembler.toPage(pageResult, request.getPageNo(), request.getPageSize(), request.getMetricCodes());
    }
}


