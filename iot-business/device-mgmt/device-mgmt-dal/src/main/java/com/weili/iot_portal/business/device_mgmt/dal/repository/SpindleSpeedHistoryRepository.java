package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.SpindleSpeedHistoryDO;

import java.util.List;

public interface SpindleSpeedHistoryRepository {

    void insertBatch(List<SpindleSpeedHistoryDO> list);

    List<SpindleSpeedHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit);
}

