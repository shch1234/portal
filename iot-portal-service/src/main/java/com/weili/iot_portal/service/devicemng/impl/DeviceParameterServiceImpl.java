package com.weili.iot_portal.service.devicemng.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.iot_portal.common.enums.DeviceParameterTypeEnum;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceParameterDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceParameterRepository;
import com.weili.iot_portal.domain.devicemng.DeviceParameterHistoryItemVO;
import com.weili.iot_portal.domain.devicemng.DeviceParameterHistoryVO;
import com.weili.iot_portal.domain.devicemng.DeviceParameterVO;
import com.weili.iot_portal.domain.devicemng.request.DeviceParameterHistoryReq;
import com.weili.iot_portal.domain.devicemng.request.DeviceParameterUpdateReq;
import com.weili.iot_portal.service.assembler.DeviceParameterAssembler;
import com.weili.iot_portal.service.devicemng.DeviceParameterService;
import com.weili.iot_portal.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
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
    public DeviceParameterVO getCurrent(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        List<DeviceParameterDO> records = deviceParameterRepository.selectCurrent(deviceId);
        return DeviceParameterAssembler.toCurrentVO(deviceId, records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceParameterVO updateParameters(String factoryId, String deviceId,  DeviceParameterUpdateReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        long now = System.currentTimeMillis();
        expireAndInsert(deviceId, DeviceParameterTypeEnum.THEORETICAL_CYCLE.getType(),
                request.getTheoreticalCycleHours(), null,  now);
        expireAndInsert(deviceId, DeviceParameterTypeEnum.PLANNED_DOWNTIME.getType(),
                request.getPlannedDowntimeHours(), null,  now);
        expireAndInsert(deviceId, DeviceParameterTypeEnum.REMARK.getType(),
                null, request.getRemark(),  now);

        List<DeviceParameterDO> records = deviceParameterRepository.selectCurrent(deviceId);
        DeviceParameterVO vo = DeviceParameterAssembler.toCurrentVO(deviceId, records);
        vo.setDeviceId(deviceId);
        return vo;
    }

    @Override
    public DeviceParameterHistoryVO getHistory(String factoryId, DeviceParameterHistoryReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, request.getDeviceId());
        List<DeviceParameterDO> history = deviceParameterRepository.selectHistory(
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

    /**
     * 过期当前配置并插入新配置（对应 device_param_config 表的字段）
     */
    private void expireAndInsert(String deviceId,
                                 String parameterType,
                                 Double parameterValue,
                                 String parameterText,
                                 long effectiveStartTs) {
        deviceParameterRepository.expireCurrent(deviceId, parameterType, effectiveStartTs);

        DeviceParameterDO entity = new DeviceParameterDO();
        entity.setId(IdWorker.getIdStr());
        entity.setDeviceInfoId(deviceId);
        entity.setParameterType(parameterType);
        entity.setParameterValue(parameterValue == null ? null : BigDecimal.valueOf(parameterValue));
        entity.setParameterText(parameterText);
        entity.setEffectiveStartTs(effectiveStartTs);
        entity.setIsActive(Boolean.TRUE);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        deviceParameterRepository.insert(entity);
    }
}


