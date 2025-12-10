package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.api.shift.ShiftQueryApi;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.repository.devicebase.DeviceBaseInfoRepository;
import com.weili.iot_portal.domain.shift.ShiftInfoVO;
import com.weili.iot_portal.domain.shift.ShiftTimeRangeVO;
import com.weili.iot_portal.service.support.ShiftConfigurationService;
import com.weili.iot_portal.service.support.ShiftInfo;
import com.weili.iot_portal.service.support.ShiftTimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 班次查询API实现类
 * 
 * <p>实现公共接口 ShiftQueryApi，提供班次查询功能
 * 实现类放在 device-mgmt 模块中，因为班次配置数据在该模块
 */
@Service
@RequiredArgsConstructor
public class ShiftQueryApiImpl implements ShiftQueryApi {

    private final ShiftConfigurationService shiftConfigurationService;
    private final DeviceBaseInfoRepository deviceBaseInfoRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public ShiftInfoVO getDeviceCurrentShift(String factoryId, String deviceId, Long timestamp) {
        long ts = timestamp != null ? timestamp : System.currentTimeMillis();
        
        // 获取设备在当前时间的班次信息
        ShiftInfo shiftInfo = shiftConfigurationService.getCurrentShift(factoryId, deviceId, ts);
        
        // 计算班次日期
        String shiftDate = calculateShiftDate(shiftInfo, ts);
        
        // 转换为VO
        return ShiftInfoVO.builder()
                .shiftCode(shiftInfo.getCode())
                .shiftName(shiftInfo.getName())
                .startTime(shiftInfo.getStartTime())
                .endTime(shiftInfo.getEndTime())
                .durationHours(shiftInfo.getDurationHours())
                .crossDay(shiftInfo.getCrossDay())
                .shiftDate(shiftDate)
                .build();
    }

    @Override
    public ShiftInfoVO getFactoryCurrentShift(String factoryId, String workshopId, Long timestamp) {
        long ts = timestamp != null ? timestamp : System.currentTimeMillis();
        
        // 1. 获取工厂/车间下的设备列表
        List<DeviceBaseInfoDO> devices = deviceBaseInfoRepository.findByFactoryId(factoryId);
        
        // 如果指定了车间，则过滤车间设备
        if (StringUtils.hasText(workshopId)) {
            devices = devices.stream()
                    .filter(device -> workshopId.equals(device.getOrgWorkshopId()))
                    .toList();
        }
        
        // 2. 从设备中找到第一个有班次配置的设备
        for (DeviceBaseInfoDO device : devices) {
            try {
                // 获取该设备在当前时间的班次信息
                ShiftInfo shiftInfo = shiftConfigurationService.getCurrentShift(
                        factoryId, device.getId(), ts);
                
                // 3. 根据当前时间和班次信息计算班次日期和编码
                String shiftDate = calculateShiftDate(shiftInfo, ts);
                
                // 转换为VO
                return ShiftInfoVO.builder()
                        .shiftCode(shiftInfo.getCode())
                        .shiftName(shiftInfo.getName())
                        .startTime(shiftInfo.getStartTime())
                        .endTime(shiftInfo.getEndTime())
                        .durationHours(shiftInfo.getDurationHours())
                        .crossDay(shiftInfo.getCrossDay())
                        .shiftDate(shiftDate)
                        .build();
            } catch (Exception e) {
                // 如果该设备没有班次配置，继续查找下一个设备
                continue;
            }
        }
        
        // 4. 如果所有设备都没有班次配置，抛出异常
        throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                "工厂/车间下没有设备配置班次信息，无法计算当前班次，请先配置设备班次");
    }

    /**
     * 根据班次信息和当前时间计算班次日期
     * 
     * <p>处理跨天班次的情况：
     * - 如果班次跨天（如20:00-次日08:00），且当前时间在00:00-08:00之间，班次日期应该是昨天
     * - 其他情况，班次日期是今天
     */
    private String calculateShiftDate(ShiftInfo shiftInfo, long timestamp) {
        LocalDate today = LocalDate.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault());
        LocalTime currentTime = LocalTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault());
        
        // 解析班次开始和结束时间
        LocalTime shiftEndTime = LocalTime.parse(shiftInfo.getEndTime(), 
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        // 如果是跨天班次，且当前时间在结束时间之前（即班次的后半段）
        if (Boolean.TRUE.equals(shiftInfo.getCrossDay()) && currentTime.isBefore(shiftEndTime)) {
            // 跨天班次的后半段，班次日期是昨天
            return today.minusDays(1).format(DATE_FORMATTER);
        }
        
        // 其他情况，班次日期是今天
        return today.format(DATE_FORMATTER);
    }

    @Override
    public ShiftTimeRangeVO calculateShiftRange(String factoryId, String deviceId, Long timestamp) {
        long ts = timestamp != null ? timestamp : System.currentTimeMillis();
        
        // 使用内部的 ShiftConfigurationService 计算时间范围
        ShiftTimeRange shiftRange = shiftConfigurationService.calculateShiftRange(factoryId, deviceId, ts);
        
        // 转换为VO
        return ShiftTimeRangeVO.builder()
                .shiftCode(shiftRange.getShiftCode())
                .shiftName(shiftRange.getShiftName())
                .startTs(shiftRange.getStartTs())
                .endTs(shiftRange.getEndTs())
                .durationMs(shiftRange.getDurationMs())
                .build();
    }
}

