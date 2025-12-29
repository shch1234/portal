package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.common.utils.DeviceStateUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.service.device.IDeviceStateStatisticsService;
import com.weili.iot_portal.domain.ingestion.StateStatistics;
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
        long shiftDurationMillis = shiftEndTs - shiftStartTs;

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

            // 计算该记录在班次内的有效时长（所有时间都是毫秒单位）
            long recordStartTs = record.getStartTs() != null ? record.getStartTs() : shiftStartTs;
            // 如果记录的结束时间为 null（进行中），使用班次结束时间
            // 但需要确保记录的开始时间不超过班次结束时间
            long recordEndTs = record.getEndTs() != null ? record.getEndTs() : shiftEndTs;

            // 取交集（都是毫秒单位）
            long effectiveStart = Math.max(recordStartTs, shiftStartTs);
            long effectiveEnd = Math.min(recordEndTs, shiftEndTs);
            
            // 如果记录的开始时间已经超过班次结束时间，跳过这条记录
            if (recordStartTs >= shiftEndTs) {
                continue;
            }
            
            // 如果记录的结束时间（或班次结束时间）小于班次开始时间，跳过这条记录
            if (effectiveEnd <= shiftStartTs) {
                continue;
            }
            
            // 计算时长（毫秒），直接存储毫秒
            long durationMillis = Math.max(0, effectiveEnd - effectiveStart);
            
            // 防御性检查：如果单条记录的时长超过班次时长，限制为班次时长
            // 这可能是由于数据异常（如时间戳错误）导致的
            if (durationMillis > shiftDurationMillis) {
                log.warn("单条状态记录的时长超过班次时长，已限制: deviceId={}, recordId={}, duration={}, shiftDuration={}, recordStartTs={}, recordEndTs={}, shiftStartTs={}, shiftEndTs={}",
                        record.getDeviceInfoId(), record.getId(), durationMillis, shiftDurationMillis,
                        recordStartTs, recordEndTs, shiftStartTs, shiftEndTs);
                durationMillis = shiftDurationMillis;
            }

            StateStatistics stats = statsMap.computeIfAbsent(stateName,
                    k -> new StateStatistics(k, 0, 0));
            stats.durationSeconds += durationMillis;
            stats.fragmentCount++;
        }

        // 计算缺失数据时长（毫秒）
        long totalRecordedDurationMillis = statsMap.values().stream()
                .mapToLong(s -> s.durationSeconds)
                .sum();
        long missingDataMillis = Math.max(0, shiftDurationMillis - totalRecordedDurationMillis);

        // 计算占比（分子和分母都是毫秒，结果一致）
        // 注意：如果某个状态的时长超过班次时长，限制为班次时长（防止数据异常）
        for (StateStatistics stats : statsMap.values()) {
            if (shiftDurationMillis > 0) {
                long duration = Math.min(stats.durationSeconds, shiftDurationMillis);
                stats.ratio = BigDecimal.valueOf(duration)
                        .divide(BigDecimal.valueOf(shiftDurationMillis), 4, RoundingMode.HALF_UP);
                // 确保比例值不超过 1
                if (stats.ratio.compareTo(BigDecimal.ONE) > 0) {
                    stats.ratio = BigDecimal.ONE;
                }
            }
        }

        // 添加缺失数据统计
        statsMap.put("MISSING", new StateStatistics("MISSING", missingDataMillis, 0));
        if (shiftDurationMillis > 0) {
            BigDecimal missingRatio = BigDecimal.valueOf(missingDataMillis)
                    .divide(BigDecimal.valueOf(shiftDurationMillis), 4, RoundingMode.HALF_UP);
            // 确保比例值不超过 1
            statsMap.get("MISSING").ratio = missingRatio.compareTo(BigDecimal.ONE) > 0 
                    ? BigDecimal.ONE : missingRatio;
        }

        return statsMap;
    }
}

