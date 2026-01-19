package com.weili.iot_portal.domain.metrics;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 指标计算结果
 * <p>
 * 包含所有计算出的设备指标值，包括：
 * <ul>
 *   <li>OEE（整体设备效率）</li>
 *   <li>可用率（Availability）</li>
 *   <li>性能率（Performance Rate）</li>
 *   <li>质量率（Quality Rate）</li>
 *   <li>设备利用率（Utilization Rate）</li>
 *   <li>故障率（Fault Rate）</li>
 *   <li>其他辅助指标（加工时长、实际节拍等）</li>
 * </ul>
 * <p>
 * 所有比率值统一使用 0-1 之间的小数表示（例如：0.85 表示 85%）。
 *
 * @author system
 */
public class MetricCalculationResult {
    
    /**
     * OEE（整体设备效率），0-1之间的小数
     */
    private final BigDecimal oee;
    
    /**
     * 可用率（时间开动率），0-1之间的小数
     */
    private final BigDecimal availability;
    
    /**
     * 性能率（性能开动率），0-1之间的小数
     */
    private final BigDecimal performance;
    
    /**
     * 质量率，0-1之间的小数
     */
    private final BigDecimal quality;
    
    /**
     * 设备利用率（设备开动率），0-1之间的小数
     */
    private final BigDecimal utilizationRate;
    
    /**
     * 故障率，0-1之间的小数
     * 故障率 = 故障停机总时间 ÷ 计划运行时间 × 100%
     */
    private final BigDecimal faultRate;
    
    /**
     * 加工时长（小时）
     */
    private final BigDecimal workingHours;
    
    /**
     * 实际节拍（秒），如果无法计算则为 null
     */
    private final BigDecimal actualCycleS;
    
    /**
     * 非计划停机时长（毫秒）
     */
    private final long unplannedDowntimeMillis;
    
    /**
     * 实际产量（件）
     */
    private final long actualOutput;
    
    /**
     * 合格数量（件）
     */
    private final long qualifiedOutput;
    
    /**
     * 指标Map（百分比形式，用于存储到数据库的JSON字段）
     * Key: 指标名称（availability, performance, utilizationRate, faultRate, oee, qualityRate）
     * Value: 百分比值（例如：85.0 表示 85%）
     */
    private final Map<String, Object> metrics;
    
    /**
     * 计算过程数据Map（用于调试和审计）
     * 包含所有中间计算值，如：班次时长、计划运行时长、实际运行时长等
     */
    private final Map<String, Object> calcData;
    
    public MetricCalculationResult(BigDecimal oee, BigDecimal availability, BigDecimal performance,
                                 BigDecimal quality, BigDecimal utilizationRate, BigDecimal faultRate,
                                 BigDecimal workingHours, BigDecimal actualCycleS, long unplannedDowntimeMillis,
                                 long actualOutput, long qualifiedOutput,
                                 Map<String, Object> metrics, Map<String, Object> calcData) {
        this.oee = oee;
        this.availability = availability;
        this.performance = performance;
        this.quality = quality;
        this.utilizationRate = utilizationRate;
        this.faultRate = faultRate;
        this.workingHours = workingHours;
        this.actualCycleS = actualCycleS;
        this.unplannedDowntimeMillis = unplannedDowntimeMillis;
        this.actualOutput = actualOutput;
        this.qualifiedOutput = qualifiedOutput;
        this.metrics = metrics;
        this.calcData = calcData;
    }
    
    // Getters
    public BigDecimal getOee() { return oee; }
    public BigDecimal getAvailability() { return availability; }
    public BigDecimal getPerformance() { return performance; }
    public BigDecimal getQuality() { return quality; }
    public BigDecimal getUtilizationRate() { return utilizationRate; }
    public BigDecimal getFaultRate() { return faultRate; }
    public BigDecimal getWorkingHours() { return workingHours; }
    public BigDecimal getActualCycleS() { return actualCycleS; }
    public long getUnplannedDowntimeMillis() { return unplannedDowntimeMillis; }
    public long getActualOutput() { return actualOutput; }
    public long getQualifiedOutput() { return qualifiedOutput; }
    public Map<String, Object> getMetrics() { return metrics; }
    public Map<String, Object> getCalcData() { return calcData; }
}

