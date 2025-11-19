package com.weili.iot_portal.business.alarm_mgmt.service.assembler;

import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmItemDO;
import com.weili.iot_portal.business.alarm_mgmt.domain.model.AlarmItemVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 报警列表装配器
 */
public class AlarmListAssembler {

    /**
     * DO转VO
     */
    public static AlarmItemVO toVO(AlarmItemDO item) {
        if (item == null) {
            return null;
        }
        AlarmItemVO vo = new AlarmItemVO();
        vo.setAlarmId(item.getId());
        vo.setDeviceId(item.getDeviceId());
        vo.setDeviceCode(item.getDeviceCode());
        vo.setDeviceName(item.getDeviceName());
        vo.setDeviceType(item.getDeviceTypeName());
        vo.setDeviceSubType(item.getDeviceSubTypeName());
        vo.setAlarmCode(item.getAlarmCode());
        vo.setAlarmText(item.getAlarmText());
        vo.setAlarmLevel(item.getAlarmLevel());
        vo.setStartTime(item.getStartTs());
        vo.setEndTime(item.getEndTs());
        vo.setDurationMs(item.getDurationMs());
        vo.setIsActive(item.getIsActive());
        // 日期字段转换为String
        vo.setStartShiftDate(item.getStartShiftDate() != null ? item.getStartShiftDate().toString() : null);
        vo.setStartShiftCode(item.getStartShiftCode());
        vo.setEndShiftDate(item.getEndShiftDate() != null ? item.getEndShiftDate().toString() : null);
        vo.setEndShiftCode(item.getEndShiftCode());
        return vo;
    }

    /**
     * DO列表转VO列表
     */
    public static List<AlarmItemVO> toVOList(List<AlarmItemDO> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(AlarmListAssembler::toVO)
                .collect(Collectors.toList());
    }
}

