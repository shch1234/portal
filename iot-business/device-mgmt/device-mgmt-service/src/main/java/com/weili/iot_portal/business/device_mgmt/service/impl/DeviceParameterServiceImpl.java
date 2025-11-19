package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceParameterDO;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceBaseInfoRepository;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceParameterRepository;
import com.weili.iot_portal.business.device_mgmt.domain.enums.DeviceParameterTypeEnum;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterHistoryItemVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceParameterHistoryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceParameterUpdateReq;
import com.weili.iot_portal.business.device_mgmt.service.DeviceParameterService;
import com.weili.iot_portal.business.device_mgmt.service.assembler.DeviceParameterAssembler;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备参数服务实现
 */
@Service
@RequiredArgsConstructor
public class DeviceParameterServiceImpl implements DeviceParameterService {

    private final DeviceParameterRepository deviceParameterRepository;
    private final DeviceFactoryValidator deviceFactoryValidator;

    @Override
    public DeviceParameterVO getCurrent(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        List<DeviceParameterDO> records = deviceParameterRepository.selectCurrent(tenantId, deviceId);
        return DeviceParameterAssembler.toCurrentVO(deviceId, records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceParameterVO updateParameters(String tenantId, String factoryId, String deviceId, String operator, DeviceParameterUpdateReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        long now = System.currentTimeMillis();
        expireAndInsert(tenantId, deviceId, DeviceParameterTypeEnum.THEORETICAL_CYCLE.getType(),
                request.getTheoreticalCycleHours(), null, operator, now);
        expireAndInsert(tenantId, deviceId, DeviceParameterTypeEnum.PLANNED_DOWNTIME.getType(),
                request.getPlannedDowntimeHours(), null, operator, now);
        expireAndInsert(tenantId, deviceId, DeviceParameterTypeEnum.REMARK.getType(),
                null, request.getRemark(), operator, now);

        List<DeviceParameterDO> records = deviceParameterRepository.selectCurrent(tenantId, deviceId);
        DeviceParameterVO vo = DeviceParameterAssembler.toCurrentVO(deviceId, records);
        vo.setUpdatedBy(operator);
        vo.setDeviceId(deviceId);
        return vo;
    }

    @Override
    public DeviceParameterHistoryVO getHistory(String tenantId, String factoryId, DeviceParameterHistoryReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, request.getDeviceId());
        List<DeviceParameterDO> history = deviceParameterRepository.selectHistory(
                tenantId,
                request.getDeviceId(),
                request.getStartTs(),
                request.getEndTs());
        Map<Long, List<DeviceParameterDO>> grouped = history.stream()
                .collect(Collectors.groupingBy(DeviceParameterDO::getEffectiveStartTs,
                        () -> new LinkedHashMap<>(),
                        Collectors.toList()));

        List<Long> sortedKeys = grouped.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        long total = sortedKeys.size();
        int fromIndex = Math.min((request.getPageNo() - 1) * request.getPageSize(), sortedKeys.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), sortedKeys.size());

        List<DeviceParameterHistoryItemVO> items = sortedKeys.subList(fromIndex, toIndex).stream()
                .map(ts -> DeviceParameterAssembler.toHistoryItem(ts, grouped.get(ts)))
                .collect(Collectors.toList());

        DeviceParameterHistoryVO vo = new DeviceParameterHistoryVO();
        vo.setDeviceId(request.getDeviceId());
        vo.setHistory(items);
        vo.setTotal(total);
        vo.setPageNo(request.getPageNo());
        vo.setPageSize(request.getPageSize());
        vo.setTotalPages((int) Math.ceil((double) total / request.getPageSize()));
        return vo;
    }

    private void expireAndInsert(String tenantId, String deviceId,
                                 String parameterType,
                                 Double parameterValue,
                                 String parameterText,
                                 String operator,
                                 long effectiveStartTs) {
        deviceParameterRepository.expireCurrent(tenantId, deviceId, parameterType, effectiveStartTs);

        DeviceParameterDO entity = new DeviceParameterDO();
        entity.setId(IdWorker.getIdStr());
        entity.setTenantId(tenantId);
        entity.setDeviceId(deviceId);
        entity.setParameterType(parameterType);
        entity.setParameterValue(parameterValue == null ? null : BigDecimal.valueOf(parameterValue));
        entity.setParameterText(parameterText);
        entity.setEffectiveStartTs(effectiveStartTs);
        entity.setIsActive(Boolean.TRUE);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        deviceParameterRepository.insert(entity);
    }
}


