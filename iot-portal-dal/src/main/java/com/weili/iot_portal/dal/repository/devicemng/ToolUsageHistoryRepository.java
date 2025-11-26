package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.ToolUsageHistoryDO;

import java.util.List;

public interface ToolUsageHistoryRepository {

    void insertBatch(List<ToolUsageHistoryDO> list);

    List<ToolUsageHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit);
}

