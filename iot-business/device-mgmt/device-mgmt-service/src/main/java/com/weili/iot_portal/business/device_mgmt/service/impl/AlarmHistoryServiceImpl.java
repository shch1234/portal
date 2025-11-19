package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.AlarmHistoryDO;
import com.weili.iot_portal.business.device_mgmt.dal.repository.AlarmHistoryRepository;
import com.weili.iot_portal.business.device_mgmt.domain.model.AlarmHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.AlarmHistoryQueryReq;
import com.weili.iot_portal.business.device_mgmt.service.AlarmHistoryService;
import com.weili.iot_portal.business.device_mgmt.service.assembler.AlarmHistoryAssembler;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
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
    public AlarmHistoryVO getCurrentAlarms(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        List<AlarmHistoryDO> alarms = alarmHistoryRepository.selectCurrent(tenantId, deviceId);
        return AlarmHistoryAssembler.toVO(deviceId, alarms);
    }

    @Override
    public AlarmHistoryVO getAlarmHistory(String tenantId, String factoryId, AlarmHistoryQueryReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, request.getDeviceId());
        PageResult<AlarmHistoryDO> pageResult = alarmHistoryRepository.selectPage(
                tenantId,
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
    public boolean hasActiveAlarm(String tenantId, String factoryId, String deviceId) {
        // 先验证设备是否属于指定工厂
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        // 查询是否有进行中的报警
        List<AlarmHistoryDO> activeAlarms = alarmHistoryRepository.selectCurrent(tenantId, deviceId);
        return activeAlarms != null && !activeAlarms.isEmpty();
    }
}


