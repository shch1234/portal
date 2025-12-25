package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.domain.ingestion.StateStatistics;

import java.util.List;
import java.util.Map;

/**
 * 设备状态统计服务接口
 * 负责计算设备状态统计数据
 */
public interface IDeviceStateStatisticsService {

    /**
     * 计算状态统计
     *
     * @param stateRecords  状态记录列表
     * @param shiftStartTs 班次开始时间（秒）
     * @param shiftEndTs   班次结束时间（秒）
     * @return 状态统计Map，key为状态名称（如WORKING、STANDBY等），value为统计结果
     */
    Map<String, StateStatistics> calculateStatistics(
            List<DeviceStateRecordDO> stateRecords,
            long shiftStartTs,
            long shiftEndTs);
}

