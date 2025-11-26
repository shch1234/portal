package com.weili.iot_portal.service.alarm.assembler;

import com.weili.iot_portal.dal.dataobject.alarm.AlarmDeviceStatisticsDO;
import com.weili.iot_portal.domain.alarm.AlarmDeviceItemVO;
import com.weili.iot_portal.domain.alarm.CurrentAlarmDeviceCountVO;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 报警统计装配器
 */
public class AlarmStatisticsAssembler {

    /**
     * 转换为当前报警设备数量VO
     */
    public static CurrentAlarmDeviceCountVO toCountVO(String factoryId, String factoryName,
                                                      String workshopId, String workshopName,
                                                      List<AlarmDeviceStatisticsDO> statistics) {
        CurrentAlarmDeviceCountVO vo = new CurrentAlarmDeviceCountVO();
        vo.setFactoryId(factoryId);
        vo.setFactoryName(factoryName);
        vo.setWorkshopId(workshopId);
        vo.setWorkshopName(workshopName);
        vo.setDeviceCount(CollectionUtils.isEmpty(statistics) ? 0 : statistics.size());
        return vo;
    }

    /**
     * 转换为报警设备项VO
     */
    public static AlarmDeviceItemVO toDeviceItemVO(AlarmDeviceStatisticsDO statistics) {
        AlarmDeviceItemVO vo = new AlarmDeviceItemVO();
        vo.setDeviceId(statistics.getDeviceId());
        vo.setDeviceCode(statistics.getDeviceCode());
        vo.setDeviceName(statistics.getDeviceName());
        vo.setDeviceType(statistics.getDeviceTypeName());
        vo.setDeviceSubType(statistics.getDeviceSubTypeName());
        vo.setWorkshopName(statistics.getWorkshopName());
        vo.setAlarmCount(statistics.getAlarmCount() != null ? statistics.getAlarmCount() : 0);
        vo.setLatestAlarmStartTs(statistics.getLatestAlarmStartTs());
        return vo;
    }

    /**
     * 批量转换为报警设备项VO列表
     */
    public static List<AlarmDeviceItemVO> toDeviceItemVOList(List<AlarmDeviceStatisticsDO> statistics) {
        if (CollectionUtils.isEmpty(statistics)) {
            return List.of();
        }
        return statistics.stream()
                .map(AlarmStatisticsAssembler::toDeviceItemVO)
                .collect(Collectors.toList());
    }
}

