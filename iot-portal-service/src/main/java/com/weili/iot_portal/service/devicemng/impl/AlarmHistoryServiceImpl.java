package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.alarm.AlarmHistoryDO;
import com.weili.iot_portal.dal.repository.alarm.AlarmHistoryRepository;
import com.weili.iot_portal.domain.devicemng.AlarmHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.AlarmHistoryQueryReq;
import com.weili.iot_portal.service.assembler.AlarmHistoryAssembler;
import com.weili.iot_portal.service.devicemng.AlarmHistoryService;
import com.weili.iot_portal.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报警历史服务实现
 */
@Service
@RequiredArgsConstructor
public class AlarmHistoryServiceImpl implements AlarmHistoryService {

    private final AlarmHistoryRepository alarmHistoryRepository;
    private final DeviceFactoryValidator deviceFactoryValidator;

    @Override
    public AlarmHistoryVO getCurrentAlarms(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        List<AlarmHistoryDO> alarms = alarmHistoryRepository.selectCurrent(deviceId);
        return AlarmHistoryAssembler.toVO(deviceId, alarms);
    }

    @Override
    public AlarmHistoryVO getAlarmHistory(String factoryId, AlarmHistoryQueryReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, request.getDeviceId());
        PageResult<AlarmHistoryDO> pageResult = alarmHistoryRepository.selectPage(
                request.getDeviceId(),
                request.getStartTs(),
                request.getEndTs(),
                request.getInProgress(),
                request.getPageNo(),
                request.getPageSize()
        );
        return AlarmHistoryAssembler.toVO(request.getDeviceId(), pageResult,
                request.getPageNo(), request.getPageSize());
    }

    @Override
    public boolean hasActiveAlarm(String factoryId, String deviceId) {
        // 先验证设备是否属于指定工厂
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        // 查询是否有进行中的报警
        List<AlarmHistoryDO> activeAlarms = alarmHistoryRepository.selectCurrent(deviceId);
        return activeAlarms != null && !activeAlarms.isEmpty();
    }
}


