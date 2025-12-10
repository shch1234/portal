package com.weili.iot_portal.service.devicebase.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceTypeDO;
import com.weili.iot_portal.dal.ddd.device.DeviceTypePageQuery;
import com.weili.iot_portal.dal.repository.devicebase.DeviceTypeRepository;
import com.weili.iot_portal.domain.devicebase.DeviceTypeVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeUpdateReq;
import com.weili.iot_portal.service.assembler.DeviceTypeAssembler;
import com.weili.iot_portal.service.devicebase.DeviceTypeService;
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
    public DeviceTypeVO create(DeviceTypeCreateReq request) {
        validateCreateReq(request);
        checkTypeCodeUnique(request.getTypeCode(), null);
        validateParentType(request.getParentTypeId());

        DeviceTypeDO entity = DeviceTypeAssembler.fromCreateReq(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        repository.insert(entity);

        String parentTypeDictValue = resolveParentDictValue(entity.getParentTypeId());
        return DeviceTypeAssembler.toVO(entity, parentTypeDictValue);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceTypeVO update(DeviceTypeUpdateReq request) {
        DeviceTypeDO entity = repository.findById(request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备类型不存在"));

        if (StringUtils.isNotBlank(request.getTypeDictValue())) {
            entity.setTypeDictValue(request.getTypeDictValue());
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
        entity.setUpdateTime(LocalDateTime.now());

        repository.update(entity);

        String parentTypeDictValue = resolveParentDictValue(entity.getParentTypeId());
        return DeviceTypeAssembler.toVO(entity, parentTypeDictValue);
    }

    @Override
    public DeviceTypeVO get(String id) {
        DeviceTypeDO entity = repository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备类型不存在"));
        String parentTypeDictValue = resolveParentDictValue(entity.getParentTypeId());
        return DeviceTypeAssembler.toVO(entity, parentTypeDictValue);
    }

    @Override
    public PageResult<DeviceTypeVO> page(DeviceTypeQueryReq request) {
        DeviceTypePageQuery query = new DeviceTypePageQuery();
        query.setTypeCodeLike(request.getTypeCodeLike());
        query.setTypeDictValueLike(request.getTypeDictValueLike());
        query.setParentTypeId(request.getParentTypeId());
        query.setLevelNo(request.getLevelNo());
        query.setCategories(request.getCategories());
        query.setIsActive(request.getIsActive());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<DeviceTypeDO> pageResult = repository.selectPage(query);
        Map<String, String> parentNameCache = buildParentDictCache(pageResult.getList());
        List<DeviceTypeVO> list = pageResult.getList().stream()
                .map(item -> DeviceTypeAssembler.toVO(item, parentNameCache.get(item.getParentTypeId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        return repository.deleteById(id);
    }

    private void validateCreateReq(DeviceTypeCreateReq request) {
        if (StringUtils.isBlank(request.getTypeCode())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "类型编码不能为空");
        }
        if (StringUtils.isBlank(request.getTypeDictValue())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "类型字典值不能为空");
        }
        if (request.getLevelNo() == null || request.getLevelNo() < 1) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "层级不合法");
        }
    }

    private void validateParentType(String parentTypeId) {
        if (StringUtils.isBlank(parentTypeId)) {
            return;
        }
        repository.findById(parentTypeId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "父级类型不存在"));
    }

    private void checkTypeCodeUnique(String typeCode, String excludeId) {
        if (repository.existsByTypeCode(typeCode, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "类型编码已存在");
        }
    }

    private String resolveParentDictValue(String parentId) {
        if (StringUtils.isBlank(parentId)) {
            return null;
        }
        return repository.findById(parentId)
                .map(DeviceTypeDO::getTypeDictValue)
                .orElse(null);
    }

    private Map<String, String> buildParentDictCache(List<DeviceTypeDO> records) {
        List<String> parentIds = records.stream()
                .map(DeviceTypeDO::getParentTypeId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return parentIds.stream()
                .map(id -> repository.findById(id).orElse(null))
                .filter(item -> item != null)
                .collect(Collectors.toMap(DeviceTypeDO::getId, DeviceTypeDO::getTypeDictValue));
    }
}

