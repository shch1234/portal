package com.weili.iot_portal.business.digital_screen.service.assembler;

import com.weili.iot_portal.business.common.domain.model.device.DeviceBaseInfoVO;
import com.weili.iot_portal.business.alarm_mgmt.domain.model.AlarmRankingItemVO;
import com.weili.iot_portal.business.device_mgmt.api.DeviceStateService.DeviceStatusVO;
import com.weili.iot_portal.business.digital_screen.domain.model.AlarmRankingVO;
import com.weili.iot_portal.business.digital_screen.domain.model.FactoryLayoutDeviceVO;
import com.weili.iot_portal.business.digital_screen.domain.model.FactoryLayoutVO;
import com.weili.iot_portal.business.digital_screen.domain.model.FactoryMetricsTrendVO;
import com.weili.iot_portal.business.digital_screen.domain.model.FactoryMetricsVO;
import com.weili.iot_portal.business.digital_screen.domain.model.FactoryStatusSummaryVO;
import com.weili.iot_portal.business.efficiency_mgmt.domain.model.FactoryMetricsTrendVO as EfficiencyTrendVO;
import com.weili.iot_portal.business.efficiency_mgmt.domain.model.FactoryMetricsVO as EfficiencyMetricsVO;
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

    public FactoryMetricsVO toFactoryMetrics(com.weili.iot_portal.business.efficiency_mgmt.domain.model.FactoryMetricsVO efficiencyMetrics) {
        if (efficiencyMetrics == null) {
            return FactoryMetricsVO.builder()
                    .currentAverageOee(BigDecimal.ZERO)
                    .currentAverageUtilizationRate(BigDecimal.ZERO)
                    .historyTrend(List.of())
                    .build();
        }

        // 转换历史趋势
        List<FactoryMetricsTrendVO> historyTrend = CollectionUtils.emptyIfNull(efficiencyMetrics.getHistoryTrend())
                .stream()
                .map(this::toTrendVO)
                .collect(Collectors.toList());

        return FactoryMetricsVO.builder()
                .factoryId(efficiencyMetrics.getFactoryId())
                .factoryName(efficiencyMetrics.getFactoryName())
                .currentAverageOee(efficiencyMetrics.getAverageOee())
                .currentAverageUtilizationRate(efficiencyMetrics.getAverageUtilizationRate())
                .historyTrend(historyTrend)
                .build();
    }

    private FactoryMetricsTrendVO toTrendVO(com.weili.iot_portal.business.efficiency_mgmt.domain.model.FactoryMetricsTrendVO efficiencyTrend) {
        if (efficiencyTrend == null) {
            return null;
        }
        return FactoryMetricsTrendVO.builder()
                .shiftDate(efficiencyTrend.getShiftDate())
                .shiftCode(efficiencyTrend.getShiftCode())
                .shiftName(efficiencyTrend.getShiftName())
                .averageOee(efficiencyTrend.getAverageOee())
                .averageUtilizationRate(efficiencyTrend.getAverageUtilizationRate())
                .build();
    }
}


