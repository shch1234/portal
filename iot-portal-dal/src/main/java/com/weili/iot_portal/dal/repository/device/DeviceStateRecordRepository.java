package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;

import java.util.List;
import java.util.Optional;

/**
 * 设备状态时间线仓储
 */
public interface DeviceStateRecordRepository {

    List<DeviceStateRecordDO> selectByRange(Long deviceId, Long startTs, Long endTs);

    List<DeviceStateRecordDO> selectRecent(Long deviceId, Long startTs, int limit);

    /**
     * 查询设备最新的状态记录（进行中或最近结束的）
     * 优先返回 end_ts IS NULL 的记录，如果没有则返回 end_ts 最大的记录
     *
     * @param deviceId   设备ID
     * @return 最新的状态记录，如果不存在返回 Optional.empty()
     */
    Optional<DeviceStateRecordDO> findLatestState(Long deviceId);

    void insert(DeviceStateRecordDO record);

    void update(DeviceStateRecordDO record);
}


