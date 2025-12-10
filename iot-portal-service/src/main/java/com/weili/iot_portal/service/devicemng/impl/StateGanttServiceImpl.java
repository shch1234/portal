package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.api.shift.ShiftQueryApi;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateTimelineRepository;
import com.weili.iot_portal.domain.devicemng.StateGanttVO;
import com.weili.iot_portal.domain.shift.ShiftTimeRangeVO;
import com.weili.iot_portal.service.assembler.StateGanttAssembler;
import com.weili.iot_portal.service.devicemng.StateGanttService;
import com.weili.iot_portal.service.support.DeviceFactoryValidator;
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
    public StateGanttVO getCurrentShiftGantt(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        
        // 通过公共接口获取当前班次的时间范围
        ShiftTimeRangeVO shiftRange = shiftQueryApi.calculateShiftRange(factoryId, deviceId, null);
        
        List<DeviceStateTimelineDO> records = deviceStateTimelineRepository
                .selectByRange(deviceId, shiftRange.getStartTs(), shiftRange.getEndTs());
        return StateGanttAssembler.toVO(records, shiftRange.getStartTs(), shiftRange.getEndTs());
    }

    @Override
    public StateGanttVO getHistoryGantt(String factoryId, String deviceId, Long startTs, Long endTs) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        if (startTs == null || endTs == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "开始/结束时间不能为空");
        }
        if (startTs >= endTs) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "开始时间必须小于结束时间");
        }
        List<DeviceStateTimelineDO> records = deviceStateTimelineRepository
                .selectByRange(deviceId, startTs, endTs);
        return StateGanttAssembler.toVO(records, startTs, endTs);
    }
}


