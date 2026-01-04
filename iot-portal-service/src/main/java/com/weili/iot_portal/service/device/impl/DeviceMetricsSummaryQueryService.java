package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceMetricSummaryRepository;
import com.weili.iot_portal.domain.device.req.DeviceMetricStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceMetricStatisticsRespVO;
import com.weili.iot_portal.domain.ingestion.RealtimeMetricSnapshot;
import com.weili.iot_portal.service.cache.DeviceMetricsCacheService;
import com.weili.iot_portal.service.device.IDeviceMetricsSummaryQueryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author luying
 * @className DeviceMetricsSummaryQueryService
 * @description
 * @date 2026-01-04 14:42
 **/
@Service
@Slf4j

public class DeviceMetricsSummaryQueryService implements IDeviceMetricsSummaryQueryService {
    @Resource
    private DeviceInfoRepository deviceInfoRepository;
    @Resource
    private DeviceMetricsCacheService deviceMetricsCacheService;
    @Resource
    private DeviceMetricSummaryRepository deviceMetricSummaryRepository;

    @Override
    public DeviceMetricStatisticsRespVO getDeviceMetricStatistics(DeviceMetricStatisticsReqVO queryReqVO) {
        DeviceMetricStatisticsRespVO respVO = new DeviceMetricStatisticsRespVO();
        Long deviceInfoId = queryReqVO.getDeviceInfoId();
        Optional<DeviceInfoDO> optional = deviceInfoRepository.findById(deviceInfoId);
        if (optional.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
        DeviceInfoDO deviceInfoDO = optional.get();

        // 1. 当前指标值：从缓存获取实时指标快照
        Optional<RealtimeMetricSnapshot> snapshotOptional = deviceMetricsCacheService.getDeviceRealtimeMetrics(deviceInfoDO.getOrgFactoryId(), deviceInfoId);
        RealtimeMetricSnapshot snapshot = snapshotOptional.orElse(RealtimeMetricSnapshot.empty());

        // 将实时指标快照转换为 MetricDetailVO
        DeviceMetricStatisticsRespVO.MetricDetailVO currentMetric = convertSnapshotToMetricDetail(snapshot);
        respVO.setCurrentMetricValue(currentMetric);

        // 2. 指标明细列表：根据是否传参决定查询范围
        LocalDate startShiftDate = queryReqVO.getStartTime();
        LocalDate endShiftDate = queryReqVO.getEndTime();
        if (startShiftDate == null || endShiftDate == null) {
            endShiftDate = LocalDate.now();
            startShiftDate = endShiftDate.minusDays(6); // 包含今天共7天
        }
        // 查询时间范围需要转换为时间戳（毫秒）
        long startTsMillis = startShiftDate.atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long endTsMillis = endShiftDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();

        // 从 device_metrics_summary 查询时间范围内的指标汇总数据
        List<DeviceMetricSummaryDO> metricsList = deviceMetricSummaryRepository.selectFinalizedInRange(
                deviceInfoId,
                startTsMillis,
                endTsMillis
        );

        // 按日期分组汇总（一天可能有多个班次，取平均值）
        Map<LocalDate, List<DeviceMetricSummaryDO>> dailyMetricsMap = new HashMap<>();
        if (metricsList != null && !metricsList.isEmpty()) {
            dailyMetricsMap = metricsList.stream()
                    .collect(Collectors.groupingBy(DeviceMetricSummaryDO::getShiftDate));
        }

        // 构建图表数据（按日期排序）
        // 重要：必须保持横坐标完整，即使某些日期没有数据也要返回（值为0），确保前端能正确渲染图表
        List<DeviceMetricStatisticsRespVO.MetricDetailVO> detailList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (LocalDate date = startShiftDate; !date.isAfter(endShiftDate); date = date.plusDays(1)) {
            DeviceMetricStatisticsRespVO.MetricDetailVO detail =
                    new DeviceMetricStatisticsRespVO.MetricDetailVO();

            // 设置日期标签（横坐标）：保证每个日期都有值
            detail.setDateLabel(date.format(formatter));

            List<DeviceMetricSummaryDO> dayMetrics = dailyMetricsMap.get(date);
            if (dayMetrics != null && !dayMetrics.isEmpty()) {
                // 计算当天各指标的平均值
                detail.setOee(calculateAverage(dayMetrics, DeviceMetricSummaryDO::getOee));
                detail.setAvailability(calculateAverage(dayMetrics, DeviceMetricSummaryDO::getAvailability));
                detail.setPerformance(calculateAverage(dayMetrics, DeviceMetricSummaryDO::getPerformance));
                detail.setUtilizationRate(calculateAverage(dayMetrics, DeviceMetricSummaryDO::getUtilizationRate));

                // 停机率 = 100 - 可用率
                BigDecimal availability = detail.getAvailability();
                if (availability != null) {
                    detail.setDowntimeRate(BigDecimal.valueOf(100).subtract(availability)
                            .setScale(1, RoundingMode.HALF_UP));
                } else {
                    detail.setDowntimeRate(BigDecimal.ZERO);
                }
            } else {
                // 没有数据的日期，设置为0
                detail.setOee(BigDecimal.ZERO);
                detail.setAvailability(BigDecimal.ZERO);
                detail.setPerformance(BigDecimal.ZERO);
                detail.setUtilizationRate(BigDecimal.ZERO);
                detail.setDowntimeRate(BigDecimal.ZERO);
            }

            detailList.add(detail);
        }

        respVO.setMetricDetails(detailList);

        return respVO;
    }

    /**
     * 计算指标的平均值，并转换为百分比形式（0-100）
     *
     * @param metrics 指标列表
     * @param getter  指标获取函数
     * @return 平均值（百分比形式），如果没有有效数据则返回 0
     */
    private BigDecimal calculateAverage(List<DeviceMetricSummaryDO> metrics,
                                        java.util.function.Function<DeviceMetricSummaryDO, BigDecimal> getter) {
        List<BigDecimal> values = metrics.stream()
                .map(getter)
                .filter(Objects::nonNull).toList();

        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算平均值，并转换为百分比形式（0-100）
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 将实时指标快照转换为 MetricDetailVO
     * <p>
     * 实时指标快照已经是百分比形式（0-1），需要转换为（0-100）
     *
     * @param snapshot 实时指标快照
     * @return MetricDetailVO
     */
    private DeviceMetricStatisticsRespVO.MetricDetailVO convertSnapshotToMetricDetail(RealtimeMetricSnapshot snapshot) {
        DeviceMetricStatisticsRespVO.MetricDetailVO detail = new DeviceMetricStatisticsRespVO.MetricDetailVO();
        // OEE（整体设备效率）：已经是 0-1 范围，转换为百分比 0-100
        detail.setOee(toPercentage(snapshot.getOee()));

        // 时间开动率（可用率）：availabilityRate -> availability
        detail.setAvailability(toPercentage(snapshot.getAvailabilityRate()));

        // 性能开动率（性能率）：performanceRate -> performance
        detail.setPerformance(toPercentage(snapshot.getPerformanceRate()));

        // 设备开动率（设备利用率）：uptimeRate -> utilizationRate
        detail.setUtilizationRate(toPercentage(snapshot.getUptimeRate()));

        // 停机率：faultRate -> downtimeRate
        // 注意：停机率也可以计算为 100 - 可用率，但这里直接使用 faultRate
        detail.setDowntimeRate(toPercentage(snapshot.getFaultRate()));

        return detail;
    }

    /**
     * 将 0-1 范围的比率转换为 0-100 的百分比
     *
     * @param rate 比率值（0-1）
     * @return 百分比值（0-100）
     */
    private BigDecimal toPercentage(BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        return rate.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
