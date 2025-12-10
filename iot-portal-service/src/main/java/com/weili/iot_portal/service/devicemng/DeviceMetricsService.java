package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.DeviceMetricHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.DeviceMetricHistoryReq;

import java.util.List;

/**
 * 设备指标服务
 */
public interface DeviceMetricsService {

    DeviceMetricHistoryVO getCurrentMetrics(String factoryId, String deviceId, List<String> metricCodes);

    DeviceMetricHistoryVO getShiftMetrics(String factoryId, DeviceMetricHistoryReq request);
}


