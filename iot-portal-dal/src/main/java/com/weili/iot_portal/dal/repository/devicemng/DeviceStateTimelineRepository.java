package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;

import java.util.List;
import java.util.Optional;

/**
 * 设备状态时间线仓储
 */
public interface DeviceStateTimelineRepository {

    List<DeviceStateTimelineDO> selectByRange(String deviceId, Long startTs, Long endTs);

    List<DeviceStateTimelineDO> selectRecent(String deviceId, Long startTs, int limit);

    /**
     * 查询设备最新的状态记录（进行中或最近结束的）
     * 优先返回 end_ts IS NULL 的记录，如果没有则返回 end_ts 最大的记录
     *
     * @param deviceId   设备ID
     * @return 最新的状态记录，如果不存在返回 Optional.empty()
     */
    Optional<DeviceStateTimelineDO> findLatestState(String deviceId);
}


