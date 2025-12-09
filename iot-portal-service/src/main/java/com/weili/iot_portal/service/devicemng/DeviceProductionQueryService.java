package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.dal.repository.devicemng.DeviceProductionRecordRepository;
import com.weili.iot_portal.service.support.ShiftConfigurationService;
import com.weili.iot_portal.service.support.ShiftTimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceProductionQueryService {

    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final ShiftConfigurationService shiftConfigurationService;

    /**
     * 当前班次已完成数量（end_ts 落在当前班次范围）
     */
    public long currentShiftCompletedCount(String tenantId, String factoryId, String deviceId) {
        long now = System.currentTimeMillis();
        ShiftTimeRange range = shiftConfigurationService.calculateShiftRange(tenantId, factoryId, deviceId, now);
        Long start = range.getStartTs() != null ? range.getStartTs() / 1000 : null;
        Long end = range.getEndTs() != null ? range.getEndTs() / 1000 : null;
        return deviceProductionRecordRepository.countCompletedInRange(tenantId, deviceId, start, end);
    }
}


