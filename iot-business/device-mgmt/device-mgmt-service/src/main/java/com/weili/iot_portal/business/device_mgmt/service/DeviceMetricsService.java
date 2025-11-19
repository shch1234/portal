package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceMetricHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceMetricHistoryReq;

import java.util.List;

/**
 * 设备指标服务
 */
public interface DeviceMetricsService {

    DeviceMetricHistoryVO getCurrentMetrics(String tenantId, String factoryId, String deviceId, List<String> metricCodes);

    DeviceMetricHistoryVO getShiftMetrics(String tenantId, String factoryId, DeviceMetricHistoryReq request);
}


