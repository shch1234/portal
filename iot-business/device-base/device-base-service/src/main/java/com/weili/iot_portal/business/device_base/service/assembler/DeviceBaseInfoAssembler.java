package com.weili.iot_portal.business.device_base.service.assembler;

import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_base.domain.model.DeviceBaseInfoListVO;
import com.weili.iot_portal.business.device_base.domain.model.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceBaseInfoBaseReq;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
        if (entity == null) {
            return null;
        }
        return new DeviceBaseInfoVO()
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

    public static List<DeviceBaseInfoVO> toVOList(List<DeviceBaseInfoDO> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(DeviceBaseInfoAssembler::toVO)
                .collect(Collectors.toList());
    }

    public static List<DeviceBaseInfoListVO> toListVOList(List<DeviceBaseInfoDO> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(DeviceBaseInfoAssembler::toListVO)
                .collect(Collectors.toList());
    }

    private static Long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
