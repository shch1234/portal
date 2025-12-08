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

    /**
     * 累加各状态的时长（对应 device_state_summary 表的字段，单位：秒）
     */
    private static void accumulateDurations(DeviceStateSummaryDO summary, Map<DeviceStateEnum, Long> durationMap) {
        // 直接使用秒，不进行转换
        Long workingS = summary.getWorkingDurationS() != null ? summary.getWorkingDurationS().longValue() : null;
        Long standbyS = summary.getStandbyDurationS() != null ? summary.getStandbyDurationS().longValue() : null;
        Long faultS = summary.getFaultDurationS() != null ? summary.getFaultDurationS().longValue() : null;
        Long shutdownS = summary.getShutdownDurationS() != null ? summary.getShutdownDurationS().longValue() : null;
        
        mergeDuration(durationMap, DeviceStateEnum.WORKING, workingS);
        mergeDuration(durationMap, DeviceStateEnum.RUNNING, workingS);
        mergeDuration(durationMap, DeviceStateEnum.STANDBY, standbyS);
        mergeDuration(durationMap, DeviceStateEnum.FAULT, faultS);
        mergeDuration(durationMap, DeviceStateEnum.SHUTDOWN, shutdownS);
    }

    private static void mergeDuration(Map<DeviceStateEnum, Long> map, DeviceStateEnum state, Long value) {
        if (value == null || value <= 0) {
            return;
        }
        map.merge(state, value, Long::sum);
    }
}


