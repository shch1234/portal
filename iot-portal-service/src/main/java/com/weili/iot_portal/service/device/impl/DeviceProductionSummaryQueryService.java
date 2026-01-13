package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionSummaryRepository;
import com.weili.iot_portal.domain.device.req.DeviceProductionStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceProductionStatisticsRespVO;
import com.weili.iot_portal.service.device.IDeviceProductionSummaryQueryService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author luying
 * @className DeviceProductionSummaryQueryService
 * @description
 * @date 2026-01-04 14:45
 **/
@Service
@Slf4j
public class DeviceProductionSummaryQueryService implements IDeviceProductionSummaryQueryService {
    @Resource
    private DeviceInfoRepository deviceInfoRepository;
    @Resource
    private DeviceProductionRecordRepository productionRecordRepository;
    @Resource
    private DeviceProductionSummaryRepository productionSummaryRepository;
    @Resource
    private IShiftCalculationService shiftCalculationService;


    @Override
    public DeviceProductionStatisticsRespVO getDeviceProductionStatistics(DeviceProductionStatisticsReqVO reqVO) {
        DeviceProductionStatisticsRespVO respVO = new DeviceProductionStatisticsRespVO();

        Long deviceInfoId = reqVO.getDeviceInfoId();
        Optional<DeviceInfoDO> optional = deviceInfoRepository.findById(deviceInfoId);
        if (optional.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
        DeviceInfoDO deviceInfo = optional.get();
        
        // 1. 当日加工数：根据当前时间计算班次日期（考虑跨天班次的情况）
        long currentTimestamp = System.currentTimeMillis();
        LocalDate currentShiftDate = shiftCalculationService.getShiftDate(
                deviceInfo.getOrgFactoryId(),
                deviceInfoId,
                currentTimestamp
        );
        
        long currentShiftCount = productionRecordRepository.countByDate(
                deviceInfoId,
                currentShiftDate
        );
        respVO.setTodayProductionCount((int) currentShiftCount);

        // 2. 产量明细列表
        LocalDate startShiftDate = reqVO.getStartTime();
        LocalDate endShiftDate = reqVO.getEndTime();
        if (startShiftDate == null || endShiftDate == null) {
            endShiftDate = LocalDate.now();
            startShiftDate = endShiftDate.minusDays(6); // 包含今天共7天
        }
        // 从 device_production_summary 查询日期范围内的汇总数据
        List<DeviceProductionSummaryDO> summaryList = productionSummaryRepository.findByShiftDateRange(
                deviceInfoId,
                startShiftDate,
                endShiftDate
        );

        // 按日期分组汇总（一天可能有多个班次）
        Map<LocalDate, Integer> dailyProductionMap = new HashMap<>();
        if (summaryList != null && !summaryList.isEmpty()) {
            dailyProductionMap = summaryList.stream()
                    .collect(Collectors.groupingBy(
                            DeviceProductionSummaryDO::getShiftDate,
                            Collectors.summingInt(s -> s.getPartCount() != null ? s.getPartCount() : 0)
                    ));
        }

        // 构建图表数据（按日期排序）
        // 重要：必须保持横坐标完整，即使某些日期没有数据也要返回（值为0），确保前端能正确渲染图表
        List<DeviceProductionStatisticsRespVO.ProductionDetailVO> detailList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (LocalDate date = startShiftDate; !date.isAfter(endShiftDate); date = date.plusDays(1)) {
            DeviceProductionStatisticsRespVO.ProductionDetailVO detail =
                    new DeviceProductionStatisticsRespVO.ProductionDetailVO();

            // 设置日期标签（横坐标）：保证每个日期都有值
            detail.setDateLabel(date.format(formatter));

            // 根据横坐标匹配查询到的数据，不存在则设置为0
            detail.setProductionCount(dailyProductionMap.getOrDefault(date, 0));
            detailList.add(detail);
        }

        respVO.setProductionDetails(detailList);

        return respVO;
    }
}
