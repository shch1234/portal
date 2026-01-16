package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.ddd.device.DeviceAlarmHistoryQuery;

import java.util.List;

public interface DeviceAlarmHistoryRepository {

    List<DeviceAlarmHistoryDO> findActiveByDevice(Long factoryId, Long deviceId);

    /**
     * 查询有未结束报警的设备ID列表（去重）
     * 用于批量判断设备是否有报警，性能优化：只查询设备ID字段
     * 
     * @param deviceIds 设备ID列表
     * @return 有未结束报警的设备ID集合
     */
    List<Long> findDeviceIdsWithActiveAlarm(List<Long> deviceIds);

    /**
     * 分页查询设备告警历史
     * @return 告警历史列表
     */
    PageResult<DeviceAlarmHistoryDO> selectPage(DeviceAlarmHistoryQuery query);

    /**
     * 查询报警时长TOP N
     * @param factoryId 工厂ID
     * @param topN TOP N数量
     * @return 报警时长TOP N列表
     */
    List<DeviceAlarmHistoryDO> findTopByDuration(Long factoryId, Integer topN);

    void insert(DeviceAlarmHistoryDO record);

    void updateById(DeviceAlarmHistoryDO record);
}


