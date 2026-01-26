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
     * 查询是否存在相同刀具号和时间戳接近的记录（用于去重检查）
     * 检查时间戳在指定范围内的记录，避免并发创建重复记录
     *
     * @param deviceId 设备ID
     * @param toolNo 刀具号
     * @param startTs 开始时间戳（毫秒）
     * @param timeRangeMs 时间范围（毫秒），检查 startTs ± timeRangeMs 范围内的记录
     * @return 如果存在则返回记录，否则返回null
     */
    DeviceToolRecordDO findByDeviceIdAndToolNoAndTimeRange(Long deviceId, String toolNo, Long startTs, Long timeRangeMs);

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

