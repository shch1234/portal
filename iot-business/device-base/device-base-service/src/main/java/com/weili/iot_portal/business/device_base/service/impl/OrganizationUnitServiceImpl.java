package com.weili.iot_portal.business.device_base.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.OrganizationUnitDO;
import com.weili.iot_portal.business.device_base.dal.ddd.OrganizationUnitPageQuery;
import com.weili.iot_portal.business.device_base.dal.repository.OrganizationUnitRepository;
import com.weili.iot_portal.business.device_base.domain.model.OrganizationUnitVO;
import com.weili.iot_portal.business.device_base.domain.model.request.OrganizationUnitCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.OrganizationUnitQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.OrganizationUnitUpdateReq;
import com.weili.iot_portal.business.device_base.service.OrganizationUnitService;
import com.weili.iot_portal.business.device_base.service.assembler.OrganizationUnitAssembler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织单元服务实现
 */
@Service
@RequiredArgsConstructor
public class OrganizationUnitServiceImpl implements OrganizationUnitService {

    private final OrganizationUnitRepository organizationUnitRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationUnitVO create(String tenantId, String operator, OrganizationUnitCreateReq request) {
        ensureTenant(tenantId);
        validateCreate(request);
        checkUnitCodeUnique(tenantId, request.getUnitCode(), null);
        String path = buildPath(tenantId, request.getParentId(), request.getUnitCode());

        OrganizationUnitDO entity = OrganizationUnitAssembler.fromCreateReq(tenantId, operator, request, path);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        organizationUnitRepository.insert(entity);

        String parentName = resolveParentName(tenantId, entity.getParentId());
        return OrganizationUnitAssembler.toVO(entity, parentName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationUnitVO update(String tenantId, String operator, OrganizationUnitUpdateReq request) {
        ensureTenant(tenantId);
        OrganizationUnitDO entity = organizationUnitRepository.findById(tenantId, request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "组织单元不存在"));

        if (StringUtils.isNotBlank(request.getUnitName())) {
            entity.setUnitName(request.getUnitName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getLocation() != null) {
            entity.setLocation(request.getLocation());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        entity.setUpdatedBy(operator);
        entity.setUpdateTime(LocalDateTime.now());
        organizationUnitRepository.update(entity);

        String parentName = resolveParentName(tenantId, entity.getParentId());
        return OrganizationUnitAssembler.toVO(entity, parentName);
    }

    @Override
    public OrganizationUnitVO get(String tenantId, String id) {
        ensureTenant(tenantId);
        OrganizationUnitDO entity = organizationUnitRepository.findById(tenantId, id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "组织单元不存在"));
        String parentName = resolveParentName(tenantId, entity.getParentId());
        return OrganizationUnitAssembler.toVO(entity, parentName);
    }

    @Override
    public PageResult<OrganizationUnitVO> page(String tenantId, OrganizationUnitQueryReq request) {
        ensureTenant(tenantId);
        OrganizationUnitPageQuery query = new OrganizationUnitPageQuery();
        query.setTenantId(tenantId);
        query.setUnitCodeLike(request.getUnitCodeLike());
        query.setUnitNameLike(request.getUnitNameLike());
        query.setUnitTypes(request.getUnitTypes());
        query.setParentId(request.getParentId());
        query.setLevel(request.getLevel());
        query.setIsActive(request.getIsActive());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<OrganizationUnitDO> pageResult = organizationUnitRepository.selectPage(query);
        Map<String, String> parentNameCache = buildParentNameCache(tenantId, pageResult.getList());
        List<OrganizationUnitVO> list = pageResult.getList().stream()
                .map(item -> OrganizationUnitAssembler.toVO(item, parentNameCache.get(item.getParentId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String tenantId, String id) {
        ensureTenant(tenantId);
        return organizationUnitRepository.deleteById(tenantId, id);
    }

    private void ensureTenant(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "未获取到租户信息");
        }
    }

    private void validateCreate(OrganizationUnitCreateReq request) {
        if (StringUtils.isBlank(request.getUnitCode())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "单元编码不能为空");
        }
        if (StringUtils.isBlank(request.getUnitName())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "单元名称不能为空");
        }
        if (StringUtils.isBlank(request.getUnitType())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "单元类型不能为空");
        }
        if (request.getLevel() == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "层级不能为空");
        }
    }

    private void checkUnitCodeUnique(String tenantId, String unitCode, String excludeId) {
        if (organizationUnitRepository.existsByUnitCode(tenantId, unitCode, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "单元编码已存在");
        }
    }

    private String buildPath(String tenantId, String parentId, String unitCode) {
        if (StringUtils.isBlank(parentId)) {
            return "/" + unitCode;
        }
        OrganizationUnitDO parent = organizationUnitRepository.findById(tenantId, parentId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "父级组织不存在"));
        return parent.getPath() + "/" + unitCode;
    }

    private String resolveParentName(String tenantId, String parentId) {
        if (StringUtils.isBlank(parentId)) {
            return null;
        }
        return organizationUnitRepository.findById(tenantId, parentId)
                .map(OrganizationUnitDO::getUnitName)
                .orElse(null);
    }

    private Map<String, String> buildParentNameCache(String tenantId, List<OrganizationUnitDO> records) {
        List<String> parentIds = records.stream()
                .map(OrganizationUnitDO::getParentId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return parentIds.stream()
                .map(id -> organizationUnitRepository.findById(tenantId, id).orElse(null))
                .filter(item -> item != null)
                .collect(Collectors.toMap(OrganizationUnitDO::getId, OrganizationUnitDO::getUnitName));
    }
}

