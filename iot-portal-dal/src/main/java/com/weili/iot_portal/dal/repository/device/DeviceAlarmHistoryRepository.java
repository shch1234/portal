package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;

import java.util.List;

public interface DeviceAlarmHistoryRepository {

    List<DeviceAlarmHistoryDO> findActiveByDevice(String factoryId, String deviceId);

    /**
     * 查询时间范围内的报警（包含已结束和未结束）
     */
    List<DeviceAlarmHistoryDO> findByRange(String factoryId, String deviceId, Long startTs, Long endTs);

    void insert(DeviceAlarmHistoryDO record);

    void updateById(DeviceAlarmHistoryDO record);
}


