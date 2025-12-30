package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 设备状态汇总仓储
 */
public interface DeviceStateSummaryRepository {

    List<DeviceStateSummaryDO> selectByRange(Long deviceId, Long startTs, Long endTs);

    /**
     * 按班次日期范围查询设备状态汇总
     */
    List<DeviceStateSummaryDO> selectByShiftDateRange(Long deviceId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询指定日期范围内未完成汇总的记录
     */
    List<DeviceStateSummaryDO> selectPending(LocalDate startDate, LocalDate endDate);

    /**
     * 根据设备与班次唯一键查询
     */
    DeviceStateSummaryDO findByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode);

    /**
     * 新增汇总
     */
    void insert(DeviceStateSummaryDO entity);

    /**
     * 更新汇总
     */
    void update(DeviceStateSummaryDO entity);
}


