package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.business.common.api.shift.ShiftQueryApi;
import com.weili.iot_portal.business.common.domain.model.shift.ShiftTimeRangeVO;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceStateTimelineDO;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceStateTimelineRepository;
import com.weili.iot_portal.business.device_mgmt.domain.model.StateGanttVO;
import com.weili.iot_portal.business.device_mgmt.service.StateGanttService;
import com.weili.iot_portal.business.device_mgmt.service.assembler.StateGanttAssembler;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 状态甘特服务实现
 * 
 * <p>通过公共接口 ShiftQueryApi 获取班次信息，实现模块间解耦
 */
@Service
@RequiredArgsConstructor
public class StateGanttServiceImpl implements StateGanttService {

    private final DeviceStateTimelineRepository deviceStateTimelineRepository;
    private final DeviceFactoryValidator deviceFactoryValidator;
    private final ShiftQueryApi shiftQueryApi;

    @Override
    public StateGanttVO getCurrentShiftGantt(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        
        // 通过公共接口获取当前班次的时间范围
        ShiftTimeRangeVO shiftRange = shiftQueryApi.calculateShiftRange(tenantId, factoryId, deviceId, null);
        
        List<DeviceStateTimelineDO> records = deviceStateTimelineRepository
                .selectByRange(tenantId, deviceId, shiftRange.getStartTs(), shiftRange.getEndTs());
        return StateGanttAssembler.toVO(records, shiftRange.getStartTs(), shiftRange.getEndTs());
    }

    @Override
    public StateGanttVO getHistoryGantt(String tenantId, String factoryId, String deviceId, Long startTs, Long endTs) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        if (startTs == null || endTs == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "开始/结束时间不能为空");
        }
        if (startTs >= endTs) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "开始时间必须小于结束时间");
        }
        List<DeviceStateTimelineDO> records = deviceStateTimelineRepository
                .selectByRange(tenantId, deviceId, startTs, endTs);
        return StateGanttAssembler.toVO(records, startTs, endTs);
    }
}


