package com.weili.iot_portal.service.factory.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.domain.ingestion.*;
import com.weili.iot_portal.service.cache.FactoryMetricsCacheService;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceMetricsService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工厂实时指标计算服务
 * 负责聚合设备实时指标到工厂级别
 */
@Slf4j
@Service
public class FactoryRealtimeMetricsService {

    private static final String DEVICE_STATUS_ACTIVE = "ACTIVE";
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final int DECIMAL_SCALE = 4;
    private static final long MAX_TIMESTAMP_DIFF_SECONDS = 600L; // 10分钟
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DeviceInfoRepository deviceInfoRepository;
    private final ICheckpointService<CheckpointData> checkpointService;
    private final IShiftCalculationService shiftCalculationService;
    private final IShiftConfigService shiftConfigService;
    private final IDeviceMetricsService deviceMetricsService;
    private final FactoryMetricsCacheService factoryMetricsCacheService;

    public FactoryRealtimeMetricsService(
            DeviceInfoRepository deviceInfoRepository,
            @Qualifier("factoryMetricsCheckpointService")
            ICheckpointService<CheckpointData> checkpointService,
            IShiftCalculationService shiftCalculationService,
            IShiftConfigService shiftConfigService,
            IDeviceMetricsService deviceMetricsService,
            FactoryMetricsCacheService factoryMetricsCacheService) {
        this.deviceInfoRepository = deviceInfoRepository;
        this.checkpointService = checkpointService;
        this.shiftCalculationService = shiftCalculationService;
        this.shiftConfigService = shiftConfigService;
        this.deviceMetricsService = deviceMetricsService;
        this.factoryMetricsCacheService = factoryMetricsCacheService;
    }

    /**
     * 处理所有工厂的实时指标计算
     */
    public BatchProcessResult processAllFactoriesRealtimeWithCheckpoint(
            long calculationTimeSeconds, int batchSize, long timeoutMillis) {
        List<DeviceInfoDO> allDevices = deviceInfoRepository.findActiveWithFactory();
        if (allDevices == null || allDevices.isEmpty()) {
            return BatchProcessResult.completed(0, 0, 0);
        }

        int success = 0, skip = 0, error = 0;

        // 过滤设备：只处理监控中、在用状态、且关联工厂的设备
        Map<Long, List<DeviceInfoDO>> byFactory = filterAndGroupDevicesByFactory(allDevices);
        int skippedCount = allDevices.size() - byFactory.values().stream().mapToInt(List::size).sum();
        if (skippedCount > 0) {
            skip += skippedCount;
        }

        for (Map.Entry<Long, List<DeviceInfoDO>> entry : byFactory.entrySet()) {
            Long factoryId = entry.getKey();
            List<DeviceInfoDO> devices = entry.getValue();
            try {
                BatchProcessResult r = processFactoryRealtime(
                        factoryId, devices, calculationTimeSeconds, batchSize, timeoutMillis);
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

    /**
     * 处理单个工厂的实时指标计算
     */
    private BatchProcessResult processFactoryRealtime(Long factoryId,
                                                      List<DeviceInfoDO> devices,
                                                      long calculationTimeSeconds,
                                                      int batchSize,
                                                      long timeoutMillis) {
        // 1. 检查点处理
        Set<Long> processedIds = checkpointService.getProcessedDeviceIds(factoryId, calculationTimeSeconds);
        List<DeviceInfoDO> remaining = devices.stream()
                .filter(d -> !processedIds.contains(d.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) {
            checkpointService.clearCheckpoint(factoryId, calculationTimeSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }

        // 2. 批量加载实时指标数据
        Map<Long, RealtimeMetricSnapshot> metricsMap = loadRealtimeMetricsData(factoryId, remaining);
        if (metricsMap.isEmpty()) {
            log.debug("工厂实时指标: 未找到任何设备的实时指标数据: factoryId={}, deviceCount={}", 
                    factoryId, remaining.size());
            checkpointService.clearCheckpoint(factoryId, calculationTimeSeconds);
            return BatchProcessResult.completed(0, remaining.size(), 0);
        }

        // 3. 批量处理并聚合指标
        AggregationResult result = aggregateMetricsInBatches(
                factoryId, remaining, metricsMap, calculationTimeSeconds,
                batchSize, timeoutMillis, processedIds);
        
        // 设置总设备数（用于计算数据完整性）
        result.totalDevices = devices.size();

        // 4. 持久化结果
        if (result.isCompleted()) {
            writeFactoryRealtimeMetrics(factoryId, result, calculationTimeSeconds);
            checkpointService.clearCheckpoint(factoryId, calculationTimeSeconds);
        } else {
            checkpointService.saveCheckpoint(factoryId, calculationTimeSeconds, 
                    new ArrayList<>(processedIds));
        }

        return result.toBatchProcessResult();
    }

    /**
     * 批量加载实时指标数据
     */
    private Map<Long, RealtimeMetricSnapshot> loadRealtimeMetricsData(
            Long factoryId, List<DeviceInfoDO> devices) {
        List<Long> deviceIds = devices.stream()
                .map(DeviceInfoDO::getId)
                .collect(Collectors.toList());
        return deviceMetricsService.batchGetDeviceRealtimeMetrics(factoryId, deviceIds);
    }

    /**
     * 批量处理并聚合指标
     */
    private AggregationResult aggregateMetricsInBatches(
            Long factoryId,
            List<DeviceInfoDO> remaining,
            Map<Long, RealtimeMetricSnapshot> metricsMap,
            long calculationTimeSeconds,
            int batchSize,
            long timeoutMillis,
            Set<Long> processedIds) {
        
        AggregationResult result = new AggregationResult();
        List<Long> newProcessed = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < remaining.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remaining.size());
            List<DeviceInfoDO> batch = remaining.subList(i, endIndex);

            for (DeviceInfoDO device : batch) {
                try {
                    RealtimeMetricSnapshot snap = metricsMap.get(device.getId());
                    if (snap == null) {
                        result.skip++;
                        continue;
                    }
                    
                    // 使用设备级指标计算时的时间戳来计算权重
                    long deviceCalcTimeSeconds = getValidCalculationTime(
                            snap.getUpdatedAtSec(), calculationTimeSeconds, factoryId, device.getId());
                    
                    long weight = calculatePlannedDurationSeconds(
                            factoryId, device.getId(), deviceCalcTimeSeconds);
                    if (weight <= 0) {
                        result.skip++;
                        continue;
                    }

                    // 聚合指标
                    result.addMetrics(snap, weight);
                    result.validDevices++;
                    result.success++;
                    processedIds.add(device.getId());
                    newProcessed.add(device.getId());
                } catch (Exception ex) {
                    result.error++;
                    log.error("工厂实时指标-处理设备失败: factoryId={}, deviceId={}", 
                            factoryId, device.getId(), ex);
                }
            }

            // 保存检查点
            if (!newProcessed.isEmpty()) {
                checkpointService.saveCheckpoint(factoryId, calculationTimeSeconds, 
                        new ArrayList<>(processedIds));
                newProcessed.clear();
            }

            // 超时检查
            if (System.currentTimeMillis() - start > timeoutMillis) {
                log.warn("工厂实时指标超时: factoryId={}, 已处理={}, 剩余={}", 
                        factoryId, processedIds.size(), remaining.size() - processedIds.size());
                result.completed = false;
                return result;
            }
        }

        result.completed = true;
        return result;
    }

    /**
     * 获取有效的计算时间戳
     * 如果设备级指标时间戳与当前时间差距过大，使用当前时间
     */
    private long getValidCalculationTime(long deviceUpdatedAtSec, long currentTimeSeconds, 
                                        Long factoryId, Long deviceId) {
        if (deviceUpdatedAtSec <= 0) {
            return currentTimeSeconds;
        }
        
        long timeDiffSeconds = Math.abs(currentTimeSeconds - deviceUpdatedAtSec);
        if (timeDiffSeconds > MAX_TIMESTAMP_DIFF_SECONDS) {
            log.warn("工厂实时指标: 设备级指标时间戳与当前时间差距过大，使用当前时间: " +
                    "factoryId={}, deviceId={}, deviceUpdatedAt={}, currentTime={}, diff={}秒",
                    factoryId, deviceId, deviceUpdatedAtSec, currentTimeSeconds, timeDiffSeconds);
            return currentTimeSeconds;
        }
        
        return deviceUpdatedAtSec;
    }

    /**
     * 计算权重（秒）
     * <p>
     * 工厂级实时指标权重计算：计算从班次日期第一个班次开始时间到当前时间的"已过日历时长"。
     * 这与设备级实时指标计算保持一致，使用已过时长作为权重。
     * </p>
     */
    private long calculatePlannedDurationSeconds(Long factoryId, Long deviceId, long calcTimeSeconds) {
        long calcTimeMs = secondsToMillis(calcTimeSeconds);
        
        // 1. 获取当前时间对应的班次日期（考虑跨天班次）
        LocalDate shiftDate = shiftCalculationService.getShiftDate(factoryId, deviceId, calcTimeMs);
        if (shiftDate == null) {
            log.debug("工厂实时指标权重计算: 无法计算班次日期，返回0: factoryId={}, deviceId={}", 
                    factoryId, deviceId);
            return 0;
        }
        
        // 2. 获取班次配置，找到第一个班次的开始时间
        com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO shiftConfig = 
                shiftConfigService.getCurrentConfiguration(factoryId, deviceId, calcTimeMs);
        if (shiftConfig == null || shiftConfig.getShifts() == null || shiftConfig.getShifts().isEmpty()) {
            log.debug("工厂实时指标权重计算: 无法获取班次配置，返回0: factoryId={}, deviceId={}", 
                    factoryId, deviceId);
            return 0;
        }
        
        // 3. 使用工具类计算已过日历时长
        com.weili.iot_portal.dal.dataobject.device.DeviceShiftDefinition firstShift = shiftConfig.getShifts().get(0);
        return com.weili.iot_portal.service.device.util.StateDurationUtils.calculateElapsedCalendarSeconds(
                shiftDate,
                firstShift.getStartTime(),
                firstShift.getEndTime(),
                firstShift.getCrossDay(),
                calcTimeMs
        );
    }

    /**
     * 写入工厂实时指标到缓存（通过缓存服务）
     */
    private void writeFactoryRealtimeMetrics(Long factoryId, AggregationResult result, long updatedAtSec) {
        BigDecimal oee = calculateWeightedAverage(result.sumOee, result.sumWeight);
        BigDecimal uptime = calculateWeightedAverage(result.sumUptime, result.sumWeight);
        BigDecimal perf = calculateWeightedAverage(result.sumPerf, result.sumWeight);
        BigDecimal avail = calculateWeightedAverage(result.sumAvail, result.sumWeight);
        BigDecimal fault = calculateWeightedAverage(result.sumFault, result.sumWeight);

        BigDecimal dataCompleteness = result.totalDevices == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(result.validDevices)
                .divide(BigDecimal.valueOf(result.totalDevices), DECIMAL_SCALE, RoundingMode.HALF_UP);

        FactoryRealtimeMetricSnapshot snapshot = new FactoryRealtimeMetricSnapshot(
                oee, uptime, perf, avail, fault,
                result.sumWeight, result.validDevices, result.totalDevices,
                dataCompleteness, updatedAtSec);

        factoryMetricsCacheService.saveFactoryRealtimeMetrics(factoryId, snapshot);
    }

    /**
     * 过滤并按工厂分组设备
     */
    private Map<Long, List<DeviceInfoDO>> filterAndGroupDevicesByFactory(List<DeviceInfoDO> allDevices) {
        Map<Long, List<DeviceInfoDO>> byFactory = allDevices.stream()
                .filter(this::isDeviceValidForProcessing)
                .collect(Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId));
        
        long filtered = byFactory.values().stream().mapToLong(List::size).sum();
        int skippedCount = allDevices.size() - (int) filtered;
        if (skippedCount > 0) {
            log.info("工厂实时指标: 总设备数={}, 符合条件设备数={}, 已跳过={} (未监控/非在用/未关联工厂)", 
                    allDevices.size(), filtered, skippedCount);
        }
        return byFactory;
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
     * 秒转毫秒
     */
    private long secondsToMillis(long seconds) {
        return seconds * MILLIS_PER_SECOND;
    }

    /**
     * 聚合结果封装类
     */
    private static class AggregationResult {
        BigDecimal sumOee = BigDecimal.ZERO;
        BigDecimal sumUptime = BigDecimal.ZERO;
        BigDecimal sumPerf = BigDecimal.ZERO;
        BigDecimal sumAvail = BigDecimal.ZERO;
        BigDecimal sumFault = BigDecimal.ZERO;
        long sumWeight = 0;
        int validDevices = 0;
        int totalDevices = 0;
        int success = 0;
        int skip = 0;
        int error = 0;
        boolean completed = true;

        void addMetrics(RealtimeMetricSnapshot snap, long weight) {
            sumWeight += weight;
            BigDecimal w = BigDecimal.valueOf(weight);
            sumOee = sumOee.add(snap.getOee().multiply(w));
            sumUptime = sumUptime.add(snap.getUptimeRate().multiply(w));
            sumPerf = sumPerf.add(snap.getPerformanceRate().multiply(w));
            sumAvail = sumAvail.add(snap.getAvailabilityRate().multiply(w));
            sumFault = sumFault.add(snap.getFaultRate().multiply(w));
        }

        boolean isCompleted() {
            return completed;
        }

        BatchProcessResult toBatchProcessResult() {
            return completed 
                    ? BatchProcessResult.completed(success, skip, error)
                    : BatchProcessResult.incomplete(success, skip, error);
        }
    }
}

