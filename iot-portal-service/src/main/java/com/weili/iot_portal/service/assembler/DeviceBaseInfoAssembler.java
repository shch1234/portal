package com.weili.iot_portal.service.assembler;

import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoListVO;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoBaseReq;
import org.apache.commons.lang3.StringUtils;

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
        return BeanUtils.toBean(entity, DeviceBaseInfoVO.class);
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
}
