package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.StateStatsVO;

/**
 * 状态统计服务
 */
public interface StateStatsService {

    StateStatsVO getCurrentShiftStats(String tenantId, String factoryId, String deviceId);

    StateStatsVO getHistoryStats(String tenantId, String factoryId, String deviceId, Long startTs, Long endTs);
}


