package com.weili.iot_portal.task.devicemng;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.dataobject.devicemng.ShiftConfigurationDO;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceStateSummaryMapper;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceBaseInfoMapper;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateTimelineRepository;
import com.weili.iot_portal.dal.repository.devicemng.ShiftConfigurationRepository;
import com.weili.iot_portal.dal.repository.devicebase.DeviceBaseInfoRepository;
import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.service.support.ShiftConfigurationService;
import com.weili.iot_portal.service.support.ShiftTimeRange;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 设备状态汇总定时任务
 * 
 * 功能：当一个班次结束后，延迟一段时间（可配置）启动定时任务，
 * 统计刚结束的这个班次的状态数据，更新 device_state_summary 表
 * 
 * 配置说明：
 * - shift.summary.delay-minutes: 班次结束后延迟多少分钟启动统计（默认5分钟）
 * - shift.summary.batch-size: 每批处理的设备数量（默认50）
 * - 建议在XXL-Job中配置cron表达式，例如：每5分钟执行一次
 * 
 * 使用框架：BaseScheduledJob（部分）
 * - 统一异常处理
 * - 统一统计收集
 * - 统一日志记录
 * 注意：由于需要按租户和工厂分组处理，且每个工厂使用检查点机制，
 * 因此保留了部分自定义逻辑，但异常处理和统计收集已统一。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStateSummaryJob extends BaseScheduledJob {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String CALCULATION_SOURCE_SCHEDULED = "SCHEDULED";
    private static final String CHECKPOINT_KEY_PREFIX = "device_state_summary:checkpoint:";
    private static final long CHECKPOINT_TTL_SECONDS = 24 * 3600; // 24小时

    @Value("${shift.summary.delay-minutes:5}")
    private int delayMinutes;
    
    @Value("${shift.summary.batch-size:50}")
    private int batchSize; // 每批处理的设备数量

    private final DeviceBaseInfoRepository deviceBaseInfoRepository;
    private final DeviceBaseInfoMapper deviceBaseInfoMapper;
    private final ShiftConfigurationRepository shiftConfigurationRepository;
    private final ShiftConfigurationService shiftConfigurationService;
    private final DeviceStateTimelineRepository stateTimelineRepository;
    private final DeviceStateSummaryMapper stateSummaryMapper;
    private final RedisClient redisClient;

    @Override
    protected String getJobName() {
        return "设备状态汇总任务";
    }

    /**
     * 执行入口
     */
    @Override
    @XxlJob("deviceStateSummaryJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        // 计算统计时间点（当前时间 - 延迟时间）
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        long statisticsTimeSeconds = currentTimeSeconds - (delayMinutes * 60L);
        
        XxlJobHelper.log("统计时间点: {} (当前时间: {}), 延迟配置: {} 分钟", 
                Instant.ofEpochSecond(statisticsTimeSeconds), 
                Instant.ofEpochSecond(currentTimeSeconds),
                delayMinutes);

        // 查询所有设备
        List<DeviceBaseInfoDO> allDevices = queryAllDevices();
        
        if (allDevices.isEmpty()) {
            return JobExecutionResult.empty();
        }

        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        // 先按租户分组，再按工厂分组处理
        long totalDeviceCount = allDevices.size();
        Map<String, Map<String, List<DeviceBaseInfoDO>>> devicesByTenantAndFactory = allDevices.stream()
                .filter(device -> device.getOrgFactoryId() != null) // 过滤掉未关联工厂的设备
                .collect(Collectors.groupingBy(
                        DeviceBaseInfoDO::getTenantUuid,
                        Collectors.groupingBy(DeviceBaseInfoDO::getOrgFactoryId)
                ));
        
        long filteredDeviceCount = devicesByTenantAndFactory.values().stream()
                .flatMap(factoryMap -> factoryMap.values().stream())
                .mapToLong(List::size)
                .sum();
        
        if (totalDeviceCount > filteredDeviceCount) {
            long skippedCount = totalDeviceCount - filteredDeviceCount;
            XxlJobHelper.log("警告: 有 {} 个设备未关联工厂，已跳过", skippedCount);
            log.warn("设备状态汇总任务: 有 {} 个设备未关联工厂，已跳过", skippedCount);
            skipCount += (int) skippedCount;
        }

        // 按租户和工厂分组处理
        for (Map.Entry<String, Map<String, List<DeviceBaseInfoDO>>> tenantEntry : devicesByTenantAndFactory.entrySet()) {
            String tenantId = tenantEntry.getKey();
            Map<String, List<DeviceBaseInfoDO>> devicesByFactory = tenantEntry.getValue();

            XxlJobHelper.log("处理租户: {}, 工厂数量: {}", tenantId, devicesByFactory.size());

            for (Map.Entry<String, List<DeviceBaseInfoDO>> factoryEntry : devicesByFactory.entrySet()) {
                String factoryId = factoryEntry.getKey();
                List<DeviceBaseInfoDO> devices = factoryEntry.getValue();

                XxlJobHelper.log("处理工厂: {}, 设备数量: {}", factoryId, devices.size());

                try {
                    // 使用检查点机制处理工厂设备
                    ProcessResult factoryResult = processFactoryWithCheckpoint(
                            tenantId, factoryId, devices, statisticsTimeSeconds);
                    
                    // 统计工厂处理结果
                    if (factoryResult.getSuccessCount().isPresent()) {
                        int count = factoryResult.getSuccessCount().get();
                        successCount += count;
                        XxlJobHelper.log("工厂处理完成: factoryId={}, 成功={}, 跳过={}, 失败={}, 是否完成={}", 
                                factoryId, count, factoryResult.getSkipCount(), 
                                factoryResult.getErrorCount(), factoryResult.isCompleted());
                    }
                    
                    skipCount += factoryResult.getSkipCount();
                    errorCount += factoryResult.getErrorCount();
                } catch (Exception e) {
                    errorCount += devices.size(); // 工厂处理失败，该工厂所有设备计入失败
                    log.error("处理工厂失败: tenantId={}, factoryId={}, deviceCount={}", 
                            tenantId, factoryId, devices.size(), e);
                    XxlJobHelper.log("处理工厂失败: factoryId={}, error={}", factoryId, e.getMessage());
                    // 继续处理下一个工厂，不中断
                }
            }
        }

        return JobExecutionResult.of(successCount, skipCount, errorCount);
    }

    /**
     * 查询所有设备
     * 注意：这里需要根据实际业务需求调整，可能需要查询所有租户的设备
     * 当前实现：通过Mapper直接查询所有设备（不按租户过滤）
     * 如果需要按租户处理，需要先查询所有租户，然后遍历查询每个租户的设备
     */
    private List<DeviceBaseInfoDO> queryAllDevices() {
        // 通过Mapper查询所有设备（不按租户过滤）
        // 注意：如果系统是多租户隔离的，这里需要先查询所有租户，然后遍历查询
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceBaseInfoDO::getDeleted, false); // 只查询未删除的设备
        return deviceBaseInfoMapper.selectList(wrapper);
    }

    /**
     * 处理单个设备的班次统计
     * 
     * @param tenantId 租户ID
     * @param device 设备信息
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @return true-已处理，false-跳过（无班次配置或班次未结束）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean processDeviceShift(String tenantId, DeviceBaseInfoDO device, long statisticsTimeSeconds) {
        // 1. 查询设备在当前时间的班次配置
        Optional<ShiftConfigurationDO> configOpt = shiftConfigurationRepository
                .findActiveByDeviceAndTime(tenantId, device.getId(), statisticsTimeSeconds * 1000L);

        if (configOpt.isEmpty()) {
            // 设备未配置班次，跳过
            return false;
        }

        ShiftConfigurationDO config = configOpt.get();

        // 2. 计算已结束的班次
        // 统计时间点应该落在刚结束的班次内，所以需要找到前一个班次
        ShiftTimeRange previousShiftRange = calculatePreviousShiftRange(
                tenantId, device.getOrgFactoryId(), device.getId(), config, statisticsTimeSeconds);

        if (previousShiftRange == null) {
            // 无法计算前一个班次，跳过
            return false;
        }

        // 3. 检查班次是否已经结束（统计时间点 >= 班次结束时间 + 延迟时间）
        long shiftEndTimeSeconds = previousShiftRange.getEndTs() / 1000;
        long expectedStatisticsTime = shiftEndTimeSeconds + (delayMinutes * 60L);
        
        if (statisticsTimeSeconds < expectedStatisticsTime) {
            // 班次还未到统计时间，跳过
            return false;
        }

        // 4. 检查是否已经统计过（避免重复统计）
        LocalDate shiftDate = calculateShiftDate(previousShiftRange, shiftEndTimeSeconds);
        DeviceStateSummaryDO existingSummary = findExistingSummary(
                tenantId, device.getId(), shiftDate, previousShiftRange.getShiftCode());

        if (existingSummary != null && Boolean.TRUE.equals(existingSummary.getIsFinalized())) {
            // 已经统计过且已确定，跳过
            return false;
        }

        // 5. 统计班次状态数据
        List<DeviceStateTimelineDO> stateRecords = stateTimelineRepository.selectByRange(
                tenantId, device.getId(), 
                previousShiftRange.getStartTs() / 1000, 
                previousShiftRange.getEndTs() / 1000);

        // 6. 计算状态统计
        Map<String, StateStatistics> stateStats = calculateStateStatisticsInternal(
                stateRecords, previousShiftRange.getStartTs() / 1000, previousShiftRange.getEndTs() / 1000);

        // 7. 更新或插入汇总记录
        saveOrUpdateSummary(tenantId, device.getId(), device.getOrgFactoryId(), shiftDate, previousShiftRange, stateStats);

        return true;
    }

    /**
     * 计算前一个班次的时间范围
     */
    private ShiftTimeRange calculatePreviousShiftRange(String tenantId, String factoryId, String deviceId,
                                                       ShiftConfigurationDO config, long statisticsTimeSeconds) {
        try {
            // 获取当前时间点的班次
            ShiftTimeRange currentShift = shiftConfigurationService.calculateShiftRange(
                    tenantId, factoryId, deviceId, statisticsTimeSeconds * 1000L);

            // 计算前一个班次
            // 前一个班次的结束时间 = 当前班次的开始时间
            long previousShiftEndTs = currentShift.getStartTs();
            
            // 根据班次配置计算前一个班次的开始时间
            ShiftTimeRange previousShift = calculateShiftRangeByEndTime(
                    config, previousShiftEndTs);

            return previousShift;
        } catch (Exception e) {
            log.warn("计算前一个班次失败: tenantId={}, deviceId={}, error={}", 
                    tenantId, deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 根据结束时间计算班次时间范围
     */
    private ShiftTimeRange calculateShiftRangeByEndTime(ShiftConfigurationDO config, long endTsMillis) {
        LocalDateTime endDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(endTsMillis),
                ZoneId.systemDefault());

        LocalDate shiftDate = endDateTime.toLocalDate();
        LocalTime endTime = endDateTime.toLocalTime();

        // 查找匹配的班次
        List<ShiftConfigurationDO.ShiftDefinition> shifts = getShiftDefinitions(config);
        
        for (ShiftConfigurationDO.ShiftDefinition shift : shifts) {
            LocalTime shiftEndTime = LocalTime.parse(shift.getEndTime(), TIME_FORMATTER);
            
            // 检查是否匹配（考虑跨天情况）
            boolean matches = false;
            if (Boolean.TRUE.equals(shift.getCrossDay())) {
                // 跨天班次：结束时间可能是当天的结束时间或次日的结束时间
                matches = endTime.equals(shiftEndTime) || 
                         endTime.equals(shiftEndTime.minusHours(24));
            } else {
                matches = endTime.equals(shiftEndTime);
            }

            if (matches) {
                // 计算开始时间
                LocalTime shiftStartTime = LocalTime.parse(shift.getStartTime(), TIME_FORMATTER);
                LocalDateTime shiftStart;
                
                if (Boolean.TRUE.equals(shift.getCrossDay())) {
                    // 跨天班次：开始时间是前一天
                    shiftStart = shiftDate.minusDays(1).atTime(shiftStartTime);
                } else {
                    shiftStart = shiftDate.atTime(shiftStartTime);
                }

                long startTs = shiftStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

                return ShiftTimeRange.builder()
                        .shiftCode(shift.getCode())
                        .shiftName(shift.getName())
                        .startTs(startTs)
                        .endTs(endTsMillis)
                        .durationMs(endTsMillis - startTs)
                        .build();
            }
        }

        return null;
    }

    /**
     * 获取班次定义列表
     */
    private List<ShiftConfigurationDO.ShiftDefinition> getShiftDefinitions(ShiftConfigurationDO config) {
        List<ShiftConfigurationDO.ShiftDefinition> shifts = new ArrayList<>();

        // 班次1
        if (StringUtils.isNotBlank(config.getShift1Code())) {
            ShiftConfigurationDO.ShiftDefinition shift1 = new ShiftConfigurationDO.ShiftDefinition();
            shift1.setCode(config.getShift1Code());
            shift1.setName(config.getShift1Name());
            shift1.setStartTime(config.getShift1StartTime());
            shift1.setEndTime(config.getShift1EndTime());
            shift1.setDurationHours(config.getShift1DurationS() != null ? 
                    config.getShift1DurationS() / 3600 : null);
            shift1.setCrossDay(isCrossDay(config.getShift1StartTime(), config.getShift1EndTime()));
            shifts.add(shift1);
        }

        // 班次2
        if (StringUtils.isNotBlank(config.getShift2Code())) {
            ShiftConfigurationDO.ShiftDefinition shift2 = new ShiftConfigurationDO.ShiftDefinition();
            shift2.setCode(config.getShift2Code());
            shift2.setName(config.getShift2Name());
            shift2.setStartTime(config.getShift2StartTime());
            shift2.setEndTime(config.getShift2EndTime());
            shift2.setDurationHours(config.getShift2DurationS() != null ? 
                    config.getShift2DurationS() / 3600 : null);
            shift2.setCrossDay(isCrossDay(config.getShift2StartTime(), config.getShift2EndTime()));
            shifts.add(shift2);
        }

        // 班次3（3班制）
        if (config.getShiftMode() != null && config.getShiftMode() == 3 
                && StringUtils.isNotBlank(config.getShift3Code())) {
            ShiftConfigurationDO.ShiftDefinition shift3 = new ShiftConfigurationDO.ShiftDefinition();
            shift3.setCode(config.getShift3Code());
            shift3.setName(config.getShift3Name());
            shift3.setStartTime(config.getShift3StartTime());
            shift3.setEndTime(config.getShift3EndTime());
            shift3.setDurationHours(config.getShift3DurationS() != null ? 
                    config.getShift3DurationS() / 3600 : null);
            shift3.setCrossDay(isCrossDay(config.getShift3StartTime(), config.getShift3EndTime()));
            shifts.add(shift3);
        }

        return shifts;
    }

    /**
     * 判断班次是否跨天
     */
    private boolean isCrossDay(String startTime, String endTime) {
        try {
            LocalTime start = LocalTime.parse(startTime, TIME_FORMATTER);
            LocalTime end = LocalTime.parse(endTime, TIME_FORMATTER);
            return end.isBefore(start) || end.equals(start);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算班次日期
     */
    private LocalDate calculateShiftDate(ShiftTimeRange shiftRange, long endTimeSeconds) {
        LocalDateTime endDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(endTimeSeconds),
                ZoneId.systemDefault());
        return endDateTime.toLocalDate();
    }

    /**
     * 查找已存在的汇总记录
     */
    private DeviceStateSummaryDO findExistingSummary(String tenantId, String deviceId, 
                                                     LocalDate shiftDate, String shiftCode) {
        LambdaQueryWrapper<DeviceStateSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceStateSummaryDO::getTenantUuid, tenantId)
                .eq(DeviceStateSummaryDO::getDeviceInfoId, deviceId)
                .eq(DeviceStateSummaryDO::getSummaryDate, shiftDate)
                .eq(DeviceStateSummaryDO::getShiftCode, shiftCode)
                .last("limit 1");
        return stateSummaryMapper.selectOne(wrapper);
    }

    /**
     * 计算状态统计（供补偿任务调用）
     */
    public Map<String, StateStatistics> calculateStateStatisticsInternal(
            List<DeviceStateTimelineDO> stateRecords, long shiftStartTs, long shiftEndTs) {
        
        Map<String, StateStatistics> statsMap = new HashMap<>();
        long shiftDurationSeconds = shiftEndTs - shiftStartTs;

        // 初始化所有状态
        for (String state : Arrays.asList("WORKING", "STANDBY", "FAULT", "SHUTDOWN", "UNKNOWN")) {
            statsMap.put(state, new StateStatistics(state, 0, 0));
        }

        // 统计状态记录
        for (DeviceStateTimelineDO record : stateRecords) {
            String stateCode = record.getStateCode();
            if (stateCode == null) {
                continue;
            }

            // 计算该记录在班次内的有效时长
            long recordStartTs = record.getStartTs() != null ? record.getStartTs() : shiftStartTs;
            long recordEndTs = record.getEndTs() != null ? record.getEndTs() : shiftEndTs;

            // 取交集
            long effectiveStart = Math.max(recordStartTs, shiftStartTs);
            long effectiveEnd = Math.min(recordEndTs, shiftEndTs);
            long duration = Math.max(0, effectiveEnd - effectiveStart);

            StateStatistics stats = statsMap.computeIfAbsent(stateCode, 
                    k -> new StateStatistics(k, 0, 0));
            stats.durationSeconds += duration;
            stats.fragmentCount++;
        }

        // 计算缺失数据时长
        long totalRecordedDuration = statsMap.values().stream()
                .mapToLong(s -> s.durationSeconds)
                .sum();
        long missingDataSeconds = Math.max(0, shiftDurationSeconds - totalRecordedDuration);

        // 计算占比
        for (StateStatistics stats : statsMap.values()) {
            if (shiftDurationSeconds > 0) {
                stats.ratio = BigDecimal.valueOf(stats.durationSeconds)
                        .divide(BigDecimal.valueOf(shiftDurationSeconds), 4, RoundingMode.HALF_UP);
            }
        }

        // 添加缺失数据统计
        statsMap.put("MISSING", new StateStatistics("MISSING", missingDataSeconds, 0));
        if (shiftDurationSeconds > 0) {
            BigDecimal missingRatio = BigDecimal.valueOf(missingDataSeconds)
                    .divide(BigDecimal.valueOf(shiftDurationSeconds), 4, RoundingMode.HALF_UP);
            statsMap.get("MISSING").ratio = missingRatio;
        }

        return statsMap;
    }

    /**
     * 保存或更新汇总记录（供补偿任务调用）
     */
    public void saveOrUpdateSummary(String tenantId, String deviceId, String orgFactoryId, LocalDate shiftDate,
                                    ShiftTimeRange shiftRange, Map<String, StateStatistics> stateStats) {
        
        DeviceStateSummaryDO summary = findExistingSummary(
                tenantId, deviceId, shiftDate, shiftRange.getShiftCode());

        if (summary == null) {
            summary = new DeviceStateSummaryDO();
            summary.setId(IdWorker.getIdStr());
            summary.setTenantUuid(tenantId);
            summary.setDeviceInfoId(deviceId);
            summary.setOrgFactoryId(orgFactoryId);
            summary.setSummaryDate(shiftDate);
            summary.setShiftCode(shiftRange.getShiftCode());
        } else {
            // 更新时也更新orgFactoryId（防止设备迁移到其他工厂）
            summary.setOrgFactoryId(orgFactoryId);
        }

        // 更新字段
        summary.setShiftStartTs(shiftRange.getStartTs() / 1000);
        summary.setShiftEndTs(shiftRange.getEndTs() / 1000);
        summary.setIsFinalized(true);
        summary.setCalculatedTime(System.currentTimeMillis() / 1000);
        summary.setCalculationSource(CALCULATION_SOURCE_SCHEDULED);

        // 设置状态统计
        StateStatistics working = stateStats.getOrDefault("WORKING", new StateStatistics("WORKING", 0, 0));
        StateStatistics standby = stateStats.getOrDefault("STANDBY", new StateStatistics("STANDBY", 0, 0));
        StateStatistics fault = stateStats.getOrDefault("FAULT", new StateStatistics("FAULT", 0, 0));
        StateStatistics shutdown = stateStats.getOrDefault("SHUTDOWN", new StateStatistics("SHUTDOWN", 0, 0));
        StateStatistics missing = stateStats.getOrDefault("MISSING", new StateStatistics("MISSING", 0, 0));

        summary.setWorkingDurationS((int) working.durationSeconds);
        summary.setStandbyDurationS((int) standby.durationSeconds);
        summary.setFaultDurationS((int) fault.durationSeconds);
        summary.setShutdownDurationS((int) shutdown.durationSeconds);
        summary.setMissingDataS((int) missing.durationSeconds);

        summary.setWorkingRatio(working.ratio);
        summary.setStandbyRatio(standby.ratio);
        summary.setFaultRatio(fault.ratio);
        summary.setShutdownRatio(shutdown.ratio);

        // 计算数据完整度
        long shiftDurationSeconds = (shiftRange.getEndTs() - shiftRange.getStartTs()) / 1000;
        if (shiftDurationSeconds > 0) {
            long totalRecordedDuration = working.durationSeconds + standby.durationSeconds 
                    + fault.durationSeconds + shutdown.durationSeconds;
            BigDecimal completeness = BigDecimal.valueOf(totalRecordedDuration)
                    .divide(BigDecimal.valueOf(shiftDurationSeconds), 4, RoundingMode.HALF_UP);
            summary.setDataCompleteness(completeness);
        }

        // 构建状态统计JSON
        Map<String, Object> stateStatisticsJson = new HashMap<>();
        for (Map.Entry<String, StateStatistics> entry : stateStats.entrySet()) {
            Map<String, Object> stateInfo = new HashMap<>();
            stateInfo.put("durationSeconds", entry.getValue().durationSeconds);
            stateInfo.put("ratio", entry.getValue().ratio);
            stateInfo.put("fragmentCount", entry.getValue().fragmentCount);
            stateStatisticsJson.put(entry.getKey(), stateInfo);
        }
        summary.setStateStatistics(stateStatisticsJson);

        // 保存或更新
        if (summary.getId() != null && findExistingSummary(tenantId, deviceId, shiftDate, shiftRange.getShiftCode()) != null) {
            stateSummaryMapper.updateById(summary);
        } else {
            stateSummaryMapper.insert(summary);
        }
    }

    /**
     * 使用检查点机制处理工厂设备（支持失败恢复）
     */
    private ProcessResult processFactoryWithCheckpoint(String tenantId, String factoryId,
                                                       List<DeviceBaseInfoDO> devices,
                                                       long statisticsTimeSeconds) {
        // 加载检查点
        CheckpointData checkpoint = loadCheckpoint(tenantId, factoryId, statisticsTimeSeconds);
        
        Set<String> processedDeviceIds = checkpoint != null 
                ? new HashSet<>(checkpoint.getProcessedDeviceIds())
                : new HashSet<>();
        
        // 过滤已处理的设备
        List<DeviceBaseInfoDO> remainingDevices = devices.stream()
                .filter(device -> !processedDeviceIds.contains(device.getId()))
                .collect(Collectors.toList());
        
        if (remainingDevices.isEmpty()) {
            XxlJobHelper.log("工厂 {} 所有设备已处理，清除检查点", factoryId);
            clearCheckpoint(tenantId, factoryId, statisticsTimeSeconds);
            return ProcessResult.completed(0, 0, 0);
        }
        
        if (checkpoint != null) {
            XxlJobHelper.log("从检查点恢复: 工厂={}, 已处理={}, 剩余={}", 
                    factoryId, processedDeviceIds.size(), remainingDevices.size());
        }
        
        // 分批处理剩余设备
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        List<String> newProcessedIds = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < remainingDevices.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remainingDevices.size());
            List<DeviceBaseInfoDO> batch = remainingDevices.subList(i, endIndex);
            
            for (DeviceBaseInfoDO device : batch) {
                try {
                    boolean processed = processDeviceShift(tenantId, device, statisticsTimeSeconds);
                    if (processed) {
                        successCount++;
                        newProcessedIds.add(device.getId());
                        processedDeviceIds.add(device.getId());
                    } else {
                        skipCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("处理设备失败: tenantId={}, factoryId={}, deviceId={}, deviceCode={}", 
                            tenantId, factoryId, device.getId(), device.getDeviceCode(), e);
                    XxlJobHelper.log("处理设备失败: factoryId={}, deviceCode={}, error={}", 
                            factoryId, device.getDeviceCode(), e.getMessage());
                    // 继续处理下一个设备，不中断
                }
            }
            
            // 每批处理完后更新检查点
            if (!newProcessedIds.isEmpty()) {
                List<String> allProcessedIds = new ArrayList<>(processedDeviceIds);
                saveCheckpoint(tenantId, factoryId, statisticsTimeSeconds, allProcessedIds);
                newProcessedIds.clear();
            }
            
            // 检查是否超时（预留5分钟）
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > 25 * 60 * 1000) { // 25分钟
                XxlJobHelper.log("处理超时，保存检查点: 工厂={}, 已处理={}, 剩余={}", 
                        factoryId, processedDeviceIds.size(), remainingDevices.size() - processedDeviceIds.size());
                return ProcessResult.incomplete(successCount, skipCount, errorCount);
            }
        }
        
        // 所有设备都处理完成，清除检查点
        if (processedDeviceIds.size() >= devices.size()) {
            clearCheckpoint(tenantId, factoryId, statisticsTimeSeconds);
            return ProcessResult.completed(successCount, skipCount, errorCount);
        } else {
            // 还有未处理的设备，保存检查点
            List<String> allProcessedIds = new ArrayList<>(processedDeviceIds);
            saveCheckpoint(tenantId, factoryId, statisticsTimeSeconds, allProcessedIds);
            return ProcessResult.incomplete(successCount, skipCount, errorCount);
        }
    }
    
    /**
     * 保存检查点
     */
    private void saveCheckpoint(String tenantId, String factoryId, 
                                long statisticsTimeSeconds, 
                                List<String> processedDeviceIds) {
        String key = buildCheckpointKey(tenantId, factoryId, statisticsTimeSeconds);
        
        CheckpointData checkpoint = new CheckpointData();
        checkpoint.setTenantId(tenantId);
        checkpoint.setFactoryId(factoryId);
        checkpoint.setStatisticsTimeSeconds(statisticsTimeSeconds);
        checkpoint.setProcessedDeviceIds(processedDeviceIds);
        checkpoint.setTotalDeviceCount(processedDeviceIds.size());
        checkpoint.setLastUpdateTime(System.currentTimeMillis() / 1000);
        
        try {
            redisClient.set(key, JsonUtils.toJsonString(checkpoint), 
                    CHECKPOINT_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("保存检查点: key={}, processedCount={}", key, processedDeviceIds.size());
        } catch (Exception e) {
            log.error("保存检查点失败: key={}", key, e);
        }
    }
    
    /**
     * 加载检查点
     */
    private CheckpointData loadCheckpoint(String tenantId, String factoryId, 
                                         long statisticsTimeSeconds) {
        String key = buildCheckpointKey(tenantId, factoryId, statisticsTimeSeconds);
        try {
            String value = redisClient.get(key);
            if (StringUtils.isBlank(value)) {
                return null;
            }
            return JsonUtils.parseObject(value, CheckpointData.class);
        } catch (Exception e) {
            log.error("加载检查点失败: key={}", key, e);
            return null;
        }
    }
    
    /**
     * 清除检查点
     */
    private void clearCheckpoint(String tenantId, String factoryId, 
                                long statisticsTimeSeconds) {
        String key = buildCheckpointKey(tenantId, factoryId, statisticsTimeSeconds);
        try {
            redisClient.delete(key);
            log.debug("清除检查点: key={}", key);
        } catch (Exception e) {
            log.error("清除检查点失败: key={}", key, e);
        }
    }
    
    /**
     * 构建检查点Key
     */
    private String buildCheckpointKey(String tenantId, String factoryId, long statisticsTimeSeconds) {
        return String.format("%s%s:%s:%d", CHECKPOINT_KEY_PREFIX, tenantId, factoryId, statisticsTimeSeconds);
    }
    
    /**
     * 检查点数据
     */
    @Data
    private static class CheckpointData {
        private String tenantId;
        private String factoryId;
        private long statisticsTimeSeconds;
        private List<String> processedDeviceIds;
        private int totalDeviceCount;
        private long lastUpdateTime;
    }
    
    /**
     * 处理结果
     */
    @Data
    private static class ProcessResult {
        private Optional<Integer> successCount;
        private int skipCount;
        private int errorCount;
        private boolean completed;
        
        public static ProcessResult completed(int successCount, int skipCount, int errorCount) {
            ProcessResult result = new ProcessResult();
            result.successCount = Optional.of(successCount);
            result.skipCount = skipCount;
            result.errorCount = errorCount;
            result.completed = true;
            return result;
        }
        
        public static ProcessResult incomplete(int successCount, int skipCount, int errorCount) {
            ProcessResult result = new ProcessResult();
            result.successCount = Optional.of(successCount);
            result.skipCount = skipCount;
            result.errorCount = errorCount;
            result.completed = false;
            return result;
        }
    }

    /**
     * 状态统计内部类（供补偿任务调用）
     */
    public static class StateStatistics {
        String stateCode;
        long durationSeconds;
        int fragmentCount;
        BigDecimal ratio = BigDecimal.ZERO;

        StateStatistics(String stateCode, long durationSeconds, int fragmentCount) {
            this.stateCode = stateCode;
            this.durationSeconds = durationSeconds;
            this.fragmentCount = fragmentCount;
        }
    }
}

