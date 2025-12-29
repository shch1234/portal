package com.weili.iot_portal.service.metrics;

/**
 * 指标计算上下文
 * <p>
 * 包含计算设备指标（OEE、可用率、性能率等）所需的所有输入数据。
 * 所有时长字段统一使用毫秒（milliseconds）作为单位。
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
public class MetricCalculationContext {
    
    /**
     * 班次时长（毫秒）= shift_end_ts - shift_start_ts
     */
    private final long shiftDurationMillis;
    
    /**
     * 计划停机时长（秒），从设备参数配置中获取
     */
    private final long plannedDowntimeSeconds;
    
    /**
     * 计划停机时长（毫秒）= plannedDowntimeSeconds * 1000
     */
    private final long plannedDowntimeMillis;
    
    /**
     * 计划运行时长（毫秒）= 班次时长 - 计划停机时长
     */
    private final long plannedRuntimeMillis;
    
    /**
     * 待机时长（毫秒），从状态汇总中获取
     */
    private final long standbyMillis;
    
    /**
     * 故障时长（毫秒），从状态汇总中获取
     */
    private final long faultMillis;
    
    /**
     * 关机时长（毫秒），从状态汇总中获取
     */
    private final long shutdownMillis;
    
    /**
     * 加工时长（毫秒），从状态汇总中获取
     */
    private final long workingMillis;
    
    /**
     * 非计划停机时长（毫秒）= 待机时长 + 故障时长 + 关机时长
     */
    private final long unplannedDowntimeMillis;
    
    /**
     * 实际运行时长（毫秒）= 计划运行时长 - 非计划停机时长
     */
    private final long actualRuntimeMillis;
    
    /**
     * 实际产量（件），从产量汇总中获取
     */
    private final long actualOutput;
    
    /**
     * 合格数量（件），从产量汇总中获取
     */
    private final long qualifiedOutput;
    
    /**
     * 理论节拍（秒），从设备参数配置中获取
     */
    private final long theoreticalCycleSeconds;
    
    /**
     * 可用率计算的分母（毫秒），可选参数
     * <p>
     * 如果为0或未设置，默认使用班次时长（shiftDurationMillis）
     * <p>
     * 使用场景：
     * <ul>
     *   <li>班次指标汇总：使用班次时长（默认）</li>
     *   <li>实时指标计算：使用已过日历时长</li>
     * </ul>
     */
    private final long availabilityDenominatorMillis;
    
    public MetricCalculationContext(long shiftDurationMillis, long plannedDowntimeSeconds,
                                   long plannedDowntimeMillis, long plannedRuntimeMillis,
                                   long standbyMillis, long faultMillis, long shutdownMillis,
                                   long workingMillis, long unplannedDowntimeMillis,
                                   long actualRuntimeMillis, long actualOutput,
                                   long qualifiedOutput, long theoreticalCycleSeconds) {
        this(shiftDurationMillis, plannedDowntimeSeconds, plannedDowntimeMillis, plannedRuntimeMillis,
                standbyMillis, faultMillis, shutdownMillis, workingMillis, unplannedDowntimeMillis,
                actualRuntimeMillis, actualOutput, qualifiedOutput, theoreticalCycleSeconds, 0L);
    }
    
    public MetricCalculationContext(long shiftDurationMillis, long plannedDowntimeSeconds,
                                   long plannedDowntimeMillis, long plannedRuntimeMillis,
                                   long standbyMillis, long faultMillis, long shutdownMillis,
                                   long workingMillis, long unplannedDowntimeMillis,
                                   long actualRuntimeMillis, long actualOutput,
                                   long qualifiedOutput, long theoreticalCycleSeconds,
                                   long availabilityDenominatorMillis) {
        this.shiftDurationMillis = shiftDurationMillis;
        this.plannedDowntimeSeconds = plannedDowntimeSeconds;
        this.plannedDowntimeMillis = plannedDowntimeMillis;
        this.plannedRuntimeMillis = plannedRuntimeMillis;
        this.standbyMillis = standbyMillis;
        this.faultMillis = faultMillis;
        this.shutdownMillis = shutdownMillis;
        this.workingMillis = workingMillis;
        this.unplannedDowntimeMillis = unplannedDowntimeMillis;
        this.actualRuntimeMillis = actualRuntimeMillis;
        this.actualOutput = actualOutput;
        this.qualifiedOutput = qualifiedOutput;
        this.theoreticalCycleSeconds = theoreticalCycleSeconds;
        this.availabilityDenominatorMillis = availabilityDenominatorMillis;
    }
    
    // Getters
    public long getShiftDurationMillis() { return shiftDurationMillis; }
    public long getPlannedDowntimeSeconds() { return plannedDowntimeSeconds; }
    public long getPlannedDowntimeMillis() { return plannedDowntimeMillis; }
    public long getPlannedRuntimeMillis() { return plannedRuntimeMillis; }
    public long getStandbyMillis() { return standbyMillis; }
    public long getFaultMillis() { return faultMillis; }
    public long getShutdownMillis() { return shutdownMillis; }
    public long getWorkingMillis() { return workingMillis; }
    public long getUnplannedDowntimeMillis() { return unplannedDowntimeMillis; }
    public long getActualRuntimeMillis() { return actualRuntimeMillis; }
    public long getActualOutput() { return actualOutput; }
    public long getQualifiedOutput() { return qualifiedOutput; }
    public long getTheoreticalCycleSeconds() { return theoreticalCycleSeconds; }
    public long getAvailabilityDenominatorMillis() { return availabilityDenominatorMillis; }
}

