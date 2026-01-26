package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 设备状态时间线仓储
 */
public interface DeviceStateRecordRepository {

    /**
     * 按时间范围查询设备状态记录
     *
     * @param deviceId 设备ID
     * @param startTs  开始时间（毫秒，Unix时间戳）
     * @param endTs    结束时间（毫秒，Unix时间戳）
     * @return 与时间范围有交集的状态记录列表，按开始时间升序排列
     */
    List<DeviceStateRecordDO> selectByRange(Long deviceId, Long startTs, Long endTs);

    /**
     * 查询最近的N条设备状态记录
     *
     * @param deviceId 设备ID
     * @param startTs  起始时间（毫秒，Unix时间戳，可为null表示不限制）
     * @param limit    返回记录数量限制
     * @return 最近的N条记录，按开始时间升序排列
     */
    List<DeviceStateRecordDO> selectRecent(Long deviceId, Long startTs, int limit);

    /**
     * 按班次查询设备状态记录
     * <p>
     * 使用班次维度查询，性能优于时间范围查询（可以使用班次维度索引）
     * </p>
     *
     * @param deviceId 设备ID
     * @param shiftDate 班次日期
     * @param shiftCode 班次编码（1-一班 2-二班 3-三班）
     * @return 该班次的所有状态记录，按开始时间升序排列
     */
    List<DeviceStateRecordDO> selectByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode);

    /**
     * 查询设备最新的状态记录（进行中或最近结束的）
     * 优先返回 end_ts IS NULL 的记录，如果没有则返回 end_ts 最大的记录
     *
     * @param deviceId   设备ID
     * @return 最新的状态记录，如果不存在返回 Optional.empty()
     */
    Optional<DeviceStateRecordDO> findLatestState(Long deviceId);

    /**
     * 查询所有进行中的状态记录（end_ts IS NULL）
     * <p>
     * 用于定时任务扫描跨班次记录
     * </p>
     *
     * @param startTsAfter 只查询开始时间在此时间之后的记录（用于性能优化，避免扫描过旧的数据）
     * @param limit 限制返回的记录数量（用于分批处理）
     * @return 进行中的状态记录列表，按开始时间升序排列
     */
    List<DeviceStateRecordDO> findAllOngoing(Long startTsAfter, Integer limit);

    void insert(DeviceStateRecordDO record);

    void update(DeviceStateRecordDO record);

    /**
     * 根据ID删除记录
     *
     * @param id 记录ID
     */
    void deleteById(Long id);

    /**
     * 查询指定日期范围内有状态记录的所有设备+班次组合
     * 用于扫描缺失的汇总记录
     *
     * @param startDate 开始日期（包含）
     * @param endDate 结束日期（包含）
     * @return 设备+班次组合列表，每个组合包含 deviceInfoId, shiftDate, shiftCode
     */
    List<DeviceShiftKey> findDistinctDeviceShifts(LocalDate startDate, LocalDate endDate);

    /**
     * 按班次日期范围查询设备状态记录
     * <p>
     * 用于甘特图展示，返回数据按开始时间升序排列，确保时间轴正确显示
     * </p>
     *
     * @param deviceId 设备ID
     * @param startDate 开始班次日期（包含）
     * @param endDate 结束班次日期（包含）
     * @return 班次日期范围内的所有状态记录，按开始时间（start_ts）升序排列
     */
    List<DeviceStateRecordDO> selectByShiftDateRange(Long deviceId, LocalDate startDate, LocalDate endDate);

    /**
     * 设备+班次组合（用于缺失记录扫描）
     */
    record DeviceShiftKey(Long deviceInfoId, Long orgFactoryId, LocalDate shiftDate, Integer shiftCode) {
    }
}


