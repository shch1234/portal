package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.service.device.IDeviceStateRecordRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态记录修复服务实现
 * 用于修复历史遗留的异常记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStateRecordRepairService implements IDeviceStateRecordRepairService {

    private final DeviceStateRecordRepository stateRecordRepository;

    /**
     * 修复历史遗留的未结束记录
     * 将未结束且开始时间早于指定时间的记录标记为已结束（使用 start_ts + 1毫秒）
     * 并在 properties 中标记为有问题
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean repairHistoricalOngoingRecord(DeviceStateRecordDO record, long beforeTime) {
        if (record == null || record.getId() == null) {
            return false;
        }

        // 检查是否需要修复
        if (record.getEndTs() != null) {
            // 已经结束，不需要修复
            return false;
        }

        if (record.getStartTs() == null || record.getStartTs() >= beforeTime) {
            // 开始时间不早于指定时间，不需要修复
            return false;
        }

        try {
            // 设置结束时间为开始时间 + 1毫秒（表示零时长记录，标记为异常）
            long repairedEndTs = record.getStartTs() + 1;
            record.setEndTs(repairedEndTs);
            record.setDurationS(1L); // 1毫秒

            // 在 properties 中标记为有问题
            Map<String, Object> properties = record.getProperties();
            if (properties == null) {
                properties = new HashMap<>();
            } else {
                properties = new HashMap<>(properties);
            }
            properties.put("repaired", true);
            properties.put("repair_reason", "历史遗留的未结束记录，已自动修复");
            properties.put("repair_time", System.currentTimeMillis());
            properties.put("original_end_ts", null); // 记录原始状态（未结束）
            record.setProperties(properties);

            // 更新记录
            stateRecordRepository.update(record);

            log.info("修复历史遗留的未结束记录: recordId={}, deviceId={}, startTs={}, repairedEndTs={}",
                    record.getId(), record.getDeviceInfoId(), record.getStartTs(), repairedEndTs);

            return true;
        } catch (Exception e) {
            log.error("修复历史遗留记录失败: recordId={}, deviceId={}, error={}",
                    record.getId(), record.getDeviceInfoId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量修复历史遗留的未结束记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchRepairHistoricalOngoingRecords(long beforeTime, int batchSize) {
        log.info("开始批量修复历史遗留的未结束记录: beforeTime={}, batchSize={}", beforeTime, batchSize);

        int totalRepaired = 0;
        int batchCount = 0;

        while (true) {
            // 查询未结束的记录（开始时间早于指定时间）
            // findAllOngoing 的第一个参数是 startTsAfter，表示查询 start_ts >= startTsAfter 的记录
            // 我们需要查询所有未结束的记录，所以传入一个很早的时间
            List<DeviceStateRecordDO> ongoingRecords = stateRecordRepository.findAllOngoing(
                    beforeTime - (365L * 24 * 60 * 60 * 1000), // 查询一年前的记录作为起点
                    batchSize);

            if (ongoingRecords.isEmpty()) {
                break;
            }

            batchCount++;
            int batchRepaired = 0;

            for (DeviceStateRecordDO record : ongoingRecords) {
                // 只修复开始时间早于指定时间的记录
                if (record.getStartTs() != null && record.getStartTs() < beforeTime) {
                    if (repairHistoricalOngoingRecord(record, beforeTime)) {
                        batchRepaired++;
                        totalRepaired++;
                    }
                }
            }

            log.info("批量修复进度: 第{}批, 本批修复{}条, 累计修复{}条", 
                    batchCount, batchRepaired, totalRepaired);

            // 如果本批记录数小于batchSize，说明已经处理完所有记录
            if (ongoingRecords.size() < batchSize) {
                break;
            }
        }

        log.info("批量修复完成: 共修复{}条历史遗留的未结束记录", totalRepaired);
        return totalRepaired;
    }
}
