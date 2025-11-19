package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurveVO;

/**
 * 实时曲线服务
 */
public interface RealtimeCurveService {

    RealtimeCurveVO getRealtimeSpindleLoad(String tenantId, String factoryId, String deviceId);

    RealtimeCurveVO getHistorySpindleLoad(String tenantId, String factoryId, String deviceId, Long startTs, Long endTs);

    RealtimeCurveVO getRealtimeSpindleSpeed(String tenantId, String factoryId, String deviceId);

    RealtimeCurveVO getHistorySpindleSpeed(String tenantId, String factoryId, String deviceId, Long startTs, Long endTs);

    RealtimeCurveVO getRealtimeFeedRate(String tenantId, String factoryId, String deviceId);

    RealtimeCurveVO getHistoryFeedRate(String tenantId, String factoryId, String deviceId, Long startTs, Long endTs);

    com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeMetricValueVO getRealtimeFeedOverride(String tenantId, String factoryId, String deviceId);
}


