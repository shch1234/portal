package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.dal.dataobject.devicebase.DeviceTypeDO;
import com.weili.iot_portal.domain.devicebase.DeviceTypeVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeCreateReq;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 设备类型装配器
 */
public final class DeviceTypeAssembler {

    private DeviceTypeAssembler() {
    }

    public static DeviceTypeDO fromCreateReq(DeviceTypeCreateReq request) {
        DeviceTypeDO entity = new DeviceTypeDO();
        entity.setId(UUID.randomUUID().toString());
        entity.setTypeCode(request.getTypeCode());
        entity.setTypeDictValue(request.getTypeDictValue());
        entity.setParentTypeId(request.getParentTypeId());
        entity.setLevelNo(request.getLevelNo());
        entity.setCategory(request.getCategory());
        entity.setDescription(request.getDescription());
        entity.setIcon(request.getIcon());
        entity.setCustomFields(request.getCustomFields());
        entity.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return entity;
    }

    public static DeviceTypeVO toVO(DeviceTypeDO entity, String parentTypeDictValue) {
        return new DeviceTypeVO()
                .setId(entity.getId())
                .setTypeCode(entity.getTypeCode())
                .setTypeDictValue(entity.getTypeDictValue())
                .setParentTypeId(entity.getParentTypeId())
                .setParentTypeCode(entity.getParentTypeCode())
                .setParentDictValue(parentTypeDictValue != null ? parentTypeDictValue : entity.getParentDictValue())
                .setLevelNo(entity.getLevelNo())
                .setCategory(entity.getCategory())
                .setDescription(entity.getDescription())
                .setIcon(entity.getIcon())
                .setCustomFields(entity.getCustomFields())
                .setIsActive(entity.getIsActive())
                .setSortOrder(entity.getSortOrder())
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime());
    }

    private static Long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        ZonedDateTime zonedDateTime = time.atZone(ZoneId.systemDefault());
        return zonedDateTime.toInstant().toEpochMilli();
    }
}

