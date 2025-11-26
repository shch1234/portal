package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.domain.alarm.AlarmRankingItemVO;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoVO;
import com.weili.iot_portal.domain.devicemng.DeviceStatusVO;
import com.weili.iot_portal.domain.digital.AlarmRankingVO;
import com.weili.iot_portal.domain.digital.FactoryLayoutDeviceVO;
import com.weili.iot_portal.domain.digital.FactoryLayoutVO;
import com.weili.iot_portal.domain.digital.FactoryStatusSummaryVO;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class DigitalScreenAssembler {

    private static final String STATUS_RUNNING = "加工中";
    private static final String STATUS_STANDBY = "待机";
    private static final String STATUS_FAULT = "故障";
    private static final String STATUS_SHUTDOWN = "关机";

    public FactoryLayoutVO toFactoryLayout(String factoryId,
                                           String factoryName,
                                           List<DeviceBaseInfoVO> devices,
                                           Map<String, DeviceStatusVO> statusMap) {
        List<FactoryLayoutDeviceVO> deviceVOs = CollectionUtils.emptyIfNull(devices)
                .stream()
                .map(device -> FactoryLayoutDeviceVO.builder()
                        .deviceId(device.getId())
                        .deviceCode(device.getDeviceCode())
                        .deviceTypeName(device.getDeviceTypeName())
                        .deviceSubTypeName(device.getDeviceSubTypeName())
                        .modelName(device.getModelName())
                        .currentStatus(resolveStatus(statusMap, device.getId()))
                        .build())
                .collect(Collectors.toList());

        return FactoryLayoutVO.builder()
                .factoryId(factoryId)
                .factoryName(factoryName)
                .devices(deviceVOs)
                .build();
    }

    public FactoryStatusSummaryVO toStatusSummary(String factoryId,
                                                  String factoryName,
                                                  List<DeviceBaseInfoVO> devices,
                                                  Map<String, DeviceStatusVO> statusMap) {
        long total = CollectionUtils.size(devices);
        long running = 0;
        long standby = 0;
        long fault = 0;
        long shutdown = 0;

        for (DeviceBaseInfoVO device : CollectionUtils.emptyIfNull(devices)) {
            String status = resolveStatus(statusMap, device.getId());
            switch (status) {
                case STATUS_RUNNING -> running++;
                case STATUS_STANDBY -> standby++;
                case STATUS_FAULT -> fault++;
                case STATUS_SHUTDOWN -> shutdown++;
                default -> {
                }
            }
        }

        return FactoryStatusSummaryVO.builder()
                .factoryId(factoryId)
                .factoryName(factoryName)
                .totalCount(total)
                .runningCount(running)
                .standbyCount(standby)
                .faultCount(fault)
                .shutdownCount(shutdown)
                .runningRatio(calcRatio(running, total))
                .standbyRatio(calcRatio(standby, total))
                .faultRatio(calcRatio(fault, total))
                .shutdownRatio(calcRatio(shutdown, total))
                .build();
    }

    private static String resolveStatus(Map<String, DeviceStatusVO> statusMap, String deviceId) {
        if (statusMap == null || deviceId == null) {
            return "未知";
        }
        DeviceStatusVO statusVO = statusMap.get(deviceId);
        if (statusVO == null) {
            return "未知";
        }
        return statusVO.getCurrentStatus() != null ? statusVO.getCurrentStatus() : "未知";
    }

    private static BigDecimal calcRatio(long count, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    public List<AlarmRankingVO> toAlarmRanking(List<AlarmRankingItemVO> items) {
        return items == null ? List.of() : items.stream()
                .map(item -> AlarmRankingVO.builder()
                        .deviceCode(item.getDeviceCode())
                        .deviceTypeName(item.getDeviceTypeName())
                        .deviceSubTypeName(item.getDeviceSubTypeName())
                        .alarmText(item.getAlarmText())
                        .durationMs(item.getDurationMs())
                        .build())
                .collect(Collectors.toList());
    }
}


