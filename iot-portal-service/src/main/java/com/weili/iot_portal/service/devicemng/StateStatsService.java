package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.StateStatsVO;

/**
 * 状态统计服务
 */
public interface StateStatsService {

    StateStatsVO getCurrentShiftStats(String factoryId, String deviceId);

    StateStatsVO getHistoryStats(String factoryId, String deviceId, Long startTs, Long endTs);
}


