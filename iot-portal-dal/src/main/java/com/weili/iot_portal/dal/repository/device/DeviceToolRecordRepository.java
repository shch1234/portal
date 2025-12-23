package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;

import java.util.List;

public interface DeviceToolRecordRepository {

    void insertBatch(List<DeviceToolRecordDO> list);

    List<DeviceToolRecordDO> selectByRange(Long deviceId, Long startTs, Long endTs, Integer limit);

    /**
     * 查询设备最新的"进行中"刀具记录（end_ts IS NULL）
     */
    DeviceToolRecordDO findLatestOngoing(Long deviceId);

    /**
     * 插入单条记录
     */
    void insert(DeviceToolRecordDO record);

    /**
     * 按 ID 更新
     */
    void updateById(DeviceToolRecordDO record);
}

