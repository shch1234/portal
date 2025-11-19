package com.weili.iot_portal.business.device_mgmt.service.assembler;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceModelDO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceBaseInfoListVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoBaseReq;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 设备基础信息转换器
 */
public final class DeviceBaseInfoAssembler {

    private DeviceBaseInfoAssembler() {
    }

    public static DeviceBaseInfoDO fromCreateReq(DeviceBaseInfoBaseReq req, Supplier<String> idSupplier) {
        DeviceBaseInfoDO entity = new DeviceBaseInfoDO();
        entity.setId(Optional.ofNullable(idSupplier).map(Supplier::get).orElseGet(() -> UUID.randomUUID().toString()));
        applyCommonFields(req, entity);
        return entity;
    }

    public static void copyForUpdate(DeviceBaseInfoBaseReq req, DeviceBaseInfoDO target) {
        applyCommonFields(req, target);
    }

    private static void applyCommonFields(DeviceBaseInfoBaseReq req, DeviceBaseInfoDO target) {
        target.setDeviceCode(req.getDeviceCode());
        target.setDeviceName(req.getDeviceName());
        target.setDeviceTypeId(req.getDeviceTypeId());
        target.setDeviceModelId(req.getDeviceModelId());
        target.setTbDeviceId(req.getTbDeviceId());
        target.setDeviceTypeName(req.getDeviceTypeName());
        target.setDeviceSubTypeName(req.getDeviceSubTypeName());
        target.setModelName(req.getModelName());
        target.setManufacturer(req.getManufacturer());
        target.setFactoryId(req.getFactoryId());
        target.setWorkshopId(req.getWorkshopId());
        target.setProductionLineId(req.getProductionLineId());
        target.setFactoryName(req.getFactoryName());
        target.setWorkshopName(req.getWorkshopName());
        target.setProductionLineName(req.getProductionLineName());
        target.setDeviceStatus(StringUtils.defaultIfBlank(req.getDeviceStatus(), "INACTIVE"));
        target.setIsMonitored(req.getIsMonitored() == null ? Boolean.TRUE : req.getIsMonitored());
        target.setRemarks(req.getRemarks());
        target.setExtraProperties(req.getExtraProperties());
    }

    public static DeviceBaseInfoVO toVO(DeviceBaseInfoDO entity) {
        return toVO(entity, null);
    }

    /**
     * 转换为VO，包含关联的设备型号信息
     *
     * @param entity 设备基础信息
     * @param deviceModel 设备型号（可选，用于获取数控系统、控制器型号）
     * @return 设备基础信息VO
     */
    public static DeviceBaseInfoVO toVO(DeviceBaseInfoDO entity, DeviceModelDO deviceModel) {
        if (entity == null) {
            return null;
        }
        
        DeviceBaseInfoVO vo = new DeviceBaseInfoVO()
                .setId(entity.getId())
                .setTenantId(entity.getTenantId())
                .setTbDeviceId(entity.getTbDeviceId())
                .setDeviceCode(entity.getDeviceCode())
                .setDeviceName(entity.getDeviceName())
                .setDeviceTypeId(entity.getDeviceTypeId())
                .setDeviceModelId(entity.getDeviceModelId())
                .setDeviceTypeName(entity.getDeviceTypeName())
                .setDeviceSubTypeName(entity.getDeviceSubTypeName())
                .setModelName(entity.getModelName())
                .setManufacturer(entity.getManufacturer())
                .setFactoryId(entity.getFactoryId())
                .setWorkshopId(entity.getWorkshopId())
                .setProductionLineId(entity.getProductionLineId())
                .setFactoryName(entity.getFactoryName())
                .setWorkshopName(entity.getWorkshopName())
                .setProductionLineName(entity.getProductionLineName())
                .setDeviceStatus(entity.getDeviceStatus())
                .setIsMonitored(entity.getIsMonitored())
                .setExtraProperties(entity.getExtraProperties())
                .setRemarks(entity.getRemarks())
                .setCreatedTime(toEpochMilli(entity.getCreateTime()))
                .setUpdatedTime(toEpochMilli(entity.getUpdateTime()))
                .setCreatedBy(entity.getCreatedBy())
                .setUpdatedBy(entity.getUpdatedBy());
        
        // 从设备型号中提取数控系统和控制器型号
        if (deviceModel != null && deviceModel.getTypeSpecificAttrs() != null) {
            Map<String, Object> typeAttrs = deviceModel.getTypeSpecificAttrs();
            if (typeAttrs.containsKey("cncSystem")) {
                Object cncSystem = typeAttrs.get("cncSystem");
                vo.setCncSystem(cncSystem != null ? String.valueOf(cncSystem) : null);
            }
            if (typeAttrs.containsKey("controllerModel")) {
                Object controllerModel = typeAttrs.get("controllerModel");
                vo.setControllerModel(controllerModel != null ? String.valueOf(controllerModel) : null);
            }
        }
        
        return vo;
    }

    public static DeviceBaseInfoListVO toListVO(DeviceBaseInfoDO entity) {
        if (entity == null) {
            return null;
        }
        return new DeviceBaseInfoListVO()
                .setId(entity.getId())
                .setDeviceCode(entity.getDeviceCode())
                .setDeviceTypeName(entity.getDeviceTypeName())
                .setDeviceSubTypeName(entity.getDeviceSubTypeName())
                .setModelName(entity.getModelName())
                .setWorkshopName(entity.getWorkshopName());
    }

    private static Long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}


