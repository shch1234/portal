package com.weili.iot_portal.service.dashboard.impl;

import com.weili.basic.common.enums.ErrorCodeEnum;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceOrgRelationDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceOrgRelationRepository;
import com.weili.iot_portal.dal.repository.device.DeviceTypeRelationRepository;
import com.weili.iot_portal.dal.repository.factory.FactoryMetricSummaryRepository;
import com.weili.iot_portal.domain.dashboard.AlarmDurationTopRespVO;
import com.weili.iot_portal.domain.dashboard.DeviceListRespVO;
import com.weili.iot_portal.domain.dashboard.DeviceStateStatisticsRespVO;
import com.weili.iot_portal.domain.dashboard.MetricTrendRespVO;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import com.weili.iot_portal.service.dashboard.IDashboardService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
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
 * Dashboard业务服务实现
 *
 * @author luying
 */
@Slf4j
@Service
public class DashboardService implements IDashboardService {

    /**
     * 设备状态定义常量
     */
    private static final String STATE_ONLINE = "0";      // 在线状态
    private static final String STATE_OFFLINE = "1";     // 离线状态
    private static final String STATE_FAULT = "2";       // 故障状态

    /**
     * 指标类型常量
     */
    private static final String METRIC_TYPE_OEE = "OEE";
    private static final String METRIC_TYPE_UTILIZATION = "UTILIZATION";

    @Resource
    private DeviceInfoRepository deviceInfoRepository;

    @Resource
    private DeviceStateCacheService deviceStateCacheService;

    @Resource
    private DeviceOrgRelationRepository deviceOrgRelationRepository;

    @Resource
    private DeviceTypeRelationRepository deviceTypeRelationRepository;

    @Resource
    private DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;

    @Resource
    private FactoryMetricSummaryRepository factoryMetricSummaryRepository;


    private Long getFactoryId(Long factoryCode) {
        Optional<DeviceOrgRelationDO> optional = deviceOrgRelationRepository.findByUnitCode(String.valueOf(factoryCode));
        if (optional.isPresent()) {
            DeviceOrgRelationDO orgRelationDO = optional.get();
            return orgRelationDO.getId();
        }
        throw new IotPortalException(ErrorCodeEnum.PARAMS_ILLEGAL);
    }

    @Override
    public DeviceStateStatisticsRespVO getDeviceStateStatistics(Long factoryCode) {
        Long factoryId = getFactoryId(factoryCode);
        List<DeviceInfoDO> monitoredDevices = deviceInfoRepository.findMonitoredDevices(factoryId);

        // 2. 统计设备状态
        int totalDevices = monitoredDevices.size();
        int onlineDevices = 0;
        int offlineDevices = 0;
        int faultDevices = 0;

        if (totalDevices == 0) {
            DeviceStateStatisticsRespVO result = new DeviceStateStatisticsRespVO();
            result.setTotalDevices(0);
            result.setOnlineDevices(0);
            result.setOfflineDevices(0);
            result.setFaultDevices(0);
            return result;
        }

        // 3. 收集设备ID列表，准备批量查询
        List<Long> deviceIds = monitoredDevices.stream()
                .map(DeviceInfoDO::getId)
                .collect(Collectors.toList());

        // 4. 批量从Redis获取设备的实时状态
        Map<Long, Map<Object, Object>> deviceStateMap = deviceStateCacheService.batchGetState(factoryId, deviceIds);

        // 5. 遍历所有被监控的设备，根据Redis返回的状态进行分类统计
        for (DeviceInfoDO device : monitoredDevices) {
            Map<Object, Object> stateData = deviceStateMap.get(device.getId());
            String state = null;

            if (stateData != null && !stateData.isEmpty()) {
                Object stateValue = stateData.get(DeviceStateEventFields.STATE);
                state = stateValue != null ? stateValue.toString() : null;
            }

            // 根据状态进行分类统计
            if (StringUtils.isBlank(state)) {
                // 如果Redis中没有状态数据，视为离线
                offlineDevices++;
            } else if (STATE_ONLINE.equals(state)) {
                onlineDevices++;
            } else if (STATE_OFFLINE.equals(state)) {
                offlineDevices++;
            } else if (STATE_FAULT.equals(state)) {
                faultDevices++;
            } else {
                // 未知状态，默认视为离线
                offlineDevices++;
            }
        }

        // 6. 构建返回结果
        DeviceStateStatisticsRespVO result = new DeviceStateStatisticsRespVO();
        result.setTotalDevices(totalDevices);
        result.setOnlineDevices(onlineDevices);
        result.setOfflineDevices(offlineDevices);
        result.setFaultDevices(faultDevices);
        return result;
    }

    @Override
    public List<AlarmDurationTopRespVO> getAlarmDurationTop(Long factoryCode, Integer topN) {
        // 1. 从Repository查询报警时长TOP N
        Long factoryId = getFactoryId(factoryCode);
        List<DeviceAlarmHistoryDO> alarmHistories = deviceAlarmHistoryRepository.findTopByDuration(factoryId, topN);

        if (alarmHistories == null || alarmHistories.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 获取所有相关的设备ID
        List<Long> deviceIds = alarmHistories.stream()
                .map(DeviceAlarmHistoryDO::getDeviceInfoId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 批量查询设备信息
        List<DeviceInfoDO> devices = deviceInfoRepository.selectByIds(deviceIds);
        Map<Long, DeviceInfoDO> deviceMap = devices.stream()
                .collect(Collectors.toMap(DeviceInfoDO::getId, Function.identity()));

        // 4. 组装返回结果
        List<AlarmDurationTopRespVO> result = new ArrayList<>();
        for (DeviceAlarmHistoryDO alarm : alarmHistories) {
            AlarmDurationTopRespVO vo = new AlarmDurationTopRespVO();

            DeviceInfoDO device = deviceMap.get(alarm.getDeviceInfoId());
            if (device != null) {
                vo.setDeviceCode(device.getDeviceCode());
                vo.setDeviceTypeCode(device.getDeviceTypeCode());
            }

            vo.setAlarmText(alarm.getAlarmText());
            vo.setDurationS(alarm.getDurationS() / 1000);

            result.add(vo);
        }
        return result;
    }

    @Override
    public MetricTrendRespVO getMetricTrend(Long factoryCode, String metricType, LocalDate startDate, LocalDate endDate) {
        // 1. 查询指定时间范围内的工厂指标汇总数据
        Long factoryId = getFactoryId(factoryCode);
        List<FactoryMetricSummaryDO> summaries = factoryMetricSummaryRepository
                .selectFinalizedInRange(factoryId, startDate, endDate);

        // 2. 构建返回数据
        MetricTrendRespVO result = new MetricTrendRespVO();
        result.setMetricType(metricType);

        List<String> xAxis = new ArrayList<>();
        List<BigDecimal> yAxis = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d");

        // 3. 按照班次日期分组，计算每天的平均值
        Map<LocalDate, List<FactoryMetricSummaryDO>> dateGroupMap = summaries.stream()
                .collect(Collectors.groupingBy(FactoryMetricSummaryDO::getShiftDate));

        // 4. 生成完整的日期序列（包括没有数据的日期）
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            String dateStr = currentDate.format(formatter);
            xAxis.add(dateStr);

            // 获取当天的所有班次数据
            List<FactoryMetricSummaryDO> dayData = dateGroupMap.get(currentDate);
            BigDecimal value = BigDecimal.ZERO;

            if (dayData != null && !dayData.isEmpty()) {
                // 计算当天所有班次的平均值
                if (METRIC_TYPE_OEE.equals(metricType)) {
                    value = dayData.stream()
                            .map(FactoryMetricSummaryDO::getAverageOee)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(dayData.size()), 4, RoundingMode.HALF_UP);
                } else if (METRIC_TYPE_UTILIZATION.equals(metricType)) {
                    value = dayData.stream()
                            .map(FactoryMetricSummaryDO::getAverageUtilizationRate)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(dayData.size()), 4, RoundingMode.HALF_UP);
                }
            }

            yAxis.add(value);
            currentDate = currentDate.plusDays(1);
        }

        result.setTime(xAxis);
        result.setValue(yAxis);
        return result;
    }

    @Override
    public List<DeviceListRespVO> getDeviceList(Long factoryCode) {
        // 1. 通过Repository查询所有被监控的设备（isMonitored = true）
        Long factoryId = getFactoryId(factoryCode);
        List<DeviceInfoDO> monitoredDevices = deviceInfoRepository.findMonitoredDevices(factoryId);

        if (monitoredDevices == null || monitoredDevices.isEmpty()) {
            log.info("未查询到监控设备");
            return new ArrayList<>();
        }

        // 2. 收集设备ID列表，准备批量查询
        List<Long> deviceIds = monitoredDevices.stream()
                .map(DeviceInfoDO::getId)
                .collect(Collectors.toList());

        // 3. 批量从Redis获取设备的实时状态
        Map<Long, Map<Object, Object>> deviceStateMap = deviceStateCacheService.batchGetState(factoryId, deviceIds);

        List<String> deviceTypeCodes = monitoredDevices.stream().map(DeviceInfoDO::getDeviceTypeCode).distinct().toList();
        List<DeviceTypeRelationDO> typeRelationList = deviceTypeRelationRepository.selectByCodes(deviceTypeCodes);
        Map<String, DeviceTypeRelationDO> typeCodeMap = typeRelationList.stream().collect(Collectors.toMap(DeviceTypeRelationDO::getTypeCode, d -> d));
        // 4. 遍历所有被监控的设备，从批量查询结果中获取状态并组装VO
        List<DeviceListRespVO> result = new ArrayList<>(monitoredDevices.size());
        for (DeviceInfoDO device : monitoredDevices) {
            Map<Object, Object> stateData = deviceStateMap.get(device.getId());
            String state = STATE_OFFLINE; // 默认离线

            if (stateData != null && !stateData.isEmpty()) {
                Object stateValue = stateData.get(DeviceStateEventFields.STATE);
                if (stateValue != null) {
                    state = stateValue.toString();
                }
            }

            // 组装VO
            DeviceListRespVO vo = new DeviceListRespVO();
            vo.setDeviceId(device.getId());
            vo.setDeviceCode(device.getDeviceCode());
            vo.setDeviceTypeCode(device.getDeviceTypeCode());
            vo.setDeviceTypeName(typeCodeMap.containsKey(device.getDeviceTypeCode()) ?
                    typeCodeMap.get(device.getDeviceTypeCode()).getDescription() :
                    "未知");
            vo.setState(state);
            result.add(vo);
        }
        return result;
    }
}
