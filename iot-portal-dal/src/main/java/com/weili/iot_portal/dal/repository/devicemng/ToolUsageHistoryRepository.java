package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.ToolUsageHistoryDO;

import java.util.List;

public interface ToolUsageHistoryRepository {

    void insertBatch(List<ToolUsageHistoryDO> list);

    List<ToolUsageHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit);

    /**
     * 查询设备最新的“进行中”刀具记录（end_ts IS NULL）
     */
    ToolUsageHistoryDO findLatestOngoing(String tenantId, String deviceId);

    /**
     * 插入单条记录
     */
    void insert(ToolUsageHistoryDO record);

    /**
     * 按 ID 更新
     */
    void updateById(ToolUsageHistoryDO record);
}

