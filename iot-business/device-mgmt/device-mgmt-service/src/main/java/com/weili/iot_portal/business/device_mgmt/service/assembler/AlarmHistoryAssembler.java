package com.weili.iot_portal.business.device_mgmt.service.assembler;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.AlarmHistoryDO;
import com.weili.iot_portal.business.device_mgmt.domain.model.AlarmHistoryItemVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.AlarmHistoryVO;
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

    private static AlarmHistoryItemVO toItem(AlarmHistoryDO record) {
        AlarmHistoryItemVO item = new AlarmHistoryItemVO();
        item.setAlarmId(record.getId());
        item.setDeviceId(record.getDeviceId());
        item.setAlarmCode(record.getAlarmCode());
        item.setAlarmText(record.getAlarmText());
        item.setAlarmLevel(record.getAlarmLevel());
        item.setStartTs(record.getStartTs());
        item.setEndTs(record.getEndTs());
        item.setDurationMs(record.getDurationMs());
        item.setInProgress(Boolean.TRUE.equals(record.getIsActive()));
        return item;
    }
}


