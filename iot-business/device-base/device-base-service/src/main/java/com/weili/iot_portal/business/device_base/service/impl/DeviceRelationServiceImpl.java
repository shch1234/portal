package com.weili.iot_portal.business.device_base.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceRelationDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceRelationPageQuery;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceBaseInfoRepository;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceRelationRepository;
import com.weili.iot_portal.business.device_base.domain.model.DeviceRelationVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationUpdateReq;
import com.weili.iot_portal.business.device_base.service.DeviceRelationService;
import com.weili.iot_portal.business.device_base.service.assembler.DeviceRelationAssembler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备关系服务实现
 */
@Service
@RequiredArgsConstructor
public class DeviceRelationServiceImpl implements DeviceRelationService {

    private final DeviceRelationRepository deviceRelationRepository;
    private final DeviceBaseInfoRepository deviceBaseInfoRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceRelationVO create(String tenantId, String operator, DeviceRelationCreateReq request) {
        validateCreateRequest(request);
        DeviceBaseInfoDO fromDevice = ensureDeviceExists(tenantId, request.getFromDeviceId(), "源设备不存在");
        DeviceBaseInfoDO toDevice = ensureDeviceExists(tenantId, request.getToDeviceId(), "目标设备不存在");
        checkRelationUnique(tenantId, request.getFromDeviceId(), request.getToDeviceId(), request.getRelationType(), null);

        DeviceRelationDO entity = DeviceRelationAssembler.fromCreateReq(tenantId, operator, request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        deviceRelationRepository.insert(entity);

        return DeviceRelationAssembler.toVO(entity, fromDevice, toDevice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceRelationVO update(String tenantId, String operator, DeviceRelationUpdateReq request) {
        DeviceRelationDO entity = deviceRelationRepository.findById(tenantId, request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备关系不存在"));

        DeviceRelationAssembler.copyForUpdate(request, entity);
        entity.setUpdatedBy(operator);
        entity.setUpdateTime(LocalDateTime.now());
        deviceRelationRepository.update(entity);

        Map<String, DeviceBaseInfoDO> deviceMap = loadDeviceBriefs(tenantId,
                Set.of(entity.getFromDeviceId(), entity.getToDeviceId()));
        return DeviceRelationAssembler.toVO(entity,
                deviceMap.get(entity.getFromDeviceId()), deviceMap.get(entity.getToDeviceId()));
    }

    @Override
    public DeviceRelationVO get(String tenantId, String id) {
        DeviceRelationDO entity = deviceRelationRepository.findById(tenantId, id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备关系不存在"));
        Map<String, DeviceBaseInfoDO> deviceMap = loadDeviceBriefs(tenantId,
                Set.of(entity.getFromDeviceId(), entity.getToDeviceId()));
        return DeviceRelationAssembler.toVO(entity,
                deviceMap.get(entity.getFromDeviceId()), deviceMap.get(entity.getToDeviceId()));
    }

    @Override
    public PageResult<DeviceRelationVO> page(String tenantId, DeviceRelationQueryReq request) {
        DeviceRelationPageQuery query = new DeviceRelationPageQuery();
        query.setTenantId(tenantId);
        query.setFromDeviceId(request.getFromDeviceId());
        query.setToDeviceId(request.getToDeviceId());
        query.setRelationTypes(request.getRelationTypes());
        query.setIsActive(request.getIsActive());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<DeviceRelationDO> pageResult = deviceRelationRepository.selectPage(query);
        Set<String> deviceIds = new HashSet<>();
        for (DeviceRelationDO record : pageResult.getList()) {
            if (StringUtils.isNotBlank(record.getFromDeviceId())) {
                deviceIds.add(record.getFromDeviceId());
            }
            if (StringUtils.isNotBlank(record.getToDeviceId())) {
                deviceIds.add(record.getToDeviceId());
            }
        }
        Map<String, DeviceBaseInfoDO> deviceMap = loadDeviceBriefs(tenantId, deviceIds);

        List<DeviceRelationVO> list = pageResult.getList().stream()
                .map(item -> DeviceRelationAssembler.toVO(item,
                        deviceMap.get(item.getFromDeviceId()), deviceMap.get(item.getToDeviceId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String tenantId, String id) {
        DeviceRelationDO entity = deviceRelationRepository.findById(tenantId, id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备关系不存在"));
        return deviceRelationRepository.deleteById(tenantId, entity.getId());
    }

    private void validateCreateRequest(DeviceRelationCreateReq request) {
        if (request.getFromDeviceId().equals(request.getToDeviceId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "源设备和目标设备不能相同");
        }
    }

    private DeviceBaseInfoDO ensureDeviceExists(String tenantId, String deviceId, String message) {
        return deviceBaseInfoRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), message));
    }

    private void checkRelationUnique(String tenantId, String fromDeviceId, String toDeviceId,
                                     String relationType, String excludeId) {
        if (deviceRelationRepository.existsRelation(tenantId, fromDeviceId, toDeviceId, relationType, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备关系已存在");
        }
    }

    private Map<String, DeviceBaseInfoDO> loadDeviceBriefs(String tenantId, Set<String> deviceIds) {
        Map<String, DeviceBaseInfoDO> result = new HashMap<>();
        for (String deviceId : deviceIds) {
            if (StringUtils.isBlank(deviceId)) {
                continue;
            }
            deviceBaseInfoRepository.findById(tenantId, deviceId)
                    .ifPresent(device -> result.put(deviceId, device));
        }
        return result;
    }
}

