package com.weili.iot_portal.business.device_mgmt.service.assembler;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceModelDO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceModelVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceModelCreateReq;

import java.time.ZoneId;
import java.util.UUID;

/**
 * 设备型号装配器
 */
public final class DeviceModelAssembler {

    private DeviceModelAssembler() {
    }

    public static DeviceModelDO fromCreateReq(String tenantId, String operator, DeviceModelCreateReq request) {
        DeviceModelDO entity = new DeviceModelDO();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(tenantId);
        entity.setModelCode(request.getModelCode());
        entity.setModelName(request.getModelName());
        entity.setDeviceTypeId(request.getDeviceTypeId());
        entity.setManufacturer(request.getManufacturer());
        entity.setSpecifications(request.getSpecifications());
        entity.setTypeSpecificAttrs(request.getTypeSpecificAttrs());
        entity.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        return entity;
    }

    public static DeviceModelVO toVO(DeviceModelDO entity, String deviceTypeName) {
        return new DeviceModelVO()
                .setId(entity.getId())
                .setModelCode(entity.getModelCode())
                .setModelName(entity.getModelName())
                .setDeviceTypeId(entity.getDeviceTypeId())
                .setDeviceTypeName(deviceTypeName)
                .setManufacturer(entity.getManufacturer())
                .setSpecifications(entity.getSpecifications())
                .setTypeSpecificAttrs(entity.getTypeSpecificAttrs())
                .setIsActive(entity.getIsActive())
                .setCreatedTime(toEpochMilli(entity.getCreateTime()))
                .setUpdatedTime(toEpochMilli(entity.getUpdateTime()));
    }

    private static Long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}


