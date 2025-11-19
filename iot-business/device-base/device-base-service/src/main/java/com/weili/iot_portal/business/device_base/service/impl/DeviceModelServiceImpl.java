package com.weili.iot_portal.business.device_base.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceModelDO;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceTypeDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceModelPageQuery;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceModelRepository;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceTypeRepository;
import com.weili.iot_portal.business.device_base.domain.model.DeviceModelVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceModelCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceModelQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceModelUpdateReq;
import com.weili.iot_portal.business.device_base.service.DeviceModelService;
import com.weili.iot_portal.business.device_base.service.assembler.DeviceModelAssembler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备型号服务实现
 */
@Service
@RequiredArgsConstructor
public class DeviceModelServiceImpl implements DeviceModelService {

    private final DeviceModelRepository deviceModelRepository;
    private final DeviceTypeRepository deviceTypeRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceModelVO create(String tenantId, String operator, DeviceModelCreateReq request) {
        ensureTenant(tenantId);
        validateCreate(request);
        checkModelCodeUnique(tenantId, request.getModelCode(), null);
        DeviceTypeDO type = deviceTypeRepository.findById(tenantId, request.getDeviceTypeId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备类型不存在"));

        DeviceModelDO entity = DeviceModelAssembler.fromCreateReq(tenantId, operator, request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        deviceModelRepository.insert(entity);

        return DeviceModelAssembler.toVO(entity, type.getTypeName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceModelVO update(String tenantId, String operator, DeviceModelUpdateReq request) {
        ensureTenant(tenantId);
        DeviceModelDO entity = deviceModelRepository.findById(tenantId, request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备型号不存在"));

        if (StringUtils.isNotBlank(request.getModelName())) {
            entity.setModelName(request.getModelName());
        }
        if (request.getManufacturer() != null) {
            entity.setManufacturer(request.getManufacturer());
        }
        if (request.getSpecifications() != null) {
            entity.setSpecifications(request.getSpecifications());
        }
        if (request.getTypeSpecificAttrs() != null) {
            entity.setTypeSpecificAttrs(request.getTypeSpecificAttrs());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        entity.setUpdatedBy(operator);
        entity.setUpdateTime(LocalDateTime.now());
        deviceModelRepository.update(entity);

        String typeName = resolveDeviceTypeName(tenantId, entity.getDeviceTypeId());
        return DeviceModelAssembler.toVO(entity, typeName);
    }

    @Override
    public DeviceModelVO get(String tenantId, String id) {
        ensureTenant(tenantId);
        DeviceModelDO entity = deviceModelRepository.findById(tenantId, id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备型号不存在"));
        String typeName = resolveDeviceTypeName(tenantId, entity.getDeviceTypeId());
        return DeviceModelAssembler.toVO(entity, typeName);
    }

    @Override
    public PageResult<DeviceModelVO> page(String tenantId, DeviceModelQueryReq request) {
        ensureTenant(tenantId);
        DeviceModelPageQuery query = new DeviceModelPageQuery();
        query.setTenantId(tenantId);
        query.setModelCodeLike(request.getModelCodeLike());
        query.setModelNameLike(request.getModelNameLike());
        query.setDeviceTypeIds(request.getDeviceTypeIds());
        query.setManufacturer(request.getManufacturer());
        query.setIsActive(request.getIsActive());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<DeviceModelDO> pageResult = deviceModelRepository.selectPage(query);
        Map<String, String> typeNameCache = buildTypeNameCache(tenantId, pageResult.getList());
        List<DeviceModelVO> list = pageResult.getList().stream()
                .map(item -> DeviceModelAssembler.toVO(item, typeNameCache.get(item.getDeviceTypeId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String tenantId, String id) {
        ensureTenant(tenantId);
        return deviceModelRepository.deleteById(tenantId, id);
    }

    private void ensureTenant(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "未获取到租户信息");
        }
    }

    private void validateCreate(DeviceModelCreateReq request) {
        if (StringUtils.isBlank(request.getModelCode())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "型号编码不能为空");
        }
        if (StringUtils.isBlank(request.getModelName())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "型号名称不能为空");
        }
        if (StringUtils.isBlank(request.getDeviceTypeId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备类型不能为空");
        }
    }

    private void checkModelCodeUnique(String tenantId, String modelCode, String excludeId) {
        if (deviceModelRepository.existsByModelCode(tenantId, modelCode, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "型号编码已存在");
        }
    }

    private String resolveDeviceTypeName(String tenantId, String deviceTypeId) {
        if (StringUtils.isBlank(deviceTypeId)) {
            return null;
        }
        return deviceTypeRepository.findById(tenantId, deviceTypeId)
                .map(DeviceTypeDO::getTypeName)
                .orElse(null);
    }

    private Map<String, String> buildTypeNameCache(String tenantId, List<DeviceModelDO> records) {
        List<String> typeIds = records.stream()
                .map(DeviceModelDO::getDeviceTypeId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        return typeIds.stream()
                .map(id -> deviceTypeRepository.findById(tenantId, id).orElse(null))
                .filter(item -> item != null)
                .collect(Collectors.toMap(DeviceTypeDO::getId, DeviceTypeDO::getTypeName));
    }
}

