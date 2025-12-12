package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;

import java.util.List;
import java.util.Optional;

public interface DeviceProductionRecordRepository {

    Optional<DeviceProductionRecordDO> findLatestOngoing(String deviceId);

    void insert(DeviceProductionRecordDO record);

    void updateById(DeviceProductionRecordDO record);

    List<DeviceProductionRecordDO> findByShift(String deviceId, Integer shiftCode, java.time.LocalDate shiftDate);

    /**
     * 统计在时间范围内已完成（end_ts 落入区间）的记录数量
     */
    long countCompletedInRange(String deviceId, Long startTs, Long endTs);
}


