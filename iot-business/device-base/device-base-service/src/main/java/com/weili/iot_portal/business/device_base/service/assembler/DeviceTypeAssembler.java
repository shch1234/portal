package com.weili.iot_portal.business.device_base.service.assembler;

import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceTypeDO;
import com.weili.iot_portal.business.device_base.domain.model.DeviceTypeVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceTypeCreateReq;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 设备类型装配器
 */
public final class DeviceTypeAssembler {

    private DeviceTypeAssembler() {
    }

    public static DeviceTypeDO fromCreateReq(String tenantId, String operator, DeviceTypeCreateReq request) {
        DeviceTypeDO entity = new DeviceTypeDO();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(tenantId);
        entity.setTypeCode(request.getTypeCode());
        entity.setTypeName(request.getTypeName());
        entity.setParentTypeId(request.getParentTypeId());
        entity.setLevel(request.getLevel());
        entity.setCategory(request.getCategory());
        entity.setDescription(request.getDescription());
        entity.setIcon(request.getIcon());
        entity.setCustomFields(request.getCustomFields());
        entity.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        return entity;
    }

    public static DeviceTypeVO toVO(DeviceTypeDO entity, String parentTypeName) {
        return new DeviceTypeVO()
                .setId(entity.getId())
                .setTypeCode(entity.getTypeCode())
                .setTypeName(entity.getTypeName())
                .setParentTypeId(entity.getParentTypeId())
                .setParentTypeName(parentTypeName)
                .setLevel(entity.getLevel())
                .setCategory(entity.getCategory())
                .setDescription(entity.getDescription())
                .setIcon(entity.getIcon())
                .setCustomFields(entity.getCustomFields())
                .setIsActive(entity.getIsActive())
                .setSortOrder(entity.getSortOrder())
                .setCreatedTime(toEpochMilli(entity.getCreateTime()))
                .setUpdatedTime(toEpochMilli(entity.getUpdateTime()));
    }

    private static Long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        ZonedDateTime zonedDateTime = time.atZone(ZoneId.systemDefault());
        return zonedDateTime.toInstant().toEpochMilli();
    }
}

