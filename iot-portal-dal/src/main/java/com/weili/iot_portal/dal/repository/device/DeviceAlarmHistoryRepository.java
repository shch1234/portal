package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;

import java.util.List;

public interface DeviceAlarmHistoryRepository {

    List<DeviceAlarmHistoryDO> findActiveByDevice(Long factoryId, Long deviceId);

    /**
     * 查询时间范围内的报警（包含已结束和未结束）
     */
    List<DeviceAlarmHistoryDO> findByRange(Long factoryId, Long deviceId, Long startTs, Long endTs);

    /**
     * 分页查询设备告警历史
     *
     * @param deviceId 设备ID
     * @param startTs 开始时间
     * @param endTs 结束时间
     * @param offset 偏移量
     * @param limit 限制条数
     * @return 告警历史列表
     */
    List<DeviceAlarmHistoryDO> findByRangeWithPage(Long deviceId, Long startTs, Long endTs, Integer offset, Integer limit);

    /**
     * 统计设备告警历史总数
     *
     * @param deviceId 设备ID
     * @param startTs 开始时间
     * @param endTs 结束时间
     * @return 记录总数
     */
    Long countByRange(Long deviceId, Long startTs, Long endTs);

    void insert(DeviceAlarmHistoryDO record);

    void updateById(DeviceAlarmHistoryDO record);
}


