package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.FeedRateHistoryDO;

import java.util.List;

public interface FeedRateHistoryRepository {

    void insertBatch(List<FeedRateHistoryDO> list);

    List<FeedRateHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit);
}

