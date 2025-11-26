package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateSummaryDO;
import com.weili.iot_portal.domain.devicemng.StateStatsItemVO;
import com.weili.iot_portal.domain.devicemng.StateStatsVO;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 状态统计装配器
 */
@UtilityClass
public class StateStatsAssembler {

    public static StateStatsVO toVO(String deviceId, Long startTs, Long endTs, List<DeviceStateSummaryDO> summaries) {
        StateStatsVO vo = new StateStatsVO();
        vo.setDeviceId(deviceId);
        vo.setStartTs(startTs);
        vo.setEndTs(endTs);

        Map<DeviceStateEnum, Long> durationMap = new EnumMap<>(DeviceStateEnum.class);
        summaries.forEach(summary -> accumulateDurations(summary, durationMap));

        long totalDuration = durationMap.values().stream().mapToLong(Long::longValue).sum();
        vo.setTotalDuration(totalDuration);

        List<StateStatsItemVO> items = new ArrayList<>();
        durationMap.forEach((state, duration) -> {
            if (duration == null || duration <= 0) {
                return;
            }
            StateStatsItemVO item = new StateStatsItemVO();
            item.setState(state);
            item.setDurationMs(duration);
            item.setRatio(totalDuration == 0 ? 0D : (double) duration / totalDuration);
            items.add(item);
        });
        vo.setStats(items);
        return vo;
    }

    private static void accumulateDurations(DeviceStateSummaryDO summary, Map<DeviceStateEnum, Long> durationMap) {
        mergeDuration(durationMap, DeviceStateEnum.WORKING, summary.getWorkingDurationMs());
        mergeDuration(durationMap, DeviceStateEnum.RUNNING, summary.getWorkingDurationMs());
        mergeDuration(durationMap, DeviceStateEnum.STANDBY, summary.getStandbyDurationMs());
        mergeDuration(durationMap, DeviceStateEnum.FAULT, summary.getFaultDurationMs());
        mergeDuration(durationMap, DeviceStateEnum.SHUTDOWN, summary.getShutdownDurationMs());
    }

    private static void mergeDuration(Map<DeviceStateEnum, Long> map, DeviceStateEnum state, Long value) {
        if (value == null || value <= 0) {
            return;
        }
        map.merge(state, value, Long::sum);
    }
}


