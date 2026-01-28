package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;

/**
 * 设备状态记录修复服务
 * 用于修复历史遗留的异常记录
 */
public interface IDeviceStateRecordRepairService {

    /**
     * 修复历史遗留的未结束记录
     * 将未结束且开始时间早于指定时间的记录标记为已结束（使用特殊值）
     * 
     * @param record 需要修复的记录
     * @param beforeTime 早于该时间的记录才需要修复（毫秒时间戳）
     * @return 是否成功修复
     */
    boolean repairHistoricalOngoingRecord(DeviceStateRecordDO record, long beforeTime);

    /**
     * 批量修复历史遗留的未结束记录
     * 
     * @param beforeTime 早于该时间的记录才需要修复（毫秒时间戳）
     * @param batchSize 每批处理的记录数
     * @return 修复的记录数
     */
    int batchRepairHistoricalOngoingRecords(long beforeTime, int batchSize);
}
