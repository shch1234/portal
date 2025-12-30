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
     * @return duration_s（秒），如果不存在则返回 Optional.empty()
     */
    Optional<Integer> findLatestCompletedDurationS(Long deviceId);

    /**
     * 统计当天的加工数量
     */
    long countByDate(Long deviceInfoId, LocalDate shiftDate, Integer shiftCode);
}


