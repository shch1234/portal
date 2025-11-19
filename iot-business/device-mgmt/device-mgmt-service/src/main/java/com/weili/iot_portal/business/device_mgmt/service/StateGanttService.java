package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.StateGanttVO;

/**
 * 状态甘特服务
 */
public interface StateGanttService {

    StateGanttVO getCurrentShiftGantt(String tenantId, String factoryId, String deviceId);

    StateGanttVO getHistoryGantt(String tenantId, String factoryId, String deviceId, Long startTs, Long endTs);
}


