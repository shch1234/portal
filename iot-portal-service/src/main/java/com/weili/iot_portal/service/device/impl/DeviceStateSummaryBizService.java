package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateSummaryRepository;
import com.weili.iot_portal.domain.device.req.DeviceStateSummaryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceStateSummaryRespVO;
import com.weili.iot_portal.domain.device.resp.StateRatioStatistics;
import com.weili.iot_portal.domain.device.resp.StateStatItem;
import com.weili.iot_portal.domain.device.resp.StateTimeSegment;
import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import com.weili.iot_portal.service.device.IDeviceInfoBizService;
import com.weili.iot_portal.service.device.IDeviceStateSummaryBizService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备状态汇总业务服务实现
 */
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
    private IShiftCalculationService shiftCalculationService;


    @Override
    public DeviceStateSummaryRespVO getDeviceStateSummary(DeviceStateSummaryQueryReqVO queryReqVO) {
        // 0. 查询设备当前状态
        DeviceInfoDO deviceInfo = deviceInfoBizService.getDeviceInfo(queryReqVO.getDeviceId());
        if (deviceInfo == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND, "设备不存在");
        }
        String stateValue = deviceStateCacheService.getStateValue(deviceInfo.getOrgFactoryId(), deviceInfo.getId());
        String heartbeat = deviceStateCacheService.getHeartbeat(deviceInfo.getOrgFactoryId(), deviceInfo.getId());

        LocalDate startShiftDate;
        LocalDate endShiftDate = null;
        if (queryReqVO.getStartTime() == null || queryReqVO.getEndTime() == null) {
            ShiftDateAndCode currentShiftRange = shiftCalculationService.getShiftDateAndCode(
                    deviceInfo.getOrgFactoryId(),
                    deviceInfo.getId(),
                    System.currentTimeMillis()
            );
            startShiftDate = currentShiftRange.shiftDate();
        } else {
            ShiftDateAndCode startShiftInfo = shiftCalculationService.getShiftDateAndCode(
                    deviceInfo.getOrgFactoryId(),
                    deviceInfo.getId(),
                    queryReqVO.getStartTime()
            );

            ShiftDateAndCode endShiftInfo = shiftCalculationService.getShiftDateAndCode(
                    deviceInfo.getOrgFactoryId(),
                    deviceInfo.getId(),
                    queryReqVO.getEndTime()
            );
            startShiftDate = startShiftInfo.shiftDate();
            endShiftDate = endShiftInfo.shiftDate();
        }

        // 2. 使用班次日期范围查询汇总数据（用于饼图）
        List<DeviceStateSummaryDO> summaryList = deviceStateSummaryRepository.selectByShiftDateRange(
                queryReqVO.getDeviceId(),
                startShiftDate,
                endShiftDate
        );

        // 3. 查询状态记录数据（用于时间轴）
        List<DeviceStateRecordDO> stateRecordList = deviceStateRecordRepository.selectByShiftDateRange(
                queryReqVO.getDeviceId(),
                startShiftDate,
                endShiftDate
        );

        return DeviceStateSummaryRespVO.builder()
                .currentState(stateValue)
                .currentHeart(StringUtils.isNotBlank(heartbeat))
                .ratioStatistics(buildRatioStatistics(summaryList))
                .timelineData(buildTimelineData(stateRecordList))
                .build();
    }

    /**
     * 构建状态占比统计（饼图数据）
     */
    private StateRatioStatistics buildRatioStatistics(List<DeviceStateSummaryDO> summaryList) {
        // 汇总各状态的时长
        int totalStandby = 0;
        int totalWorking = 0;
        int totalShutdown = 0;
        int totalFault = 0;

        for (DeviceStateSummaryDO summary : summaryList) {
            totalStandby += (summary.getStandbyDurationS() != null ? summary.getStandbyDurationS() : 0);
            totalWorking += (summary.getWorkingDurationS() != null ? summary.getWorkingDurationS() : 0);
            totalShutdown += (summary.getShutdownDurationS() != null ? summary.getShutdownDurationS() : 0);
            totalFault += (summary.getFaultDurationS() != null ? summary.getFaultDurationS() : 0);
        }

        int totalDuration = totalStandby + totalWorking + totalShutdown + totalFault;

        // 计算占比（保留4位小数）
        BigDecimal standbyRatio = calculateRatio(totalStandby, totalDuration);
        BigDecimal workingRatio = calculateRatio(totalWorking, totalDuration);
        BigDecimal shutdownRatio = calculateRatio(totalShutdown, totalDuration);
        BigDecimal faultRatio = calculateRatio(totalFault, totalDuration);

        return StateRatioStatistics.builder()
                .standby(buildStateStatItem(DeviceStateEnum.STANDBY, totalStandby, standbyRatio))
                .working(buildStateStatItem(DeviceStateEnum.WORKING, totalWorking, workingRatio))
                .shutdown(buildStateStatItem(DeviceStateEnum.SHUTDOWN, totalShutdown, shutdownRatio))
                .fault(buildStateStatItem(DeviceStateEnum.FAULT, totalFault, faultRatio))
                .totalDuration(totalDuration)
                .build();
    }

    /**
     * 构建状态统计项
     */
    private StateStatItem buildStateStatItem(DeviceStateEnum stateEnum, int duration, BigDecimal ratio) {
        return StateStatItem.builder()
                .stateName(stateEnum.getDescription())
                .stateCode(stateEnum.name())
                .duration(duration)
                .ratio(ratio)
                .build();
    }

    /**
     * 构建时间轴数据（甘特图）
     * 注意：必须保证按时间顺序返回，确保前端甘特图正确渲染
     */
    private List<StateTimeSegment> buildTimelineData(List<DeviceStateRecordDO> stateRecordList) {
        if (stateRecordList == null || stateRecordList.isEmpty()) {
            return new ArrayList<>();
        }

        List<StateTimeSegment> timelineData = new ArrayList<>();

        for (DeviceStateRecordDO record : stateRecordList) {
            // 跳过没有结束时间的记录（进行中的状态）
            if (record.getEndTs() == null) {
                continue;
            }

            // 从编码转换为枚举
            DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(record.getStateCode());

            StateTimeSegment segment = StateTimeSegment.builder()
                    .stateCode(stateEnum.name())
                    .stateName(stateEnum.getDescription())
                    .startTime(record.getStartTs())
                    .endTime(record.getEndTs())
                    .build();

            timelineData.add(segment);
        }

        // 额外排序保障：确保按开始时间升序，即使数据库查询未正确排序
        timelineData.sort((o1, o2) -> Long.compare(o1.getStartTime(), o2.getStartTime()));

        return timelineData;
    }

    /**
     * 计算占比
     */
    private BigDecimal calculateRatio(int duration, int totalDuration) {
        if (totalDuration == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(duration)
                .divide(BigDecimal.valueOf(totalDuration), 4, RoundingMode.HALF_UP);
    }
}
