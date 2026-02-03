package com.weili.iot_portal.service.dashboard.impl;

import com.weili.basic.common.enums.ErrorCodeEnum;
import com.weili.iot_portal.common.enums.DeviceStateEnum;
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
     * 设备状态编码常量
     * 与设备状态编码保持一致：0-SHUTDOWN 1-WORKING 2-STANDBY 3-FAULT 255-UNKNOWN/离线
     */
    private static final String STATE_SHUTDOWN = String.valueOf(DeviceStateEnum.SHUTDOWN.getCode());
    private static final String STATE_WORKING = String.valueOf(DeviceStateEnum.WORKING.getCode());
    private static final String STATE_STANDBY = String.valueOf(DeviceStateEnum.STANDBY.getCode());
    private static final String STATE_FAULT = String.valueOf(DeviceStateEnum.FAULT.getCode());
    private static final String STATE_OFFLINE = String.valueOf(DeviceStateEnum.UNKNOWN.getCode());

    /**
     * 心跳状态常量
     */
    private static final String HEARTBEAT_ACTIVE = "1";   // 有心跳
    private static final String HEARTBEAT_INACTIVE = "0";  // 无心跳

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
        int shutdownDevices = 0;  // 状态为0的设备数
        int workingDevices = 0;    // 状态为1的设备数
        int standbyDevices = 0;    // 状态为2的设备数
        int faultDevices = 0;      // 状态为3的设备数
        int offlineDevices = 0;   // 状态为255的设备数

        if (totalDevices == 0) {
            DeviceStateStatisticsRespVO result = new DeviceStateStatisticsRespVO();
            result.setTotalDevices(0);
            result.setOnlineDevices(0);
            result.setOfflineDevices(0);
            result.setFaultDevices(0);
            result.setShutdownDevices(0);
            result.setWorkingDevices(0);
            result.setStandbyDevices(0);
            return result;
        }

        // 3. 收集设备ID列表，准备批量查询
        List<Long> deviceIds = monitoredDevices.stream()
                .map(DeviceInfoDO::getId)
                .collect(Collectors.toList());

        // 4. 批量从Redis获取设备心跳状态
        Map<Long, String> heartbeatMap = deviceStateCacheService.batchGetHeartbeatStatus(factoryId, deviceIds);
        
        // 5. 批量从Redis获取设备的实时状态值（优化：只获取state字段，减少90%+内存占用）
        Map<Long, String> deviceStateMap = deviceStateCacheService.batchGetStateValue(factoryId, deviceIds);

        // 6. 遍历所有被监控的设备，根据心跳和状态进行分类统计
        for (DeviceInfoDO device : monitoredDevices) {
            Long deviceId = device.getId();
            
            // 首先判断心跳：没有心跳或没有对应的设备key认为离线
            String heartbeat = heartbeatMap.get(deviceId);
            if (heartbeat == null || !HEARTBEAT_ACTIVE.equals(heartbeat)) {
                // 没有心跳，视为离线
                offlineDevices++;
                continue;
            }
            
            // 有心跳时，从deviceStateMap直接获取设备状态值（0,1,2,3,255）
            String state = deviceStateMap.get(deviceId);
            
            // 根据设备状态进行分类统计
            // 设备状态：0-SHUTDOWN（关机） 1-WORKING（加工中） 2-STANDBY（待机） 3-FAULT（故障） 255-UNKNOWN（未知/离线）
            if (StringUtils.isBlank(state)) {
                // 如果Redis中没有状态数据，视为离线（状态255）
                offlineDevices++;
            } else if (STATE_SHUTDOWN.equals(state)) {
                shutdownDevices++;
            } else if (STATE_WORKING.equals(state)) {
                workingDevices++;
            } else if (STATE_STANDBY.equals(state)) {
                standbyDevices++;
            } else if (STATE_FAULT.equals(state)) {
                faultDevices++;
            } else if (STATE_OFFLINE.equals(state)) {
                // 状态为255，视为离线
                offlineDevices++;
            } else {
                // 未知状态码，视为离线
                offlineDevices++;
            }
        }

        // 7. 计算在线设备数（状态为0,1,2,3的设备总和）
        int onlineDevices = shutdownDevices + workingDevices + standbyDevices + faultDevices;

        // 8. 构建返回结果
        DeviceStateStatisticsRespVO result = new DeviceStateStatisticsRespVO();
        result.setTotalDevices(totalDevices);
        result.setOnlineDevices(onlineDevices);
        result.setOfflineDevices(offlineDevices);
        result.setFaultDevices(faultDevices);
        result.setShutdownDevices(shutdownDevices);
        result.setWorkingDevices(workingDevices);
        result.setStandbyDevices(standbyDevices);
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

        // 4. 收集所有设备类型编码，批量查询设备类型信息
        List<String> deviceTypeCodes = devices.stream()
                .map(DeviceInfoDO::getDeviceTypeCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, String> deviceTypeNameMap = new HashMap<>();
        if (!deviceTypeCodes.isEmpty()) {
            List<DeviceTypeRelationDO> deviceTypes = deviceTypeRelationRepository.selectByCodes(deviceTypeCodes);
            deviceTypeNameMap = deviceTypes.stream()
                    .filter(type -> type.getDescription() != null)
                    .collect(Collectors.toMap(
                            DeviceTypeRelationDO::getTypeCode,
                            DeviceTypeRelationDO::getDescription,
                            (existing, replacement) -> existing // 如果有重复，保留第一个
                    ));
        }

        // 5. 组装返回结果
        List<AlarmDurationTopRespVO> result = new ArrayList<>();
        for (DeviceAlarmHistoryDO alarm : alarmHistories) {
            AlarmDurationTopRespVO vo = new AlarmDurationTopRespVO();

            DeviceInfoDO device = deviceMap.get(alarm.getDeviceInfoId());
            if (device != null) {
                vo.setDeviceCode(device.getDeviceCode());
                vo.setDeviceTypeCode(device.getDeviceTypeCode());
                // 设置设备类型名称
                if (device.getDeviceTypeCode() != null) {
                    vo.setDeviceTypeName(deviceTypeNameMap.get(device.getDeviceTypeCode()));
                }
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
                // 计算当天所有班次的平均值（数据库存储的是小数格式 0-1）
                BigDecimal averageValue = BigDecimal.ZERO;
                if (METRIC_TYPE_OEE.equals(metricType)) {
                    averageValue = dayData.stream()
                            .map(FactoryMetricSummaryDO::getAverageOee)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(dayData.size()), 4, RoundingMode.HALF_UP);
                } else if (METRIC_TYPE_UTILIZATION.equals(metricType)) {
                    averageValue = dayData.stream()
                            .map(FactoryMetricSummaryDO::getAverageUtilizationRate)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(dayData.size()), 4, RoundingMode.HALF_UP);
                }
                // 转换为百分比形式（0-100），保留1位小数
                value = averageValue.multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
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

        // 3. 批量从Redis获取设备心跳状态
        Map<Long, String> heartbeatMap = deviceStateCacheService.batchGetHeartbeatStatus(factoryId, deviceIds);
        
        // 4. 批量从Redis获取设备的实时状态值（优化：只获取state字段，减少90%+内存占用）
        Map<Long, String> deviceStateMap = deviceStateCacheService.batchGetStateValue(factoryId, deviceIds);

        List<String> deviceTypeCodes = monitoredDevices.stream().map(DeviceInfoDO::getDeviceTypeCode).distinct().toList();
        List<DeviceTypeRelationDO> typeRelationList = deviceTypeRelationRepository.selectByCodes(deviceTypeCodes);
        Map<String, DeviceTypeRelationDO> typeCodeMap = typeRelationList.stream().collect(Collectors.toMap(DeviceTypeRelationDO::getTypeCode, d -> d));
        
        // 5. 遍历所有被监控的设备，从批量查询结果中获取状态并组装VO
        List<DeviceListRespVO> result = new ArrayList<>(monitoredDevices.size());
        for (DeviceInfoDO device : monitoredDevices) {
            Long deviceId = device.getId();
            String state = STATE_OFFLINE; // 默认离线（255）
            
            // 首先判断心跳：没有心跳或没有对应的设备key认为离线
            String heartbeat = heartbeatMap.get(deviceId);
            if (heartbeat != null && HEARTBEAT_ACTIVE.equals(heartbeat)) {
                // 有心跳时，从deviceStateMap直接获取设备状态值（0,1,2,3）
                String stateStr = deviceStateMap.get(deviceId);
                if (stateStr != null) {
                    // 只接受有效的设备状态（0,1,2,3），其他情况视为离线（255）
                    if (STATE_SHUTDOWN.equals(stateStr) || STATE_WORKING.equals(stateStr) 
                            || STATE_STANDBY.equals(stateStr) || STATE_FAULT.equals(stateStr)) {
                        state = stateStr;
                    } else {
                        // 状态不在 0,1,2,3 范围内，视为离线（255）
                        state = STATE_OFFLINE;
                    }
                } else {
                    // deviceState 中没有状态值，视为离线（255）
                    state = STATE_OFFLINE;
                }
            }
            // 如果没有心跳，state 保持为 STATE_OFFLINE（255）

            // 组装VO
            DeviceListRespVO vo = new DeviceListRespVO();
            vo.setDeviceId(deviceId);
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
