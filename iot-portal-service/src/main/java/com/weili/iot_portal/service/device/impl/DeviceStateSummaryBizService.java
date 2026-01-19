package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftDefinition;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateSummaryRepository;
import com.weili.iot_portal.domain.device.req.DeviceStateSummaryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceStateSummaryRespVO;
import com.weili.iot_portal.domain.device.resp.StateRatioStatistics;
import com.weili.iot_portal.domain.device.resp.StateTimeSegment;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import com.weili.iot_portal.domain.ingestion.StateStatistics;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import com.weili.iot_portal.service.device.IDeviceInfoBizService;
import com.weili.iot_portal.service.device.IDeviceStateSummaryBizService;
import com.weili.iot_portal.service.device.IDeviceStateStatisticsService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.weili.iot_portal.service.device.util.DeviceLogContext.*;

/**
 * 设备状态汇总业务服务实现
 */
@Slf4j
@Service
public class DeviceStateSummaryBizService implements IDeviceStateSummaryBizService {

    @Resource
    private IDeviceInfoBizService deviceInfoBizService;
    @Resource
    private DeviceStateCacheService deviceStateCacheService;
    @Resource
    private DeviceStateSummaryRepository deviceStateSummaryRepository;
    @Resource
    private DeviceStateRecordRepository deviceStateRecordRepository;
    @Resource
    private IDeviceStateStatisticsService deviceStateStatisticsService;
    @Resource
    private IShiftConfigService shiftConfigService;
    @Resource
    private IShiftCalculationService shiftCalculationService;
    @Resource
    private DeviceShiftSummaryService deviceShiftSummaryService;

    @Override
    public DeviceStateSummaryRespVO getDeviceStateSummary(DeviceStateSummaryQueryReqVO queryReqVO) {
        // 0. 查询设备当前状态
        DeviceInfoDO deviceInfo = deviceInfoBizService.getDeviceInfo(queryReqVO.getDeviceId());
        if (deviceInfo == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND, "设备不存在");
        }
        
        // 设置设备编号到 MDC，使日志能够显示设备编号
        setDeviceCode(deviceInfo);
        
        try {
            String stateValue = deviceStateCacheService.getStateValue(deviceInfo.getOrgFactoryId(), deviceInfo.getId());
        String heartbeat = deviceStateCacheService.getHeartbeat(deviceInfo.getOrgFactoryId(), deviceInfo.getId());

        // 1. 获取当前时间对应的班次日期（考虑跨天班次）
        // 例如：2026-1-15 04:00 属于 2026-1-14 的第二班，则 currentShiftDate = 2026-1-14
        long currentTime = System.currentTimeMillis();
        LocalDate currentShiftDate = shiftCalculationService.getShiftDate(
                deviceInfo.getOrgFactoryId(), queryReqVO.getDeviceId(), currentTime);
        
        // 2. 处理查询日期范围
        LocalDate startShiftDate = queryReqVO.getStartTime();
        LocalDate endShiftDate = queryReqVO.getEndTime();
        
        // 如果没有传日期，默认使用当前班次日期（班次角度的"今天"）
        if (startShiftDate == null || endShiftDate == null) {
            startShiftDate = currentShiftDate;
            endShiftDate = currentShiftDate;
        }
        
        // 如果结束日期是未来，截断到当前班次日期
        if (endShiftDate.isAfter(currentShiftDate)) {
            endShiftDate = currentShiftDate;
        }
        
        // 3. 查询汇总数据（用于饼图）
        List<DeviceStateSummaryDO> summaryList = querySummaryData(
                queryReqVO.getDeviceId(),
                deviceInfo.getOrgFactoryId(),
                startShiftDate,
                endShiftDate,
                currentShiftDate
        );

        // 4. 查询状态记录数据（用于时间轴）
        List<DeviceStateRecordDO> stateRecordList = deviceStateRecordRepository.selectByShiftDateRange(
                queryReqVO.getDeviceId(),
                startShiftDate,
                endShiftDate
        );

        // 5. 计算查询的时间范围（用于截断 timelineData，与 ratioStatistics 保持一致）
        long queryStartTs = Long.MAX_VALUE;
        long queryEndTs = Long.MIN_VALUE;
        
        // 计算查询日期范围内所有班次的时间范围
        DeviceShiftConfigDO config = shiftConfigService.getCurrentConfiguration(
                deviceInfo.getOrgFactoryId(), queryReqVO.getDeviceId(), currentTime);
        if (config != null && config.getShifts() != null && !config.getShifts().isEmpty()) {
            for (LocalDate date = startShiftDate; !date.isAfter(endShiftDate); date = date.plusDays(1)) {
                for (DeviceShiftDefinition shiftDef : config.getShifts()) {
                    LocalTime startTime = LocalTime.parse(shiftDef.getStartTime(), DateTimeFormatter.ofPattern("HH:mm:ss"));
                    LocalDateTime shiftStartDateTime = date.atTime(startTime);
                    long referenceTimestamp = shiftStartDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    
                    java.util.Optional<ShiftTimeRange> shiftRangeOpt = shiftCalculationService.calculateAndValidateShiftRange(
                            deviceInfo.getOrgFactoryId(), queryReqVO.getDeviceId(), referenceTimestamp, date, shiftDef.getCode());
                    
                    if (shiftRangeOpt.isPresent()) {
                        ShiftTimeRange shiftRange = shiftRangeOpt.get();
                        if (shiftRange.getStartTs() != null && shiftRange.getStartTs() < queryStartTs) {
                            queryStartTs = shiftRange.getStartTs();
                        }
                        long shiftEnd = shiftRange.getEndTs() != null && shiftRange.getEndTs() > currentTime 
                                ? currentTime : (shiftRange.getEndTs() != null ? shiftRange.getEndTs() : currentTime);
                        if (shiftEnd > queryEndTs) {
                            queryEndTs = shiftEnd;
                        }
                    }
                }
            }
        }
        
        // 如果无法计算时间范围，使用记录的最小和最大时间戳
        if (queryStartTs == Long.MAX_VALUE || queryEndTs == Long.MIN_VALUE) {
            if (!stateRecordList.isEmpty()) {
                queryStartTs = stateRecordList.stream()
                        .mapToLong(r -> r.getStartTs() != null ? r.getStartTs() : Long.MAX_VALUE)
                        .min().orElse(Long.MAX_VALUE);
                queryEndTs = Math.max(
                        stateRecordList.stream()
                                .mapToLong(r -> r.getEndTs() != null ? r.getEndTs() : currentTime)
                                .max().orElse(Long.MIN_VALUE),
                        currentTime);
            } else {
                queryStartTs = currentTime;
                queryEndTs = currentTime;
            }
        }

            return DeviceStateSummaryRespVO.builder()
                    .currentState(stateValue)
                    .currentHeart("1".equals(heartbeat))
                    .ratioStatistics(buildRatioStatistics(summaryList, stateRecordList, queryStartTs, queryEndTs, currentTime))
                    .timelineData(buildTimelineData(stateRecordList, queryStartTs, queryEndTs))
                    .build();
        } finally {
            // 清除设备编号 MDC，避免线程复用导致设备编号污染
            clearDeviceCode();
        }
    }

    /**
     * 构建状态占比统计（饼图数据）
     * <p>
     * 重要：需要统计未结束的状态，与 timelineData 保持一致
     * </p>
     * 
     * @param summaryList 汇总数据列表（已结束的班次）
     * @param stateRecordList 状态记录列表（包含未结束的状态）
     * @param queryStartTs 查询开始时间戳（毫秒）
     * @param queryEndTs 查询结束时间戳（毫秒）
     * @param currentTime 当前时间戳（毫秒）
     * @return 状态占比统计
     */
    private StateRatioStatistics buildRatioStatistics(List<DeviceStateSummaryDO> summaryList,
                                                      List<DeviceStateRecordDO> stateRecordList,
                                                      long queryStartTs, long queryEndTs, long currentTime) {
        // 汇总各状态的时长（毫秒）
        long totalStandbyMillis = 0;
        long totalWorkingMillis = 0;
        long totalShutdownMillis = 0;
        long totalFaultMillis = 0;
        long totalUnknownMillis = 0;

        // 1. 累加已结束班次的汇总数据（注意：DeviceStateSummaryDO 的 *DurationS 字段实际存储的是毫秒）
        for (DeviceStateSummaryDO summary : summaryList) {
            // 获取汇总数据的时间范围
            Long shiftStartTs = summary.getShiftStartTs();
            Long shiftEndTs = summary.getShiftEndTs();
            
            // 如果汇总数据的时间范围与查询范围有交集，累加该汇总数据
            if (shiftStartTs != null && shiftEndTs != null) {
                long effectiveStart = Math.max(shiftStartTs, queryStartTs);
                long effectiveEnd = Math.min(shiftEndTs, queryEndTs);
                
                if (effectiveEnd > effectiveStart) {
                    // 计算在查询范围内的比例
                    long shiftDurationMillis = shiftEndTs - shiftStartTs;
                    if (shiftDurationMillis > 0) {
                        double ratio = (double)(effectiveEnd - effectiveStart) / shiftDurationMillis;
                        
                        long standbyMillis = summary.getStandbyDurationS() != null ? summary.getStandbyDurationS() : 0;
                        long workingMillis = summary.getWorkingDurationS() != null ? summary.getWorkingDurationS() : 0;
                        long shutdownMillis = summary.getShutdownDurationS() != null ? summary.getShutdownDurationS() : 0;
                        long faultMillis = summary.getFaultDurationS() != null ? summary.getFaultDurationS() : 0;
                        long unknownMillis = summary.getUnknownDurationS() != null ? summary.getUnknownDurationS() : 0;
                        
                        totalStandbyMillis += Math.round(standbyMillis * ratio);
                        totalWorkingMillis += Math.round(workingMillis * ratio);
                        totalShutdownMillis += Math.round(shutdownMillis * ratio);
                        totalFaultMillis += Math.round(faultMillis * ratio);
                        totalUnknownMillis += Math.round(unknownMillis * ratio);
                    }
                }
            }
        }
        
        // 2. 统计未结束的状态（与 buildTimelineData 逻辑一致）
        if (stateRecordList != null && !stateRecordList.isEmpty()) {
            // 分离已结束和未结束的状态记录
            List<DeviceStateRecordDO> ongoingRecords = stateRecordList.stream()
                    .filter(r -> r.getEndTs() == null)
                    .collect(Collectors.toList());
            
            // 如果有多条未结束的状态，只保留最新的一条（startTs最大的）
            DeviceStateRecordDO latestOngoingRecord = null;
            if (!ongoingRecords.isEmpty()) {
                latestOngoingRecord = ongoingRecords.stream()
                        .max(Comparator.comparingLong(DeviceStateRecordDO::getStartTs))
                        .orElse(null);
            }
            
            // 统计未结束的状态在查询时间范围内的时长
            if (latestOngoingRecord != null) {
                long recordStartTs = latestOngoingRecord.getStartTs() != null 
                        ? latestOngoingRecord.getStartTs() : queryStartTs;
                // 进行中状态使用当前时间作为结束时间，但不能超过查询结束时间
                long recordEndTs = Math.min(currentTime, queryEndTs);
                
                // 取交集
                long effectiveStart = Math.max(recordStartTs, queryStartTs);
                long effectiveEnd = Math.min(recordEndTs, queryEndTs);
                
                // 如果记录与查询范围有交集，累加该状态的时长
                if (effectiveEnd > effectiveStart) {
                    long durationMillis = effectiveEnd - effectiveStart;
                    DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(latestOngoingRecord.getStateCode());
                    String stateName = stateEnum.name();
                    
                    switch (stateName) {
                        case "STANDBY":
                            totalStandbyMillis += durationMillis;
                            break;
                        case "WORKING":
                            totalWorkingMillis += durationMillis;
                            break;
                        case "SHUTDOWN":
                            totalShutdownMillis += durationMillis;
                            break;
                        case "FAULT":
                            totalFaultMillis += durationMillis;
                            break;
                        case "UNKNOWN":
                            totalUnknownMillis += durationMillis;
                            break;
                    }
                }
            }
        }
        
        // 转换为秒（与 timelineData 的 durationS 单位保持一致）
        Long totalStandby = totalStandbyMillis / 1000;
        Long totalWorking = totalWorkingMillis / 1000;
        Long totalShutdown = totalShutdownMillis / 1000;
        Long totalFault = totalFaultMillis / 1000;
        Long totalUnknown = totalUnknownMillis / 1000;

        // 计算总时长（秒）
        long totalDuration = totalStandby + totalWorking + totalShutdown + totalFault + totalUnknown;

        // 计算百分比
        BigDecimal standbyRatio;
        BigDecimal workingRatio;
        BigDecimal shutdownRatio;
        BigDecimal faultRatio;
        BigDecimal unknownRatio;

        if (totalDuration == 0) {
            // 总时长为0，所有占比都为0
            standbyRatio = BigDecimal.ZERO;
            workingRatio = BigDecimal.ZERO;
            shutdownRatio = BigDecimal.ZERO;
            faultRatio = BigDecimal.ZERO;
            unknownRatio = BigDecimal.ZERO;
        } else {
            // 计算各状态的百分比，保留1位小数
            standbyRatio = BigDecimal.valueOf(totalStandby)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalDuration), 1, RoundingMode.HALF_UP);

            workingRatio = BigDecimal.valueOf(totalWorking)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalDuration), 1, RoundingMode.HALF_UP);

            shutdownRatio = BigDecimal.valueOf(totalShutdown)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalDuration), 1, RoundingMode.HALF_UP);

            faultRatio = BigDecimal.valueOf(totalFault)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalDuration), 1, RoundingMode.HALF_UP);

            unknownRatio = BigDecimal.valueOf(totalUnknown)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalDuration), 1, RoundingMode.HALF_UP);

            // 确保总和为100%
            BigDecimal sum = standbyRatio.add(workingRatio).add(shutdownRatio).add(faultRatio).add(unknownRatio);
            BigDecimal diff = BigDecimal.valueOf(100).subtract(sum);

            // 如果总和不是100，将差值加到最大的那个百分比上
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                // 找出最大的占比
                if (totalStandby >= totalWorking && totalStandby >= totalShutdown && totalStandby >= totalFault && totalStandby >= totalUnknown) {
                    standbyRatio = standbyRatio.add(diff);
                } else if (totalWorking >= totalShutdown && totalWorking >= totalFault && totalWorking >= totalUnknown) {
                    workingRatio = workingRatio.add(diff);
                } else if (totalShutdown >= totalFault && totalShutdown >= totalUnknown) {
                    shutdownRatio = shutdownRatio.add(diff);
                } else if (totalFault >= totalUnknown) {
                    faultRatio = faultRatio.add(diff);
                } else {
                    unknownRatio = unknownRatio.add(diff);
                }
            }
        }

        return StateRatioStatistics.builder()
                .standbyDur(totalStandby)
                .workingDur(totalWorking)
                .shutdownDur(totalShutdown)
                .faultDur(totalFault)
                .unknownDur(totalUnknown)
                .standbyRatio(standbyRatio)
                .workingRatio(workingRatio)
                .shutdownRatio(shutdownRatio)
                .faultRatio(faultRatio)
                .unknownRatio(unknownRatio)
                .build();
    }

    /**
     * 构建时间轴数据（甘特图）
     * 注意：必须保证按时间顺序返回，确保前端甘特图正确渲染
     * <p>
     * 对于未结束的状态（endTs == null），只显示最新的一条（startTs最大的），使用当前时间作为结束时间
     * 历史的未结束状态会被过滤掉，避免显示错误的历史数据
     * </p>
     * <p>
     * 重要：对记录进行时间范围截断，与 ratioStatistics 保持一致
     * 即使记录已经按班次拆分，但可能包含查询范围之外的时间，需要截断到查询时间范围内
     * </p>
     * 
     * @param stateRecordList 状态记录列表
     * @param queryStartTs 查询开始时间戳（毫秒）
     * @param queryEndTs 查询结束时间戳（毫秒）
     * @return 时间轴数据列表
     */
    private List<StateTimeSegment> buildTimelineData(List<DeviceStateRecordDO> stateRecordList, 
                                                      long queryStartTs, long queryEndTs) {
        if (stateRecordList == null || stateRecordList.isEmpty()) {
            return new ArrayList<>();
        }

        // 分离已结束和未结束的状态记录
        List<DeviceStateRecordDO> endedRecords = new ArrayList<>();
        List<DeviceStateRecordDO> ongoingRecords = new ArrayList<>();
        
        for (DeviceStateRecordDO record : stateRecordList) {
            if (record.getEndTs() == null) {
                ongoingRecords.add(record);
            } else {
                endedRecords.add(record);
            }
        }
        
        // 如果有多条未结束的状态，只保留最新的一条（startTs最大的）
        // 正常情况下应该只有一条未结束的状态，但可能存在历史遗留数据
        DeviceStateRecordDO latestOngoingRecord = null;
        if (!ongoingRecords.isEmpty()) {
            latestOngoingRecord = ongoingRecords.stream()
                    .max(Comparator.comparingLong(DeviceStateRecordDO::getStartTs))
                    .orElse(null);
        }

        List<StateTimeSegment> timelineData = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // 处理已结束的状态记录
        for (DeviceStateRecordDO record : endedRecords) {
            // 计算记录在查询时间范围内的有效时间范围（与 calculateStatistics 逻辑一致）
            long recordStartTs = record.getStartTs() != null ? record.getStartTs() : queryStartTs;
            long recordEndTs = record.getEndTs() != null ? record.getEndTs() : queryEndTs;
            
            // 取交集（与 calculateStatistics 第56-57行逻辑一致）
            long effectiveStart = Math.max(recordStartTs, queryStartTs);
            long effectiveEnd = Math.min(recordEndTs, queryEndTs);
            
            // 如果记录与查询范围没有交集，跳过
            if (recordStartTs >= queryEndTs || effectiveEnd <= queryStartTs) {
                continue;
            }
            
            DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(record.getStateCode());
            // 计算持续时间（秒）：(有效结束时间 - 有效开始时间) / 1000
            long durationS = (effectiveEnd - effectiveStart) / 1000;
            StateTimeSegment segment = StateTimeSegment.builder()
                    .stateCode(stateEnum.name())
                    .stateName(stateEnum.getDescription())
                    .startTime(effectiveStart)
                    .endTime(effectiveEnd)
                    .durationS(durationS)
                    .build();
            timelineData.add(segment);
        }

        // 处理最新的未结束状态（如果存在）
        if (latestOngoingRecord != null) {
            // 计算记录在查询时间范围内的有效时间范围
            long recordStartTs = latestOngoingRecord.getStartTs() != null 
                    ? latestOngoingRecord.getStartTs() : queryStartTs;
            // 进行中状态使用当前时间作为结束时间，但不能超过查询结束时间
            long recordEndTs = Math.min(currentTime, queryEndTs);
            
            // 取交集
            long effectiveStart = Math.max(recordStartTs, queryStartTs);
            long effectiveEnd = Math.min(recordEndTs, queryEndTs);
            
            // 如果记录与查询范围没有交集，跳过
            if (recordStartTs >= queryEndTs || effectiveEnd <= queryStartTs) {
                // 不添加，跳过
            } else {
                DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(latestOngoingRecord.getStateCode());
                // 计算持续时间（秒）：(有效结束时间 - 有效开始时间) / 1000
                long durationS = (effectiveEnd - effectiveStart) / 1000;
                StateTimeSegment segment = StateTimeSegment.builder()
                        .stateCode(stateEnum.name())
                        .stateName(stateEnum.getDescription())
                        .startTime(effectiveStart)
                        .endTime(effectiveEnd)
                        .durationS(durationS)
                        .build();
                timelineData.add(segment);
            }
        }

        // 额外排序保障：确保按开始时间升序，即使数据库查询未正确排序
        timelineData.sort(Comparator.comparingLong(StateTimeSegment::getStartTime));

        return timelineData;
    }

    /**
     * 查询汇总数据
     * <p>
     * 逻辑：
     * 1. 如果查询范围只包含历史日期（早于当前班次日期）：从 device_state_summary 表查询
     * 2. 如果查询范围包含当前班次日期：
     *    - 历史部分：从 device_state_summary 表查询（当前班次日期之前）
     *    - 当前班次日期：从 device_state_record 表实时统计（包括未结束的状态）
     * 3. 确保不重复：历史数据排除当前班次日期的数据
     * </p>
     * 
     * @param deviceId 设备ID
     * @param factoryId 工厂ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param currentShiftDate 当前时间对应的班次日期（班次角度的"今天"）
     * @return 汇总数据列表
     */
    private List<DeviceStateSummaryDO> querySummaryData(
            Long deviceId, Long factoryId, LocalDate startDate, LocalDate endDate, LocalDate currentShiftDate) {
        
        List<DeviceStateSummaryDO> summaryList = new ArrayList<>();
        
        // 判断查询范围是否包含当前班次日期
        boolean includesCurrentShiftDate = !startDate.isAfter(currentShiftDate) && !endDate.isBefore(currentShiftDate);
        
        if (includesCurrentShiftDate) {
            // 情况1：查询范围包含当前班次日期
            
            // 1.1 查询历史数据（当前班次日期之前的数据，从 device_state_summary 表查询）
            LocalDate historyEndDate = currentShiftDate.minusDays(1);
            if (!startDate.isAfter(historyEndDate)) {
                List<DeviceStateSummaryDO> historySummaries = deviceStateSummaryRepository.selectByShiftDateRange(
                        deviceId, startDate, historyEndDate);
                summaryList.addAll(historySummaries);
            }
            
            // 1.2 实时统计当前班次日期的数据（从 device_state_record 表查询，包括未结束的状态）
            List<DeviceStateSummaryDO> currentShiftDateSummaries = calculateShiftDateSummary(deviceId, factoryId, currentShiftDate);
            summaryList.addAll(currentShiftDateSummaries);
            
        } else {
            // 情况2：查询范围只包含历史日期，直接从 device_state_summary 表查询
            summaryList = deviceStateSummaryRepository.selectByShiftDateRange(deviceId, startDate, endDate);
        }
        
        return summaryList;
    }

    /**
     * 计算指定班次日期的汇总数据（从 device_state_record 实时统计，按日期聚合，不区分班次）
     * <p>
     * 逻辑：
     * 1. 查询该班次日期的所有状态记录（不区分班次）
     * 2. 计算时间范围：从该日期第一个班次的开始时间到当前时间
     * 3. 统计所有状态记录，按日期聚合
     * </p>
     * 
     * @param deviceId 设备ID
     * @param factoryId 工厂ID
     * @param shiftDate 班次日期
     * @return 汇总数据列表（按日期聚合，不区分班次）
     */
    private List<DeviceStateSummaryDO> calculateShiftDateSummary(Long deviceId, Long factoryId, LocalDate shiftDate) {
        List<DeviceStateSummaryDO> summaries = new ArrayList<>();
        
        long currentTime = System.currentTimeMillis();
        
        // 1. 查询该班次日期的所有状态记录（不区分班次）
        List<DeviceStateRecordDO> stateRecords = deviceStateRecordRepository.selectByShiftDateRange(
                deviceId, shiftDate, shiftDate);
        
        if (stateRecords == null || stateRecords.isEmpty()) {
            log.debug("该班次日期无状态记录: deviceId={}, shiftDate={}", deviceId, shiftDate);
            return summaries;
        }
        
        // 2. 计算该日期的时间范围
        // 获取该日期第一个班次的开始时间和最后一个班次的结束时间
        DeviceShiftConfigDO config = shiftConfigService.getCurrentConfiguration(factoryId, deviceId, currentTime);
        if (config == null || config.getShifts() == null || config.getShifts().isEmpty()) {
            log.warn("无法获取班次配置，跳过该班次日期的数据统计: deviceId={}, factoryId={}, shiftDate={}", 
                    deviceId, factoryId, shiftDate);
            return summaries;
        }
        
        // 计算该日期所有班次的时间范围
        long dateStartTs = Long.MAX_VALUE;
        long dateEndTs = Long.MIN_VALUE;
        
        for (DeviceShiftDefinition shiftDef : config.getShifts()) {
            // 使用班次开始时间作为参考时间点，确保能正确找到该班次
            // 对于跨天班次（如 20:00-次日08:00），使用开始时间（20:00）作为参考时间点
            // 这样可以确保 findShiftByTime 能正确找到该班次，而不是返回默认的第一个班次
            LocalTime startTime = LocalTime.parse(shiftDef.getStartTime(), DateTimeFormatter.ofPattern("HH:mm:ss"));
            LocalDateTime shiftStartDateTime = shiftDate.atTime(startTime);
            long referenceTimestamp = shiftStartDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            java.util.Optional<ShiftTimeRange> shiftRangeOpt = shiftCalculationService.calculateAndValidateShiftRange(
                    factoryId, deviceId, referenceTimestamp, shiftDate, shiftDef.getCode());
            
            if (shiftRangeOpt.isPresent()) {
                ShiftTimeRange shiftRange = shiftRangeOpt.get();
                if (shiftRange.getStartTs() != null && shiftRange.getStartTs() < dateStartTs) {
                    dateStartTs = shiftRange.getStartTs();
                }
                // 对于进行中的班次，使用当前时间作为结束时间
                long shiftEnd = shiftRange.getEndTs() != null && shiftRange.getEndTs() > currentTime 
                        ? currentTime : (shiftRange.getEndTs() != null ? shiftRange.getEndTs() : currentTime);
                if (shiftEnd > dateEndTs) {
                    dateEndTs = shiftEnd;
                }
            }
        }
        
        if (dateStartTs == Long.MAX_VALUE || dateEndTs == Long.MIN_VALUE) {
            log.warn("无法计算该班次日期的时间范围: deviceId={}, shiftDate={}", deviceId, shiftDate);
            return summaries;
        }
        
        // 3. 统计状态数据（按日期聚合，不区分班次）
        Map<String, StateStatistics> stateStats = deviceStateStatisticsService.calculateStatistics(
                stateRecords, dateStartTs, dateEndTs);
        
        // 4. 构建汇总记录（使用第一个班次的信息作为代表，但数据是聚合的）
        DeviceShiftDefinition firstShift = config.getShifts().get(0);
        ShiftTimeRange dateRange = ShiftTimeRange.builder()
                .shiftCode(firstShift.getCode())
                .shiftName(firstShift.getName())
                .shiftDate(shiftDate)
                .startTs(dateStartTs)
                .endTs(dateEndTs)
                .durationMs(dateEndTs - dateStartTs)
                .build();
        
        DeviceStateSummaryDO summary = buildSummaryFromStatistics(
                deviceId, factoryId, shiftDate, firstShift.getCode(), dateRange, stateStats);
        summaries.add(summary);
        
        log.debug("按日期聚合统计完成: deviceId={}, shiftDate={}, startTs={}, endTs={}, 记录数={}",
                deviceId, shiftDate, dateStartTs, dateEndTs, stateRecords.size());
        
        return summaries;
    }


    /**
     * 从统计数据构建汇总记录
     * <p>
     * 直接复用 DeviceShiftSummaryService.populateStateStatisticsFields 方法
     * 确保与定时任务计算的汇总数据格式完全一致
     * </p>
     */
    private DeviceStateSummaryDO buildSummaryFromStatistics(
            Long deviceId, Long factoryId, LocalDate date, Integer shiftCode,
            ShiftTimeRange shiftRange, Map<String, StateStatistics> stateStats) {
        
        // 创建汇总记录并设置基础字段
        DeviceStateSummaryDO summary = new DeviceStateSummaryDO();
        summary.setDeviceInfoId(deviceId);
        summary.setOrgFactoryId(factoryId);
        summary.setSummaryDate(date);
        summary.setShiftCode(shiftCode);
        summary.setShiftStartTs(shiftRange.getStartTs());
        summary.setShiftEndTs(shiftRange.getEndTs());
        
        // 直接调用 DeviceShiftSummaryService 的方法填充状态统计字段
        // 这样可以确保逻辑完全一致，避免重复代码
        deviceShiftSummaryService.populateStateStatisticsFields(summary, stateStats);
        
        return summary;
    }
}
