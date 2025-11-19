package com.weili.iot_portal.business.alarm_mgmt.service.assembler;

import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmItemDO;
import com.weili.iot_portal.business.alarm_mgmt.domain.model.AlarmRankingItemVO;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class AlarmRankingAssembler {

    public AlarmRankingItemVO toVO(AlarmItemDO item) {
        if (item == null) {
            return null;
        }
        return AlarmRankingItemVO.builder()
                .deviceId(item.getDeviceId())
                .deviceCode(item.getDeviceCode())
                .deviceTypeName(item.getDeviceTypeName())
                .deviceSubTypeName(item.getDeviceSubTypeName())
                .alarmText(item.getAlarmText())
                .durationMs(item.getDurationMs())
                .build();
    }

    public List<AlarmRankingItemVO> toList(List<AlarmItemDO> list) {
        return list == null ? List.of() : list.stream()
                .map(AlarmRankingAssembler::toVO)
                .collect(Collectors.toList());
    }
}


