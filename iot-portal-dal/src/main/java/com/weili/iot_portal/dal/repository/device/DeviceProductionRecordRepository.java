package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;

import java.time.LocalDate;
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
     * 统计当天的加工数量
     */
    long countByDate(Long deviceInfoId, LocalDate shiftDate, Integer shiftCode);
}


