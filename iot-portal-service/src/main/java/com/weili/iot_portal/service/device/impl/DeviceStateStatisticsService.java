package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.common.utils.DeviceStateUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.service.device.IDeviceStateStatisticsService;
import com.weili.iot_portal.service.model.StateStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态统计服务实现
 */
@Slf4j
@Service
public class DeviceStateStatisticsService implements IDeviceStateStatisticsService {

    @Override
    public Map<String, StateStatistics> calculateStatistics(
            List<DeviceStateRecordDO> stateRecords,
            long shiftStartTs,
            long shiftEndTs) {

        Map<String, StateStatistics> statsMap = new HashMap<>();
        long shiftDurationSeconds = shiftEndTs - shiftStartTs;

        // 初始化所有状态
        for (String state : DeviceStateUtils.getAllStateNames()) {
            statsMap.put(state, new StateStatistics(state, 0, 0));
        }

        // 统计状态记录
        for (DeviceStateRecordDO record : stateRecords) {
            Integer stateCode = record.getStateCode();
            if (stateCode == null) {
                continue;
            }

            // 将数字编码转换为状态名称
            DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(stateCode);
            String stateName = stateEnum.name();

            // 计算该记录在班次内的有效时长
            long recordStartTs = record.getStartTs() != null ? record.getStartTs() : shiftStartTs;
            long recordEndTs = record.getEndTs() != null ? record.getEndTs() : shiftEndTs;

            // 取交集
            long effectiveStart = Math.max(recordStartTs, shiftStartTs);
            long effectiveEnd = Math.min(recordEndTs, shiftEndTs);
            long duration = Math.max(0, effectiveEnd - effectiveStart);

            StateStatistics stats = statsMap.computeIfAbsent(stateName,
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
            statsMap.get("MISSING").ratio = BigDecimal.valueOf(missingDataSeconds)
                    .divide(BigDecimal.valueOf(shiftDurationSeconds), 4, RoundingMode.HALF_UP);
        }

        return statsMap;
    }
}

