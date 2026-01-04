package com.weili.iot_portal.domain.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备指标计算器
 * <p>
 * 提供设备指标（OEE、可用率、性能率等）的计算功能。
 * 所有计算基于 {@link MetricCalculationContext} 提供的输入数据。
 * <p>
 * 计算公式说明：
 * <ul>
 *   <li><b>时间开动率（Availability）</b> = 实际运行时长 / 计划运行时长</li>
 *   <li><b>性能开动率（Performance Rate）</b> = (实际产量 × 理论节拍) / 实际运行时长</li>
 *   <li><b>设备利用率（Utilization Rate）</b> = 加工时长 / 班次时长</li>
 *   <li><b>故障率（Fault Rate）</b> = 故障时长 / 计划运行时长 × 100%</li>
 *   <li><b>质量率（Quality Rate）</b> = 合格数量 / 加工数量</li>
 *   <li><b>OEE</b> = 时间开动率 × 性能开动率 × 质量率</li>
 * </ul>
 * <p>
 * 使用场景：
 * <ul>
 *   <li>设备班次指标汇总</li>
 *   <li>工厂指标汇总</li>
 *   <li>实时指标计算</li>
 * </ul>
 *
 * @author system
 */
public class MetricCalculator {
    
    // 时间转换常量
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long MILLIS_PER_HOUR = 3600L * MILLIS_PER_SECOND;
    private static final BigDecimal MILLIS_PER_HOUR_DECIMAL = BigDecimal.valueOf(MILLIS_PER_HOUR);
    private static final BigDecimal PERCENTAGE_DIVISOR = BigDecimal.valueOf(100);
    
    /**
     * 计算所有指标
     *
     * @param context 指标计算上下文，包含所有计算所需的数据
     * @return 指标计算结果
     */
    public static MetricCalculationResult calculate(MetricCalculationContext context) {
        // 1. 时间开动率（Availability）
        BigDecimal availability = calculateAvailability(context);
        
        // 2. 性能开动率（Performance Rate）
        BigDecimal performance = calculatePerformance(context);
        
        // 3. 设备开动率（设备利用率/可用率）
        BigDecimal utilizationRate = calculateAvailabilityRate(context);
        
        // 4. 故障率
        BigDecimal faultRatePercent = calculateFaultRate(context);
        
        // 5. 质量率
        BigDecimal quality = calculateQuality(context);
        
        // 6. OEE
        BigDecimal oee = calculateOEE(availability, performance, quality);
        
        // 7. 加工时长（小时）
        BigDecimal workingHours = calculateWorkingHours(context);
        
        // 8. 实际节拍（秒）
        BigDecimal actualCycleS = calculateActualCycle(context);
        
        // 构建结果Map（百分比形式，用于存储到数据库的JSON字段）
        BigDecimal availabilityPercent = availability.multiply(PERCENTAGE_DIVISOR);
        BigDecimal performancePercent = performance.multiply(PERCENTAGE_DIVISOR);
        BigDecimal utilizationRatePercent = utilizationRate.multiply(PERCENTAGE_DIVISOR);
        BigDecimal qualityRatePercent = quality.multiply(PERCENTAGE_DIVISOR);
        BigDecimal oeePercent = oee.multiply(PERCENTAGE_DIVISOR);
        
        Map<String, Object> metrics = Map.of(
                "availability", availabilityPercent,
                "performance", performancePercent,
                "utilizationRate", utilizationRatePercent,
                "faultRate", faultRatePercent,
                "oee", oeePercent,
                "qualityRate", qualityRatePercent
        );
        
        // 构建计算过程数据Map（用于调试和审计）
        Map<String, Object> calcData = new HashMap<>();
        calcData.put("shiftDurationMillis", context.getShiftDurationMillis());
        calcData.put("plannedDowntimeSeconds", context.getPlannedDowntimeSeconds());
        calcData.put("plannedDowntimeMillis", context.getPlannedDowntimeMillis());
        calcData.put("plannedRuntimeMillis", context.getPlannedRuntimeMillis());
        calcData.put("standbyMillis", context.getStandbyMillis());
        calcData.put("faultMillis", context.getFaultMillis());
        calcData.put("shutdownMillis", context.getShutdownMillis());
        calcData.put("unplannedDowntimeMillis", context.getUnplannedDowntimeMillis());
        calcData.put("actualRuntimeMillis", context.getActualRuntimeMillis());
        calcData.put("actualOutput", context.getActualOutput());
        calcData.put("theoreticalCycleSeconds", context.getTheoreticalCycleSeconds());
        calcData.put("workingMillis", context.getWorkingMillis());
        
        return new MetricCalculationResult(
                oee, availability, performance, quality, utilizationRate, workingHours, actualCycleS,
                context.getUnplannedDowntimeMillis(), context.getActualOutput(), context.getQualifiedOutput(),
                metrics, calcData
        );
    }
    
    /**
     * 计算时间开动率（Availability）
     * <p>
     * 公式：时间开动率 = 实际运行时长 / 计划运行时长
     * <p>
     * 说明：
     * <ul>
     *   <li>实际运行时长 = 计划运行时长 - 非计划停机时长</li>
     *   <li>计划运行时长 = 班次时长 - 计划停机时长</li>
     *   <li>非计划停机时长 = 待机时长 + 故障时长 + 关机时长</li>
     * </ul>
     *
     * @param context 计算上下文
     * @return 时间开动率（0-1之间的小数），如果计划运行时长为0则返回0
     */
    public static BigDecimal calculateAvailability(MetricCalculationContext context) {
        if (context.getPlannedRuntimeMillis() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(context.getActualRuntimeMillis())
                .divide(BigDecimal.valueOf(context.getPlannedRuntimeMillis()), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算性能开动率（Performance Rate）
     * <p>
     * 公式：性能开动率 = (实际产量 × 理论节拍) / 实际运行时长
     * <p>
     * 说明：
     * <ul>
     *   <li>理论节拍（秒）需要转换为毫秒，与实际运行时长单位一致</li>
     *   <li>如果实际运行时长为0、理论节拍<=0或实际产量<=0，则返回0</li>
     * </ul>
     *
     * @param context 计算上下文
     * @return 性能开动率（0-1之间的小数），如果无法计算则返回0
     */
    public static BigDecimal calculatePerformance(MetricCalculationContext context) {
        if (context.getActualRuntimeMillis() == 0 
                || context.getTheoreticalCycleSeconds() <= 0 
                || context.getActualOutput() <= 0) {
            return BigDecimal.ZERO;
        }
        // 理论节拍（秒）转换为毫秒，与actualRuntimeMillis单位一致
        long theoreticalCycleMillis = context.getTheoreticalCycleSeconds() * MILLIS_PER_SECOND;
        return BigDecimal.valueOf(context.getActualOutput())
                .multiply(BigDecimal.valueOf(theoreticalCycleMillis))
                .divide(BigDecimal.valueOf(context.getActualRuntimeMillis()), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算设备开动率（设备利用率）
     * <p>
     * 公式：设备开动率 = 加工时长 / 班次时长
     * <p>
     * 说明：衡量设备在班次总时长内，实际用于加工的时间比例。
     *
     * @param context 计算上下文
     * @return 设备开动率（0-1之间的小数），如果班次时长为0则返回0
     */
    public static BigDecimal calculateUtilizationRate(MetricCalculationContext context) {
        return calculateAvailabilityRate(context);
    }
    
    /**
     * 计算可用率（Availability Rate）
     * <p>
     * 公式：可用率 = 加工时长 / 分母时长
     * <p>
     * 说明：
     * <ul>
     *   <li>如果 context.getAvailabilityDenominatorMillis() > 0，使用自定义分母</li>
     *   <li>否则，使用班次时长作为分母</li>
     *   <li>衡量设备在指定时长内，实际用于加工的时间比例</li>
     * </ul>
     * <p>
     * 使用场景：
     * <ul>
     *   <li>班次指标汇总：使用班次时长（默认）</li>
     *   <li>实时指标计算：使用已过日历时长（通过 availabilityDenominatorMillis 参数传递）</li>
     * </ul>
     *
     * @param context 计算上下文
     * @return 可用率（0-1之间的小数），如果分母时长为0则返回0
     */
    public static BigDecimal calculateAvailabilityRate(MetricCalculationContext context) {
        // 如果设置了自定义分母，使用自定义分母；否则使用班次时长
        long denominatorMillis = context.getAvailabilityDenominatorMillis() > 0 
                ? context.getAvailabilityDenominatorMillis() 
                : context.getShiftDurationMillis();
        
        if (denominatorMillis == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(context.getWorkingMillis())
                .divide(BigDecimal.valueOf(denominatorMillis), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算故障率
     * <p>
     * 公式：故障率 = 故障时长 / 计划运行时长 × 100%
     * <p>
     * 说明：衡量设备在计划运行时间内，因故障导致的停机时间比例。
     *
     * @param context 计算上下文
     * @return 故障率（百分比值，例如：5.71 表示 5.71%），如果计划运行时长为0则返回0
     */
    public static BigDecimal calculateFaultRate(MetricCalculationContext context) {
        if (context.getPlannedRuntimeMillis() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(context.getFaultMillis())
                .divide(BigDecimal.valueOf(context.getPlannedRuntimeMillis()), 4, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_DIVISOR);
    }
    
    /**
     * 计算质量率
     * <p>
     * 公式：质量率 = 合格数量 / 加工数量
     * <p>
     * 说明：
     * <ul>
     *   <li>衡量实际产出中，合格产品的比例</li>
     *   <li>如果实际产量为0，默认质量率为100%（返回1.0）</li>
     * </ul>
     *
     * @param context 计算上下文
     * @return 质量率（0-1之间的小数），如果实际产量为0则返回1.0（100%）
     */
    public static BigDecimal calculateQuality(MetricCalculationContext context) {
        if (context.getActualOutput() == 0) {
            return BigDecimal.ONE; // 如果没有产量，默认质量率为100%
        }
        return BigDecimal.valueOf(context.getQualifiedOutput())
                .divide(BigDecimal.valueOf(context.getActualOutput()), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算OEE（整体设备效率）
     * <p>
     * 公式：OEE = 时间开动率 × 性能开动率 × 质量率
     * <p>
     * 说明：OEE是衡量设备综合效率的指标，是时间开动率、性能开动率和质量率的乘积。
     *
     * @param availability 时间开动率（0-1之间的小数）
     * @param performance 性能开动率（0-1之间的小数）
     * @param quality 质量率（0-1之间的小数）
     * @return OEE（0-1之间的小数）
     */
    public static BigDecimal calculateOEE(BigDecimal availability, BigDecimal performance, BigDecimal quality) {
        return availability.multiply(performance).multiply(quality);
    }
    
    /**
     * 计算加工时长（小时）
     * <p>
     * 公式：加工时长（小时）= 加工时长（毫秒） / 毫秒每小时
     *
     * @param context 计算上下文
     * @return 加工时长（小时），保留2位小数
     */
    public static BigDecimal calculateWorkingHours(MetricCalculationContext context) {
        return BigDecimal.valueOf(context.getWorkingMillis())
                .divide(MILLIS_PER_HOUR_DECIMAL, 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算实际节拍（秒）
     * <p>
     * 公式：实际节拍（秒）= 实际运行时长（毫秒） / 加工数量 / 1000
     * <p>
     * 说明：设备生产一个产品所需的平均实际时间。
     *
     * @param context 计算上下文
     * @return 实际节拍（秒），保留2位小数。如果实际运行时长为0或实际产量为0，则返回null
     */
    public static BigDecimal calculateActualCycle(MetricCalculationContext context) {
        if (context.getActualRuntimeMillis() == 0 || context.getActualOutput() == 0) {
            return null;
        }
        return BigDecimal.valueOf(context.getActualRuntimeMillis())
                .divide(BigDecimal.valueOf(context.getActualOutput()), 2, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(MILLIS_PER_SECOND), 2, RoundingMode.HALF_UP);
    }
}

