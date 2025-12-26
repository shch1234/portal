package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateSummaryRepository;
import com.weili.iot_portal.domain.ingestion.*;
import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceShiftSummaryService;
import com.weili.iot_portal.service.device.IDeviceStateStatisticsService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备班次汇总服务实现
 */
@Slf4j
@Service
public class DeviceShiftSummaryService implements IDeviceShiftSummaryService {

    private static final String CALCULATION_SOURCE_SCHEDULED = "SCHEDULED";
    private static final String CALCULATION_SOURCE_COMPENSATION_SKIP = "COMPENSATION_SKIP";
    
    // 设备状态常量
    private static final String DEVICE_STATUS_ACTIVE = "ACTIVE";
    
    // 状态名称常量
    private static final String STATE_MISSING = "MISSING";

    private final DeviceStateSummaryRepository stateSummaryRepository;
    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateRecordRepository stateRecordRepository;
    private final IShiftCalculationService shiftCalculationService;
    private final IShiftConfigService shiftConfigService;
    private final IDeviceStateStatisticsService stateStatisticsService;
    private final ICheckpointService<CheckpointData> checkpointService;

    @Autowired
    public DeviceShiftSummaryService(DeviceStateSummaryRepository stateSummaryRepository,
                                     DeviceInfoRepository deviceInfoRepository,
                                     DeviceStateRecordRepository stateRecordRepository,
                                     IShiftCalculationService shiftCalculationService,
                                     IShiftConfigService shiftConfigService,
                                     IDeviceStateStatisticsService stateStatisticsService,
                                     @Qualifier("deviceStateSummaryCheckpointService")
                                     ICheckpointService<CheckpointData> checkpointService) {
        this.stateSummaryRepository = stateSummaryRepository;
        this.deviceInfoRepository = deviceInfoRepository;
        this.stateRecordRepository = stateRecordRepository;
        this.shiftCalculationService = shiftCalculationService;
        this.shiftConfigService = shiftConfigService;
        this.stateStatisticsService = stateStatisticsService;
        this.checkpointService = checkpointService;
    }

    @Override
    public boolean shouldProcessShift(ShiftTimeRange shiftRange, long statisticsTimeMillis) {
        // 直接使用毫秒进行比较
        return statisticsTimeMillis >= shiftRange.getEndTs();
    }

    @Override
    public BatchProcessResult processAllDevicesWithCheckpoint(
            long statisticsTimeMillis,
            int batchSize,
            long timeoutMillis) {

        // 获取所有活跃设备
        List<DeviceInfoDO> allDevices = deviceInfoRepository.findAllActive();
        if (allDevices == null || allDevices.isEmpty()) {
            return BatchProcessResult.completed(0, 0, 0);
        }

        int success = 0;
        int skip = 0;
        int error = 0;

        Map<Long, List<DeviceInfoDO>> devicesByFactory = allDevices.stream()
                .filter(this::isDeviceValidForProcessing)
                .collect(Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId));

        long filtered = devicesByFactory.values().stream().mapToLong(List::size).sum();
        long skippedCount = allDevices.size() - filtered;
        if (skippedCount > 0) {
            skip += (int) skippedCount;
            log.info("设备状态汇总: 总设备数={}, 符合条件设备数={}, 已跳过={} (未监控/非在用/未关联工厂)", 
                    allDevices.size(), filtered, skippedCount);
        }

        if (filtered == 0) {
            log.info("设备状态汇总: 没有符合条件的设备需要处理");
            return BatchProcessResult.completed(0, skip, 0);
        }

        log.info("设备状态汇总: 开始处理 {} 台符合条件的设备，按工厂分组: {}", 
                filtered, devicesByFactory.keySet());

        for (Map.Entry<Long, List<DeviceInfoDO>> factoryEntry : devicesByFactory.entrySet()) {
            Long factoryId = factoryEntry.getKey();
            List<DeviceInfoDO> devices = factoryEntry.getValue();
            try {
                BatchProcessResult factoryResult = processFactoryDevicesWithCheckpoint(
                        factoryId, devices, statisticsTimeMillis, batchSize, timeoutMillis);
                success += factoryResult.getSuccessCount();
                skip += factoryResult.getSkipCount();
                error += factoryResult.getErrorCount();
            } catch (Exception e) {
                error += devices.size();
                log.error("处理工厂失败: factoryId={}, deviceCount={}", factoryId, devices.size(), e);
            }
        }

        return BatchProcessResult.completed(success, skip, error);
    }

    @Override
    public CompensationResult compensatePendingSummaries(int compensationDays) {
        // 日期范围：从今天往前推N天（包含今天）
        // 例如：compensationDays=7，则扫描今天及之前6天，共7天
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(compensationDays - 1);
        
        log.info("补偿任务: 开始处理，日期范围 {} - {} (补偿天数={})", startDate, endDate, compensationDays);

        // 阶段1：处理已存在的未完成汇总记录
        CompensationResult result1 = compensateExistingPendingSummaries(startDate, endDate);
        
        // 阶段2：扫描缺失的汇总记录并补齐
        CompensationResult result2 = compensateMissingSummaries(startDate, endDate);
        
        // 合并结果
        int totalSuccess = result1.getSuccessCount() + result2.getSuccessCount();
        int totalSkip = result1.getSkipCount() + result2.getSkipCount();
        int totalError = result1.getErrorCount() + result2.getErrorCount();
        
        log.info("补偿任务完成: 阶段1(未完成记录)={}, 阶段2(缺失记录)={}, 总计: 成功={}, 跳过={}, 失败={}", 
                result1.getSuccessCount() + result1.getSkipCount() + result1.getErrorCount(),
                result2.getSuccessCount() + result2.getSkipCount() + result2.getErrorCount(),
                totalSuccess, totalSkip, totalError);
        
        return new CompensationResult(totalSuccess, totalSkip, totalError);
    }

    /**
     * 阶段1：处理已存在的未完成汇总记录
     */
    private CompensationResult compensateExistingPendingSummaries(LocalDate startDate, LocalDate endDate) {
        List<DeviceStateSummaryDO> pending = stateSummaryRepository.selectPending(startDate, endDate);
        
        int success = 0;
        int skip = 0;
        int error = 0;

        if (pending == null || pending.isEmpty()) {
            log.debug("补偿任务阶段1: 未发现未完成汇总记录，窗口 {} - {}", startDate, endDate);
            return new CompensationResult(success, skip, error);
        }

        log.debug("补偿任务阶段1: 发现未完成汇总 {} 条，窗口 {} - {}", pending.size(), startDate, endDate);

        for (DeviceStateSummaryDO summary : pending) {
            try {
                boolean processed = recalculateSummary(summary);
                if (processed) {
                    success++;
                } else {
                    skip++;
                }
            } catch (Exception e) {
                error++;
                log.error("补偿处理失败: summaryId={}, deviceId={}, shiftDate={}, shiftCode={}",
                        summary.getId(), summary.getDeviceInfoId(),
                        summary.getSummaryDate(), summary.getShiftCode(), e);
            }
        }

        log.debug("补偿任务阶段1完成: 成功={}, 跳过={}, 失败={}, 总计={}", 
                success, skip, error, pending.size());

        return new CompensationResult(success, skip, error);
    }

    /**
     * 阶段2：扫描缺失的汇总记录并补齐
     * 扫描 device_state_record 表，找出有状态记录但缺少汇总记录的情况
     */
    private CompensationResult compensateMissingSummaries(LocalDate startDate, LocalDate endDate) {
        log.debug("补偿任务阶段2: 开始扫描缺失的汇总记录，窗口 {} - {}", startDate, endDate);
        
        // 1. 查询有状态记录的所有设备+班次组合
        List<DeviceStateRecordRepository.DeviceShiftKey> recordsWithData = 
                stateRecordRepository.findDistinctDeviceShifts(startDate, endDate);
        
        if (recordsWithData == null || recordsWithData.isEmpty()) {
            log.debug("补偿任务阶段2: 未发现状态记录，窗口 {} - {}", startDate, endDate);
            return new CompensationResult(0, 0, 0);
        }
        
        log.debug("补偿任务阶段2: 发现 {} 个设备+班次组合有状态记录", recordsWithData.size());
        
        int success = 0;
        int skip = 0;
        int error = 0;
        
        // 2. 检查是否有对应的汇总记录
        for (DeviceStateRecordRepository.DeviceShiftKey key : recordsWithData) {
            try {
                // 检查汇总记录是否存在
                DeviceStateSummaryDO existing = stateSummaryRepository.findByShift(
                        key.deviceInfoId(), key.shiftDate(), key.shiftCode());
                
                if (existing == null) {
                    // 缺失汇总记录，创建并计算
                    boolean processed = createAndCalculateMissingSummary(key);
                    if (processed) {
                        success++;
                    } else {
                        skip++;
                    }
                } else if (Boolean.TRUE.equals(existing.getIsFinalized())) {
                    // 汇总记录已存在且已完成，跳过（避免重复处理）
                    // 这种情况表示主任务已经处理过，不需要补偿任务再处理
                    skip++;
                    log.debug("补偿任务阶段2: 跳过已完成的汇总记录: deviceId={}, shiftDate={}, shiftCode={}",
                            key.deviceInfoId(), key.shiftDate(), key.shiftCode());
                } else {
                    // 汇总记录已存在但未完成（is_finalized = false），跳过
                    // 这种情况由阶段1处理，避免重复处理
                    skip++;
                    log.debug("补偿任务阶段2: 跳过未完成的汇总记录（由阶段1处理）: deviceId={}, shiftDate={}, shiftCode={}",
                            key.deviceInfoId(), key.shiftDate(), key.shiftCode());
                }
            } catch (Exception e) {
                error++;
                log.error("补偿任务阶段2处理失败: deviceId={}, shiftDate={}, shiftCode={}",
                        key.deviceInfoId(), key.shiftDate(), key.shiftCode(), e);
            }
        }
        
        log.debug("补偿任务阶段2完成: 成功={}, 跳过={}, 失败={}, 总计={}", 
                success, skip, error, recordsWithData.size());
        
        return new CompensationResult(success, skip, error);
    }

    /**
     * 创建并计算缺失的汇总记录
     * 复用主任务的逻辑，减少代码重复
     */
    @Transactional(rollbackFor = Exception.class)
    private boolean createAndCalculateMissingSummary(DeviceStateRecordRepository.DeviceShiftKey key) {
        // 1. 查询并验证设备
        Optional<DeviceInfoDO> deviceOpt = findAndValidateDevice(key.deviceInfoId(), "补偿任务阶段2");
        if (deviceOpt.isEmpty()) {
            return false;
        }
        DeviceInfoDO device = deviceOpt.get();

        // 2. 查询班次状态数据
        LocalDate shiftDate = key.shiftDate();
        Integer shiftCode = key.shiftCode();
        
        List<DeviceStateRecordDO> stateRecords = queryStateRecords(
                key.deviceInfoId(), shiftDate, shiftCode, null);

        if (stateRecords.isEmpty()) {
            return false;
        }

        // 4. 计算并验证班次时间范围
        DeviceStateRecordDO firstRecord = stateRecords.get(0);
        long referenceTimeMillis = firstRecord.getStartTs();
        
        Optional<ShiftTimeRange> shiftRangeOpt = shiftCalculationService.calculateAndValidateShiftRange(
                device.getOrgFactoryId(),
                device.getId(),
                referenceTimeMillis,
                shiftDate,
                shiftCode);
        
        if (shiftRangeOpt.isEmpty()) {
            return false;
        }
        ShiftTimeRange shiftRange = shiftRangeOpt.get();

        // 5. 复用主任务的核心处理逻辑
        return processDeviceShiftWithData(device, shiftRange, shiftDate, stateRecords);
    }

    /**
     * 检查设备是否有效（可用于处理）
     * 统一设备过滤条件：
     * 1. 必须监控中 (isMonitored = true)
     * 2. 必须是在用状态 (deviceStatus = 'ACTIVE')
     * 3. 必须关联工厂 (orgFactoryId != null)
     */
    private boolean isDeviceValidForProcessing(DeviceInfoDO device) {
        return Boolean.TRUE.equals(device.getIsMonitored())
                && DEVICE_STATUS_ACTIVE.equals(device.getDeviceStatus())
                && device.getOrgFactoryId() != null;
    }

    /**
     * 获取设备无效的原因（用于日志记录）
     */
    private String getDeviceInvalidReason(DeviceInfoDO device) {
        if (!Boolean.TRUE.equals(device.getIsMonitored())) {
            return "设备未监控";
        }
        if (!DEVICE_STATUS_ACTIVE.equals(device.getDeviceStatus())) {
            return "设备非在用状态";
        }
        if (device.getOrgFactoryId() == null) {
            return "设备未关联工厂";
        }
        return "未知原因";
    }

    /**
     * 查询并验证设备
     * @param deviceId 设备ID
     * @param context 上下文信息（用于日志）
     * @return Optional<DeviceInfoDO> 设备信息，如果无效则返回 empty
     */
    private Optional<DeviceInfoDO> findAndValidateDevice(Long deviceId, String context) {
        Optional<DeviceInfoDO> deviceOpt = deviceInfoRepository.findById(deviceId);
        if (deviceOpt.isEmpty() || Boolean.TRUE.equals(deviceOpt.get().getDeleted())) {
            log.debug("{}: 设备不存在或已删除: deviceId={}", context, deviceId);
            return Optional.empty();
        }
        DeviceInfoDO device = deviceOpt.get();
        if (!isDeviceValidForProcessing(device)) {
            log.debug("{}: 跳过设备: deviceId={}, reason={}", context, deviceId, getDeviceInvalidReason(device));
            return Optional.empty();
        }
        return Optional.of(device);
    }

    /**
     * 查询班次状态记录（带兼容性处理）
     * 优先使用班次维度查询，如果结果为空则回退到时间范围查询
     * 
     * @param deviceId 设备ID
     * @param shiftDate 班次日期
     * @param shiftCode 班次编码
     * @param shiftRange 班次时间范围（用于兼容性回退查询，可为null）
     * @return 状态记录列表
     */
    private List<DeviceStateRecordDO> queryStateRecords(
            Long deviceId,
            LocalDate shiftDate,
            Integer shiftCode,
            ShiftTimeRange shiftRange) {
        
        // 优先使用班次维度查询，性能更优
        List<DeviceStateRecordDO> stateRecords = stateRecordRepository.selectByShift(
                deviceId, shiftDate, shiftCode);

        // 兼容性处理：如果班次维度查询结果为空，回退到时间范围查询
        // 这可能发生在历史数据没有班次信息的情况下
        if (stateRecords.isEmpty() && shiftRange != null) {
            log.warn("班次维度查询结果为空，回退到时间范围查询: deviceId={}, shiftDate={}, shiftCode={}",
                    deviceId, shiftDate, shiftCode);
            stateRecords = stateRecordRepository.selectByRange(
                    deviceId,
                    shiftRange.getStartTs(),
                    shiftRange.getEndTs());
            
            // 如果时间范围查询也为空，记录警告
            if (stateRecords.isEmpty()) {
                log.warn("时间范围查询结果也为空: deviceId={}, startTs={}, endTs={}",
                        deviceId, shiftRange.getStartTs(), shiftRange.getEndTs());
            }
        }
        
        return stateRecords;
    }

    /**
     * 处理设备班次汇总的核心逻辑（提取的公共方法）
     * 用于主任务和补偿任务复用
     * 
     * @param device 设备信息
     * @param shiftRange 班次时间范围
     * @param shiftDate 班次日期
     * @param stateRecords 状态记录列表
     * @return 是否处理成功
     */
    private boolean processDeviceShiftWithData(
            DeviceInfoDO device,
            ShiftTimeRange shiftRange,
            LocalDate shiftDate,
            List<DeviceStateRecordDO> stateRecords) {
        
        // 1. 计算状态统计
        Map<String, StateStatistics> stateStats = stateStatisticsService.calculateStatistics(
                stateRecords,
                shiftRange.getStartTs(),
                shiftRange.getEndTs());

        // 2. 获取班次配置
        var shiftConfig = shiftConfigService.getCurrentConfiguration(
                device.getOrgFactoryId(), device.getId(), shiftRange.getStartTs());

        // 3. 保存或更新汇总记录
        ProcessResult result = processDeviceShift(device, shiftConfig, shiftRange, shiftDate, stateStats);
        
        return result.isProcessed();
    }

    @Override
    public BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long statisticsTimeMillis,
            int batchSize,
            long timeoutMillis) {

        // 加载检查点，获取已处理的设备ID
        // 检查点服务使用秒，需要转换
        long statisticsTimeSeconds = statisticsTimeMillis / 1000;
        Set<Long> processedDeviceIds = checkpointService.getProcessedDeviceIds(
                factoryId, statisticsTimeSeconds);

        // 过滤已处理的设备
        List<DeviceInfoDO> remainingDevices = devices.stream()
                .filter(device -> !processedDeviceIds.contains(device.getId()))
                .collect(Collectors.toList());

        if (remainingDevices.isEmpty()) {
            // 所有设备已处理，清除检查点
            checkpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }

        // 分批处理剩余设备
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        List<Long> newProcessedIds = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < remainingDevices.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remainingDevices.size());
            List<DeviceInfoDO> batch = remainingDevices.subList(i, endIndex);

            for (DeviceInfoDO device : batch) {
                try {
                    ProcessResult result = processDeviceShiftComplete(
                            device, statisticsTimeMillis);
                    if (result.isProcessed()) {
                        successCount++;
                        newProcessedIds.add(device.getId());
                        processedDeviceIds.add(device.getId());
                    } else {
                        skipCount++;
                        log.debug("跳过设备: factoryId={}, deviceId={}, reason={}",
                                factoryId, device.getId(), result.getSkipReason());
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("处理设备失败: factoryId={}, deviceId={}, deviceCode={}",
                            factoryId, device.getId(), device.getDeviceCode(), e);
                    // 继续处理下一个设备，不中断
                }
            }

            // 每批处理完后更新检查点
            if (!newProcessedIds.isEmpty()) {
                List<Long> allProcessedIds = new ArrayList<>(processedDeviceIds);
                checkpointService.saveCheckpoint(factoryId, statisticsTimeSeconds, allProcessedIds);
                newProcessedIds.clear();
            }

            // 检查是否超时
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutMillis) {
                log.info("处理超时，保存检查点: 工厂={}, 已处理={}, 剩余={}",
                        factoryId, processedDeviceIds.size(), remainingDevices.size() - processedDeviceIds.size());
                return BatchProcessResult.incomplete(successCount, skipCount, errorCount);
            }
        }

        // 所有设备都处理完成，清除检查点
        if (processedDeviceIds.size() >= devices.size()) {
            checkpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
            return BatchProcessResult.completed(successCount, skipCount, errorCount);
        } else {
            // 还有未处理的设备，保存检查点
            List<Long> allProcessedIds = new ArrayList<>(processedDeviceIds);
            checkpointService.saveCheckpoint(factoryId, statisticsTimeSeconds, allProcessedIds);
            return BatchProcessResult.incomplete(successCount, skipCount, errorCount);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessResult processDeviceShiftComplete(
            DeviceInfoDO device,
            long statisticsTimeMillis) {

        // 1. 查询设备在当前时间的班次配置（无配置则使用默认配置）
        var shiftConfig = shiftConfigService.getCurrentConfiguration(
                device.getOrgFactoryId(), device.getId(), statisticsTimeMillis);

        // 2. 计算已结束的班次
        // 统计时间点应该落在刚结束的班次内，所以需要找到前一个班次
        ShiftTimeRange previousShiftRange = shiftCalculationService.calculatePreviousShiftRange(
                device.getOrgFactoryId(), device.getId(), statisticsTimeMillis);

        if (previousShiftRange == null) {
            // 无法计算前一个班次，跳过
            return ProcessResult.skipped("无法计算前一个班次");
        }

        // 3. 检查班次是否已经结束
        if (!shouldProcessShift(previousShiftRange, statisticsTimeMillis)) {
            // 班次还未到统计时间，跳过
            return ProcessResult.skipped("班次未到统计时间");
        }

        // 4. 获取班次日期和编码（直接从 previousShiftRange 获取）
        LocalDate shiftDate = previousShiftRange.getShiftDate();
        Integer shiftCode = previousShiftRange.getShiftCode();

        // 5. 查询班次状态数据（带兼容性处理）
        List<DeviceStateRecordDO> stateRecords = queryStateRecords(
                device.getId(), shiftDate, shiftCode, previousShiftRange);

        // 6. 复用核心处理逻辑
        boolean processed = processDeviceShiftWithData(device, previousShiftRange, shiftDate, stateRecords);
        return processed ? ProcessResult.processed() : ProcessResult.skipped("处理失败");
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessResult processDeviceShift(
            DeviceInfoDO device,
            DeviceShiftConfigDO config,
            ShiftTimeRange shiftRange,
            LocalDate shiftDate,
            Map<String, StateStatistics> stateStats) {

        // 检查是否已经统计过（避免重复统计）
        DeviceStateSummaryDO existingSummary = stateSummaryRepository.findByShift(
                device.getId(), shiftDate, shiftRange.getShiftCode());

        if (existingSummary != null && Boolean.TRUE.equals(existingSummary.getIsFinalized())) {
            // 已经统计过且已确定，跳过
            return ProcessResult.skipped("已统计过");
        }

        // 保存或更新汇总记录
        saveOrUpdateSummary(device.getId(), device.getOrgFactoryId(), shiftDate, shiftRange, stateStats);

        return ProcessResult.processed();
    }


    @Override
    public void forceUpdateSummary(
            Long deviceId,
            Long orgFactoryId,
            LocalDate shiftDate,
            ShiftTimeRange shiftRange,
            Map<String, StateStatistics> stateStats) {
        // 强制更新，不检查是否已统计过
        saveOrUpdateSummary(deviceId, orgFactoryId, shiftDate, shiftRange, stateStats);
    }

    /**
     * 保存或更新汇总记录
     */
    private void saveOrUpdateSummary(
            Long deviceId,
            Long orgFactoryId,
            LocalDate shiftDate,
            ShiftTimeRange shiftRange,
            Map<String, StateStatistics> stateStats) {

        DeviceStateSummaryDO summary = findOrCreateSummary(deviceId, orgFactoryId, shiftDate, shiftRange);
        populateSummaryFields(summary, shiftRange);
        populateStateStatisticsFields(summary, stateStats);
        calculateAndSetCompleteness(summary, shiftRange, stateStats);
        buildAndSetStateStatisticsJson(summary, stateStats);
        persistSummary(summary);
    }

    /**
     * 查找或创建汇总记录
     */
    private DeviceStateSummaryDO findOrCreateSummary(
            Long deviceId,
            Long orgFactoryId,
            LocalDate shiftDate,
            ShiftTimeRange shiftRange) {
        
        DeviceStateSummaryDO summary = stateSummaryRepository.findByShift(
                deviceId, shiftDate, shiftRange != null ? shiftRange.getShiftCode() : null);

        if (summary == null) {
            summary = new DeviceStateSummaryDO();
            summary.setDeviceInfoId(deviceId);
            summary.setOrgFactoryId(orgFactoryId);
            summary.setSummaryDate(shiftDate);
            if (shiftRange != null) {
                summary.setShiftCode(shiftRange.getShiftCode());
            }
        } else {
            // 更新时也更新orgFactoryId（防止设备迁移到其他工厂）
            summary.setOrgFactoryId(orgFactoryId);
        }
        
        return summary;
    }

    /**
     * 填充汇总记录的基础字段
     */
    private void populateSummaryFields(DeviceStateSummaryDO summary, ShiftTimeRange shiftRange) {
        if (shiftRange != null) {
            summary.setShiftStartTs(shiftRange.getStartTs());
            summary.setShiftEndTs(shiftRange.getEndTs());
        }

        summary.setIsFinalized(true);
        summary.setCalculatedTime(System.currentTimeMillis());
        summary.setCalculationSource(CALCULATION_SOURCE_SCHEDULED);
    }

    /**
     * 填充状态统计字段
     */
    private void populateStateStatisticsFields(
            DeviceStateSummaryDO summary,
            Map<String, StateStatistics> stateStats) {
        
        StateStatistics working = getStateStatistics(stateStats, DeviceStateEnum.WORKING.name());
        StateStatistics standby = getStateStatistics(stateStats, DeviceStateEnum.STANDBY.name());
        StateStatistics fault = getStateStatistics(stateStats, DeviceStateEnum.FAULT.name());
        StateStatistics shutdown = getStateStatistics(stateStats, DeviceStateEnum.SHUTDOWN.name());
        StateStatistics missing = getStateStatistics(stateStats, STATE_MISSING);

        summary.setWorkingDurationS((int) working.durationSeconds);
        summary.setStandbyDurationS((int) standby.durationSeconds);
        summary.setFaultDurationS((int) fault.durationSeconds);
        summary.setShutdownDurationS((int) shutdown.durationSeconds);
        summary.setMissingDataS((int) missing.durationSeconds);

        summary.setWorkingRatio(working.ratio);
        summary.setStandbyRatio(standby.ratio);
        summary.setFaultRatio(fault.ratio);
        summary.setShutdownRatio(shutdown.ratio);
    }

    /**
     * 获取状态统计（带默认值）
     */
    private StateStatistics getStateStatistics(Map<String, StateStatistics> stateStats, String stateName) {
        return stateStats.getOrDefault(stateName, new StateStatistics(stateName, 0, 0));
    }

    /**
     * 计算并设置数据完整度
     */
    private void calculateAndSetCompleteness(
            DeviceStateSummaryDO summary,
            ShiftTimeRange shiftRange,
            Map<String, StateStatistics> stateStats) {
        
        if (shiftRange == null) {
            return;
        }
        
        long shiftDurationMillis = shiftRange.getEndTs() - shiftRange.getStartTs();
        if (shiftDurationMillis <= 0) {
            return;
        }
        
        StateStatistics working = getStateStatistics(stateStats, DeviceStateEnum.WORKING.name());
        StateStatistics standby = getStateStatistics(stateStats, DeviceStateEnum.STANDBY.name());
        StateStatistics fault = getStateStatistics(stateStats, DeviceStateEnum.FAULT.name());
        StateStatistics shutdown = getStateStatistics(stateStats, DeviceStateEnum.SHUTDOWN.name());
        
        long totalRecordedDurationMillis = working.durationSeconds + standby.durationSeconds
                + fault.durationSeconds + shutdown.durationSeconds;
        
        BigDecimal completeness = BigDecimal.valueOf(totalRecordedDurationMillis)
                .divide(BigDecimal.valueOf(shiftDurationMillis), 4, RoundingMode.HALF_UP);
        
        summary.setDataCompleteness(completeness);
    }

    /**
     * 构建并设置状态统计JSON
     */
    private void buildAndSetStateStatisticsJson(
            DeviceStateSummaryDO summary,
            Map<String, StateStatistics> stateStats) {
        
        Map<String, Object> stateStatisticsJson = new HashMap<>();
        for (Map.Entry<String, StateStatistics> entry : stateStats.entrySet()) {
            Map<String, Object> stateInfo = new HashMap<>();
            stateInfo.put("durationSeconds", entry.getValue().durationSeconds);
            stateInfo.put("ratio", entry.getValue().ratio);
            stateInfo.put("fragmentCount", entry.getValue().fragmentCount);
            stateStatisticsJson.put(entry.getKey(), stateInfo);
        }
        summary.setStateStatistics(stateStatisticsJson);
    }

    /**
     * 持久化汇总记录
     */
    private void persistSummary(DeviceStateSummaryDO summary) {
        if (summary.getId() != null) {
            stateSummaryRepository.update(summary);
        } else {
            stateSummaryRepository.insert(summary);
        }
    }

    /**
     * 重新计算单条汇总记录（供补偿任务调用）
     */
    @Transactional(rollbackFor = Exception.class)
    protected boolean recalculateSummary(DeviceStateSummaryDO summary) {
        // 查询并验证设备
        Optional<DeviceInfoDO> deviceOpt = findAndValidateDevice(summary.getDeviceInfoId(), "补偿任务");
        if (deviceOpt.isEmpty()) {
            markSummaryAsFinalized(summary, "设备无效");
            return false;
        }
        DeviceInfoDO device = deviceOpt.get();

        // 获取班次日期和编码（优先使用 summary 中的数据，这是历史数据，更准确）
        LocalDate shiftDate = summary.getSummaryDate();
        Integer shiftCode = summary.getShiftCode();
        
        // 计算班次时间范围（用于统计计算）
        long shiftStartTs = summary.getShiftStartTs();
        Optional<ShiftTimeRange> shiftRangeOpt = shiftCalculationService.calculateAndValidateShiftRange(
                device.getOrgFactoryId(),
                summary.getDeviceInfoId(),
                shiftStartTs,
                shiftDate,
                shiftCode);
        
        if (shiftRangeOpt.isEmpty()) {
            return false;
        }
        ShiftTimeRange shiftRange = shiftRangeOpt.get();

        // 查询班次状态数据（带兼容性处理）
        List<DeviceStateRecordDO> stateRecords = queryStateRecords(
                summary.getDeviceInfoId(), shiftDate, shiftCode, shiftRange);

        // 如果状态记录为空，标记为已完成（表示确实没有数据）
        if (stateRecords.isEmpty()) {
            log.debug("补偿任务：设备没有状态记录，标记为已完成: deviceId={}, shiftDate={}, shiftCode={}",
                    summary.getDeviceInfoId(), shiftDate, shiftCode);
            markSummaryAsFinalized(summary, "设备无状态记录");
            return false;
        }

        // 重新计算状态统计
        Map<String, StateStatistics> stateStats =
                stateStatisticsService.calculateStatistics(
                        stateRecords,
                        shiftRange.getStartTs(),
                        shiftRange.getEndTs());

        // 强制更新汇总记录（补偿任务需要强制更新，即使已统计过）
        forceUpdateSummary(
                summary.getDeviceInfoId(),
                device.getOrgFactoryId(),
                summary.getSummaryDate(),
                shiftRange,
                stateStats);

        return true;
    }

    /**
     * 标记汇总记录为已完成（用于补偿任务）
     */
    private void markSummaryAsFinalized(DeviceStateSummaryDO summary, String reason) {
        summary.setIsFinalized(true);
        summary.setCalculatedTime(System.currentTimeMillis());
        summary.setCalculationSource(CALCULATION_SOURCE_COMPENSATION_SKIP);
        stateSummaryRepository.update(summary);
        log.debug("补偿任务：标记汇总记录为已完成: summaryId={}, deviceId={}, shiftDate={}, shiftCode={}, reason={}",
                summary.getId(), summary.getDeviceInfoId(), summary.getSummaryDate(), summary.getShiftCode(), reason);
    }
}

