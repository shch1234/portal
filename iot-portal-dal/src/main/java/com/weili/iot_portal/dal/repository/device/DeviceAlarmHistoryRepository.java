package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.ddd.device.DeviceAlarmHistoryQuery;

import java.util.List;

public interface DeviceAlarmHistoryRepository {

    List<DeviceAlarmHistoryDO> findActiveByDevice(Long factoryId, Long deviceId);

    /**
     * 分页查询设备告警历史
     * @return 告警历史列表
     */
    PageResult<DeviceAlarmHistoryDO> selectPage(DeviceAlarmHistoryQuery query);


    void insert(DeviceAlarmHistoryDO record);

    void updateById(DeviceAlarmHistoryDO record);
}


