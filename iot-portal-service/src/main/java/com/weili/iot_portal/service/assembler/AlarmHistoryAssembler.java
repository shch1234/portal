package com.weili.iot_portal.service.assembler;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.alarm.AlarmHistoryDO;
import com.weili.iot_portal.domain.devicemng.AlarmHistoryItemVO;
import com.weili.iot_portal.domain.devicemng.AlarmHistoryVO;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报警历史装配器
 */
@UtilityClass
public class AlarmHistoryAssembler {

    public static AlarmHistoryVO toVO(String deviceId, List<AlarmHistoryDO> list) {
        AlarmHistoryVO vo = new AlarmHistoryVO();
        vo.setDeviceId(deviceId);
        vo.setAlarms(CollectionUtils.isEmpty(list)
                ? Collections.emptyList()
                : list.stream().map(AlarmHistoryAssembler::toItem).collect(Collectors.toList()));
        vo.setTotal((long) vo.getAlarms().size());
        vo.setPageNo(1);
        vo.setPageSize(vo.getAlarms().size());
        vo.setTotalPages(1);
        return vo;
    }

    public static AlarmHistoryVO toVO(String deviceId, PageResult<AlarmHistoryDO> pageResult,
                                      int pageNo, int pageSize) {
        AlarmHistoryVO vo = new AlarmHistoryVO();
        vo.setDeviceId(deviceId);
        vo.setAlarms(pageResult.getList().stream()
                .map(AlarmHistoryAssembler::toItem)
                .collect(Collectors.toList()));
        vo.setTotal(pageResult.getTotal());
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);
        vo.setTotalPages((int) Math.ceil(pageResult.getTotal() / (double) pageSize));
        return vo;
    }

    /**
     * 将 DO 转换为 Item VO（对应 device_alarm_history 表的字段，时长单位：秒）
     */
    private static AlarmHistoryItemVO toItem(AlarmHistoryDO record) {
        AlarmHistoryItemVO item = new AlarmHistoryItemVO();
        item.setAlarmId(record.getId());
        item.setDeviceId(record.getDeviceInfoId());
        item.setAlarmCode(record.getAlarmCode());
        item.setAlarmText(record.getAlarmText());
        item.setAlarmLevel(record.getAlarmLevel());
        item.setStartTs(record.getStartTs());
        item.setEndTs(record.getEndTs());
        // 直接使用秒，不进行转换
        item.setDurationMs(record.getDurationS() != null ? record.getDurationS().longValue() : null);
        item.setInProgress(Boolean.TRUE.equals(record.getIsActive()));
        return item;
    }
}


