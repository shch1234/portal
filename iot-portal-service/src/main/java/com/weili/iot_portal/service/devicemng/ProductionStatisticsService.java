package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.ProductionHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.ProductionHistoryReq;

/**
 * 产量统计服务
 */
public interface ProductionStatisticsService {

    ProductionHistoryVO getCurrentShift(String tenantId, String factoryId, String deviceId);

    ProductionHistoryVO getHistory(String tenantId, String factoryId, ProductionHistoryReq request);
}


