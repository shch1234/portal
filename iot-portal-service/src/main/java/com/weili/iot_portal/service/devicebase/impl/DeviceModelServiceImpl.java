package com.weili.iot_portal.service.devicebase.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceModelDO;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceTypeDO;
import com.weili.iot_portal.dal.ddd.device.DeviceModelPageQuery;
import com.weili.iot_portal.dal.repository.devicebase.DeviceModelRepository;
import com.weili.iot_portal.dal.repository.devicebase.DeviceTypeRepository;
import com.weili.iot_portal.domain.devicebase.DeviceModelVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelUpdateReq;
import com.weili.iot_portal.service.assembler.DeviceModelAssembler;
import com.weili.iot_portal.service.devicebase.DeviceModelService;
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
 * 设备型号服务实现
 */
@Service
@RequiredArgsConstructor
public class DeviceModelServiceImpl implements DeviceModelService {

    private final DeviceModelRepository deviceModelRepository;
    private final DeviceTypeRepository deviceTypeRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceModelVO create(DeviceModelCreateReq request) {
        validateCreate(request);
        checkModelCodeUnique(request.getModelCode());
        DeviceTypeDO type = deviceTypeRepository.findByTypeCode(request.getDeviceTypeCode())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备类型不存在"));

        DeviceModelDO entity = DeviceModelAssembler.fromCreateReq(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        deviceModelRepository.insert(entity);

        return DeviceModelAssembler.toVO(entity, type.getTypeDictValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceModelVO update(DeviceModelUpdateReq request) {
        DeviceModelDO entity = deviceModelRepository.findById(request.getId())
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
        entity.setUpdateTime(LocalDateTime.now());
        deviceModelRepository.update(entity);

        String typeName = resolveDeviceTypeName(entity.getDeviceTypeCode());
        return DeviceModelAssembler.toVO(entity, typeName);
    }

    @Override
    public DeviceModelVO get(String id) {
        DeviceModelDO entity = deviceModelRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备型号不存在"));
        String typeName = resolveDeviceTypeName(entity.getDeviceTypeCode());
        return DeviceModelAssembler.toVO(entity, typeName);
    }

    @Override
    public PageResult<DeviceModelVO> page(DeviceModelQueryReq request) {
        DeviceModelPageQuery query = new DeviceModelPageQuery();
        query.setModelCodeLike(request.getModelCodeLike());
        query.setModelNameLike(request.getModelNameLike());
        query.setDeviceTypeCodes(request.getDeviceTypeCodes());
        query.setManufacturer(request.getManufacturer());
        query.setIsActive(request.getIsActive());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<DeviceModelDO> pageResult = deviceModelRepository.selectPage(query);
        Map<String, String> typeNameCache = buildTypeNameCache(pageResult.getList());
        List<DeviceModelVO> list = pageResult.getList().stream()
                .map(item -> DeviceModelAssembler.toVO(item, typeNameCache.get(item.getDeviceTypeCode())))
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        return deviceModelRepository.deleteById(id);
    }

    private void validateCreate(DeviceModelCreateReq request) {
        if (StringUtils.isBlank(request.getModelCode())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "型号编码不能为空");
        }
        if (StringUtils.isBlank(request.getModelName())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "型号名称不能为空");
        }
        if (StringUtils.isBlank(request.getDeviceTypeCode())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备类型不能为空");
        }
    }

    private void checkModelCodeUnique(String modelCode) {
        if (deviceModelRepository.existsByModelCode(modelCode, null)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "型号编码已存在");
        }
    }

    private String resolveDeviceTypeName(String deviceTypeCode) {
        if (StringUtils.isBlank(deviceTypeCode)) {
            return null;
        }
        return deviceTypeRepository.findByTypeCode(deviceTypeCode)
                .map(DeviceTypeDO::getTypeDictValue)
                .orElse(null);
    }

    private Map<String, String> buildTypeNameCache(List<DeviceModelDO> records) {
        List<String> typeCodes = records.stream()
                .map(DeviceModelDO::getDeviceTypeCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (typeCodes.isEmpty()) {
            return Map.of();
        }
        return typeCodes.stream()
                .map(code -> deviceTypeRepository.findByTypeCode(code).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(DeviceTypeDO::getTypeCode, DeviceTypeDO::getTypeDictValue));
    }
}

