package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceMetricSummaryRepository;
import com.weili.iot_portal.domain.device.req.MetricStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.MetricStatisticsRespVO;
import com.weili.iot_portal.domain.ingestion.FactoryRealtimeMetricSnapshot;
import com.weili.iot_portal.domain.ingestion.RealtimeMetricSnapshot;
import com.weili.iot_portal.service.cache.DeviceMetricsCacheService;
import com.weili.iot_portal.service.device.IMetricsSummaryQueryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author luying
 * @className MetricsSummaryQueryService
 * @description
 * @date 2026-01-04 14:42
 **/
@Service
@Slf4j
public class MetricsSummaryQueryService implements IMetricsSummaryQueryService {
    @Resource
    private DeviceInfoRepository deviceInfoRepository;
    @Resource
    private DeviceMetricsCacheService deviceMetricsCacheService;
    @Resource
    private DeviceMetricSummaryRepository deviceMetricSummaryRepository;
    @Resource
    private com.weili.iot_portal.service.cache.FactoryMetricsCacheService factoryMetricsCacheService;
    @Resource
    private com.weili.iot_portal.dal.repository.factory.FactoryMetricSummaryRepository factoryMetricSummaryRepository;

    @Override
    public MetricStatisticsRespVO getDeviceMetricStatistics(MetricStatisticsReqVO queryReqVO) {
        MetricStatisticsRespVO respVO = new MetricStatisticsRespVO();
        Long deviceInfoId = queryReqVO.getDeviceId();
        Optional<DeviceInfoDO> optional = deviceInfoRepository.findById(deviceInfoId);
        if (optional.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
        DeviceInfoDO deviceInfoDO = optional.get();

        // 1. 当前指标值：从缓存获取实时指标快照
        Optional<RealtimeMetricSnapshot> snapshotOptional = deviceMetricsCacheService.getDeviceRealtimeMetrics(deviceInfoDO.getOrgFactoryId(), deviceInfoId);
        RealtimeMetricSnapshot snapshot = snapshotOptional.orElse(RealtimeMetricSnapshot.empty());

        // 将实时指标快照转换为 MetricDetailVO
        MetricStatisticsRespVO.MetricDetailVO currentMetric = convertSnapshotToMetricDetail(snapshot);
        respVO.setCurrentMetricValue(currentMetric);

        // 2. 指标明细列表：根据是否传参决定查询范围
        LocalDate[] dateRange = calculateDateRange(queryReqVO.getStartTime(), queryReqVO.getEndTime());
        LocalDate startShiftDate = dateRange[0];
        LocalDate endShiftDate = dateRange[1];

        // 查询时间范围需要转换为时间戳（毫秒）
        long[] timestampRange = convertToTimestampRange(startShiftDate, endShiftDate);

        // 从 device_metrics_summary 查询时间范围内的指标汇总数据
        List<DeviceMetricSummaryDO> metricsList = deviceMetricSummaryRepository.selectFinalizedInRange(
                deviceInfoId,
                timestampRange[0],
                timestampRange[1]
        );

        // 按日期分组汇总（一天可能有多个班次，取平均值）
        Map<LocalDate, List<DeviceMetricSummaryDO>> dailyMetricsMap = groupByShiftDate(metricsList, DeviceMetricSummaryDO::getShiftDate);

        // 构建图表数据（按日期排序）
        List<MetricStatisticsRespVO.MetricDetailVO> detailList = buildChartData(
                startShiftDate,
                endShiftDate,
                dailyMetricsMap,
                DeviceMetricSummaryDO::getOee,
                DeviceMetricSummaryDO::getAvailability,
                DeviceMetricSummaryDO::getPerformance,
                DeviceMetricSummaryDO::getUtilizationRate
        );

        respVO.setMetricDetails(detailList);
        return respVO;
    }

    @Override
    public MetricStatisticsRespVO getFactoryMetricStatistics(MetricStatisticsReqVO reqVO) {
        MetricStatisticsRespVO respVO = new MetricStatisticsRespVO();
        Long orgFactoryId = reqVO.getOrgFactoryId();

        if (orgFactoryId == null) {
            throw new IotPortalException(IotPortalErrorCode.FACTORY_ID_EMPTY);
        }

        // 1. 当前指标值：从缓存获取工厂实时指标快照
        Optional<com.weili.iot_portal.domain.ingestion.FactoryRealtimeMetricSnapshot> snapshotOptional =
                factoryMetricsCacheService.getFactoryRealtimeMetrics(orgFactoryId);
        com.weili.iot_portal.domain.ingestion.FactoryRealtimeMetricSnapshot snapshot =
                snapshotOptional.orElse(null);

        // 将工厂实时指标快照转换为 MetricDetailVO
        MetricStatisticsRespVO.MetricDetailVO currentMetric = snapshot != null
                ? convertFactorySnapshotToMetricDetail(snapshot)
                : new MetricStatisticsRespVO.MetricDetailVO();
        respVO.setCurrentMetricValue(currentMetric);

        // 2. 指标明细列表：根据是否传参决定查询范围
        LocalDate[] dateRange = calculateDateRange(reqVO.getStartTime(), reqVO.getEndTime());
        LocalDate startShiftDate = dateRange[0];
        LocalDate endShiftDate = dateRange[1];

        // 查询时间范围需要转换为时间戳（毫秒）
        long[] timestampRange = convertToTimestampRange(startShiftDate, endShiftDate);

        // 从 factory_metric_summary 查询时间范围内的指标汇总数据
        List<com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO> metricsList =
                factoryMetricSummaryRepository.selectFinalizedInRange(
                        orgFactoryId,
                        timestampRange[0],
                        timestampRange[1]
                );

        // 按日期分组汇总（一天可能有多个班次，取平均值）
        Map<LocalDate, List<com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO>> dailyMetricsMap =
                groupByShiftDate(metricsList, com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO::getShiftDate);

        // 构建图表数据（按日期排序）
        List<MetricStatisticsRespVO.MetricDetailVO> detailList = buildChartData(
                startShiftDate,
                endShiftDate,
                dailyMetricsMap,
                com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO::getAverageOee,
                com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO::getAverageAvailability,
                com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO::getAveragePerformance,
                com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO::getAverageUtilizationRate
        );

        respVO.setMetricDetails(detailList);
        return respVO;
    }

    /**
     * 计算日期范围
     * <p>
     * 如果未传入时间范围，默认查询最近7天（包含今天）
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 日期范围数组 [startDate, endDate]
     */
    private LocalDate[] calculateDateRange(LocalDate startTime, LocalDate endTime) {
        LocalDate startShiftDate = startTime;
        LocalDate endShiftDate = endTime;
        if (startShiftDate == null || endShiftDate == null) {
            endShiftDate = LocalDate.now();
            startShiftDate = endShiftDate.minusDays(6); // 包含今天共7天
        }
        return new LocalDate[]{startShiftDate, endShiftDate};
    }

    /**
     * 将日期范围转换为时间戳范围（毫秒）
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 时间戳范围数组 [startTsMillis, endTsMillis]
     */
    private long[] convertToTimestampRange(LocalDate startDate, LocalDate endDate) {
        long startTsMillis = startDate.atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long endTsMillis = endDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        return new long[]{startTsMillis, endTsMillis};
    }

    /**
     * 按日期分组汇总
     *
     * @param metricsList       指标列表
     * @param shiftDateFunction 获取班次日期的函数
     * @param <T>               指标类型
     * @return 按日期分组的指标Map
     */
    private <T> Map<LocalDate, List<T>> groupByShiftDate(List<T> metricsList,
                                                         Function<T, LocalDate> shiftDateFunction) {
        if (metricsList == null || metricsList.isEmpty()) {
            return new HashMap<>();
        }
        return metricsList.stream()
                .collect(Collectors.groupingBy(shiftDateFunction));
    }

    /**
     * 构建图表数据（按日期排序）
     * <p>
     * 重要：必须保持横坐标完整，即使某些日期没有数据也要返回（值为0），确保前端能正确渲染图表
     *
     * @param startDate             开始日期
     * @param endDate               结束日期
     * @param dailyMetricsMap       按日期分组的指标Map
     * @param oeeGetter             OEE获取函数
     * @param availabilityGetter    可用率获取函数
     * @param performanceGetter     性能率获取函数
     * @param utilizationRateGetter 利用率获取函数
     * @param <T>                   指标类型
     * @return 指标明细列表
     */
    private <T> List<MetricStatisticsRespVO.MetricDetailVO> buildChartData(
            LocalDate startDate,
            LocalDate endDate,
            Map<LocalDate, List<T>> dailyMetricsMap,
            Function<T, BigDecimal> oeeGetter,
            Function<T, BigDecimal> availabilityGetter,
            Function<T, BigDecimal> performanceGetter,
            Function<T, BigDecimal> utilizationRateGetter) {

        List<MetricStatisticsRespVO.MetricDetailVO> detailList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            MetricStatisticsRespVO.MetricDetailVO detail = new MetricStatisticsRespVO.MetricDetailVO();

            // 设置日期标签（横坐标）：保证每个日期都有值
            detail.setDateLabel(date.format(formatter));

            List<T> dayMetrics = dailyMetricsMap.get(date);
            if (dayMetrics != null && !dayMetrics.isEmpty()) {
                // 计算当天各指标的平均值
                detail.setOee(calculateAverage(dayMetrics, oeeGetter));
                detail.setAvailability(calculateAverage(dayMetrics, availabilityGetter));
                detail.setPerformance(calculateAverage(dayMetrics, performanceGetter));
                detail.setUtilizationRate(calculateAverage(dayMetrics, utilizationRateGetter));

                // 停机率 = 100 - 可用率
                detail.setDowntimeRate(calculateDowntimeRate(detail.getAvailability()));
            } else {
                // 没有数据的日期，设置为0
                setDefaultMetricValues(detail);
            }
            detailList.add(detail);
        }
        return detailList;
    }

    /**
     * 计算停机率
     * <p>
     * 停机率 = 100 - 可用率
     *
     * @param availability 可用率
     * @return 停机率
     */
    private BigDecimal calculateDowntimeRate(BigDecimal availability) {
        if (availability != null) {
            return BigDecimal.valueOf(100).subtract(availability)
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 设置默认指标值（全部为0）
     *
     * @param detail 指标明细对象
     */
    private void setDefaultMetricValues(MetricStatisticsRespVO.MetricDetailVO detail) {
        detail.setOee(BigDecimal.ZERO);
        detail.setAvailability(BigDecimal.ZERO);
        detail.setPerformance(BigDecimal.ZERO);
        detail.setUtilizationRate(BigDecimal.ZERO);
        detail.setDowntimeRate(BigDecimal.ZERO);
    }

    /**
     * 计算指标的平均值，并转换为百分比形式（0-100）
     * <p>
     * 泛型方法，可用于设备级和工厂级指标的平均值计算
     *
     * @param metrics 指标列表
     * @param getter  指标获取函数
     * @param <T>     指标类型
     * @return 平均值（百分比形式），如果没有有效数据则返回 0
     */
    private <T> BigDecimal calculateAverage(List<T> metrics,
                                            Function<T, BigDecimal> getter) {
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
    private MetricStatisticsRespVO.MetricDetailVO convertSnapshotToMetricDetail(RealtimeMetricSnapshot snapshot) {
        MetricStatisticsRespVO.MetricDetailVO detail = new MetricStatisticsRespVO.MetricDetailVO();
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

    /**
     * 将工厂实时指标快照转换为 MetricDetailVO
     * <p>
     * 工厂实时指标快照已经是百分比形式（0-1），需要转换为（0-100）
     *
     * @param snapshot 工厂实时指标快照
     * @return MetricDetailVO
     */
    private MetricStatisticsRespVO.MetricDetailVO convertFactorySnapshotToMetricDetail(FactoryRealtimeMetricSnapshot snapshot) {
        MetricStatisticsRespVO.MetricDetailVO detail = new MetricStatisticsRespVO.MetricDetailVO();
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
}
