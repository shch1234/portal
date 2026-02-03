package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.dal.dataobject.device.*;
import com.weili.iot_portal.dal.repository.device.*;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.domain.ingestion.CheckpointData;
import com.weili.iot_portal.domain.ingestion.DataCompletenessCheckResult;
import com.weili.iot_portal.domain.metrics.MetricCalculationContext;
import com.weili.iot_portal.domain.metrics.MetricCalculationResult;
import com.weili.iot_portal.domain.metrics.MetricCalculator;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceMetricsSummaryService;
import com.weili.iot_portal.service.device.util.StateDurationUtils;
import com.weili.iot_portal.service.device.util.StateDurationUtils.StateDurations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.weili.iot_portal.service.device.util.DeviceLogContext.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备班次指标汇总服务实现
 */
@Slf4j
@Service
public class DeviceMetricsSummaryService implements IDeviceMetricsSummaryService {

    private static final String PARAM_PLANNED_DOWNTIME = "PLANNED_DOWNTIME";
    private static final String PARAM_THEORETICAL_CYCLE = "THEORETICAL_CYCLE";
    private static final String CALC_SOURCE = "SCHEDULED";

    // 计算状态常量
    private static final String CALC_STATUS_CALCULATED = "CALCULATED";
    private static final String CALC_STATUS_INCOMPLETE_DATA = "INCOMPLETE_DATA"; // 数据不完整，待重算

    // 时间转换常量
    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * 理论节拍提取结果
     */
    private static class TheoreticalCycleResult {
        private final long value; // 理论节拍值（秒）
        private final boolean useDefaultValue; // 是否使用了默认值

        public TheoreticalCycleResult(long value, boolean useDefaultValue) {
            this.value = value;
            this.useDefaultValue = useDefaultValue;
        }

        public long getValue() {
            return value;
        }

        public boolean isUseDefaultValue() {
            return useDefaultValue;
        }
    }

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateSummaryRepository deviceStateSummaryRepository;
    private final DeviceParamConfigRepository deviceParamConfigRepository;
    private final DeviceMetricSummaryRepository deviceMetricSummaryRepository;
    private final DeviceProductionSummaryRepository deviceProductionSummaryRepository;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final ICheckpointService<CheckpointData> checkpointService;

    public DeviceMetricsSummaryService(DeviceInfoRepository deviceInfoRepository,
                                       DeviceStateSummaryRepository deviceStateSummaryRepository,
                                       DeviceParamConfigRepository deviceParamConfigRepository,
                                       DeviceMetricSummaryRepository deviceMetricSummaryRepository,
                                       DeviceProductionSummaryRepository deviceProductionSummaryRepository,
                                       DeviceProductionRecordRepository deviceProductionRecordRepository,
                                       @Qualifier("deviceMetricsSummaryCheckpointService")
                                       ICheckpointService<CheckpointData> checkpointService) {
        this.deviceInfoRepository = deviceInfoRepository;
        this.deviceStateSummaryRepository = deviceStateSummaryRepository;
        this.deviceParamConfigRepository = deviceParamConfigRepository;
        this.deviceMetricSummaryRepository = deviceMetricSummaryRepository;
        this.deviceProductionSummaryRepository = deviceProductionSummaryRepository;
        this.deviceProductionRecordRepository = deviceProductionRecordRepository;
        this.checkpointService = checkpointService;
    }

    @Override
    public BatchProcessResult processAllDevicesWithCheckpoint(long statPointMillis, int batchSize, long timeoutMillis) {
        // 使用默认的7天时间范围、2小时数据就绪延迟和1小时重算间隔
        return processAllDevicesWithCheckpoint(statPointMillis, batchSize, timeoutMillis, 7, 2, 1);
    }

    /**
     * 批量处理所有设备的班次指标汇总（带检查点机制和时间范围限制）
     *
     * @param statPointMillis     统计时间点（毫秒）
     * @param batchSize           每批处理设备数
     * @param timeoutMillis       超时时间（毫秒）
     * @param lookbackDays        处理时间范围（天），只处理当前时间往前推N天内的数据
     * @param dataReadyDelayHours 数据就绪延迟时间（小时），只处理班次结束时间在统计时间点之前至少N小时的班次
     * @param recalculationIntervalHours 数据不完整记录的重算间隔时间（小时），如果距离上次计算时间超过此间隔，即使状态汇总未更新，也会重新计算
     * @return 处理结果
     */
    @Override
    public BatchProcessResult processAllDevicesWithCheckpoint(long statPointMillis, int batchSize, long timeoutMillis, int lookbackDays, int dataReadyDelayHours, int recalculationIntervalHours) {
        // 计算时间范围：从当前时间往前推N天（毫秒）
        long startTsMillis = statPointMillis - (lookbackDays * 24L * 60 * 60 * 1000);

        log.info("指标汇总: 处理时间范围 {} 天，数据就绪延迟 {} 小时", lookbackDays, dataReadyDelayHours);

        // 从 device_state_summary 表中查询有已完成汇总记录的设备ID
        // 只处理有数据的设备，而不是所有设备
        List<Long> deviceIdsWithData = deviceStateSummaryRepository.findDistinctDeviceIdsWithFinalizedSummaries(startTsMillis, statPointMillis);
        if (deviceIdsWithData == null || deviceIdsWithData.isEmpty()) {
            log.debug("指标汇总: 未发现有待处理的设备状态汇总数据");
            return BatchProcessResult.completed(0, 0, 0);
        }

        log.info("指标汇总: 发现 {} 台设备有待处理的状态汇总数据", deviceIdsWithData.size());

        // 批量查询设备信息，并按工厂分组
        List<DeviceInfoDO> devices = deviceInfoRepository.selectByIds(deviceIdsWithData);
        Map<Long, DeviceInfoDO> deviceMap = devices.stream()
                .collect(Collectors.toMap(DeviceInfoDO::getId, d -> d));

        Map<Long, List<DeviceInfoDO>> devicesByFactory = new HashMap<>();
        int notFoundCount = 0;
        for (Long deviceId : deviceIdsWithData) {
            DeviceInfoDO device = deviceMap.get(deviceId);
            if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
                notFoundCount++;
                continue;
            }
            if (device.getOrgFactoryId() == null) {
                notFoundCount++;
                continue;
            }
            devicesByFactory.computeIfAbsent(device.getOrgFactoryId(), k -> new ArrayList<>()).add(device);
        }

        if (notFoundCount > 0) {
            log.warn("指标汇总: 有 {} 个设备ID在 device_info 中未找到或已删除或未关联工厂", notFoundCount);
        }

        if (devicesByFactory.isEmpty()) {
            log.debug("指标汇总: 没有符合条件的设备需要处理");
            return BatchProcessResult.completed(0, notFoundCount, 0);
        }

        int success = 0, skip = 0, error = 0;
        // 改进2：汇总数据完整性统计
        int totalShiftsProcessed = 0;
        int totalCompleteShifts = 0;
        int totalIncompleteShifts = 0;

        for (Map.Entry<Long, List<DeviceInfoDO>> entry : devicesByFactory.entrySet()) {
            Long factoryId = entry.getKey();
            List<DeviceInfoDO> factoryDevices = entry.getValue();
            try {
                ProcessFactoryResult factoryResult = processFactoryDevicesWithCheckpointAndStats(
                        factoryId, factoryDevices, statPointMillis, batchSize, timeoutMillis, dataReadyDelayHours, recalculationIntervalHours);
                success += factoryResult.getBatchResult().getSuccessCount();
                skip += factoryResult.getBatchResult().getSkipCount();
                error += factoryResult.getBatchResult().getErrorCount();
                // 汇总统计信息
                totalShiftsProcessed += factoryResult.getTotalShifts();
                totalCompleteShifts += factoryResult.getCompleteShifts();
                totalIncompleteShifts += factoryResult.getIncompleteShifts();
            } catch (Exception e) {
                error += factoryDevices.size();
                log.error("处理工厂失败: factoryId={}, deviceCount={}", factoryId, factoryDevices.size(), e);
            }
        }

        // 改进2：记录全局数据完整性统计（只在完整率过低时输出警告）
        if (totalShiftsProcessed > 0) {
            double completenessRate = (double) totalCompleteShifts / totalShiftsProcessed;
            // 如果完整率过低，记录警告
            if (completenessRate < 0.8 && totalShiftsProcessed > 10) {
                log.warn("指标汇总全局数据完整率过低: 完整率={}% ({}/{})，建议检查产量汇总任务", 
                        String.format("%.2f", completenessRate * 100), totalCompleteShifts, totalShiftsProcessed);
            }
        }

        return BatchProcessResult.completed(success, skip + notFoundCount, error);
    }
    
    /**
     * 处理工厂结果（包含统计信息）
     */
    private static class ProcessFactoryResult {
        private final BatchProcessResult batchResult;
        private final int totalShifts;
        private final int completeShifts;
        private final int incompleteShifts;
        
        public ProcessFactoryResult(BatchProcessResult batchResult, int totalShifts, int completeShifts, int incompleteShifts) {
            this.batchResult = batchResult;
            this.totalShifts = totalShifts;
            this.completeShifts = completeShifts;
            this.incompleteShifts = incompleteShifts;
        }
        
        public BatchProcessResult getBatchResult() {
            return batchResult;
        }
        
        public int getTotalShifts() {
            return totalShifts;
        }
        
        public int getCompleteShifts() {
            return completeShifts;
        }
        
        public int getIncompleteShifts() {
            return incompleteShifts;
        }
    }

    @Override
    public BatchProcessResult processFactoryDevicesWithCheckpoint(Long factoryId,
                                                                  List<DeviceInfoDO> devices,
                                                                  long statPointMillis,
                                                                  int batchSize,
                                                                  long timeoutMillis,
                                                                  int dataReadyDelayHours,
                                                                  int recalculationIntervalHours) {
        ProcessFactoryResult result = processFactoryDevicesWithCheckpointAndStats(
                factoryId, devices, statPointMillis, batchSize, timeoutMillis, dataReadyDelayHours, recalculationIntervalHours);
        return result.getBatchResult();
    }
    
    /**
     * 处理工厂设备并返回统计信息（内部方法）
     */
    private ProcessFactoryResult processFactoryDevicesWithCheckpointAndStats(Long factoryId,
                                                                  List<DeviceInfoDO> devices,
                                                                  long statPointMillis,
                                                                  int batchSize,
                                                                  long timeoutMillis,
                                                                  int dataReadyDelayHours,
                                                                  int recalculationIntervalHours) {
        // 加载检查点
        // 注意：检查点服务使用秒，需要转换
        long statPointSeconds = statPointMillis / 1000;
        Set<Long> processedIds = checkpointService.getProcessedDeviceIds(factoryId, statPointSeconds);
        List<DeviceInfoDO> remaining = devices.stream()
                .filter(d -> !processedIds.contains(d.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) {
            checkpointService.clearCheckpoint(factoryId, statPointSeconds);
            return new ProcessFactoryResult(BatchProcessResult.completed(0, 0, 0), 0, 0, 0);
        }

        if (!processedIds.isEmpty()) {
            // 降级为debug，减少日志输出
            log.debug("指标汇总从检查点恢复: factoryId={}, 已处理={}, 剩余={}", factoryId, processedIds.size(), remaining.size());
        }

        int success = 0, skip = 0, error = 0;
        // 改进2：统计数据完整性
        int totalShiftsProcessed = 0;
        int completeShifts = 0;
        int incompleteShifts = 0;
        List<Long> newProcessed = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < remaining.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remaining.size());
            List<DeviceInfoDO> batch = remaining.subList(i, endIndex);

            for (DeviceInfoDO device : batch) {
                try {
                    ProcessDeviceResult deviceResult = processDeviceWithStats(device, statPointMillis, dataReadyDelayHours, recalculationIntervalHours);
                    if (deviceResult.isProcessed()) {
                        success++;
                        // 统计数据完整性
                        totalShiftsProcessed += deviceResult.getTotalShifts();
                        completeShifts += deviceResult.getCompleteShifts();
                        incompleteShifts += deviceResult.getIncompleteShifts();
                    } else {
                        skip++;
                    }
                    // 无论成功还是跳过，都加入检查点（避免重复处理）
                    processedIds.add(device.getId());
                    newProcessed.add(device.getId());
                } catch (Exception e) {
                    error++;
                    log.error("指标汇总失败 deviceId={}", device.getId(), e);
                    // 改进：即使失败也加入检查点，避免无限重试
                    // 注意：如果是因为数据问题导致的失败，记录会被标记为待重算，下次会重新计算
                    processedIds.add(device.getId());
                    newProcessed.add(device.getId());
                }
            }

            // 保存检查点
            checkpointService.saveCheckpoint(factoryId, statPointSeconds, new ArrayList<>(processedIds));

            // 超时检查
            if (System.currentTimeMillis() - start > timeoutMillis) {
                log.warn("指标汇总超时: factoryId={}, processed={}, remaining={}",
                        factoryId, processedIds.size(), devices.size() - processedIds.size());
                return new ProcessFactoryResult(
                        BatchProcessResult.incomplete(success, skip, error),
                        totalShiftsProcessed, completeShifts, incompleteShifts);
            }
        }

        // 全部完成，清除检查点
        checkpointService.clearCheckpoint(factoryId, statPointSeconds);
        
        // 改进2：记录数据完整性统计（只在完整率过低时输出警告）
        if (totalShiftsProcessed > 0) {
            double completenessRate = (double) completeShifts / totalShiftsProcessed;
            // 如果完整率过低，记录警告
            if (completenessRate < 0.8 && totalShiftsProcessed > 10) {
                log.warn("指标汇总数据完整率过低: 工厂={}, 完整率={}% ({}/{})，建议检查产量汇总任务", 
                        factoryId, String.format("%.2f", completenessRate * 100), completeShifts, totalShiftsProcessed);
            } else {
                // 正常情况只输出debug日志
                log.debug("指标汇总数据完整性: 工厂={}, 总班次={}, 完整={}, 不完整={}, 完整率={}%", 
                        factoryId, totalShiftsProcessed, completeShifts, incompleteShifts, 
                        String.format("%.2f", completenessRate * 100));
            }
        }
        
        return new ProcessFactoryResult(
                BatchProcessResult.completed(success, skip, error),
                totalShiftsProcessed, completeShifts, incompleteShifts);
    }
    
    /**
     * 处理设备结果（包含统计信息）
     */
    private static class ProcessDeviceResult {
        private final boolean processed;
        private final int totalShifts;
        private final int completeShifts;
        private final int incompleteShifts;
        
        public ProcessDeviceResult(boolean processed, int totalShifts, int completeShifts, int incompleteShifts) {
            this.processed = processed;
            this.totalShifts = totalShifts;
            this.completeShifts = completeShifts;
            this.incompleteShifts = incompleteShifts;
        }
        
        public boolean isProcessed() {
            return processed;
        }
        
        public int getTotalShifts() {
            return totalShifts;
        }
        
        public int getCompleteShifts() {
            return completeShifts;
        }
        
        public int getIncompleteShifts() {
            return incompleteShifts;
        }
    }
    
    /**
     * 处理单台设备并返回统计信息
     */
    private ProcessDeviceResult processDeviceWithStats(DeviceInfoDO device, long statPointMillis, 
                                                       int dataReadyDelayHours, int recalculationIntervalHours) {
        // 设置设备编号到 MDC，使日志能够显示设备编号
        setDeviceCode(device);
        
        try {
            // 计算数据就绪时间点：统计时间点往前推 N 小时
            long dataReadyCutoffMillis = statPointMillis - (dataReadyDelayHours * 3600L * 1000L);

            // 查询设备在统计时间点前的所有状态汇总记录
            List<DeviceStateSummaryDO> summaries = deviceStateSummaryRepository.selectByRange(
                    device.getId(), null, statPointMillis);
            if (summaries == null || summaries.isEmpty()) {
                return new ProcessDeviceResult(false, 0, 0, 0);
            }

            // 过滤：只处理已结束且数据已就绪的班次
            List<DeviceStateSummaryDO> readySummaries = summaries.stream()
                    .filter(s -> {
                        if (s.getShiftEndTs() == null) {
                            return false;
                        }
                        boolean isEnded = s.getShiftEndTs() <= statPointMillis;
                        if (!isEnded) {
                            return false;
                        }
                        boolean isDataReady = s.getShiftEndTs() <= dataReadyCutoffMillis;
                        if (!isDataReady) {
                            return false;
                        }
                        return true;
                    })
                    .filter(s -> Boolean.TRUE.equals(s.getIsFinalized()))
                    .collect(java.util.stream.Collectors.toList());

            if (readySummaries.isEmpty()) {
                return new ProcessDeviceResult(false, 0, 0, 0);
            }

            // 性能优化：一次性加载设备参数配置
            List<DeviceParamConfigDO> deviceParams = deviceParamConfigRepository.selectCurrent(device.getId());
            long plannedDowntimeSeconds = extractPlannedDowntime(deviceParams);

            // 性能优化：批量查询产量汇总数据
            Map<String, DeviceProductionSummaryDO> productionSummaryMap = batchQueryProductionSummaries(
                    device.getId(), readySummaries);

            // 处理每个符合条件的班次
            int processed = 0;
            int incompleteDataCount = 0;
            for (DeviceStateSummaryDO summary : readySummaries) {
                String shiftKey = buildShiftKey(summary.getSummaryDate(), summary.getShiftCode());
                DeviceProductionSummaryDO productionSummary = productionSummaryMap.get(shiftKey);

                TheoreticalCycleResult theoreticalCycleResult = extractTheoreticalCycle(deviceParams, device.getId(),
                        summary.getSummaryDate(), summary.getShiftCode());

                DataCompletenessCheckResult completenessResult = checkDataCompleteness(
                        summary, productionSummary, deviceParams, device, theoreticalCycleResult);

                upsertMetrics(summary, device, productionSummary, plannedDowntimeSeconds,
                        theoreticalCycleResult, completenessResult, recalculationIntervalHours);

                if (completenessResult.isComplete()) {
                    processed++;
                } else {
                    incompleteDataCount++;
                    log.warn("指标汇总: 数据不完整，已标记为待重算: deviceId={}, deviceCode={}, shiftDate={}, shiftCode={}, " +
                                    "缺失数据={}",
                            device.getId(), device.getDeviceCode(), summary.getSummaryDate(), summary.getShiftCode(),
                            completenessResult.getMissingData());
                }
            }

            if (incompleteDataCount > 0) {
                log.warn("指标汇总: 设备部分班次数据不完整，已标记为待重算: deviceId={}, deviceCode={}, 总班次数={}, " +
                                "完整数据={}, 待重算={}",
                        device.getId(), device.getDeviceCode(), readySummaries.size(), processed, incompleteDataCount);
            }

            return new ProcessDeviceResult(processed > 0 || incompleteDataCount > 0,
                    readySummaries.size(), processed, incompleteDataCount);
        } finally {
            // 清除设备编号 MDC，避免线程复用导致设备编号污染
            clearDeviceCode();
        }
    }

    /**
     * 处理单台设备的班次指标汇总
     *
     * @param device              设备信息
     * @param statPointMillis     统计时间点（毫秒），只处理 shift_end_ts <= statPointMillis 的已完成班次
     * @param dataReadyDelayHours 数据就绪延迟时间（小时），用于确保上游任务有足够时间完成数据生成
     *                            例如：当前时间15:00，延迟2小时，则只处理班次结束时间 <= 13:00 的班次
     * @param recalculationIntervalHours 数据不完整记录的重算间隔时间（小时），如果距离上次计算时间超过此间隔，即使状态汇总未更新，也会重新计算
     * @return true 如果至少处理了一个班次，false 如果没有符合条件的班次或处理失败
     */
    @Transactional(rollbackFor = Exception.class)
    protected boolean processDevice(DeviceInfoDO device, long statPointMillis, int dataReadyDelayHours, int recalculationIntervalHours) {
        // 计算数据就绪时间点：统计时间点往前推 N 小时
        // 只处理班次结束时间 <= (统计时间点 - 延迟时间) 的班次
        long dataReadyCutoffMillis = statPointMillis - (dataReadyDelayHours * 3600L * 1000L);

        // 查询设备在统计时间点前的所有状态汇总记录
        // 注意：selectByRange 的 endTs 参数实际查询的是 shift_start_ts <= endTs
        // 所以我们需要先查询，然后在应用层过滤 shift_end_ts <= dataReadyCutoffMillis
        List<DeviceStateSummaryDO> summaries = deviceStateSummaryRepository.selectByRange(
                device.getId(), null, statPointMillis);
        if (summaries == null || summaries.isEmpty()) {
            log.debug("指标汇总: 设备无状态汇总记录，跳过: deviceId={}, statPointMillis={}",
                    device.getId(), statPointMillis);
            return false;
        }

        // 过滤：只处理已结束且数据已就绪的班次
        // 过滤条件：
        // 1. 班次已结束（shift_end_ts <= statPointMillis）
        // 2. 数据已就绪（shift_end_ts <= dataReadyCutoffMillis，确保上游任务有足够时间完成）
        // 3. 状态汇总已确定（isFinalized = true）
        List<DeviceStateSummaryDO> readySummaries = summaries.stream()
                .filter(s -> {
                    if (s.getShiftEndTs() == null) {
                        return false;
                    }
                    // 只处理已结束的班次
                    boolean isEnded = s.getShiftEndTs() <= statPointMillis;
                    if (!isEnded) {
                        log.debug("指标汇总: 跳过正在进行中的班次: deviceId={}, shiftDate={}, shiftCode={}, " +
                                        "shiftEndTs={}, statPointMillis={}",
                                device.getId(), s.getSummaryDate(), s.getShiftCode(),
                                s.getShiftEndTs(), statPointMillis);
                        return false;
                    }
                    // 检查数据是否已就绪（延迟执行窗口）
                    boolean isDataReady = s.getShiftEndTs() <= dataReadyCutoffMillis;
                    if (!isDataReady) {
                        log.debug("指标汇总: 跳过数据未就绪的班次（延迟执行窗口）: deviceId={}, shiftDate={}, shiftCode={}, " +
                                        "shiftEndTs={}, dataReadyCutoffMillis={}, delayHours={}",
                                device.getId(), s.getSummaryDate(), s.getShiftCode(),
                                s.getShiftEndTs(), dataReadyCutoffMillis, dataReadyDelayHours);
                        return false;
                    }
                    return true;
                })
                .filter(s -> Boolean.TRUE.equals(s.getIsFinalized()))
                .collect(java.util.stream.Collectors.toList());

        if (readySummaries.isEmpty()) {
            log.debug("指标汇总: 设备无数据已就绪的状态汇总记录: deviceId={}, 总记录数={}, statPointMillis={}, dataReadyCutoffMillis={}",
                    device.getId(), summaries.size(), statPointMillis, dataReadyCutoffMillis);
            return false;
        }

        // 性能优化：一次性加载设备参数配置，避免每个班次都查询
        List<DeviceParamConfigDO> deviceParams = deviceParamConfigRepository.selectCurrent(device.getId());
        long plannedDowntimeSeconds = extractPlannedDowntime(deviceParams);

        // 性能优化：批量查询产量汇总数据，避免重复查询
        Map<String, DeviceProductionSummaryDO> productionSummaryMap = batchQueryProductionSummaries(
                device.getId(), readySummaries);

        // 处理每个符合条件的班次
        int processed = 0;
        int incompleteDataCount = 0;
        for (DeviceStateSummaryDO summary : readySummaries) {
            // 检查数据完整性：状态汇总和产量汇总都必须存在且 finalized
            String shiftKey = buildShiftKey(summary.getSummaryDate(), summary.getShiftCode());
            DeviceProductionSummaryDO productionSummary = productionSummaryMap.get(shiftKey);

            // 提取理论节拍（即使缺失也要提取，用于记录）
            // 如果参数未配置或值为0，会尝试从 device_production_record 获取默认值
            TheoreticalCycleResult theoreticalCycleResult = extractTheoreticalCycle(deviceParams, device.getId(),
                    summary.getSummaryDate(), summary.getShiftCode());

            // 检查数据完整性（如果理论节拍使用了默认值，不应该认为数据不完整）
            DataCompletenessCheckResult completenessResult = checkDataCompleteness(
                    summary, productionSummary, deviceParams, device, theoreticalCycleResult);

            // 即使数据不完整，也要插入/更新记录，但标记为待重算状态
            upsertMetrics(summary, device, productionSummary, plannedDowntimeSeconds,
                    theoreticalCycleResult, completenessResult, recalculationIntervalHours);

            if (completenessResult.isComplete()) {
                processed++;
            } else {
                incompleteDataCount++;
                // 降级为debug，减少日志输出
                log.debug("指标汇总: 数据不完整，已标记为待重算: deviceId={}, shiftDate={}, shiftCode={}, 缺失数据={}",
                        device.getId(), summary.getSummaryDate(), summary.getShiftCode(),
                        completenessResult.getMissingData());
            }
        }

        // 只在有数据不完整时输出警告（设备级别汇总）
        if (incompleteDataCount > 0) {
            log.warn("指标汇总: 设备部分班次数据不完整: deviceId={}, deviceCode={}, 总班次={}, 完整={}, 待重算={}",
                    device.getId(), device.getDeviceCode(), readySummaries.size(), processed, incompleteDataCount);
        }

        return processed > 0 || incompleteDataCount > 0;
    }

    /**
     * 批量查询产量汇总数据
     *
     * @param deviceId  设备ID
     * @param summaries 状态汇总列表
     * @return 产量汇总数据Map，key为 shiftDate_shiftCode
     */
    private Map<String, DeviceProductionSummaryDO> batchQueryProductionSummaries(
            Long deviceId, List<DeviceStateSummaryDO> summaries) {
        Map<String, DeviceProductionSummaryDO> result = new HashMap<>();
        for (DeviceStateSummaryDO summary : summaries) {
            DeviceProductionSummaryDO productionSummary = deviceProductionSummaryRepository.findByShift(
                    deviceId, summary.getSummaryDate(), summary.getShiftCode());
            if (productionSummary != null) {
                result.put(buildShiftKey(summary.getSummaryDate(), summary.getShiftCode()), productionSummary);
            }
        }
        return result;
    }

    /**
     * 构建班次唯一标识key
     */
    private String buildShiftKey(LocalDate shiftDate, Integer shiftCode) {
        return shiftDate.toString() + "_" + shiftCode;
    }

    /**
     * 检查数据完整性：状态汇总和产量汇总都必须存在且 finalized
     * <p>
     * 注意：即使数据不完整，也会返回结果对象，但标记为不完整，用于后续标记待重算状态。
     * 如果理论节拍使用了默认值（但值>0），不应该认为数据不完整。
     *
     * @param stateSummary           状态汇总数据
     * @param productionSummary      产量汇总数据（可能为null）
     * @param deviceParams           设备参数配置列表（用于检查理论节拍）
     * @param device                 设备信息（用于日志记录）
     * @param theoreticalCycleResult 理论节拍提取结果
     * @return 数据完整性检查结果
     */
    private DataCompletenessCheckResult checkDataCompleteness(
            DeviceStateSummaryDO stateSummary,
            DeviceProductionSummaryDO productionSummary,
            List<DeviceParamConfigDO> deviceParams,
            DeviceInfoDO device,
            TheoreticalCycleResult theoreticalCycleResult) {

        List<String> missingDataList = new ArrayList<>();
        boolean missingProductionData = false;
        boolean missingTheoreticalCycle = false;

        // 检查状态汇总是否已确定（在 processDevice 中已经过滤，这里双重检查确保安全）
        if (!Boolean.TRUE.equals(stateSummary.getIsFinalized())) {
            missingDataList.add("状态汇总数据未确定");
            return new DataCompletenessCheckResult(false, String.join("、", missingDataList),
                    false, false);
        }

        // 检查产量汇总是否存在且已确定
        if (productionSummary == null) {
            missingDataList.add("产量汇总数据不存在");
            missingProductionData = true;
        } else if (!Boolean.TRUE.equals(productionSummary.getIsFinalized())) {
            missingDataList.add("产量汇总数据未确定");
            missingProductionData = true;
        } else if (productionSummary.getPartCount() == null || productionSummary.getPartCount() <= 0) {
            missingDataList.add("产量数据为0或null");
            missingProductionData = true;
        }

        // 检查理论节拍是否存在且有效
        // 如果理论节拍使用了默认值但值>0，不应该认为数据不完整
        if (theoreticalCycleResult.getValue() <= 0) {
            // 理论节拍值为0或无效
            Optional<DeviceParamConfigDO> theoreticalCycleParam = deviceParams.stream()
                    .filter(p -> PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(p.getParameterType()))
                    .findFirst();
            if (theoreticalCycleParam.isEmpty()) {
                missingDataList.add("理论节拍参数不存在");
                missingTheoreticalCycle = true;
            } else {
                missingDataList.add("理论节拍参数值无效（<=0）");
                missingTheoreticalCycle = true;
            }
        }
        // 如果理论节拍使用了默认值但值>0，不认为数据不完整（但会在recalculation_reason中记录）

        boolean isComplete = missingDataList.isEmpty();
        String missingDataStr = isComplete ? "" : String.join("、", missingDataList);

        return new DataCompletenessCheckResult(isComplete, missingDataStr,
                missingProductionData, missingTheoreticalCycle);
    }

    /**
     * 从参数列表中提取计划停机时长
     */
    private long extractPlannedDowntime(List<DeviceParamConfigDO> params) {
        return params.stream()
                .filter(p -> PARAM_PLANNED_DOWNTIME.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(Number::longValue)
                .orElse(0L);
    }

    /**
     * 从参数列表中提取理论节拍
     * <p>
     * 如果参数未配置或值为0，会尝试从 device_production_record 获取最新已完成记录的 duration_s 作为默认值
     *
     * @param params    设备参数配置列表
     * @param deviceId  设备ID（用于获取默认值）
     * @param shiftDate 班次日期（用于日志）
     * @param shiftCode 班次编码（用于日志）
     * @return 理论节拍提取结果（包含值和是否使用默认值的标识）
     */
    private TheoreticalCycleResult extractTheoreticalCycle(List<DeviceParamConfigDO> params, Long deviceId, LocalDate shiftDate, Integer shiftCode) {
        Optional<DeviceParamConfigDO> theoreticalCycleParam = params.stream()
                .filter(p -> PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(p.getParameterType()))
                .findFirst();

        long theoreticalCycleSeconds = 0L;
        boolean fromConfig = false;
        boolean useDefaultValue = false;

        if (theoreticalCycleParam.isPresent()) {
            theoreticalCycleSeconds = theoreticalCycleParam.get().getParameterValue().longValue();
            fromConfig = true;
        }

        // 如果参数未配置或值为0，尝试从 device_production_record 获取默认值
        if (theoreticalCycleSeconds <= 0 && deviceId != null) {
            theoreticalCycleSeconds = calculateTheoreticalCycleFromHistory(deviceId);
            if (theoreticalCycleSeconds > 0) {
                useDefaultValue = true;
                // 降级为debug，减少日志输出
                log.debug("指标汇总: 理论节拍使用历史记录平均值: deviceId={}, shiftDate={}, shiftCode={}, defaultTheoreticalCycleSeconds={}",
                        deviceId, shiftDate, shiftCode, theoreticalCycleSeconds);
            } else {
                if (fromConfig) {
                    log.warn("指标汇总: 理论节拍参数值无效（<=0），且无法获取默认值，将导致性能开动率和OEE为0: deviceId={}, shiftDate={}, shiftCode={}, " +
                                    "theoreticalCycleSeconds={}, 请检查 device_param_config 表中的 THEORETICAL_CYCLE 参数值或确保 device_production_record 中有已完成记录",
                            deviceId, shiftDate, shiftCode, theoreticalCycleSeconds);
                } else {
                    log.warn("指标汇总: 理论节拍参数不存在，且无法获取默认值，将导致性能开动率和OEE为0: deviceId={}, shiftDate={}, shiftCode={}, " +
                                    "请在 device_param_config 表中配置 THEORETICAL_CYCLE 参数或确保 device_production_record 中有已完成记录",
                            deviceId, shiftDate, shiftCode);
                }
            }
        }

        return new TheoreticalCycleResult(theoreticalCycleSeconds, useDefaultValue);
    }

    /**
     * 从历史产量记录计算理论节拍默认值
     * <p>
     * 计算逻辑：
     * 1. 查询该设备已完成的最新5条记录
     * 2. 计算 duration_s 的平均值作为默认值
     * 3. 如果数据库的值少于5条则有几条算几条的平均值
     * 4. 如果一条都没有则使用默认值10秒
     * <p>
     * 注意：duration_s 字段实际存储的是毫秒，需要转换为秒
     * 
     * @param deviceId 设备ID
     * @return 理论节拍默认值（秒），如果不存在则返回10（默认值）
     */
    private long calculateTheoreticalCycleFromHistory(Long deviceId) {
        if (deviceId == null) {
            return 10L; // 默认值10秒
        }
        
        // 查询最新5条已完成记录的 duration_s
        List<Long> durations = deviceProductionRecordRepository.findLatestCompletedDurationsS(deviceId, 5);
        
        if (durations.isEmpty()) {
            // 如果一条都没有则使用默认值10秒
            return 10L;
        }
        
        // 计算平均值（毫秒）
        long averageDurationMs = durations.stream()
                .mapToLong(Long::longValue)
                .sum() / durations.size();
        
        // 将毫秒转换为秒
        long theoreticalCycleSeconds = averageDurationMs / 1000L;
        
        // 如果计算结果为0或负数，使用默认值10秒
        if (theoreticalCycleSeconds <= 0) {
            return 10L;
        }
        
        return theoreticalCycleSeconds;
    }

    /**
     * 更新或插入指标汇总记录
     * <p>
     * 即使数据不完整（产量数据或理论节拍缺失），也会插入/更新记录，但标记为待重算状态。
     * 下次定时任务执行时，会检查这些标记的记录并尝试重新计算。
     *
     * @param stateSummary            状态汇总数据
     * @param device                  设备信息
     * @param productionSummary       产量汇总数据（可能为null）
     * @param plannedDowntimeSeconds  计划停机时长（秒）
     * @param theoreticalCycleResult  理论节拍提取结果
     * @param completenessResult      数据完整性检查结果
     * @param recalculationIntervalHours 数据不完整记录的重算间隔时间（小时），如果距离上次计算时间超过此间隔，即使状态汇总未更新，也会重新计算
     */
    private void upsertMetrics(DeviceStateSummaryDO stateSummary, DeviceInfoDO device,
                               DeviceProductionSummaryDO productionSummary,
                               long plannedDowntimeSeconds, TheoreticalCycleResult theoreticalCycleResult,
                               DataCompletenessCheckResult completenessResult, int recalculationIntervalHours) {
        // 1. 检查是否需要更新
        DeviceMetricSummaryDO existing = deviceMetricSummaryRepository.findByShift(
                device.getId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode());

        // 如果数据完整且记录已存在且已确定，且状态汇总未更新，可以跳过
        if (completenessResult.isComplete() && shouldSkipUpdate(existing, stateSummary)) {
            return;
        }

        // 如果数据不完整，但记录已存在且标记为待重算，需要检查是否需要重算
        if (!completenessResult.isComplete() && existing != null
                && CALC_STATUS_INCOMPLETE_DATA.equals(existing.getCalculationStatus())) {
            // 检查是否需要重算：如果距离上次计算时间超过重算间隔，即使状态汇总未更新，也要重新计算
            boolean shouldRecalculate = shouldRecalculateIncompleteData(existing, recalculationIntervalHours);
            if (!shouldRecalculate && shouldSkipUpdate(existing, stateSummary)) {
                log.debug("指标汇总: 记录已标记为待重算且数据未变化，跳过: deviceId={}, shiftDate={}, shiftCode={}",
                        device.getId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode());
                return;
            }
            if (shouldRecalculate) {
                // 降级为debug，减少日志输出
                log.debug("指标汇总: 记录标记为待重算且超过重算间隔时间（{}小时），重新计算: deviceId={}, shiftDate={}, shiftCode={}",
                        recalculationIntervalHours, device.getId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode());
            }
        }

        // 2. 准备计算上下文（即使数据不完整也要准备，用于保存部分计算结果）
        MetricCalculationContext context = prepareCalculationContext(
                stateSummary, productionSummary, plannedDowntimeSeconds, theoreticalCycleResult.getValue());

        if (context == null) {
            return; // 数据验证失败，已记录日志
        }

        // 3. 计算指标（即使数据不完整也会计算，但部分指标可能为0）
        MetricCalculationResult result = MetricCalculator.calculate(context);

        // 4. 持久化数据（根据数据完整性设置不同的状态）
        persistMetrics(existing, stateSummary, device, result, plannedDowntimeSeconds,
                theoreticalCycleResult, completenessResult);
    }

    /**
     * 准备指标计算上下文
     */
    private MetricCalculationContext prepareCalculationContext(
            DeviceStateSummaryDO stateSummary,
            DeviceProductionSummaryDO productionSummary,
            long plannedDowntimeSeconds,
            long theoreticalCycleSeconds) {

        long shiftStart = stateSummary.getShiftStartTs();
        long shiftEnd = stateSummary.getShiftEndTs();
        if (shiftStart <= 0 || shiftEnd <= 0 || shiftEnd <= shiftStart) {
            log.warn("指标汇总: 班次时间范围无效，跳过: deviceId={}, shiftStart={}, shiftEnd={}",
                    stateSummary.getDeviceInfoId(), shiftStart, shiftEnd);
            return null;
        }

        // 班次时长（单位：毫秒）
        long shiftDurationMillis = shiftEnd - shiftStart;

        // 计划运行时长（单位：毫秒）= 班次时长 - 计划停机时长
        long plannedDowntimeMillis = secondsToMillis(plannedDowntimeSeconds);
        long plannedRuntimeMillis = Math.max(0, shiftDurationMillis - plannedDowntimeMillis);

        // 使用工具类提取状态时长（单位：毫秒）
        StateDurations stateDurations = StateDurationUtils.extractStateDurations(stateSummary);
        long standbyMillis = stateDurations.getStandbyMillis();
        long faultMillis = stateDurations.getFaultMillis();
        long shutdownMillis = stateDurations.getShutdownMillis();
        long workingMillis = stateDurations.getWorkingMillis();

        // 使用工具类计算非计划停机时长（单位：毫秒）
        long unplannedDowntimeMillis = stateDurations.getUnplannedDowntimeMillis();

        // 实际运行时长（单位：毫秒）= 计划运行时长 - 非计划停机时长
        long actualRuntimeMillis = Math.max(0, plannedRuntimeMillis - unplannedDowntimeMillis);

        // 实际产量（单位：件）
        long actualOutput = (productionSummary != null && productionSummary.getPartCount() != null)
                ? productionSummary.getPartCount() : 0L;

        // 合格数量
        long qualifiedOutput = (productionSummary != null && productionSummary.getQualifiedCount() != null)
                ? productionSummary.getQualifiedCount() : actualOutput;

        return new MetricCalculationContext(
                shiftDurationMillis,
                plannedDowntimeSeconds,
                plannedDowntimeMillis,
                plannedRuntimeMillis,
                standbyMillis,
                faultMillis,
                shutdownMillis,
                workingMillis,
                unplannedDowntimeMillis,
                actualRuntimeMillis,
                actualOutput,
                qualifiedOutput,
                theoreticalCycleSeconds
        );
    }

    /**
     * 检查数据不完整的记录是否需要重算（基于重算间隔时间和重算次数）
     * <p>
     * 如果距离上次计算时间超过重算间隔时间，即使状态汇总未更新，也应该重新计算。
     * 改进3：如果重算次数过多，延长重算间隔，避免无效重算。
     *
     * @param existing 已存在的指标汇总记录
     * @param recalculationIntervalHours 基础重算间隔时间（小时）
     * @return true 如果需要重算，false 如果不需要重算
     */
    private boolean shouldRecalculateIncompleteData(DeviceMetricSummaryDO existing, int recalculationIntervalHours) {
        if (existing == null || existing.getCalculatedTime() == null) {
            return true; // 如果没有计算时间，需要重算
        }

        // 改进3：根据重算次数调整重算间隔
        int actualIntervalHours = recalculationIntervalHours;
        if (existing.getRecalculationCount() != null && existing.getRecalculationCount() > 5) {
            // 如果重算次数超过5次，延长重算间隔到6小时，避免频繁无效重算
            actualIntervalHours = 6;
            log.debug("指标汇总: 记录重算次数过多（{}次），延长重算间隔到{}小时: deviceId={}, shiftDate={}, shiftCode={}",
                    existing.getRecalculationCount(), actualIntervalHours,
                    existing.getDeviceInfoId(), existing.getShiftDate(), existing.getShiftCode());
        } else if (existing.getRecalculationCount() != null && existing.getRecalculationCount() > 10) {
            // 如果重算次数超过10次，延长重算间隔到24小时
            actualIntervalHours = 24;
            log.warn("指标汇总: 记录重算次数过多（{}次），延长重算间隔到{}小时，建议人工检查: deviceId={}, shiftDate={}, shiftCode={}",
                    existing.getRecalculationCount(), actualIntervalHours,
                    existing.getDeviceInfoId(), existing.getShiftDate(), existing.getShiftCode());
        }

        long currentTimeSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND;
        long lastCalculatedTimeSeconds = existing.getCalculatedTime();
        long elapsedHours = (currentTimeSeconds - lastCalculatedTimeSeconds) / 3600;

        return elapsedHours >= actualIntervalHours;
    }

    /**
     * 检查是否需要跳过更新
     * <p>
     * 以下情况需要更新：
     * <ul>
     *   <li>记录不存在</li>
     *   <li>记录未确定（isFinalized = false）</li>
     *   <li>记录标记为待重算（INCOMPLETE_DATA）</li>
     *   <li>状态汇总已更新（calculatedTime 更新）</li>
     * </ul>
     *
     * @param existing     已存在的指标汇总记录（可能为null）
     * @param stateSummary 状态汇总数据
     * @return true 如果应该跳过更新，false 如果需要更新
     */
    private boolean shouldSkipUpdate(DeviceMetricSummaryDO existing, DeviceStateSummaryDO stateSummary) {
        if (existing == null || !Boolean.TRUE.equals(existing.getIsFinalized())) {
            return false; // 需要插入或更新
        }

        // 如果记录标记为待重算，需要重新计算
        if (CALC_STATUS_INCOMPLETE_DATA.equals(existing.getCalculationStatus())) {
            // 降级为debug，减少日志输出
            log.debug("指标汇总: 记录标记为待重算，重新计算: deviceId={}, shiftDate={}, shiftCode={}",
                    stateSummary.getDeviceInfoId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode());
            return false;
        }

        // 检查状态汇总是否在指标汇总之后被更新
        long stateSummaryCalculatedTimeMillis = stateSummary.getCalculatedTime() != null
                ? stateSummary.getCalculatedTime() : 0L;
        long metricSummaryCalculatedTimeMillis = existing.getCalculatedTime() != null
                ? existing.getCalculatedTime() * MILLIS_PER_SECOND : 0L;

        if (stateSummaryCalculatedTimeMillis <= metricSummaryCalculatedTimeMillis) {
            log.debug("指标汇总: 记录已存在且数据未变化，跳过: deviceId={}, shiftDate={}, shiftCode={}, " +
                            "stateSummaryCalculatedTime(ms)={}, metricSummaryCalculatedTime(ms)={}",
                    stateSummary.getDeviceInfoId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode(),
                    stateSummaryCalculatedTimeMillis, metricSummaryCalculatedTimeMillis);
            return true;
        }

        // 降级为debug，减少日志输出
        log.debug("指标汇总: 状态汇总已更新，重新计算: deviceId={}, shiftDate={}, shiftCode={}",
                stateSummary.getDeviceInfoId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode());
        return false;
    }

    /**
     * 持久化指标汇总数据
     * <p>
     * 根据数据完整性检查结果，设置不同的计算状态：
     * <ul>
     *   <li>数据完整：设置为 CALCULATED（已计算）</li>
     *   <li>数据不完整：设置为 INCOMPLETE_DATA（数据不完整，待重算）</li>
     * </ul>
     *
     * @param existing                已存在的记录（可能为null）
     * @param stateSummary            状态汇总数据
     * @param device                  设备信息
     * @param result                  指标计算结果
     * @param plannedDowntimeSeconds  计划停机时长（秒）
     * @param theoreticalCycleResult  理论节拍提取结果
     * @param completenessResult      数据完整性检查结果
     */
    private void persistMetrics(DeviceMetricSummaryDO existing,
                                DeviceStateSummaryDO stateSummary,
                                DeviceInfoDO device,
                                MetricCalculationResult result,
                                long plannedDowntimeSeconds,
                                TheoreticalCycleResult theoreticalCycleResult,
                                DataCompletenessCheckResult completenessResult) {

        boolean isInsert = existing == null;

        if (isInsert) {
            DeviceMetricSummaryDO record = new DeviceMetricSummaryDO();
            populateMetricSummaryFields(record, stateSummary, device, result,
                    plannedDowntimeSeconds, theoreticalCycleResult, completenessResult, null);
            deviceMetricSummaryRepository.insert(record);

            // 降级为debug，减少日志输出
            log.debug("指标汇总: 插入记录: deviceId={}, shiftDate={}, shiftCode={}, 完整={}",
                    device.getId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode(),
                    completenessResult.isComplete());
        } else {
            populateMetricSummaryFields(existing, stateSummary, device, result,
                    plannedDowntimeSeconds, theoreticalCycleResult, completenessResult, existing);
            deviceMetricSummaryRepository.update(existing);

            // 降级为debug，减少日志输出
            log.debug("指标汇总: 更新记录: deviceId={}, shiftDate={}, shiftCode={}, 完整={}",
                    device.getId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode(),
                    completenessResult.isComplete());
        }
    }

    /**
     * 填充指标汇总字段（插入和更新共用）
     * <p>
     * 根据数据完整性检查结果，设置不同的计算状态：
     * <ul>
     *   <li>数据完整：设置为 CALCULATED（已计算），isFinalized = true</li>
     *   <li>数据不完整：设置为 INCOMPLETE_DATA（数据不完整，待重算），isFinalized = false</li>
     *   <li>如果理论节拍使用了默认值：isFinalized = true，但在recalculation_reason中记录提示</li>
     * </ul>
     *
     * @param record                  指标汇总记录（插入或更新）
     * @param stateSummary            状态汇总数据
     * @param device                  设备信息
     * @param result                  指标计算结果
     * @param plannedDowntimeSeconds  计划停机时长（秒）
     * @param theoreticalCycleResult  理论节拍提取结果
     * @param completenessResult      数据完整性检查结果
     * @param existingRecord          已存在的记录（用于判断是否是重算，如果为null表示是新记录）
     */
    private void populateMetricSummaryFields(DeviceMetricSummaryDO record,
                                             DeviceStateSummaryDO stateSummary,
                                             DeviceInfoDO device,
                                             MetricCalculationResult result,
                                             long plannedDowntimeSeconds,
                                             TheoreticalCycleResult theoreticalCycleResult,
                                             DataCompletenessCheckResult completenessResult,
                                             DeviceMetricSummaryDO existingRecord) {
        record.setDeviceInfoId(device.getId());
        record.setOrgFactoryId(stateSummary.getOrgFactoryId());
        record.setShiftDate(stateSummary.getSummaryDate());
        record.setShiftCode(stateSummary.getShiftCode());
        record.setShiftStartTs(stateSummary.getShiftStartTs());
        record.setShiftEndTs(stateSummary.getShiftEndTs());
        // 设置比率值，确保在 0-1 范围内，精度不超过4位小数（DECIMAL(5,4)）
        record.setOee(normalizeRate(result.getOee()));
        record.setAvailability(normalizeRate(result.getAvailability()));
        record.setPerformance(normalizeRate(result.getPerformance()));
        record.setQuality(normalizeRate(result.getQuality()));
        record.setUtilizationRate(normalizeRate(result.getUtilizationRate()));
        record.setFaultRate(normalizeRate(result.getFaultRate()));
        record.setWorkingHours(result.getWorkingHours());
        record.setPlannedDowntimeS((int) plannedDowntimeSeconds);
        record.setUnplannedDowntimeS((int) millisToSeconds(result.getUnplannedDowntimeMillis()));
        record.setTheoreticalCycleS((int) theoreticalCycleResult.getValue());
        record.setActualCycleS(result.getActualCycleS());
        record.setProductionCount((int) result.getActualOutput());
        record.setQualifiedCount((int) result.getQualifiedOutput());
        record.setMetrics(result.getMetrics());
        record.setCalculationData(result.getCalcData());
        record.setParameterSnapshot(Map.of(
                PARAM_PLANNED_DOWNTIME, plannedDowntimeSeconds,
                PARAM_THEORETICAL_CYCLE, theoreticalCycleResult.getValue()
        ));

        // 根据数据完整性设置状态
        if (completenessResult.isComplete()) {
            // 数据完整：标记为已计算
            record.setIsFinalized(true);
            record.setCalculationStatus(CALC_STATUS_CALCULATED);
            // 如果理论节拍使用了默认值，在recalculation_reason中记录提示
            if (theoreticalCycleResult.isUseDefaultValue()) {
                record.setRecalculationReason("理论节拍未设置，已使用默认值");
            } else {
                // 重算成功，清除重算原因和重算次数（如果之前有的话）
                record.setRecalculationReason(null);
                record.setRecalculationCount(0);
            }
        } else {
            // 数据不完整：标记为待重算
            record.setIsFinalized(false); // 数据不完整，不能标记为已确定
            record.setCalculationStatus(CALC_STATUS_INCOMPLETE_DATA);
            // 记录重算原因
            record.setRecalculationReason("数据不完整：" + completenessResult.getMissingData());
            // 改进3：更新重算次数（如果记录已存在且之前标记为待重算，则增加重算次数）
            if (existingRecord != null && CALC_STATUS_INCOMPLETE_DATA.equals(existingRecord.getCalculationStatus())) {
                // 记录已存在且之前就是待重算状态，增加重算次数
                int currentCount = existingRecord.getRecalculationCount() != null ? existingRecord.getRecalculationCount() : 0;
                record.setRecalculationCount(currentCount + 1);
                record.setRecalculatedAt(millisToSeconds(System.currentTimeMillis()));
            } else {
                // 新记录或之前不是待重算状态，初始化重算次数
                record.setRecalculationCount(1);
                record.setRecalculatedAt(millisToSeconds(System.currentTimeMillis()));
            }
        }

        record.setCalculatedTime(millisToSeconds(System.currentTimeMillis()));
        record.setCalculationSource(CALC_SOURCE);
    }

    /**
     * 时间转换工具方法
     */
    private static long secondsToMillis(long seconds) {
        return seconds * MILLIS_PER_SECOND;
    }

    private static long millisToSeconds(long millis) {
        return millis / MILLIS_PER_SECOND;
    }

    private long getSafe(Integer v) {
        return v == null ? 0L : v;
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
            log.warn("[DeviceMetricsSummaryService] 比率值为负数，已规范化为0: rate={}", rate);
            return BigDecimal.ZERO;
        }
        
        // 如果值大于1，返回1（并记录警告）
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            log.warn("[DeviceMetricsSummaryService] 比率值大于1，已规范化为1: rate={}", rate);
            return BigDecimal.ONE;
        }
        
        // 保留4位小数（DECIMAL(5,4)）
        return rate.setScale(4, RoundingMode.HALF_UP);
    }
    
}

