package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.RealtimeCurveVO;
import com.weili.iot_portal.domain.devicemng.RealtimeMetricValueVO;

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

    RealtimeMetricValueVO getRealtimeFeedOverride(String tenantId, String factoryId, String deviceId);
}


