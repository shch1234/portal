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
     * 查询在时间范围内有产量记录的不同设备ID列表
     *
     * @param startTsSeconds 开始时间戳（秒）
     * @param endTsSeconds 结束时间戳（秒）
     * @return 设备ID列表
     */
    List<Long> findDistinctDeviceIdsWithProductionRecords(long startTsSeconds, long endTsSeconds);

    /**
     * 查询设备最新一条已完成记录的 duration_s（用于理论节拍默认值）
     * <p>
     * 查询条件：
     * <ul>
     *   <li>设备ID匹配</li>
     *   <li>记录已完成（end_ts 不为空）</li>
     *   <li>duration_s 不为空且大于0</li>
     *   <li>按 end_ts 降序排列，取第一条</li>
     * </ul>
     * 
     * @param deviceId 设备ID
     * @return duration_s（毫秒），如果不存在则返回 Optional.empty()
     *         注意：虽然数据库列名为 duration_s，但实际存储的是毫秒值
     * @deprecated 使用 {@link #findLatestCompletedDurationsS(Long, int)} 代替，支持计算平均值
     */
    @Deprecated
    Optional<Long> findLatestCompletedDurationS(Long deviceId);

    /**
     * 查询设备最新N条已完成记录的 duration_s 列表（用于理论节拍默认值计算平均值）
     * <p>
     * 查询条件：
     * <ul>
     *   <li>设备ID匹配</li>
     *   <li>记录已完成（end_ts 不为空）</li>
     *   <li>duration_s 不为空且大于0</li>
     *   <li>按 end_ts 降序排列，取前N条</li>
     * </ul>
     * 
     * @param deviceId 设备ID
     * @param limit 查询记录数限制（建议5条）
     * @return duration_s 列表（毫秒），按结束时间降序排列，如果不存在则返回空列表
     *         注意：虽然数据库列名为 duration_s，但实际存储的是毫秒值
     */
    List<Long> findLatestCompletedDurationsS(Long deviceId, int limit);

    /**
     * 统计当天的加工数量（只统计已完成的记录，end_ts不为null）
     */
    long countByDate(Long deviceInfoId, LocalDate shiftDate);
}


