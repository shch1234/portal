package com.weili.iot_portal.service.factory.impl;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceMetricSummaryRepository;
import com.weili.iot_portal.dal.repository.effiency.FactoryMetricsRepository;
import com.weili.iot_portal.dal.repository.factory.FactoryMetricSummaryRepository;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceMetricsService;
import com.weili.iot_portal.service.factory.IFactoryMetricsService;
import com.weili.iot_portal.service.model.BatchProcessResult;
import com.weili.iot_portal.service.model.RealtimeMetricSnapshot;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.model.ShiftTimeRange;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工厂级指标计算/汇总服务（骨架实现）
 * TODO: 后续补充具体的加权计算逻辑
 */
@Slf4j
@Service
public class FactoryMetricsService implements IFactoryMetricsService {

    private final DeviceInfoRepository deviceInfoRepository;
    private final FactoryMetricsRepository factoryMetricsRepository;
    private final FactoryMetricSummaryRepository factoryMetricSummaryRepository;
    private final DeviceMetricSummaryRepository deviceMetricSummaryRepository;
    private final ICheckpointService<ICheckpointService.CheckpointData> factoryMetricsCheckpointService;
    private final ICheckpointService<ICheckpointService.CheckpointData> factoryMetricsSummaryCheckpointService;
    private final IShiftCalculationService shiftCalculationService;
    private final IDeviceMetricsService deviceMetricsService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${factory.metrics.ttl-seconds:600}")
    private long factoryTtlSeconds;

    public FactoryMetricsService(DeviceInfoRepository deviceInfoRepository,
                                 FactoryMetricsRepository factoryMetricsRepository,
                                 FactoryMetricSummaryRepository factoryMetricSummaryRepository,
                                 DeviceMetricSummaryRepository deviceMetricSummaryRepository,
                                 @Qualifier("factoryMetricsCheckpointService")
                                 ICheckpointService<ICheckpointService.CheckpointData> factoryMetricsCheckpointService,
                                 @Qualifier("factoryMetricsSummaryCheckpointService")
                                 ICheckpointService<ICheckpointService.CheckpointData> factoryMetricsSummaryCheckpointService,
                                 IShiftCalculationService shiftCalculationService,
                                 IDeviceMetricsService deviceMetricsService,
                                 StringRedisTemplate stringRedisTemplate) {
        this.deviceInfoRepository = deviceInfoRepository;
        this.factoryMetricsRepository = factoryMetricsRepository;
        this.factoryMetricSummaryRepository = factoryMetricSummaryRepository;
        this.deviceMetricSummaryRepository = deviceMetricSummaryRepository;
        this.factoryMetricsCheckpointService = factoryMetricsCheckpointService;
        this.factoryMetricsSummaryCheckpointService = factoryMetricsSummaryCheckpointService;
        this.shiftCalculationService = shiftCalculationService;
        this.deviceMetricsService = deviceMetricsService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public BatchProcessResult processAllFactoriesRealtimeWithCheckpoint(long calculationTimeSeconds, int batchSize, long timeoutMillis) {
        List<com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO> allDevices = deviceInfoRepository.findActiveWithFactory();
        if (allDevices == null || allDevices.isEmpty()) {
            return BatchProcessResult.completed(0, 0, 0);
        }

        int success = 0, skip = 0, error = 0;

        Map<Long, List<DeviceInfoDO>> byFactory = allDevices.stream()
                .filter(d -> (d.getOrgFactoryId() != null))
                .collect(Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId));

        long filtered = byFactory.values().stream().mapToLong(List::size).sum();
        if (allDevices.size() > filtered) {
            skip += (int) (allDevices.size() - filtered);
            log.warn("工厂实时指标: 有 {} 个设备未关联工厂，已跳过", allDevices.size() - filtered);
        }

        for (Map.Entry<Long, List<DeviceInfoDO>> entry : byFactory.entrySet()) {
            Long factoryId = entry.getKey();
            List<DeviceInfoDO> devices = entry.getValue();
            try {
                BatchProcessResult r = processFactoryRealtime(factoryId, devices, calculationTimeSeconds, batchSize, timeoutMillis);
                success += r.getSuccessCount();
                skip += r.getSkipCount();
                error += r.getErrorCount();
            } catch (Exception e) {
                error += devices.size();
                log.error("工厂实时指标处理失败: factoryId={}, deviceCount={}", factoryId, devices.size(), e);
            }
        }

        return BatchProcessResult.completed(success, skip, error);
    }

    @Override
    public BatchProcessResult processAllFactoriesShiftSummaryWithCheckpoint(long statisticsTimeSeconds, int batchSize, long timeoutMillis) {
        List<com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO> allDevices = deviceInfoRepository.findActiveWithFactory();
        if (allDevices == null || allDevices.isEmpty()) {
            return BatchProcessResult.completed(0, 0, 0);
        }

        int success = 0, skip = 0, error = 0;

        Map<Long, List<DeviceInfoDO>> byFactory = allDevices.stream()
                .filter(d -> d.getOrgFactoryId() != null)
                .collect(Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId));

        long filtered = byFactory.values().stream().mapToLong(List::size).sum();
        if (allDevices.size() > filtered) {
            skip += (int) (allDevices.size() - filtered);
            log.warn("工厂班次指标: 有 {} 个设备未关联工厂，已跳过", allDevices.size() - filtered);
        }

        for (Map.Entry<Long, List<DeviceInfoDO>> entry : byFactory.entrySet()) {
            Long factoryId = entry.getKey();
            List<DeviceInfoDO> devices = entry.getValue();
            try {
                BatchProcessResult r = processFactoryShiftSummary(factoryId, devices, statisticsTimeSeconds, batchSize, timeoutMillis);
                success += r.getSuccessCount();
                skip += r.getSkipCount();
                error += r.getErrorCount();
            } catch (Exception e) {
                error += devices.size();
                log.error("工厂班次指标处理失败: factoryId={}, deviceCount={}", factoryId, devices.size(), e);
            }
        }

        return BatchProcessResult.completed(success, skip, error);
    }

    private BatchProcessResult processFactoryRealtime(Long factoryId,
                                                      List<DeviceInfoDO> devices,
                                                      long calculationTimeSeconds,
                                                      int batchSize,
                                                      long timeoutMillis) {
        java.util.Set<Long> processedIds = factoryMetricsCheckpointService.getProcessedDeviceIds(factoryId, calculationTimeSeconds);
        List<DeviceInfoDO> remaining = devices.stream()
                .filter(d -> !processedIds.contains(d.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) {
            factoryMetricsCheckpointService.clearCheckpoint(factoryId, calculationTimeSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }

        BigDecimal sumOee = BigDecimal.ZERO;
        BigDecimal sumUptime = BigDecimal.ZERO;
        BigDecimal sumPerf = BigDecimal.ZERO;
        BigDecimal sumAvail = BigDecimal.ZERO;
        BigDecimal sumFault = BigDecimal.ZERO;
        long sumWeight = 0;
        int validDevices = 0;
        int totalDevices = devices.size();

        int success = 0, skip = 0, error = 0;
        List<Long> newProcessed = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < remaining.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remaining.size());
            List<DeviceInfoDO> batch = remaining.subList(i, endIndex);

            for (DeviceInfoDO device : batch) {
                try {
                    long weight = calculatePlannedDurationSeconds(factoryId, device.getId(), calculationTimeSeconds);
                    if (weight <= 0) {
                        skip++;
                        continue;
                    }

                    Optional<RealtimeMetricSnapshot> snapOpt =
                            deviceMetricsService.getDeviceRealtimeMetrics(factoryId, device.getId());
                    if (snapOpt.isEmpty()) {
                        skip++;
                        continue;
                    }
                    RealtimeMetricSnapshot snap = snapOpt.get();

                    sumWeight += weight;
                    BigDecimal w = BigDecimal.valueOf(weight);
                    sumOee = sumOee.add(snap.getOee().multiply(w));
                    sumUptime = sumUptime.add(snap.getUptimeRate().multiply(w));
                    sumPerf = sumPerf.add(snap.getPerformanceRate().multiply(w));
                    sumAvail = sumAvail.add(snap.getAvailabilityRate().multiply(w));
                    sumFault = sumFault.add(snap.getFaultRate().multiply(w));
                    validDevices++;
                    success++;
                    processedIds.add(device.getId());
                    newProcessed.add(device.getId());
                } catch (Exception ex) {
                    error++;
                    log.error("工厂实时指标-处理设备失败: factoryId={}, deviceId={}", factoryId, device.getId(), ex);
                }
            }

            if (!newProcessed.isEmpty()) {
                factoryMetricsCheckpointService.saveCheckpoint(factoryId, calculationTimeSeconds, new ArrayList<>(processedIds));
                newProcessed.clear();
            }

            if (System.currentTimeMillis() - start > timeoutMillis) {
                log.warn("工厂实时指标超时: factoryId={}, 已处理={}, 剩余={}", factoryId, processedIds.size(), remaining.size() - processedIds.size());
                return BatchProcessResult.incomplete(success, skip, error);
            }
        }

        factoryMetricsCheckpointService.clearCheckpoint(factoryId, calculationTimeSeconds);

        writeFactoryRealtimeMetrics(factoryId, sumWeight, sumOee, sumUptime, sumPerf, sumAvail, sumFault, validDevices, totalDevices, calculationTimeSeconds);

        return BatchProcessResult.completed(success, skip, error);
    }

    private BatchProcessResult processFactoryShiftSummary(Long factoryId,
                                                          List<DeviceInfoDO> devices,
                                                          long statisticsTimeSeconds,
                                                          int batchSize,
                                                          long timeoutMillis) {
        Set<Long> processedIds = factoryMetricsSummaryCheckpointService.getProcessedDeviceIds(factoryId, statisticsTimeSeconds);
        List<DeviceInfoDO> remaining = devices.stream()
                .filter(d -> !processedIds.contains(d.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) {
            factoryMetricsSummaryCheckpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }

        Map<String, ShiftAggregate> shiftAggregates = new HashMap<>();
        int success = 0, skip = 0, error = 0;
        List<Long> newProcessed = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < remaining.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remaining.size());
            List<DeviceInfoDO> batch = remaining.subList(i, endIndex);

            for (DeviceInfoDO device : batch) {
                try {
                    boolean aggregated = aggregateDeviceShiftMetrics(device, statisticsTimeSeconds, shiftAggregates);
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
                factoryMetricsSummaryCheckpointService.saveCheckpoint(factoryId, statisticsTimeSeconds, new ArrayList<>(processedIds));
                newProcessed.clear();
            }

            if (System.currentTimeMillis() - start > timeoutMillis) {
                log.warn("工厂班次指标超时: factoryId={}, 已处理={}, 剩余={}", factoryId, processedIds.size(), remaining.size() - processedIds.size());
                return BatchProcessResult.incomplete(success, skip, error);
            }
        }

        factoryMetricsSummaryCheckpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
        persistFactoryShiftSummary(factoryId, shiftAggregates, devices.size());
        return BatchProcessResult.completed(success, skip, error);
    }

    private boolean aggregateDeviceShiftMetrics(
            DeviceInfoDO device,
            long statPointSeconds,
            Map<String, ShiftAggregate> aggregates) {
        List<DeviceMetricSummaryDO> summaries = deviceMetricSummaryRepository.selectFinalizedUpTo(device.getId(), statPointSeconds);
        if (summaries == null || summaries.isEmpty()) {
            return false;
        }

        boolean processed = false;
        for (DeviceMetricSummaryDO summary : summaries) {
            if (summary.getShiftStartTs() == null || summary.getShiftEndTs() == null) {
                continue;
            }
            long weight = summary.getShiftEndTs() - summary.getShiftStartTs();
            if (weight <= 0) {
                continue;
            }

            BigDecimal oee = metricValue(summary, "oee");
            BigDecimal uptimeRate = metricValue(summary, "uptimeRate");
            BigDecimal performanceRate = metricValue(summary, "performanceRate");
            BigDecimal availabilityRate = metricValue(summary, "availabilityRate");
            BigDecimal faultRate = metricValue(summary, "faultRate");
            if (oee == null || uptimeRate == null || performanceRate == null || availabilityRate == null || faultRate == null) {
                continue;
            }

            String shiftCode = summary.getShiftCode() == null ? null : summary.getShiftCode().toString();
            String key = buildShiftKey(summary.getShiftDate(), shiftCode);
            ShiftAggregate agg = aggregates.computeIfAbsent(key,
                    k -> new ShiftAggregate(summary.getShiftDate(), shiftCode, summary.getShiftStartTs(), summary.getShiftEndTs()));

            BigDecimal w = BigDecimal.valueOf(weight);
            agg.sumWeight += weight;
            agg.sumOee = agg.sumOee.add(oee.multiply(w));
            agg.sumUptime = agg.sumUptime.add(uptimeRate.multiply(w));
            agg.sumPerformance = agg.sumPerformance.add(performanceRate.multiply(w));
            agg.sumAvailability = agg.sumAvailability.add(availabilityRate.multiply(w));
            agg.sumFault = agg.sumFault.add(faultRate.multiply(w));
            agg.validDevices++;

            if (agg.shiftStartTs == null || summary.getShiftStartTs() < agg.shiftStartTs) {
                agg.shiftStartTs = summary.getShiftStartTs();
            }
            if (agg.shiftEndTs == null || summary.getShiftEndTs() > agg.shiftEndTs) {
                agg.shiftEndTs = summary.getShiftEndTs();
            }
            processed = true;
        }

        return processed;
    }

    private void persistFactoryShiftSummary(Long factoryId,
                                            Map<String, ShiftAggregate> aggregates,
                                            int totalDevices) {
        if (aggregates.isEmpty()) {
            return;
        }
        long nowSec = System.currentTimeMillis() / 1000;
        for (ShiftAggregate agg : aggregates.values()) {
            BigDecimal weight = BigDecimal.valueOf(agg.sumWeight == 0 ? 1 : agg.sumWeight);
            BigDecimal avgOee = agg.sumWeight == 0 ? BigDecimal.ZERO : agg.sumOee.divide(weight, 4, RoundingMode.HALF_UP);
            BigDecimal avgAvailability = agg.sumWeight == 0 ? BigDecimal.ZERO : agg.sumAvailability.divide(weight, 4, RoundingMode.HALF_UP);
            BigDecimal avgPerformance = agg.sumWeight == 0 ? BigDecimal.ZERO : agg.sumPerformance.divide(weight, 4, RoundingMode.HALF_UP);
            BigDecimal avgUtilization = agg.sumWeight == 0 ? BigDecimal.ZERO : agg.sumUptime.divide(weight, 4, RoundingMode.HALF_UP);
            BigDecimal avgFault = agg.sumWeight == 0 ? BigDecimal.ZERO : agg.sumFault.divide(weight, 4, RoundingMode.HALF_UP);

            BigDecimal dataCompleteness = totalDevices == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(agg.validDevices)
                    .divide(BigDecimal.valueOf(totalDevices), 4, RoundingMode.HALF_UP);

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("averageOee", avgOee);
            metrics.put("averageAvailability", avgAvailability);
            metrics.put("averagePerformance", avgPerformance);
            metrics.put("averageUtilizationRate", avgUtilization);
            metrics.put("averageFaultRate", avgFault);

            Map<String, Object> calcData = new HashMap<>();
            calcData.put("sumWeight", agg.sumWeight);
            calcData.put("validDevices", agg.validDevices);
            calcData.put("totalDevices", totalDevices);

            FactoryMetricSummaryDO existing = factoryMetricSummaryRepository.findByShift(factoryId, agg.shiftDate, agg.shiftCode);
            if (existing == null) {
                FactoryMetricSummaryDO record = new FactoryMetricSummaryDO();
                record.setOrgFactoryId(factoryId);
                record.setShiftDate(agg.shiftDate);
                record.setShiftCode(agg.shiftCode);
                record.setShiftStartTs(agg.shiftStartTs);
                record.setShiftEndTs(agg.shiftEndTs);
                record.setAverageOee(avgOee);
                record.setAverageAvailability(avgAvailability);
                record.setAveragePerformance(avgPerformance);
                record.setAverageQuality(null);
                record.setAverageUtilizationRate(avgUtilization);
                record.setAverageWorkingHours(null);
                record.setTotalProductionCount(0);
                record.setTotalQualifiedCount(0);
                record.setTotalPlannedDowntimeS(0);
                record.setTotalUnplannedDowntimeS(0);
                record.setMetrics(metrics);
                record.setCalculationData(calcData);
                record.setDeviceCount(agg.validDevices);
                record.setIsFinalized(Boolean.TRUE);
                record.setCalculationStatus("CALCULATED");
                record.setCalculatedTime(nowSec);
                record.setCalculationSource("SCHEDULED");
                record.setDataCompleteness(dataCompleteness);
                record.setRecalculatedAt(null);
                record.setRecalculationReason(null);
                record.setRecalculationCount(null);
                factoryMetricSummaryRepository.insert(record);
            } else {
                existing.setShiftStartTs(agg.shiftStartTs);
                existing.setShiftEndTs(agg.shiftEndTs);
                existing.setAverageOee(avgOee);
                existing.setAverageAvailability(avgAvailability);
                existing.setAveragePerformance(avgPerformance);
                existing.setAverageQuality(null);
                existing.setAverageUtilizationRate(avgUtilization);
                existing.setAverageWorkingHours(null);
                existing.setTotalProductionCount(0);
                existing.setTotalQualifiedCount(0);
                existing.setTotalPlannedDowntimeS(0);
                existing.setTotalUnplannedDowntimeS(0);
                existing.setMetrics(metrics);
                existing.setCalculationData(calcData);
                existing.setDeviceCount(agg.validDevices);
                existing.setIsFinalized(Boolean.TRUE);
                existing.setCalculationStatus("CALCULATED");
                existing.setCalculatedTime(nowSec);
                existing.setCalculationSource("SCHEDULED");
                existing.setDataCompleteness(dataCompleteness);
                existing.setRecalculatedAt(null);
                existing.setRecalculationReason(null);
                existing.setRecalculationCount(null);
                factoryMetricSummaryRepository.update(existing);
            }
        }
    }

    private BigDecimal metricValue(DeviceMetricSummaryDO summary, String key) {
        Map<String, Object> metrics = summary.getMetrics();
        if (metrics == null) {
            return null;
        }
        Object v = metrics.get(key);
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        if (v instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String buildShiftKey(java.time.LocalDate shiftDate, String shiftCode) {
        return (shiftDate == null ? "null" : shiftDate.toString()) + "#" + StringUtils.defaultIfBlank(shiftCode, "null");
    }

    private long calculatePlannedDurationSeconds(Long factoryId, Long deviceId, long calcTimeSeconds) {
        long calcTimeMs = calcTimeSeconds * 1000;
        ShiftTimeRange shift = shiftCalculationService.calculateShiftRange(factoryId, deviceId, calcTimeMs);
        if (shift == null || shift.getStartTs() == null) {
            return 0;
        }
        long startSec = shift.getStartTs() / 1000;
        long endSec = (shift.getEndTs() != null ? shift.getEndTs() : calcTimeMs) / 1000;
        return Math.max(0, endSec - startSec);
    }

    private void writeFactoryRealtimeMetrics(Long factoryId,
                                             long sumWeight,
                                             BigDecimal sumOee,
                                             BigDecimal sumUptime,
                                             BigDecimal sumPerf,
                                             BigDecimal sumAvail,
                                             BigDecimal sumFault,
                                             int validDevices,
                                             int totalDevices,
                                             long updatedAtSec) {
        BigDecimal weight = BigDecimal.valueOf(sumWeight == 0 ? 1 : sumWeight);
        BigDecimal oee = sumWeight == 0 ? BigDecimal.ZERO : sumOee.divide(weight, 4, RoundingMode.HALF_UP);
        BigDecimal uptime = sumWeight == 0 ? BigDecimal.ZERO : sumUptime.divide(weight, 4, RoundingMode.HALF_UP);
        BigDecimal perf = sumWeight == 0 ? BigDecimal.ZERO : sumPerf.divide(weight, 4, RoundingMode.HALF_UP);
        BigDecimal avail = sumWeight == 0 ? BigDecimal.ZERO : sumAvail.divide(weight, 4, RoundingMode.HALF_UP);
        BigDecimal fault = sumWeight == 0 ? BigDecimal.ZERO : sumFault.divide(weight, 4, RoundingMode.HALF_UP);

        BigDecimal dataCompleteness = totalDevices == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(validDevices)
                .divide(BigDecimal.valueOf(totalDevices), 4, RoundingMode.HALF_UP);

        String key = String.format(RedisConstant.RT_FACTORY_METRIC, factoryId == null ? "none" : factoryId);
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("metric.oee", oee.toPlainString());
        payload.put("metric.uptimeRate", uptime.toPlainString());
        payload.put("metric.performanceRate", perf.toPlainString());
        payload.put("metric.availabilityRate", avail.toPlainString());
        payload.put("metric.faultRate", fault.toPlainString());
        payload.put("meta.sumWeight", String.valueOf(sumWeight));
        payload.put("meta.validDevices", String.valueOf(validDevices));
        payload.put("meta.totalDevices", String.valueOf(totalDevices));
        payload.put("meta.dataCompleteness", dataCompleteness.toPlainString());
        payload.put("updatedAt", String.valueOf(updatedAtSec));

        stringRedisTemplate.opsForHash().putAll(key, payload);
        stringRedisTemplate.expire(key, Duration.ofSeconds(factoryTtlSeconds));
    }

    private static class ShiftAggregate {
        private final java.time.LocalDate shiftDate;
        private final String shiftCode;
        private Long shiftStartTs;
        private Long shiftEndTs;
        private BigDecimal sumOee = BigDecimal.ZERO;
        private BigDecimal sumUptime = BigDecimal.ZERO;
        private BigDecimal sumPerformance = BigDecimal.ZERO;
        private BigDecimal sumAvailability = BigDecimal.ZERO;
        private BigDecimal sumFault = BigDecimal.ZERO;
        private long sumWeight = 0;
        private int validDevices = 0;

        ShiftAggregate(java.time.LocalDate shiftDate, String shiftCode, Long shiftStartTs, Long shiftEndTs) {
            this.shiftDate = shiftDate;
            this.shiftCode = shiftCode;
            this.shiftStartTs = shiftStartTs;
            this.shiftEndTs = shiftEndTs;
        }
    }
}

