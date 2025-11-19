package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceTypeDO;
import com.weili.iot_portal.business.device_mgmt.dal.ddd.DeviceTypePageQuery;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceTypeRepository;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceTypeVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceTypeCreateReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceTypeQueryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceTypeUpdateReq;
import com.weili.iot_portal.business.device_mgmt.service.DeviceTypeService;
import com.weili.iot_portal.business.device_mgmt.service.assembler.DeviceTypeAssembler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备类型服务实现
 */
@Service
@RequiredArgsConstructor
public class DeviceTypeServiceImpl implements DeviceTypeService {

    private final DeviceTypeRepository repository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceTypeVO create(String tenantId, String operator, DeviceTypeCreateReq request) {
        ensureTenant(tenantId);
        validateCreateReq(request);
        checkTypeCodeUnique(tenantId, request.getTypeCode(), null);
        validateParentType(tenantId, request.getParentTypeId());

        DeviceTypeDO entity = DeviceTypeAssembler.fromCreateReq(tenantId, operator, request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        repository.insert(entity);

        String parentTypeName = resolveParentName(tenantId, entity.getParentTypeId());
        return DeviceTypeAssembler.toVO(entity, parentTypeName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceTypeVO update(String tenantId, String operator, DeviceTypeUpdateReq request) {
        ensureTenant(tenantId);
        DeviceTypeDO entity = repository.findById(tenantId, request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备类型不存在"));

        if (StringUtils.isNotBlank(request.getTypeName())) {
            entity.setTypeName(request.getTypeName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getIcon() != null) {
            entity.setIcon(request.getIcon());
        }
        if (request.getCustomFields() != null) {
            entity.setCustomFields(request.getCustomFields());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        entity.setUpdatedBy(operator);
        entity.setUpdateTime(LocalDateTime.now());

        repository.update(entity);

        String parentTypeName = resolveParentName(tenantId, entity.getParentTypeId());
        return DeviceTypeAssembler.toVO(entity, parentTypeName);
    }

    @Override
    public DeviceTypeVO get(String tenantId, String id) {
        ensureTenant(tenantId);
        DeviceTypeDO entity = repository.findById(tenantId, id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备类型不存在"));
        String parentTypeName = resolveParentName(tenantId, entity.getParentTypeId());
        return DeviceTypeAssembler.toVO(entity, parentTypeName);
    }

    @Override
    public PageResult<DeviceTypeVO> page(String tenantId, DeviceTypeQueryReq request) {
        ensureTenant(tenantId);
        DeviceTypePageQuery query = new DeviceTypePageQuery();
        query.setTenantId(tenantId);
        query.setTypeCodeLike(request.getTypeCodeLike());
        query.setTypeNameLike(request.getTypeNameLike());
        query.setParentTypeId(request.getParentTypeId());
        query.setLevel(request.getLevel());
        query.setCategories(request.getCategories());
        query.setIsActive(request.getIsActive());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<DeviceTypeDO> pageResult = repository.selectPage(query);
        Map<String, String> parentNameCache = buildParentNameCache(tenantId, pageResult.getList());
        List<DeviceTypeVO> list = pageResult.getList().stream()
                .map(item -> DeviceTypeAssembler.toVO(item, parentNameCache.get(item.getParentTypeId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String tenantId, String id) {
        ensureTenant(tenantId);
        return repository.deleteById(tenantId, id);
    }

    private void ensureTenant(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "未获取到租户信息");
        }
    }

    private void validateCreateReq(DeviceTypeCreateReq request) {
        if (StringUtils.isBlank(request.getTypeCode())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "类型编码不能为空");
        }
        if (StringUtils.isBlank(request.getTypeName())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "类型名称不能为空");
        }
        if (request.getLevel() == null || request.getLevel() < 1) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "层级不合法");
        }
    }

    private void validateParentType(String tenantId, String parentTypeId) {
        if (StringUtils.isBlank(parentTypeId)) {
            return;
        }
        repository.findById(tenantId, parentTypeId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "父级类型不存在"));
    }

    private void checkTypeCodeUnique(String tenantId, String typeCode, String excludeId) {
        if (repository.existsByTypeCode(tenantId, typeCode, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "类型编码已存在");
        }
    }

    private String resolveParentName(String tenantId, String parentId) {
        if (StringUtils.isBlank(parentId)) {
            return null;
        }
        return repository.findById(tenantId, parentId)
                .map(DeviceTypeDO::getTypeName)
                .orElse(null);
    }

    private Map<String, String> buildParentNameCache(String tenantId, List<DeviceTypeDO> records) {
        List<String> parentIds = records.stream()
                .map(DeviceTypeDO::getParentTypeId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return parentIds.stream()
                .map(id -> repository.findById(tenantId, id).orElse(null))
                .filter(item -> item != null)
                .collect(Collectors.toMap(DeviceTypeDO::getId, DeviceTypeDO::getTypeName));
    }
}


