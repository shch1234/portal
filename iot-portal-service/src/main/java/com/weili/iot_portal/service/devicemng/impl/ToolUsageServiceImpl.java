package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.devicemng.ToolUsageHistoryDO;
import com.weili.iot_portal.dal.repository.devicemng.ToolUsageHistoryRepository;
import com.weili.iot_portal.domain.devicemng.ToolUsageHistoryVO;
import com.weili.iot_portal.domain.devicemng.ToolUsageItemVO;
import com.weili.iot_portal.domain.devicemng.request.ToolUsageHistoryReq;
import com.weili.iot_portal.service.devicemng.ToolUsageService;
import com.weili.iot_portal.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolUsageServiceImpl implements ToolUsageService {

    private final DeviceFactoryValidator deviceFactoryValidator;
    private final ToolUsageHistoryRepository toolUsageHistoryRepository;

    @Override
    public ToolUsageHistoryVO getHistory(String tenantId, String factoryId, ToolUsageHistoryReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, request.getDeviceId());
        if (request.getStartTs() == null || request.getEndTs() == null || request.getStartTs() >= request.getEndTs()) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "无效的时间范围");
        }
        List<ToolUsageHistoryDO> records = toolUsageHistoryRepository.selectByRange(
                tenantId,
                request.getDeviceId(),
                request.getStartTs(),
                request.getEndTs(),
                request.getLimit());
        ToolUsageHistoryVO vo = new ToolUsageHistoryVO();
        vo.setDeviceId(request.getDeviceId());
        vo.setStartTs(request.getStartTs());
        vo.setEndTs(request.getEndTs());
        vo.setItems(records.stream().map(this::convert).collect(Collectors.toList()));
        return vo;
    }

    private ToolUsageItemVO convert(ToolUsageHistoryDO record) {
        ToolUsageItemVO vo = new ToolUsageItemVO();
        vo.setToolNumber(record.getToolNumber());
        vo.setToolHolderNumber(record.getToolHolderNumber());
        vo.setStartTs(record.getStartTs());
        vo.setEndTs(record.getEndTs());
        vo.setDurationMs(record.getDurationMs());
        return vo;
    }
}

