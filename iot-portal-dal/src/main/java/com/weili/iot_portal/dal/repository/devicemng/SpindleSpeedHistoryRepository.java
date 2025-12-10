package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.SpindleSpeedHistoryDO;

import java.util.List;

public interface SpindleSpeedHistoryRepository {

    void insertBatch(List<SpindleSpeedHistoryDO> list);

    List<SpindleSpeedHistoryDO> selectByRange(String deviceId, Long startTs, Long endTs, Integer limit);
}

