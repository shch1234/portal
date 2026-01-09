package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;

import java.util.List;

public interface DeviceToolRecordRepository {

    void insertBatch(List<DeviceToolRecordDO> list);

    List<DeviceToolRecordDO> selectByRange(Long deviceId, Long startTs, Long endTs, Integer limit);

    /**
     * 分页查询设备刀具记录
     *
     * @param deviceId 设备ID
     * @param offset 偏移量
     * @param limit 限制条数
     * @return 刀具记录列表
     */
    List<DeviceToolRecordDO> selectByRangeWithPage(Long deviceId, Integer offset, Integer limit);

    /**
     * 统计设备刀具记录总数
     *
     * @param deviceId 设备ID
     * @return 记录总数
     */
    Long countByDevice(Long deviceId);

    /**
     * 查询设备最新的"进行中"刀具记录（end_ts IS NULL）
     */
    DeviceToolRecordDO findLatestOngoing(Long deviceId);

    /**
     * 查询设备所有未结束的相同刀具号记录（用于修复数据不一致问题）
     *
     * @param deviceId 设备ID
     * @param toolNo 刀具号
     * @return 未结束的刀具记录列表
     */
    List<DeviceToolRecordDO> findAllOngoingByToolNo(Long deviceId, String toolNo);

    /**
     * 插入单条记录
     */
    void insert(DeviceToolRecordDO record);

    /**
     * 按 ID 更新
     */
    void updateById(DeviceToolRecordDO record);

    /**
     * 根据ID删除记录
     *
     * @param id 记录ID
     */
    void deleteById(Long id);
}

