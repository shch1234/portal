package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.FeedRateHistoryDO;

import java.util.List;

public interface FeedRateHistoryRepository {

    void insertBatch(List<FeedRateHistoryDO> list);

    List<FeedRateHistoryDO> selectByRange(String deviceId, Long startTs, Long endTs, Integer limit);
}

