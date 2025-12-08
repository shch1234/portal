package com.weili.iot_portal.service.efficiency.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.api.device.DeviceBaseDataApi;
import com.weili.iot_portal.api.device.FactoryMetricApi;
import com.weili.iot_portal.api.shift.ShiftQueryApi;
import com.weili.iot_portal.dal.dataobject.efficiency.DeviceEfficiencyMetricDO;
import com.weili.iot_portal.dal.dataobject.efficiency.FactoryMetricsShiftDO;
import com.weili.iot_portal.dal.repository.effiency.EfficiencyMetricRepository;
import com.weili.iot_portal.dal.repository.effiency.FactoryMetricsRepository;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoVO;
import com.weili.iot_portal.domain.digital.FactoryMetricsTrendVO;
import com.weili.iot_portal.domain.digital.FactoryMetricsVO;
import com.weili.iot_portal.domain.shift.ShiftInfoVO;
import com.weili.iot_portal.domain.shift.ShiftTimeRangeVO;
import com.weili.iot_portal.service.assembler.FactoryMetricsAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工厂级效率指标API实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactoryMetricsApiImpl implements FactoryMetricApi {

    private final FactoryMetricsRepository factoryMetricsRepository;
    private final EfficiencyMetricRepository efficiencyMetricRepository;
    private final DeviceBaseDataApi deviceBaseDataApi;
    private final ShiftQueryApi shiftQueryApi;

    @Override
    public FactoryMetricsVO getCurrentFactoryMetrics(String tenantId, String factoryId, Integer days) {
        validateParams(tenantId, factoryId);

        // 获取当前班次信息
        ShiftInfoVO currentShift = shiftQueryApi.getFactoryCurrentShift(tenantId, factoryId, null, null);
        String shiftDate = currentShift.getShiftDate();
        String shiftCode = currentShift.getShiftCode();
        ShiftTimeRangeVO shiftRange = shiftQueryApi.calculateShiftRange(tenantId, factoryId,
                getRepresentativeDeviceId(tenantId, factoryId), null);

        // 获取工厂名称
        String factoryName = getFactoryName(tenantId, factoryId);

        // 先尝试从历史数据表查询当前班次的数据
        Optional<FactoryMetricsShiftDO> historyData = factoryMetricsRepository.findByShift(
                tenantId, factoryId, shiftDate, shiftCode);

        FactoryMetricsShiftDO current;
        if (historyData.isPresent() && Boolean.TRUE.equals(historyData.get().getIsFinalized())) {
            // 如果历史数据已存在且已最终确定，直接使用
            current = historyData.get();
            log.debug("使用历史数据：factoryId={}, shiftDate={}, shiftCode={}", factoryId, shiftDate, shiftCode);
        } else {
            // 否则实时计算当前班次的指标值
            current = calculateCurrentMetrics(tenantId, factoryId, shiftDate, shiftCode, shiftRange);
            log.debug("实时计算指标：factoryId={}, shiftDate={}, shiftCode={}", factoryId, shiftDate, shiftCode);
        }

        // 查询历史趋势（使用传入的天数参数，默认7天）
        int dayCount = days != null && days > 0 ? days : 7;
        LocalDate endDate = LocalDate.parse(shiftDate, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate startDate = endDate.minusDays(dayCount - 1);  // 过去N天（包含今天）
        List<FactoryMetricsShiftDO> history = factoryMetricsRepository.findHistory(
                tenantId, factoryId,
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

        return FactoryMetricsAssembler.toVO(current, history, factoryName, shiftRange);
    }

    @Override
    public FactoryMetricsVO getFactoryMetricsHistory(String tenantId, String factoryId, Integer days) {
        validateParams(tenantId, factoryId);

        int dayCount = days != null && days > 0 ? days : 7;  // 默认7天

        // 获取当前班次信息（用于确定查询范围）
        ShiftInfoVO currentShift = shiftQueryApi.getFactoryCurrentShift(tenantId, factoryId, null, null);
        LocalDate endDate = LocalDate.parse(currentShift.getShiftDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate startDate = endDate.minusDays(dayCount - 1);  // 过去N天（包含今天）

        // 获取工厂名称
        String factoryName = getFactoryName(tenantId, factoryId);

        // 查询历史数据
        List<FactoryMetricsShiftDO> history = factoryMetricsRepository.findHistory(
                tenantId, factoryId,
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

        // 构建返回对象（不包含当前实时值）
        FactoryMetricsVO vo = FactoryMetricsVO.builder()
                .factoryId(factoryId)
                .factoryName(factoryName)
                .build();

        if (CollectionUtils.isNotEmpty(history)) {
            List<FactoryMetricsTrendVO> trends = history.stream()
                    .map(FactoryMetricsAssembler::toTrendVO)
                    .collect(Collectors.toList());
            vo.setHistoryTrend(trends);
        } else {
            vo.setHistoryTrend(new ArrayList<>());
        }

        return vo;
    }

    /**
     * 实时计算当前班次的工厂级指标
     */
    private FactoryMetricsShiftDO calculateCurrentMetrics(String tenantId, String factoryId,
                                                          String shiftDate, String shiftCode,
                                                          ShiftTimeRangeVO shiftRange) {
        // 获取工厂下所有设备
        List<DeviceBaseInfoVO> devices = deviceBaseDataApi.getDevicesByFactory(tenantId, factoryId, null);
        if (CollectionUtils.isEmpty(devices)) {
            return createEmptyMetrics(tenantId, factoryId, shiftDate, shiftCode, shiftRange);
        }

        // 查询所有设备在当前班次的指标数据
        List<DeviceEfficiencyMetricDO> deviceMetrics = new ArrayList<>();
        for (DeviceBaseInfoVO device : devices) {
            try {
                // 查询设备的OEE指标（用于计算平均OEE）
                List<DeviceEfficiencyMetricDO> oeeMetrics = efficiencyMetricRepository.selectDeviceMetrics(
                        tenantId, factoryId, null, "oee", shiftDate, shiftCode,
                        "metricValue", "DESC", 1, 1).getList();
                
                if (CollectionUtils.isNotEmpty(oeeMetrics)) {
                    deviceMetrics.addAll(oeeMetrics);
                }
            } catch (Exception e) {
                log.warn("查询设备指标失败：deviceId={}, error={}", device.getId(), e.getMessage());
            }
        }

        if (CollectionUtils.isEmpty(deviceMetrics)) {
            return createEmptyMetrics(tenantId, factoryId, shiftDate, shiftCode, shiftRange);
        }

        // 从设备级指标汇总计算工厂级指标
        // 注意：这里需要从device_metrics_shift表的calculation_data中提取实际运行时间、加工时间、计划时间等
        // 简化实现：假设可以从设备级指标直接计算
        List<FactoryMetricsAssembler.DeviceMetricsForAggregation> aggregationData = 
                buildAggregationData(devices, deviceMetrics, shiftRange);

        // 计算平均OEE和平均设备利用率
        BigDecimal averageOee = FactoryMetricsAssembler.calculateAverageOee(aggregationData);
        BigDecimal averageUtilizationRate = FactoryMetricsAssembler.calculateAverageUtilizationRate(aggregationData);

        // 构建返回对象（对应 factory_metric_summary 表的字段）
        FactoryMetricsShiftDO result = new FactoryMetricsShiftDO();
        result.setTenantUuid(tenantId);
        result.setOrgFactoryId(factoryId);
        result.setShiftDate(LocalDate.parse(shiftDate, DateTimeFormatter.ISO_LOCAL_DATE));
        result.setShiftCode(shiftCode);
        result.setShiftStartTs(shiftRange.getStartTs());
        result.setShiftEndTs(shiftRange.getEndTs());
        result.setShiftDurationMs(shiftRange.getDurationMs());
        result.setDeviceCount(devices.size());
        result.setIsFinalized(false);  // 当前班次未结束，标记为未最终确定

        // 设置指标值
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("averageOee", averageOee);
        metrics.put("averageUtilizationRate", averageUtilizationRate);
        result.setMetrics(metrics);

        // 设置计算数据（用于审计）
        Map<String, Object> calculationData = new HashMap<>();
        calculationData.put("deviceCount", devices.size());
        calculationData.put("calculationTime", System.currentTimeMillis());
        calculationData.put("calculationSource", "REALTIME");
        result.setCalculationData(calculationData);

        return result;
    }

    /**
     * 构建聚合数据（用于计算工厂级指标）
     * 
     * <p>注意：这里需要从设备级指标的calculation_data中提取实际运行时间、加工时间、计划时间等
     * 当前简化实现，实际应该从device_metrics_shift表的calculation_data字段中提取
     */
    private List<FactoryMetricsAssembler.DeviceMetricsForAggregation> buildAggregationData(
            List<DeviceBaseInfoVO> devices,
            List<DeviceEfficiencyMetricDO> deviceMetrics,
            ShiftTimeRangeVO shiftRange) {
        
        List<FactoryMetricsAssembler.DeviceMetricsForAggregation> result = new ArrayList<>();
        
        // 创建设备指标Map（key为deviceId）
        Map<String, DeviceEfficiencyMetricDO> metricsMap = deviceMetrics.stream()
                .collect(Collectors.toMap(
                        DeviceEfficiencyMetricDO::getDeviceId,
                        m -> m,
                        (m1, m2) -> m1  // 如果有重复，保留第一个
                ));

        // 为每个设备构建聚合数据
        for (DeviceBaseInfoVO device : devices) {
            DeviceEfficiencyMetricDO metric = metricsMap.get(device.getId());
            if (metric == null) {
                continue;  // 跳过没有指标数据的设备
            }

            FactoryMetricsAssembler.DeviceMetricsForAggregation agg = 
                    new FactoryMetricsAssembler.DeviceMetricsForAggregation();
            
            // 设置OEE（从设备级指标获取）
            agg.setOee(metric.getMetricValue());

            // TODO: 从device_metrics_shift表的calculation_data中提取以下数据
            // 当前简化实现，使用班次时长作为近似值
            BigDecimal shiftDurationMs = BigDecimal.valueOf(shiftRange.getDurationMs());
            
            // 实际运行时间 = 班次时长 × 时间开动率（简化）
            // 这里应该从calculation_data中获取actualRunTime
            agg.setActualRunTime(shiftDurationMs);  // 简化：使用班次时长
            
            // 加工时间 = 实际运行时间 × 设备开动率（简化）
            // 这里应该从calculation_data中获取workingTime
            agg.setWorkingTime(shiftDurationMs.multiply(BigDecimal.valueOf(0.8)));  // 简化：假设80%为加工时间
            
            // 计划时间 = 班次时长（简化）
            // 这里应该从calculation_data中获取plannedTime
            agg.setPlannedTime(shiftDurationMs);

            result.add(agg);
        }

        return result;
    }

    /**
     * 创建空指标对象（当没有设备或数据时）
     */
    /**
     * 创建空指标对象（对应 factory_metric_summary 表的字段）
     */
    private FactoryMetricsShiftDO createEmptyMetrics(String tenantId, String factoryId,
                                                     String shiftDate, String shiftCode,
                                                     ShiftTimeRangeVO shiftRange) {
        FactoryMetricsShiftDO result = new FactoryMetricsShiftDO();
        result.setTenantUuid(tenantId);
        result.setOrgFactoryId(factoryId);
        result.setShiftDate(LocalDate.parse(shiftDate, DateTimeFormatter.ISO_LOCAL_DATE));
        result.setShiftCode(shiftCode);
        result.setShiftStartTs(shiftRange.getStartTs());
        result.setShiftEndTs(shiftRange.getEndTs());
        result.setShiftDurationMs(shiftRange.getDurationMs());
        result.setDeviceCount(0);
        result.setIsFinalized(false);

        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("averageOee", BigDecimal.ZERO);
        metrics.put("averageUtilizationRate", BigDecimal.ZERO);
        result.setMetrics(metrics);

        return result;
    }

    /**
     * 获取代表性设备ID（用于查询班次配置）
     */
    private String getRepresentativeDeviceId(String tenantId, String factoryId) {
        List<DeviceBaseInfoVO> devices = deviceBaseDataApi.getDevicesByFactory(tenantId, factoryId, null);
        if (CollectionUtils.isEmpty(devices)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), 
                    "工厂下没有设备，无法查询班次信息");
        }
        return devices.get(0).getId();
    }

    /**
     * 获取工厂名称
     */
    private String getFactoryName(String tenantId, String factoryId) {
        List<DeviceBaseInfoVO> devices = deviceBaseDataApi.getDevicesByFactory(tenantId, factoryId, null);
        if (CollectionUtils.isNotEmpty(devices)) {
            return devices.get(0).getFactoryName();
        }
        return null;
    }

    /**
     * 参数校验
     */
    private void validateParams(String tenantId, String factoryId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "未获取到租户信息");
        }
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息");
        }
    }
}

