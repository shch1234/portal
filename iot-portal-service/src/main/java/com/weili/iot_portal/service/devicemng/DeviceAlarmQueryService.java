package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceAlarmHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceAlarmQueryService {

    private final DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;

    /**
     * 查询设备当前未结束的报警
     */
    public List<DeviceAlarmHistoryDO> listActive(String tenantId, String factoryId, String deviceId) {
        return deviceAlarmHistoryRepository.findActiveByDevice(tenantId, factoryId, deviceId);
    }

    /**
     * 查询时间范围内的报警（包含已结束、未结束）
     */
    public List<DeviceAlarmHistoryDO> listByRange(String tenantId, String factoryId, String deviceId, Long startTs, Long endTs) {
        return deviceAlarmHistoryRepository.findByRange(tenantId, factoryId, deviceId, startTs, endTs);
    }
}


