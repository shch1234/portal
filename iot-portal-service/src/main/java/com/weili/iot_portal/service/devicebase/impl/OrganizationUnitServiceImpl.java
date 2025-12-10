package com.weili.iot_portal.service.devicebase.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.OrganizationUnitDO;
import com.weili.iot_portal.dal.ddd.device.OrganizationUnitPageQuery;
import com.weili.iot_portal.dal.repository.devicebase.OrganizationUnitRepository;
import com.weili.iot_portal.domain.devicebase.OrganizationUnitVO;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitCreateReq;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitQueryReq;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitUpdateReq;
import com.weili.iot_portal.service.assembler.OrganizationUnitAssembler;
import com.weili.iot_portal.service.devicebase.OrganizationUnitService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public OrganizationUnitVO create(OrganizationUnitCreateReq request) {
        validateCreate(request);
        checkUnitCodeUnique(request.getUnitCode());
        String path = buildPath(request.getOrgParentId(), request.getUnitCode());

        OrganizationUnitDO entity = OrganizationUnitAssembler.fromCreateReq(request, path);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        organizationUnitRepository.insert(entity);

        String parentName = resolveParentName(entity.getOrgParentId());
        return OrganizationUnitAssembler.toVO(entity, parentName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationUnitVO update(OrganizationUnitUpdateReq request) {
        OrganizationUnitDO entity = organizationUnitRepository.findById(request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "组织单元不存在"));

        if (StringUtils.isNotBlank(request.getUnitName())) {
            entity.setUnitName(request.getUnitName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        entity.setUpdateTime(LocalDateTime.now());
        organizationUnitRepository.update(entity);

        String parentName = resolveParentName(entity.getOrgParentId());
        return OrganizationUnitAssembler.toVO(entity, parentName);
    }

    @Override
    public OrganizationUnitVO get(String id) {
        OrganizationUnitDO entity = organizationUnitRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "组织单元不存在"));
        String parentName = resolveParentName(entity.getOrgParentId());
        return OrganizationUnitAssembler.toVO(entity, parentName);
    }

    @Override
    public PageResult<OrganizationUnitVO> page(OrganizationUnitQueryReq request) {
        OrganizationUnitPageQuery query = new OrganizationUnitPageQuery();
        query.setUnitCodeLike(request.getUnitCodeLike());
        query.setUnitNameLike(request.getUnitNameLike());
        query.setUnitTypeValues(request.getUnitTypeValues());
        query.setOrgParentId(request.getOrgParentId());
        query.setLevelNo(request.getLevelNo());
        query.setIsActive(request.getIsActive());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<OrganizationUnitDO> pageResult = organizationUnitRepository.selectPage(query);
        Map<String, String> parentNameCache = buildParentNameCache(pageResult.getList());
        List<OrganizationUnitVO> list = pageResult.getList().stream()
                .map(item -> OrganizationUnitAssembler.toVO(item, parentNameCache.get(item.getOrgParentId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        return organizationUnitRepository.deleteById(id);
    }

    private void validateCreate(OrganizationUnitCreateReq request) {
        if (StringUtils.isBlank(request.getUnitCode())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "单元编码不能为空");
        }
        if (StringUtils.isBlank(request.getUnitName())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "单元名称不能为空");
        }
        if (StringUtils.isBlank(request.getUnitTypeValue())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "单元类型值不能为空");
        }
        if (request.getLevelNo() == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "层级不能为空");
        }
    }

    private void checkUnitCodeUnique(String unitCode) {
        if (organizationUnitRepository.existsByUnitCode(unitCode, null)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "单元编码已存在");
        }
    }

    private String buildPath(String parentId, String unitCode) {
        if (StringUtils.isBlank(parentId)) {
            return "/" + unitCode;
        }
        OrganizationUnitDO parent = organizationUnitRepository.findById(parentId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "父级组织不存在"));
        return parent.getPath() + "/" + unitCode;
    }

    private String resolveParentName(String parentId) {
        if (StringUtils.isBlank(parentId)) {
            return null;
        }
        return organizationUnitRepository.findById(parentId)
                .map(OrganizationUnitDO::getUnitName)
                .orElse(null);
    }

    /**
     * 构建父级名称缓存（用于批量查询优化）
     */
    private Map<String, String> buildParentNameCache(List<OrganizationUnitDO> records) {
        List<String> parentIds = records.stream()
                .map(OrganizationUnitDO::getOrgParentId)
                .filter(StringUtils::isNotBlank)
                .distinct().toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return parentIds.stream()
                .map(id -> organizationUnitRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(OrganizationUnitDO::getId, OrganizationUnitDO::getUnitName));
    }
}

