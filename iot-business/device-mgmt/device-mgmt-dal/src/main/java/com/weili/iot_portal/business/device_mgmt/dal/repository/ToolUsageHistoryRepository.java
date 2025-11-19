package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.ToolUsageHistoryDO;

import java.util.List;

public interface ToolUsageHistoryRepository {

    void insertBatch(List<ToolUsageHistoryDO> list);

    List<ToolUsageHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit);
}

