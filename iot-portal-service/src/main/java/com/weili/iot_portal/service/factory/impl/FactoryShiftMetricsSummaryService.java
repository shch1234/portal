package com.weili.iot_portal.service.factory.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceMetricSummaryRepository;
import com.weili.iot_portal.dal.repository.factory.FactoryMetricSummaryRepository;
import com.weili.iot_portal.domain.factory.ShiftAggregate;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.domain.ingestion.CheckpointData;
import com.weili.iot_portal.service.device.ICheckpointService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工厂班次指标汇总服务
 * 负责聚合设备班次指标汇总到工厂级别
 */
@Slf4j
@Service
public class FactoryShiftMetricsSummaryService {

    private static final String DEVICE_STATUS_ACTIVE = "ACTIVE";
    private static final BigDecimal PERCENTAGE_DIVISOR = BigDecimal.valueOf(100);
    private static final int DECIMAL_SCALE = 4;
    private static final String CALC_STATUS_CALCULATED = "CALCULATED";
    private static final String CALC_SOURCE_SCHEDULED = "SCHEDULED";
    
    // 时间转换常量
    private static final long MILLIS_PER_SECOND = 1000L;

    // 指标键名常量
    private static final String METRIC_KEY_OEE = "oee";
    private static final String METRIC_KEY_UPTIME_RATE = "uptimeRate";
    private static final String METRIC_KEY_UTILIZATION_RATE = "utilizationRate";
    private static final String METRIC_KEY_PERFORMANCE_RATE = "performanceRate";
    private static final String METRIC_KEY_PERFORMANCE = "performance";
    private static final String METRIC_KEY_AVAILABILITY_RATE = "availabilityRate";
    private static final String METRIC_KEY_AVAILABILITY = "availability";
    private static final String METRIC_KEY_FAULT_RATE = "faultRate";

    private final DeviceInfoRepository deviceInfoRepository;
    private final FactoryMetricSummaryRepository factoryMetricSummaryRepository;
    private final DeviceMetricSummaryRepository deviceMetricSummaryRepository;
    private final ICheckpointService<CheckpointData> checkpointService;

    public FactoryShiftMetricsSummaryService(
            DeviceInfoRepository deviceInfoRepository,
            FactoryMetricSummaryRepository factoryMetricSummaryRepository,
            DeviceMetricSummaryRepository deviceMetricSummaryRepository,
            @Qualifier("factoryMetricsSummaryCheckpointService")
            ICheckpointService<CheckpointData> checkpointService) {
        this.deviceInfoRepository = deviceInfoRepository;
        this.factoryMetricSummaryRepository = factoryMetricSummaryRepository;
        this.deviceMetricSummaryRepository = deviceMetricSummaryRepository;
        this.checkpointService = checkpointService;
    }

    /**
     * 处理所有工厂的班次指标汇总
     */
    public BatchProcessResult processAllFactoriesShiftSummaryWithCheckpoint(
            long statisticsTimeSeconds, int batchSize, long timeoutMillis, 
            int lookbackDays, int dataReadyDelayHours) {
        // 计算时间范围
        long startTsSeconds = statisticsTimeSeconds - (lookbackDays * 24L * 3600L);
        long dataReadyCutoffSeconds = statisticsTimeSeconds - (dataReadyDelayHours * 3600L);
        
        log.info("工厂班次指标汇总: 处理时间范围 {} 天，数据就绪延迟 {} 小时，开始时间戳(秒)={}, 结束时间戳(秒)={}, 数据就绪截止时间(秒)={}", 
                lookbackDays, dataReadyDelayHours, startTsSeconds, statisticsTimeSeconds, dataReadyCutoffSeconds);
        
        // 优化：只查询有设备指标汇总数据的设备ID（避免查询所有设备）
        // 注意：Repository方法期望毫秒，需要转换
        long startTsMillis = startTsSeconds * MILLIS_PER_SECOND;
        long dataReadyCutoffMillis = dataReadyCutoffSeconds * MILLIS_PER_SECOND;
        List<Long> deviceIdsWithData = deviceMetricSummaryRepository.findDistinctDeviceIdsWithFinalizedSummaries(
                startTsMillis, dataReadyCutoffMillis);
        if (deviceIdsWithData == null || deviceIdsWithData.isEmpty()) {
            log.info("工厂班次指标汇总: 未发现有待处理的设备指标汇总数据");
            return BatchProcessResult.completed(0, 0, 0);
        }
        
        log.info("工厂班次指标汇总: 发现 {} 台设备有待处理的指标汇总数据", deviceIdsWithData.size());
        
        // 批量查询设备信息，并按工厂分组
        Map<Long, List<DeviceInfoDO>> devicesByFactory = loadAndGroupDevicesByFactory(deviceIdsWithData);
        
        if (devicesByFactory.isEmpty()) {
            log.info("工厂班次指标汇总: 没有符合条件的设备需要处理");
            return BatchProcessResult.completed(0, deviceIdsWithData.size(), 0);
        }

        int success = 0, skip = 0, error = 0;
        int notFoundCount = deviceIdsWithData.size() - devicesByFactory.values().stream()
                .mapToInt(List::size).sum();

        for (Map.Entry<Long, List<DeviceInfoDO>> entry : devicesByFactory.entrySet()) {
            Long factoryId = entry.getKey();
            List<DeviceInfoDO> factoryDevices = entry.getValue();
            try {
                BatchProcessResult r = processFactoryShiftSummary(
                        factoryId, factoryDevices, statisticsTimeSeconds, batchSize, timeoutMillis, 
                        lookbackDays, dataReadyDelayHours);
                success += r.getSuccessCount();
                skip += r.getSkipCount();
                error += r.getErrorCount();
            } catch (Exception e) {
                error += factoryDevices.size();
                log.error("工厂班次指标处理失败: factoryId={}, deviceCount={}", factoryId, factoryDevices.size(), e);
            }
        }

        return BatchProcessResult.completed(success, skip + notFoundCount, error);
    }

    /**
     * 加载设备信息并按工厂分组
     */
    private Map<Long, List<DeviceInfoDO>> loadAndGroupDevicesByFactory(List<Long> deviceIds) {
        List<DeviceInfoDO> devices = deviceInfoRepository.selectByIds(deviceIds);
        Map<Long, DeviceInfoDO> deviceMap = devices.stream()
                .collect(Collectors.toMap(DeviceInfoDO::getId, d -> d));
        
        Map<Long, List<DeviceInfoDO>> devicesByFactory = new HashMap<>();
        int notFoundCount = 0;
        for (Long deviceId : deviceIds) {
            DeviceInfoDO device = deviceMap.get(deviceId);
            if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
                notFoundCount++;
                continue;
            }
            if (!isDeviceValidForProcessing(device)) {
                notFoundCount++;
                continue;
            }
            devicesByFactory.computeIfAbsent(device.getOrgFactoryId(), k -> new ArrayList<>()).add(device);
        }
        
        if (notFoundCount > 0) {
            log.warn("工厂班次指标汇总: 有 {} 个设备ID在 device_info 中未找到或已删除或不符合条件", notFoundCount);
        }
        
        return devicesByFactory;
    }

    /**
     * 处理单个工厂的班次指标汇总
     */
    private BatchProcessResult processFactoryShiftSummary(Long factoryId,
                                                          List<DeviceInfoDO> devices,
                                                          long statisticsTimeSeconds,
                                                          int batchSize,
                                                          long timeoutMillis,
                                                          int lookbackDays,
                                                          int dataReadyDelayHours) {
        // 1. 检查点处理
        Set<Long> processedIds = checkpointService.getProcessedDeviceIds(factoryId, statisticsTimeSeconds);
        List<DeviceInfoDO> remaining = devices.stream()
                .filter(d -> !processedIds.contains(d.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) {
            checkpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }
        
        if (!processedIds.isEmpty()) {
            log.debug("工厂班次指标汇总: 从检查点恢复: factoryId={}, 已处理={}, 剩余={}", 
                    factoryId, processedIds.size(), remaining.size());
        }

        // 2. 计算时间范围
        long startTsSeconds = statisticsTimeSeconds - (lookbackDays * 24L * 3600L);
        long dataReadyCutoffSeconds = statisticsTimeSeconds - (dataReadyDelayHours * 3600L);
        
        // 3. 批量加载数据
        Set<String> processedShifts = loadProcessedShifts(factoryId, startTsSeconds, dataReadyCutoffSeconds);
        Map<Long, List<DeviceMetricSummaryDO>> summariesByDevice = loadDeviceMetricSummaries(
                remaining, startTsSeconds, dataReadyCutoffSeconds);
        
        // 4. 批量处理并聚合
        Map<String, ShiftAggregate> shiftAggregates = new HashMap<>();
        BatchProcessResult result = aggregateInBatches(
                factoryId, remaining, summariesByDevice, shiftAggregates, processedShifts,
                batchSize, timeoutMillis, processedIds, statisticsTimeSeconds);

        // 5. 持久化结果
        if (result.getSuccessCount() > 0 || result.getSkipCount() > 0) {
            if (!shiftAggregates.isEmpty()) {
                persistFactoryShiftSummary(factoryId, shiftAggregates, devices.size());
            }
        }

        return result;
    }

    /**
     * 批量加载设备指标汇总数据
     */
    private Map<Long, List<DeviceMetricSummaryDO>> loadDeviceMetricSummaries(
            List<DeviceInfoDO> devices, long startTsSeconds, long dataReadyCutoffSeconds) {
        List<Long> deviceIds = devices.stream()
                .map(DeviceInfoDO::getId)
                .collect(Collectors.toList());
        // 注意：Repository方法期望毫秒，需要转换
        long startTsMillis = startTsSeconds * MILLIS_PER_SECOND;
        long dataReadyCutoffMillis = dataReadyCutoffSeconds * MILLIS_PER_SECOND;
        return deviceMetricSummaryRepository.selectFinalizedInRangeBatch(
                deviceIds, startTsMillis, dataReadyCutoffMillis);
    }

    /**
     * 批量处理并聚合指标
     */
    private BatchProcessResult aggregateInBatches(
            Long factoryId,
            List<DeviceInfoDO> remaining,
            Map<Long, List<DeviceMetricSummaryDO>> summariesByDevice,
            Map<String, ShiftAggregate> shiftAggregates,
            Set<String> processedShifts,
            int batchSize,
            long timeoutMillis,
            Set<Long> processedIds,
            long statisticsTimeSeconds) {
        
        int success = 0, skip = 0, error = 0;
        List<Long> newProcessed = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < remaining.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remaining.size());
            List<DeviceInfoDO> batch = remaining.subList(i, endIndex);

            for (DeviceInfoDO device : batch) {
                try {
                    List<DeviceMetricSummaryDO> summaries = summariesByDevice.getOrDefault(
                            device.getId(), Collections.emptyList());
                    boolean aggregated = aggregateDeviceShiftMetricsFromBatch(
                            device, summaries, shiftAggregates, processedShifts);
                    if (aggregated) {
                        success++;
                    } else {
                        skip++;
                    }
                    processedIds.add(device.getId());
                    newProcessed.add(device.getId());
                } catch (Exception ex) {
                    error++;
                    log.error("工厂班次指标-处理设备失败: factoryId={}, deviceId={}", factoryId, device.getId(), ex);
                }
            }

            if (!newProcessed.isEmpty()) {
                checkpointService.saveCheckpoint(factoryId, statisticsTimeSeconds, new ArrayList<>(processedIds));
                newProcessed.clear();
            }

            if (System.currentTimeMillis() - start > timeoutMillis) {
                log.warn("工厂班次指标超时: factoryId={}, 已处理={}, 剩余={}", 
                        factoryId, processedIds.size(), remaining.size() - processedIds.size());
                return BatchProcessResult.incomplete(success, skip, error);
            }
        }

        checkpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
        return BatchProcessResult.completed(success, skip, error);
    }

    /**
     * 预先加载已处理的班次集合
     */
    private Set<String> loadProcessedShifts(Long factoryId, long startTsSeconds, long endTsSeconds) {
        List<FactoryMetricSummaryDO> processed = factoryMetricSummaryRepository.selectFinalizedInRange(
                factoryId, startTsSeconds, endTsSeconds);
        
        return processed.stream()
                .map(r -> buildShiftKey(r.getShiftDate(), r.getShiftCode()))
                .collect(Collectors.toSet());
    }
    
    /**
     * 从批量查询结果中聚合设备班次指标到工厂班次指标
     */
    private boolean aggregateDeviceShiftMetricsFromBatch(
            DeviceInfoDO device,
            List<DeviceMetricSummaryDO> summaries,
            Map<String, ShiftAggregate> aggregates,
            Set<String> processedShifts) {
        if (summaries == null || summaries.isEmpty()) {
            return false;
        }
        
        Long factoryId = device.getOrgFactoryId();
        boolean processed = false;
        int skippedCount = 0;
        int alreadyProcessedCount = 0;
        
        for (DeviceMetricSummaryDO summary : summaries) {
            if (!validateSummaryRecord(device, summary)) {
                skippedCount++;
                continue;
            }
            
            String shiftKey = buildShiftKey(summary.getShiftDate(), 
                    summary.getShiftCode() == null ? null : summary.getShiftCode().toString());
            
            if (processedShifts.contains(shiftKey)) {
                alreadyProcessedCount++;
                log.debug("工厂班次指标汇总: 跳过已处理的班次: factoryId={}, shiftDate={}, shiftCode={}", 
                        factoryId, summary.getShiftDate(), summary.getShiftCode());
                continue;
            }
            
            MetricsExtractionResult metrics = extractMetricsFromSummary(summary);
            if (metrics == null) {
                skippedCount++;
                log.debug("工厂班次指标汇总: 跳过记录（指标数据缺失）: deviceId={}, shiftDate={}, shiftCode={}", 
                        device.getId(), summary.getShiftDate(), summary.getShiftCode());
                continue;
            }
            
            aggregateMetricsToShift(summary, metrics, aggregates);
            processed = true;
        }
        
        if (!processed && skippedCount > 0) {
            log.warn("工厂班次指标汇总: 设备有汇总数据但全部被跳过: deviceId={}, factoryId={}, 总记录数={}, 跳过数={}, 已处理数={}", 
                    device.getId(), device.getOrgFactoryId(), summaries.size(), skippedCount, alreadyProcessedCount);
        } else if (alreadyProcessedCount > 0) {
            log.debug("工厂班次指标汇总: 跳过已处理的班次: deviceId={}, factoryId={}, 已处理数={}", 
                    device.getId(), factoryId, alreadyProcessedCount);
        }
        
        return processed;
    }
    
    /**
     * 验证汇总记录是否有效
     */
    private boolean validateSummaryRecord(DeviceInfoDO device, DeviceMetricSummaryDO summary) {
        if (summary.getShiftStartTs() == null || summary.getShiftEndTs() == null) {
            log.debug("工厂班次指标汇总: 跳过记录（班次时间范围无效）: deviceId={}, shiftDate={}, shiftCode={}", 
                    device.getId(), summary.getShiftDate(), summary.getShiftCode());
            return false;
        }
        
        long weight = summary.getShiftEndTs() - summary.getShiftStartTs();
        if (weight <= 0) {
            log.debug("工厂班次指标汇总: 跳过记录（权重<=0）: deviceId={}, shiftDate={}, shiftCode={}, weight={}", 
                    device.getId(), summary.getShiftDate(), summary.getShiftCode(), weight);
            return false;
        }
        
        return true;
    }
    
    /**
     * 从汇总记录中提取指标值
     */
    private MetricsExtractionResult extractMetricsFromSummary(DeviceMetricSummaryDO summary) {
        BigDecimal oee = metricValue(summary, METRIC_KEY_OEE);
        BigDecimal uptimeRate = metricValue(summary, METRIC_KEY_UPTIME_RATE, METRIC_KEY_UTILIZATION_RATE);
        BigDecimal performanceRate = metricValue(summary, METRIC_KEY_PERFORMANCE_RATE, METRIC_KEY_PERFORMANCE);
        BigDecimal availabilityRate = metricValue(summary, METRIC_KEY_AVAILABILITY_RATE, METRIC_KEY_AVAILABILITY);
        BigDecimal faultRate = metricValue(summary, METRIC_KEY_FAULT_RATE);
        
        if (oee == null || uptimeRate == null || performanceRate == null 
                || availabilityRate == null || faultRate == null) {
            return null;
        }
        
        return new MetricsExtractionResult(oee, uptimeRate, performanceRate, availabilityRate, faultRate);
    }
    
    /**
     * 将指标聚合到班次
     */
    private void aggregateMetricsToShift(DeviceMetricSummaryDO summary, MetricsExtractionResult metrics, 
                                        Map<String, ShiftAggregate> aggregates) {
        String shiftCode = summary.getShiftCode() == null ? null : summary.getShiftCode().toString();
        String key = buildShiftKey(summary.getShiftDate(), shiftCode);
        ShiftAggregate agg = aggregates.computeIfAbsent(key,
                k -> new ShiftAggregate(summary.getShiftDate(), shiftCode, summary.getShiftStartTs(), summary.getShiftEndTs()));
        
        long weight = summary.getShiftEndTs() - summary.getShiftStartTs();
        BigDecimal w = BigDecimal.valueOf(weight);
        
        agg.sumWeight += weight;
        agg.sumOee = agg.sumOee.add(metrics.oee.multiply(w));
        agg.sumUptime = agg.sumUptime.add(metrics.uptimeRate.multiply(w));
        agg.sumPerformance = agg.sumPerformance.add(metrics.performanceRate.multiply(w));
        agg.sumAvailability = agg.sumAvailability.add(metrics.availabilityRate.multiply(w));
        agg.sumFault = agg.sumFault.add(metrics.faultRate.multiply(w));
        agg.validDevices++;
        
        // 更新班次时间范围
        if (agg.shiftStartTs == null || summary.getShiftStartTs() < agg.shiftStartTs) {
            agg.shiftStartTs = summary.getShiftStartTs();
        }
        if (agg.shiftEndTs == null || summary.getShiftEndTs() > agg.shiftEndTs) {
            agg.shiftEndTs = summary.getShiftEndTs();
        }
    }

    /**
     * 持久化工厂班次指标汇总
     */
    private void persistFactoryShiftSummary(Long factoryId,
                                            Map<String, ShiftAggregate> aggregates,
                                            int totalDevices) {
        if (aggregates.isEmpty()) {
            log.warn("工厂班次指标汇总: 无有效汇总数据，跳过持久化: factoryId={}, totalDevices={}", factoryId, totalDevices);
            return;
        }
        
        long nowSec = System.currentTimeMillis() / 1000;
        
        // 批量查询已存在的记录
        List<FactoryMetricSummaryDO> existingRecords = batchQueryExistingRecords(factoryId, aggregates);
        Map<String, FactoryMetricSummaryDO> existingMap = existingRecords.stream()
                .collect(Collectors.toMap(
                    r -> buildShiftKey(r.getShiftDate(), r.getShiftCode()),
                    r -> r,
                    (r1, r2) -> r1
                ));
        
        for (ShiftAggregate agg : aggregates.values()) {
            String shiftKey = buildShiftKey(agg.shiftDate, agg.shiftCode);
            FactoryMetricSummaryDO existing = existingMap.get(shiftKey);
            
            if (shouldSkipUpdate(existing, agg)) {
                log.debug("工厂班次指标汇总: 数据未变化，跳过更新: factoryId={}, shiftDate={}, shiftCode={}", 
                        factoryId, agg.shiftDate, agg.shiftCode);
                continue;
            }
            
            AverageMetrics avgMetrics = calculateAverageMetrics(agg, totalDevices);
            FactoryMetricSummaryDO record = buildFactoryMetricRecord(factoryId, agg, avgMetrics, totalDevices, nowSec);
            saveOrUpdateFactoryMetric(record, existing);
        }
    }
    
    /**
     * 批量查询已存在的工厂指标汇总记录
     */
    private List<FactoryMetricSummaryDO> batchQueryExistingRecords(
            Long factoryId, Map<String, ShiftAggregate> aggregates) {
        List<FactoryMetricSummaryDO> result = new ArrayList<>();
        for (ShiftAggregate agg : aggregates.values()) {
            FactoryMetricSummaryDO existing = factoryMetricSummaryRepository.findByShift(
                    factoryId, agg.shiftDate, agg.shiftCode);
            if (existing != null) {
                result.add(existing);
            }
        }
        return result;
    }
    
    /**
     * 检查是否需要跳过更新
     */
    private boolean shouldSkipUpdate(FactoryMetricSummaryDO existing, ShiftAggregate agg) {
        if (existing == null || !Boolean.TRUE.equals(existing.getIsFinalized())) {
            return false;
        }
        
        if (existing.getDeviceCount() == null || existing.getDeviceCount() != agg.validDevices) {
            return false;
        }
        
        Map<String, Object> calcData = existing.getCalculationData();
        if (calcData != null) {
            Object sumWeightObj = calcData.get("sumWeight");
            if (sumWeightObj != null) {
                long existingWeight = ((Number) sumWeightObj).longValue();
                if (existingWeight != agg.sumWeight) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 计算平均值指标
     */
    private AverageMetrics calculateAverageMetrics(ShiftAggregate agg, int totalDevices) {
        BigDecimal avgOee = normalizeRate(calculateWeightedAverage(agg.sumOee, agg.sumWeight));
        BigDecimal avgAvailability = normalizeRate(calculateWeightedAverage(agg.sumAvailability, agg.sumWeight));
        BigDecimal avgPerformance = normalizeRate(calculateWeightedAverage(agg.sumPerformance, agg.sumWeight));
        BigDecimal avgUtilization = normalizeRate(calculateWeightedAverage(agg.sumUptime, agg.sumWeight));
        BigDecimal avgFault = normalizeRate(calculateWeightedAverage(agg.sumFault, agg.sumWeight));
        
        BigDecimal dataCompleteness = totalDevices == 0
                ? BigDecimal.ZERO
                : normalizeRate(BigDecimal.valueOf(agg.validDevices)
                .divide(BigDecimal.valueOf(totalDevices), DECIMAL_SCALE, RoundingMode.HALF_UP));
        
        return new AverageMetrics(avgOee, avgAvailability, avgPerformance, avgUtilization, avgFault, dataCompleteness);
    }
    
    /**
     * 构建工厂指标记录
     */
    private FactoryMetricSummaryDO buildFactoryMetricRecord(Long factoryId, ShiftAggregate agg, 
                                                           AverageMetrics avgMetrics, int totalDevices, long nowSec) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("averageOee", avgMetrics.avgOee);
        metrics.put("averageAvailability", avgMetrics.avgAvailability);
        metrics.put("averagePerformance", avgMetrics.avgPerformance);
        metrics.put("averageUtilizationRate", avgMetrics.avgUtilization);
        metrics.put("averageFaultRate", avgMetrics.avgFault);
        
        Map<String, Object> calcData = new HashMap<>();
        calcData.put("sumWeight", agg.sumWeight);
        calcData.put("validDevices", agg.validDevices);
        calcData.put("totalDevices", totalDevices);
        
        FactoryMetricSummaryDO record = new FactoryMetricSummaryDO();
        record.setOrgFactoryId(factoryId);
        record.setShiftDate(agg.shiftDate);
        record.setShiftCode(agg.shiftCode);
        record.setShiftStartTs(agg.shiftStartTs);
        record.setShiftEndTs(agg.shiftEndTs);
        record.setAverageOee(avgMetrics.avgOee);
        record.setAverageAvailability(avgMetrics.avgAvailability);
        record.setAveragePerformance(avgMetrics.avgPerformance);
        record.setAverageQuality(null);
        record.setAverageUtilizationRate(avgMetrics.avgUtilization);
        record.setAverageWorkingHours(null);
        record.setTotalProductionCount(0);
        record.setTotalQualifiedCount(0);
        record.setTotalPlannedDowntimeS(0);
        record.setTotalUnplannedDowntimeS(0);
        record.setMetrics(metrics);
        record.setCalculationData(calcData);
        record.setDeviceCount(agg.validDevices);
        record.setIsFinalized(Boolean.TRUE);
        record.setCalculationStatus(CALC_STATUS_CALCULATED);
        record.setCalculatedTime(nowSec);
        record.setCalculationSource(CALC_SOURCE_SCHEDULED);
        record.setDataCompleteness(avgMetrics.dataCompleteness);
        record.setRecalculatedAt(null);
        record.setRecalculationReason(null);
        record.setRecalculationCount(null);
        
        return record;
    }
    
    /**
     * 保存或更新工厂指标记录
     */
    private void saveOrUpdateFactoryMetric(FactoryMetricSummaryDO record, FactoryMetricSummaryDO existing) {
        if (existing == null) {
            factoryMetricSummaryRepository.insert(record);
            log.info("工厂班次指标汇总: 插入新记录: factoryId={}, shiftDate={}, shiftCode={}, avgOee={}, validDevices={}", 
                    record.getOrgFactoryId(), record.getShiftDate(), record.getShiftCode(), 
                    record.getAverageOee(), record.getDeviceCount());
        } else {
            existing.setShiftStartTs(record.getShiftStartTs());
            existing.setShiftEndTs(record.getShiftEndTs());
            existing.setAverageOee(record.getAverageOee());
            existing.setAverageAvailability(record.getAverageAvailability());
            existing.setAveragePerformance(record.getAveragePerformance());
            existing.setAverageQuality(record.getAverageQuality());
            existing.setAverageUtilizationRate(record.getAverageUtilizationRate());
            existing.setAverageWorkingHours(record.getAverageWorkingHours());
            existing.setTotalProductionCount(record.getTotalProductionCount());
            existing.setTotalQualifiedCount(record.getTotalQualifiedCount());
            existing.setTotalPlannedDowntimeS(record.getTotalPlannedDowntimeS());
            existing.setTotalUnplannedDowntimeS(record.getTotalUnplannedDowntimeS());
            existing.setMetrics(record.getMetrics());
            existing.setCalculationData(record.getCalculationData());
            existing.setDeviceCount(record.getDeviceCount());
            existing.setIsFinalized(record.getIsFinalized());
            existing.setCalculationStatus(record.getCalculationStatus());
            existing.setCalculatedTime(record.getCalculatedTime());
            existing.setCalculationSource(record.getCalculationSource());
            existing.setDataCompleteness(record.getDataCompleteness());
            existing.setRecalculatedAt(record.getRecalculatedAt());
            existing.setRecalculationReason(record.getRecalculationReason());
            existing.setRecalculationCount(record.getRecalculationCount());
            factoryMetricSummaryRepository.update(existing);
            log.info("工厂班次指标汇总: 更新已存在记录: factoryId={}, shiftDate={}, shiftCode={}, avgOee={}, validDevices={}", 
                    existing.getOrgFactoryId(), existing.getShiftDate(), existing.getShiftCode(), 
                    existing.getAverageOee(), existing.getDeviceCount());
        }
    }

    /**
     * 从 metrics Map 中提取指标值，并转换为小数形式（0-1范围）
     */
    private BigDecimal metricValue(DeviceMetricSummaryDO summary, String key, String fallbackKey) {
        Map<String, Object> metrics = summary.getMetrics();
        if (metrics == null) {
            return null;
        }
        Object v = metrics.get(key);
        if (v == null && fallbackKey != null) {
            v = metrics.get(fallbackKey);
        }
        if (v == null) {
            return null;
        }
        
        BigDecimal value;
        if (v instanceof BigDecimal) {
            value = (BigDecimal) v;
        } else if (v instanceof Number) {
            value = BigDecimal.valueOf(((Number) v).doubleValue());
        } else if (v instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                value = new BigDecimal(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        } else {
            return null;
        }
        
        // 如果值大于1，认为是百分比形式，需要除以100转换为小数
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return value.divide(PERCENTAGE_DIVISOR, DECIMAL_SCALE, RoundingMode.HALF_UP);
        }
        
        return value;
    }
    
    /**
     * 从 metrics Map 中提取指标值（单键版本）
     */
    private BigDecimal metricValue(DeviceMetricSummaryDO summary, String key) {
        return metricValue(summary, key, null);
    }

    /**
     * 构建班次key
     */
    private String buildShiftKey(java.time.LocalDate shiftDate, String shiftCode) {
        return (shiftDate == null ? "null" : shiftDate.toString()) + "#" + StringUtils.defaultIfBlank(shiftCode, "null");
    }

    /**
     * 检查设备是否有效
     */
    private boolean isDeviceValidForProcessing(DeviceInfoDO device) {
        return Boolean.TRUE.equals(device.getIsMonitored())
                && DEVICE_STATUS_ACTIVE.equals(device.getDeviceStatus())
                && device.getOrgFactoryId() != null;
    }

    /**
     * 计算加权平均值
     */
    private BigDecimal calculateWeightedAverage(BigDecimal sum, long weight) {
        return weight == 0 ? BigDecimal.ZERO 
                : sum.divide(BigDecimal.valueOf(weight), DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * 规范化比率值，确保在 0-1 范围内，精度不超过4位小数（DECIMAL(5,4)）
     * <p>
     * 处理规则：
     * <ul>
     *   <li>如果值为 null，返回 BigDecimal.ZERO</li>
     *   <li>如果值 < 0，返回 BigDecimal.ZERO</li>
     *   <li>如果值 > 1，返回 BigDecimal.ONE（并记录警告日志）</li>
     *   <li>否则，保留4位小数并返回</li>
     * </ul>
     * </p>
     * 
     * @param rate 原始比率值
     * @return 规范化后的比率值（0-1之间，精度4位小数）
     */
    private BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        
        // 如果值小于0，返回0
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("[FactoryShiftMetricsSummaryService] 比率值为负数，已规范化为0: rate={}", rate);
            return BigDecimal.ZERO;
        }
        
        // 如果值大于1，返回1（并记录警告）
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            log.warn("[FactoryShiftMetricsSummaryService] 比率值大于1，已规范化为1: rate={}", rate);
            return BigDecimal.ONE;
        }
        
        // 保留4位小数（DECIMAL(5,4)）
        return rate.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 指标提取结果封装类
     */
    private static class MetricsExtractionResult {
        final BigDecimal oee;
        final BigDecimal uptimeRate;
        final BigDecimal performanceRate;
        final BigDecimal availabilityRate;
        final BigDecimal faultRate;
        
        MetricsExtractionResult(BigDecimal oee, BigDecimal uptimeRate, BigDecimal performanceRate, 
                               BigDecimal availabilityRate, BigDecimal faultRate) {
            this.oee = oee;
            this.uptimeRate = uptimeRate;
            this.performanceRate = performanceRate;
            this.availabilityRate = availabilityRate;
            this.faultRate = faultRate;
        }
    }
    
    /**
     * 平均值指标封装类
     */
    private static class AverageMetrics {
        final BigDecimal avgOee;
        final BigDecimal avgAvailability;
        final BigDecimal avgPerformance;
        final BigDecimal avgUtilization;
        final BigDecimal avgFault;
        final BigDecimal dataCompleteness;
        
        AverageMetrics(BigDecimal avgOee, BigDecimal avgAvailability, BigDecimal avgPerformance,
                      BigDecimal avgUtilization, BigDecimal avgFault, BigDecimal dataCompleteness) {
            this.avgOee = avgOee;
            this.avgAvailability = avgAvailability;
            this.avgPerformance = avgPerformance;
            this.avgUtilization = avgUtilization;
            this.avgFault = avgFault;
            this.dataCompleteness = dataCompleteness;
        }
    }
}

