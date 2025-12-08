package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.dal.dataobject.efficiency.FactoryMetricsShiftDO;
import com.weili.iot_portal.domain.digital.FactoryMetricsTrendVO;
import com.weili.iot_portal.domain.digital.FactoryMetricsVO;
import com.weili.iot_portal.domain.shift.ShiftTimeRangeVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工厂级效率指标装配器
 */
public class FactoryMetricsAssembler {

    /**
     * DO转VO（包含历史趋势）
     */
    public static FactoryMetricsVO toVO(FactoryMetricsShiftDO current, List<FactoryMetricsShiftDO> history,
                                        String factoryName, ShiftTimeRangeVO shiftRange) {
        FactoryMetricsVO vo = new FactoryMetricsVO();

        // 提取指标值
        if (current != null && current.getMetrics() != null) {
              Map<String, Object> metrics = current.getMetrics();
            vo.setAverageOee(getBigDecimalValue(metrics.get("averageOee")));
            vo.setAverageUtilizationRate(getBigDecimalValue(metrics.get("averageUtilizationRate")));
        }

        // 转换历史趋势
        if (history != null && !history.isEmpty()) {
            List<FactoryMetricsTrendVO> trends = history.stream()
                    .map(FactoryMetricsAssembler::toTrendVO)
                    .collect(Collectors.toList());
            vo.setHistoryTrend(trends);
        }

        return vo;
    }

    /**
     * DO转趋势VO（对应 factory_metric_summary 表的字段）
     */
    public static FactoryMetricsTrendVO toTrendVO(FactoryMetricsShiftDO item) {
        if (item == null) {
            return null;
        }
        
        FactoryMetricsTrendVO vo = FactoryMetricsTrendVO.builder()
                .shiftDate(item.getShiftDate() != null ? item.getShiftDate().toString() : null)
                .shiftCode(item.getShiftCode())
                .shiftName(parseShiftName(item.getShiftCode()))
                .build();

        // 提取指标值
        if (item.getMetrics() != null) {
            Map<String, Object> metrics = item.getMetrics();
            vo.setAverageOee(getBigDecimalValue(metrics.get("averageOee")));
            vo.setAverageUtilizationRate(getBigDecimalValue(metrics.get("averageUtilizationRate")));
        }

        return vo;
    }

    /**
     * 从 Map 中安全获取 BigDecimal 值
     */
    private static BigDecimal getBigDecimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析班次名称
     */
    private static String parseShiftName(String shiftCode) {
        if (shiftCode == null) {
            return null;
        }
        return switch (shiftCode) {
            case "SHIFT_1" -> "早班";
            case "SHIFT_2" -> "晚班";
            case "SHIFT_3" -> "夜班";
            default -> shiftCode;
        };
    }

    /**
     * 计算平均OEE
     * 
     * <p>公式：平均OEE = 全部设备"有效生产时间"之和 ÷ 全部设备"实际运行时间"之和
     * 其中：单设备有效生产时间 = 实际运行时间 × 单设备OEE
     * 
     * @param deviceMetrics 设备级指标列表（每个设备包含oee和实际运行时间）
     * @return 平均OEE（百分比，0-100）
     */
    public static BigDecimal calculateAverageOee(List<DeviceMetricsForAggregation> deviceMetrics) {
        if (deviceMetrics == null || deviceMetrics.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalEffectiveTime = BigDecimal.ZERO;  // 总有效生产时间
        BigDecimal totalActualTime = BigDecimal.ZERO;     // 总实际运行时间

        for (DeviceMetricsForAggregation device : deviceMetrics) {
            BigDecimal oee = device.getOee() != null ? device.getOee() : BigDecimal.ZERO;
            BigDecimal actualTime = device.getActualRunTime() != null ? device.getActualRunTime() : BigDecimal.ZERO;
            
            // 单设备有效生产时间 = 实际运行时间 × OEE
            BigDecimal effectiveTime = actualTime.multiply(oee).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            
            totalEffectiveTime = totalEffectiveTime.add(effectiveTime);
            totalActualTime = totalActualTime.add(actualTime);
        }

        if (totalActualTime.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 平均OEE = 总有效生产时间 ÷ 总实际运行时间 × 100
        return totalEffectiveTime
                .divide(totalActualTime, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算平均设备利用率
     * 
     * <p>公式：平均设备利用率 = 总加工时间 ÷ 总计划时间
     * 
     * @param deviceMetrics 设备级指标列表（每个设备包含加工时间和计划时间）
     * @return 平均设备利用率（百分比，0-100）
     */
    public static BigDecimal calculateAverageUtilizationRate(List<DeviceMetricsForAggregation> deviceMetrics) {
        if (deviceMetrics == null || deviceMetrics.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalWorkingTime = BigDecimal.ZERO;  // 总加工时间
        BigDecimal totalPlannedTime = BigDecimal.ZERO;   // 总计划时间

        for (DeviceMetricsForAggregation device : deviceMetrics) {
            BigDecimal workingTime = device.getWorkingTime() != null ? device.getWorkingTime() : BigDecimal.ZERO;
            BigDecimal plannedTime = device.getPlannedTime() != null ? device.getPlannedTime() : BigDecimal.ZERO;
            
            totalWorkingTime = totalWorkingTime.add(workingTime);
            totalPlannedTime = totalPlannedTime.add(plannedTime);
        }

        if (totalPlannedTime.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 平均设备利用率 = 总加工时间 ÷ 总计划时间 × 100
        return totalWorkingTime
                .divide(totalPlannedTime, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 设备指标聚合数据（用于计算工厂级指标）
     */
    public static class DeviceMetricsForAggregation {
        private BigDecimal oee;                    // OEE（百分比）
        private BigDecimal actualRunTime;         // 实际运行时间（毫秒）
        private BigDecimal workingTime;           // 加工时间（毫秒）
        private BigDecimal plannedTime;            // 计划时间（毫秒）

        public BigDecimal getOee() {
            return oee;
        }

        public void setOee(BigDecimal oee) {
            this.oee = oee;
        }

        public BigDecimal getActualRunTime() {
            return actualRunTime;
        }

        public void setActualRunTime(BigDecimal actualRunTime) {
            this.actualRunTime = actualRunTime;
        }

        public BigDecimal getWorkingTime() {
            return workingTime;
        }

        public void setWorkingTime(BigDecimal workingTime) {
            this.workingTime = workingTime;
        }

        public BigDecimal getPlannedTime() {
            return plannedTime;
        }

        public void setPlannedTime(BigDecimal plannedTime) {
            this.plannedTime = plannedTime;
        }
    }
}

