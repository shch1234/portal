package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;
import com.weili.iot_portal.domain.devicemng.StateGanttSegmentVO;
import com.weili.iot_portal.domain.devicemng.StateGanttVO;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 状态甘特装配器
 */
@UtilityClass
public class StateGanttAssembler {

    public static StateGanttVO toVO(List<DeviceStateTimelineDO> timeline, Long startTs, Long endTs) {
        StateGanttVO vo = new StateGanttVO();
        vo.setStartTs(startTs);
        vo.setEndTs(endTs);
        vo.setTotal(timeline == null ? 0L : (long) timeline.size());
        vo.setSegments(CollectionUtils.isEmpty(timeline)
                ? Collections.emptyList()
                : timeline.stream().map(StateGanttAssembler::toSegment).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 将 DO 转换为 Segment VO（对应 device_state_record 表的字段，时长单位：秒）
     */
    private static StateGanttSegmentVO toSegment(DeviceStateTimelineDO record) {
        StateGanttSegmentVO vo = new StateGanttSegmentVO();
        vo.setId(record.getId());
        vo.setState(DeviceStateEnum.of(record.getStateCode()));
        vo.setStartTs(record.getStartTs());
        vo.setEndTs(record.getEndTs());
        vo.setDurationMs(record.getDurationS() != null ? record.getDurationS().longValue() : null);
        return vo;
    }
}


