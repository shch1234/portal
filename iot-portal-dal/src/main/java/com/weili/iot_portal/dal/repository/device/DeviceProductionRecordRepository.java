package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;

import java.util.List;
import java.util.Optional;

public interface DeviceProductionRecordRepository {

    Optional<DeviceProductionRecordDO> findLatestOngoing(Long deviceId);

    void insert(DeviceProductionRecordDO record);

    void updateById(DeviceProductionRecordDO record);

    List<DeviceProductionRecordDO> findByShift(Long deviceId, Integer shiftCode, java.time.LocalDate shiftDate);

    /**
     * 统计在时间范围内已完成（end_ts 落入区间）的记录数量
     */
    long countCompletedInRange(Long deviceId, Long startTs, Long endTs);

    /**
     * 查询在时间范围内有产量记录的不同设备ID列表
     * 
     * @param startTsSeconds 开始时间戳（秒）
     * @param endTsSeconds 结束时间戳（秒）
     * @return 设备ID列表
     */
    List<Long> findDistinctDeviceIdsWithProductionRecords(long startTsSeconds, long endTsSeconds);
}


