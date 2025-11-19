package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.ProductionHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ProductionHistoryReq;

/**
 * 产量统计服务
 */
public interface ProductionStatisticsService {

    ProductionHistoryVO getCurrentShift(String tenantId, String factoryId, String deviceId);

    ProductionHistoryVO getHistory(String tenantId, String factoryId, ProductionHistoryReq request);
}


