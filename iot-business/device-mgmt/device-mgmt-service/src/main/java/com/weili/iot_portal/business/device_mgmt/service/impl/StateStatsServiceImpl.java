package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.business.common.api.shift.ShiftQueryApi;
import com.weili.iot_portal.business.common.domain.model.shift.ShiftTimeRangeVO;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceStateSummaryDO;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceStateSummaryRepository;
import com.weili.iot_portal.business.device_mgmt.domain.model.StateStatsVO;
import com.weili.iot_portal.business.device_mgmt.service.StateStatsService;
import com.weili.iot_portal.business.device_mgmt.service.assembler.StateStatsAssembler;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 状态统计服务实现
 * 
 * <p>通过公共接口 ShiftQueryApi 获取班次信息，实现模块间解耦
 */
@Service
@RequiredArgsConstructor
public class StateStatsServiceImpl implements StateStatsService {

    private final DeviceStateSummaryRepository deviceStateSummaryRepository;
    private final DeviceFactoryValidator deviceFactoryValidator;
    private final ShiftQueryApi shiftQueryApi;

    @Override
    public StateStatsVO getCurrentShiftStats(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        
        // 通过公共接口获取当前班次的时间范围
        ShiftTimeRangeVO shiftRange = shiftQueryApi.calculateShiftRange(tenantId, factoryId, deviceId, null);
        
        List<DeviceStateSummaryDO> summaries = deviceStateSummaryRepository
                .selectByRange(tenantId, deviceId, shiftRange.getStartTs(), shiftRange.getEndTs());
        return StateStatsAssembler.toVO(deviceId, shiftRange.getStartTs(), shiftRange.getEndTs(), summaries);
    }

    @Override
    public StateStatsVO getHistoryStats(String tenantId, String factoryId, String deviceId, Long startTs, Long endTs) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        if (startTs == null || endTs == null || startTs >= endTs) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "无效的时间范围");
        }
        List<DeviceStateSummaryDO> summaries = deviceStateSummaryRepository
                .selectByRange(tenantId, deviceId, startTs, endTs);
        return StateStatsAssembler.toVO(deviceId, startTs, endTs, summaries);
    }
}


