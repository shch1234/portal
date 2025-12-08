package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.dal.dataobject.devicebase.DeviceModelDO;
import com.weili.iot_portal.domain.devicebase.DeviceModelVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelCreateReq;

import java.util.UUID;

/**
 * 设备型号装配器
 */
public final class DeviceModelAssembler {

    private DeviceModelAssembler() {
    }

    public static DeviceModelDO fromCreateReq(String tenantId, DeviceModelCreateReq request) {
        DeviceModelDO entity = new DeviceModelDO();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantUuid(tenantId);
        entity.setModelCode(request.getModelCode());
        entity.setModelName(request.getModelName());
        entity.setDeviceTypeCode(request.getDeviceTypeCode());
        entity.setManufacturer(request.getManufacturer());
        entity.setSpecifications(request.getSpecifications());
        entity.setTypeSpecificAttrs(request.getTypeSpecificAttrs());
        entity.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        return entity;
    }

    public static DeviceModelVO toVO(DeviceModelDO entity, String deviceTypeName) {
        return new DeviceModelVO()
                .setId(entity.getId())
                .setModelCode(entity.getModelCode())
                .setModelName(entity.getModelName())
                .setDeviceTypeCode(entity.getDeviceTypeCode())
                .setDeviceTypeName(deviceTypeName)
                .setManufacturer(entity.getManufacturer())
                .setSpecifications(entity.getSpecifications())
                .setTypeSpecificAttrs(entity.getTypeSpecificAttrs())
                .setIsActive(entity.getIsActive())
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime());
    }
}

