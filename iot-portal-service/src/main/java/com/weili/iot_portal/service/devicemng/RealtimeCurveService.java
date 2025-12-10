package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.RealtimeCurveVO;
import com.weili.iot_portal.domain.devicemng.RealtimeMetricValueVO;

/**
 * 实时曲线服务
 */
public interface RealtimeCurveService {

    RealtimeCurveVO getRealtimeSpindleLoad(String factoryId, String deviceId);

    RealtimeCurveVO getHistorySpindleLoad(String factoryId, String deviceId, Long startTs, Long endTs);

    RealtimeCurveVO getRealtimeSpindleSpeed(String factoryId, String deviceId);

    RealtimeCurveVO getHistorySpindleSpeed(String factoryId, String deviceId, Long startTs, Long endTs);

    RealtimeCurveVO getRealtimeFeedRate(String factoryId, String deviceId);

    RealtimeCurveVO getHistoryFeedRate(String factoryId, String deviceId, Long startTs, Long endTs);

    RealtimeMetricValueVO getRealtimeFeedOverride(String factoryId, String deviceId);
}


