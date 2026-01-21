package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceMetricSummaryRepository;
import com.weili.iot_portal.dal.repository.factory.FactoryMetricSummaryRepository;
import com.weili.iot_portal.domain.device.req.MetricDeviceDataReqVO;
import com.weili.iot_portal.domain.device.req.MetricStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.MetricDeviceDataRespVO;
import com.weili.iot_portal.domain.device.resp.MetricStatisticsRespVO;
import com.weili.iot_portal.domain.ingestion.FactoryRealtimeMetricSnapshot;
import com.weili.iot_portal.domain.ingestion.RealtimeMetricSnapshot;
import com.weili.iot_portal.service.cache.DeviceMetricsCacheService;
import com.weili.iot_portal.service.cache.FactoryMetricsCacheService;
import com.weili.iot_portal.service.device.IMetricsSummaryQueryService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
    private FactoryMetricsCacheService factoryMetricsCacheService;
    @Resource
    private FactoryMetricSummaryRepository factoryMetricSummaryRepository;
    @Resource
    private IShiftCalculationService shiftCalculationService;

    @Override
    public MetricStatisticsRespVO getDeviceMetricStatistics(MetricStatisticsReqVO queryReqVO) {
        MetricStatisticsRespVO respVO = new MetricStatisticsRespVO();
        Long deviceInfoId = queryReqVO.getDeviceId();
        Optional<DeviceInfoDO> optional = deviceInfoRepository.findById(deviceInfoId);
        if (optional.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
        DeviceInfoDO deviceInfoDO = optional.get();

        // 1. 指标明细列表：根据是否传参决定查询范围
        LocalDate[] dateRange = calculateDateRange(queryReqVO.getStartTime(), queryReqVO.getEndTime());
        LocalDate startShiftDate = dateRange[0];
        LocalDate endShiftDate = dateRange[1];

        // 从 device_metrics_summary 查询时间范围内的指标汇总数据
        List<DeviceMetricSummaryDO> metricsList = deviceMetricSummaryRepository.selectFinalizedInRange(
                deviceInfoId,
                startShiftDate,
                endShiftDate
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
                DeviceMetricSummaryDO::getUtilizationRate,
                DeviceMetricSummaryDO::getFaultRate
        );

        // 2. 如果明细列表中包含今天的日期，用当前实时指标值替换今天的数据
        LocalDate todayShiftDate = shiftCalculationService.getShiftDate(
                deviceInfoDO.getOrgFactoryId(), deviceInfoId, System.currentTimeMillis());
        if (todayShiftDate != null) {
            // 从缓存获取实时指标快照
            Optional<RealtimeMetricSnapshot> snapshotOptional = deviceMetricsCacheService.getDeviceRealtimeMetrics(
                    deviceInfoDO.getOrgFactoryId(), deviceInfoId);
            if (snapshotOptional.isPresent()) {
                RealtimeMetricSnapshot snapshot = snapshotOptional.get();
                MetricStatisticsRespVO.MetricDetailVO currentMetric = convertSnapshotToMetricDetail(snapshot);
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
                String todayLabel = todayShiftDate.format(formatter);
                
                // 查找今天的数据并替换
                for (MetricStatisticsRespVO.MetricDetailVO detail : detailList) {
                    if (todayLabel.equals(detail.getDateLabel())) {
                        // 用当前实时指标值替换今天的数据
                        detail.setOee(currentMetric.getOee());
                        detail.setAvailability(currentMetric.getAvailability());
                        detail.setPerformance(currentMetric.getPerformance());
                        detail.setUtilizationRate(currentMetric.getUtilizationRate());
                        detail.setDowntimeRate(currentMetric.getDowntimeRate());
                        break;
                    }
                }
            }
        }

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

        // 1. 指标明细列表：根据是否传参决定查询范围
        LocalDate[] dateRange = calculateDateRange(reqVO.getStartTime(), reqVO.getEndTime());
        LocalDate startShiftDate = dateRange[0];
        LocalDate endShiftDate = dateRange[1];
        // 从 factory_metric_summary 查询时间范围内的指标汇总数据
        List<FactoryMetricSummaryDO> metricsList =
                factoryMetricSummaryRepository.selectFinalizedInRange(
                        orgFactoryId,
                        startShiftDate,
                        endShiftDate
                );

        // 按日期分组汇总（一天可能有多个班次，取平均值）
        Map<LocalDate, List<FactoryMetricSummaryDO>> dailyMetricsMap =
                groupByShiftDate(metricsList, FactoryMetricSummaryDO::getShiftDate);

        // 构建图表数据（按日期排序）
        List<MetricStatisticsRespVO.MetricDetailVO> detailList = buildChartData(
                startShiftDate,
                endShiftDate,
                dailyMetricsMap,
                FactoryMetricSummaryDO::getAverageOee,
                FactoryMetricSummaryDO::getAverageAvailability,
                FactoryMetricSummaryDO::getAveragePerformance,
                FactoryMetricSummaryDO::getAverageUtilizationRate,
                FactoryMetricSummaryDO::getAverageFaultRate
        );

        // 2. 如果明细列表中包含今天的日期，用当前实时指标值替换今天的数据
        // 获取工厂下的一个设备来获取班次日期（工厂下所有设备的班次日期应该相同）
        List<DeviceInfoDO> factoryDevices = deviceInfoRepository.findMonitoredDevices(orgFactoryId);
        if (!factoryDevices.isEmpty()) {
            DeviceInfoDO sampleDevice = factoryDevices.get(0);
            LocalDate todayShiftDate = shiftCalculationService.getShiftDate(
                    orgFactoryId, sampleDevice.getId(), System.currentTimeMillis());
            if (todayShiftDate != null) {
                // 从缓存获取工厂实时指标快照
                Optional<FactoryRealtimeMetricSnapshot> snapshotOptional = 
                        factoryMetricsCacheService.getFactoryRealtimeMetrics(orgFactoryId);
                if (snapshotOptional.isPresent()) {
                    FactoryRealtimeMetricSnapshot snapshot = snapshotOptional.get();
                    MetricStatisticsRespVO.MetricDetailVO currentMetric = 
                            convertFactorySnapshotToMetricDetail(snapshot);
                    
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
                    String todayLabel = todayShiftDate.format(formatter);
                    
                    // 查找今天的数据并替换
                    for (MetricStatisticsRespVO.MetricDetailVO detail : detailList) {
                        if (todayLabel.equals(detail.getDateLabel())) {
                            // 用当前实时指标值替换今天的数据
                            detail.setOee(currentMetric.getOee());
                            detail.setAvailability(currentMetric.getAvailability());
                            detail.setPerformance(currentMetric.getPerformance());
                            detail.setUtilizationRate(currentMetric.getUtilizationRate());
                            detail.setDowntimeRate(currentMetric.getDowntimeRate());
                            break;
                        }
                    }
                }
            }
        }

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
     * @param faultRateGetter       故障率获取函数（0-1范围的小数）
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
            Function<T, BigDecimal> utilizationRateGetter,
            Function<T, BigDecimal> faultRateGetter) {

        List<MetricStatisticsRespVO.MetricDetailVO> detailList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            MetricStatisticsRespVO.MetricDetailVO detail = new MetricStatisticsRespVO.MetricDetailVO();

            // 设置日期标签（横坐标）：保证每个日期都有值
            detail.setDateLabel(date.format(formatter));

            List<T> dayMetrics = dailyMetricsMap.get(date);
            if (dayMetrics != null && !dayMetrics.isEmpty()) {
                // 计算当天各指标的平均值（已转换为百分比形式 0-100）
                detail.setOee(calculateAverage(dayMetrics, oeeGetter));
                detail.setAvailability(calculateAverage(dayMetrics, availabilityGetter));
                detail.setPerformance(calculateAverage(dayMetrics, performanceGetter));
                detail.setUtilizationRate(calculateAverage(dayMetrics, utilizationRateGetter));

                // 停机率：使用故障率（从数据库字段faultRate读取，calculateAverage已转换为百分比形式 0-100）
                BigDecimal faultRate = calculateAverage(dayMetrics, faultRateGetter);
                detail.setDowntimeRate(faultRate != null ? faultRate : BigDecimal.ZERO);
            } else {
                // 没有数据的日期，设置为0
                setDefaultMetricValues(detail);
            }
            detailList.add(detail);
        }
        return detailList;
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
     * 注意：Redis中存储的实时指标快照是小数形式（0-1），需要转换为百分比形式（0-100）返回
     * 与数据库字段格式保持一致（数据库存储小数，接口返回百分比）
     *
     * @param snapshot 实时指标快照（值是小数形式 0-1）
     * @return MetricDetailVO（值是百分比形式 0-100）
     */
    private MetricStatisticsRespVO.MetricDetailVO convertSnapshotToMetricDetail(RealtimeMetricSnapshot snapshot) {
        MetricStatisticsRespVO.MetricDetailVO detail = new MetricStatisticsRespVO.MetricDetailVO();
        // OEE（整体设备效率）：Redis中是小数（0-1），转换为百分比（0-100）
        detail.setOee(toPercentage(snapshot.getOee()));

        // 时间开动率（可用率）：availabilityRate -> availability
        detail.setAvailability(toPercentage(snapshot.getAvailabilityRate()));

        // 性能开动率（性能率）：performanceRate -> performance
        detail.setPerformance(toPercentage(snapshot.getPerformanceRate()));

        // 设备开动率（设备利用率）：uptimeRate -> utilizationRate
        detail.setUtilizationRate(toPercentage(snapshot.getUptimeRate()));

        // 停机率：faultRate -> downtimeRate（使用故障率）
        detail.setDowntimeRate(toPercentage(snapshot.getFaultRate()));

        return detail;
    }

    /**
     * 将 0-1 范围的比率转换为 0-100 的百分比
     * <p>
     * Redis中存储的实时指标快照是小数形式（0-1），需要转换为百分比形式（0-100）返回
     * 与数据库字段格式保持一致（数据库存储小数，接口返回百分比）
     *
     * @param rate 比率值（0-1范围的小数）
     * @return 百分比值（0-100，保留1位小数）
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
     * 注意：Redis中存储的工厂实时指标快照是小数形式（0-1），需要转换为百分比形式（0-100）返回
     * 与数据库字段格式保持一致（数据库存储小数，接口返回百分比）
     *
     * @param snapshot 工厂实时指标快照（值是小数形式 0-1）
     * @return MetricDetailVO（值是百分比形式 0-100）
     */
    private MetricStatisticsRespVO.MetricDetailVO convertFactorySnapshotToMetricDetail(FactoryRealtimeMetricSnapshot snapshot) {
        MetricStatisticsRespVO.MetricDetailVO detail = new MetricStatisticsRespVO.MetricDetailVO();
        // OEE（整体设备效率）：Redis中是小数（0-1），转换为百分比（0-100）
        detail.setOee(toPercentage(snapshot.getOee()));

        // 时间开动率（可用率）：availabilityRate -> availability
        detail.setAvailability(toPercentage(snapshot.getAvailabilityRate()));

        // 性能开动率（性能率）：performanceRate -> performance
        detail.setPerformance(toPercentage(snapshot.getPerformanceRate()));

        // 设备开动率（设备利用率）：uptimeRate -> utilizationRate
        detail.setUtilizationRate(toPercentage(snapshot.getUptimeRate()));

        // 停机率：faultRate -> downtimeRate（使用故障率）
        detail.setDowntimeRate(toPercentage(snapshot.getFaultRate()));

        return detail;
    }

    @Override
    public List<MetricDeviceDataRespVO> getDeviceMetricTop(MetricDeviceDataReqVO reqVO) {
        Long orgFactoryId = reqVO.getOrgFactoryId();
        LocalDate shiftDate = reqVO.getStartTime();
        Integer shiftCode = reqVO.getShiftCode();
        String deviceCode = reqVO.getDeviceCode();
        int topN = reqVO.getTop() != null ? reqVO.getTop() : 5;
        if (shiftDate == null) {
            shiftDate = LocalDate.now();
        }

        // 如果指定了设备编码，先查询设备ID
        final Long deviceInfoId;
        if (StringUtils.isNotBlank(deviceCode)) {
            Optional<DeviceInfoDO> deviceInfo = deviceInfoRepository.findByDeviceCode(deviceCode);
            if (deviceInfo.isEmpty()) {
                // 如果设备不存在，返回空结果
                return new ArrayList<>();
            }
            deviceInfoId = deviceInfo.get().getId();
        } else {
            deviceInfoId = null;
        }

        // 查询指定日期的班次数据
        // 筛选条件：orgFactoryId（工厂ID，为null时查询所有工厂）、shiftDate（班次日期）、shiftCode（班次编码，为null时查询所有班次）
        List<DeviceMetricSummaryDO> allMetrics = deviceMetricSummaryRepository
                .selectByFactoryAndShift(orgFactoryId, shiftDate, shiftCode);

        // 如果指定了设备编码，过滤出该设备的数据
        if (deviceInfoId != null) {
            final Long finalDeviceInfoId = deviceInfoId;
            allMetrics = allMetrics.stream()
                    .filter(metric -> finalDeviceInfoId.equals(metric.getDeviceInfoId()))
                    .collect(Collectors.toList());
        }

        if (allMetrics.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询设备信息
        List<Long> deviceIds = allMetrics.stream()
                .map(DeviceMetricSummaryDO::getDeviceInfoId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, DeviceInfoDO> deviceInfoMap = deviceInfoRepository.selectByIds(deviceIds)
                .stream()
                .collect(Collectors.toMap(DeviceInfoDO::getId, Function.identity()));

        // 构建响应数据
        List<MetricDeviceDataRespVO> result = new ArrayList<>();

        // 如果指定了班次编码，直接处理该班次的数据；否则按班次分组处理所有班次
        if (shiftCode != null) {
            // 指定了班次编码，直接处理该班次数据
            MetricDeviceDataRespVO respVO = new MetricDeviceDataRespVO();
            respVO.setShiftDate(shiftDate);
            respVO.setShiftCode(shiftCode);

            // 时间开动率TopN（availability）
            respVO.setAvailability(buildTopNDeviceList(allMetrics, deviceInfoMap, topN,
                    DeviceMetricSummaryDO::getAvailability));

            // 性能开动率TopN（performance）
            respVO.setPerformance(buildTopNDeviceList(allMetrics, deviceInfoMap, topN,
                    DeviceMetricSummaryDO::getPerformance));

            // OEE指标TopN
            respVO.setOee(buildTopNDeviceList(allMetrics, deviceInfoMap, topN,
                    DeviceMetricSummaryDO::getOee));

            // 设备开动率TopN（utilizationRate）
            respVO.setUtilizationRate(buildTopNDeviceList(allMetrics, deviceInfoMap, topN,
                    DeviceMetricSummaryDO::getUtilizationRate));

            // 停机率TopN（100 - availability，降序排列）
            respVO.setDowntimeRate(buildDowntimeRateTopN(allMetrics, deviceInfoMap, topN));

            result.add(respVO);
        } else {
            // 未指定班次编码，按班次分组处理所有班次
            Map<Integer, List<DeviceMetricSummaryDO>> shiftMetricsMap = allMetrics.stream()
                    .collect(Collectors.groupingBy(DeviceMetricSummaryDO::getShiftCode));

            // 对每个班次构建TopN数据
            for (Map.Entry<Integer, List<DeviceMetricSummaryDO>> entry : shiftMetricsMap.entrySet()) {
                Integer currentShiftCode = entry.getKey();
                List<DeviceMetricSummaryDO> shiftMetrics = entry.getValue();

                MetricDeviceDataRespVO respVO = new MetricDeviceDataRespVO();
                respVO.setShiftDate(shiftDate);
                respVO.setShiftCode(currentShiftCode);

                // 时间开动率TopN（availability）
                respVO.setAvailability(buildTopNDeviceList(shiftMetrics, deviceInfoMap, topN,
                        DeviceMetricSummaryDO::getAvailability));

                // 性能开动率TopN（performance）
                respVO.setPerformance(buildTopNDeviceList(shiftMetrics, deviceInfoMap, topN,
                        DeviceMetricSummaryDO::getPerformance));

                // OEE指标TopN
                respVO.setOee(buildTopNDeviceList(shiftMetrics, deviceInfoMap, topN,
                        DeviceMetricSummaryDO::getOee));

                // 设备开动率TopN（utilizationRate）
                respVO.setUtilizationRate(buildTopNDeviceList(shiftMetrics, deviceInfoMap, topN,
                        DeviceMetricSummaryDO::getUtilizationRate));

                // 停机率TopN（100 - availability，降序排列）
                respVO.setDowntimeRate(buildDowntimeRateTopN(shiftMetrics, deviceInfoMap, topN));

                result.add(respVO);
            }

            // 按班次编码排序
            result.sort(Comparator.comparing(MetricDeviceDataRespVO::getShiftCode));
        }

        return result;
    }

    /**
     * 构建TopN设备列表
     *
     * @param metrics       设备指标列表
     * @param deviceInfoMap 设备信息Map
     * @param topN          TopN数量
     * @param metricGetter  指标获取函数
     * @return TopN设备列表
     */
    private List<MetricDeviceDataRespVO.MetricDeviceDataVO> buildTopNDeviceList(
            List<DeviceMetricSummaryDO> metrics,
            Map<Long, DeviceInfoDO> deviceInfoMap,
            int topN,
            Function<DeviceMetricSummaryDO, BigDecimal> metricGetter) {

        return metrics.stream()
                .filter(m -> metricGetter.apply(m) != null)
                .sorted((m1, m2) -> {
                    BigDecimal v1 = metricGetter.apply(m1);
                    BigDecimal v2 = metricGetter.apply(m2);
                    return v2.compareTo(v1); // 降序排列
                })
                .limit(topN)
                .map(metric -> {
                    DeviceInfoDO deviceInfo = deviceInfoMap.get(metric.getDeviceInfoId());
                    MetricDeviceDataRespVO.MetricDeviceDataVO vo = new MetricDeviceDataRespVO.MetricDeviceDataVO();
                    if (deviceInfo != null) {
                        vo.setDeviceId(String.valueOf(deviceInfo.getId()));
                        vo.setDeviceCode(deviceInfo.getDeviceCode());
                        vo.setDeviceName(deviceInfo.getDeviceName());
                    }
                    // 转换为百分比形式（0-100）
                    vo.setValue(toPercentage(metricGetter.apply(metric)));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建停机率TopN列表
     * 停机率 = 故障率（从faultRate字段读取）
     *
     * @param metrics       设备指标列表
     * @param deviceInfoMap 设备信息Map
     * @param topN          TopN数量
     * @return 停机率TopN设备列表
     */
    private List<MetricDeviceDataRespVO.MetricDeviceDataVO> buildDowntimeRateTopN(
            List<DeviceMetricSummaryDO> metrics,
            Map<Long, DeviceInfoDO> deviceInfoMap,
            int topN) {

        return metrics.stream()
                .filter(m -> m.getFaultRate() != null)
                .sorted((m1, m2) -> {
                    // 停机率（故障率）越高越靠前
                    BigDecimal v1 = m1.getFaultRate();
                    BigDecimal v2 = m2.getFaultRate();
                    return v2.compareTo(v1); // 降序排列（故障率高的在前）
                })
                .limit(topN)
                .map(metric -> {
                    DeviceInfoDO deviceInfo = deviceInfoMap.get(metric.getDeviceInfoId());
                    MetricDeviceDataRespVO.MetricDeviceDataVO vo = new MetricDeviceDataRespVO.MetricDeviceDataVO();
                    if (deviceInfo != null) {
                        vo.setDeviceId(String.valueOf(deviceInfo.getId()));
                        vo.setDeviceCode(deviceInfo.getDeviceCode());
                        vo.setDeviceName(deviceInfo.getDeviceName());
                    }
                    // 停机率 = 故障率（转换为百分比）
                    vo.setValue(toPercentage(metric.getFaultRate()));
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
