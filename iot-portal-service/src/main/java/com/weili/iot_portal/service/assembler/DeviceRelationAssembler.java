package com.weili.iot_portal.service.assembler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceRelationDO;
import com.weili.iot_portal.domain.devicebase.DeviceRelationVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceRelationCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceRelationUpdateReq;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;

/**
 * 设备关系装配器
 */
@UtilityClass
public class DeviceRelationAssembler {

    public static DeviceRelationDO fromCreateReq(String tenantId, DeviceRelationCreateReq request) {
        DeviceRelationDO entity = new DeviceRelationDO();
        entity.setId(IdWorker.getIdStr());
        entity.setTenantId(tenantId);
        entity.setFromDeviceId(request.getFromDeviceId());
        entity.setToDeviceId(request.getToDeviceId());
        entity.setRelationType(request.getRelationType());
        entity.setRelationName(request.getRelationName());
        entity.setDescription(request.getDescription());
        entity.setProperties(request.getProperties());
        entity.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        return entity;
    }

    public static void copyForUpdate(DeviceRelationUpdateReq request, DeviceRelationDO entity) {
        if (StringUtils.isNotBlank(request.getRelationName())) {
            entity.setRelationName(request.getRelationName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getProperties() != null) {
            entity.setProperties(request.getProperties());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
    }

    public static DeviceRelationVO toVO(DeviceRelationDO entity,
                                        DeviceBaseInfoDO fromDevice,
                                        DeviceBaseInfoDO toDevice) {
        DeviceRelationVO vo = new DeviceRelationVO();
        vo.setId(entity.getId());
        vo.setFromDeviceId(entity.getFromDeviceId());
        if (fromDevice != null) {
            vo.setFromDeviceCode(fromDevice.getDeviceCode());
            vo.setFromDeviceName(fromDevice.getDeviceName());
        }
        vo.setToDeviceId(entity.getToDeviceId());
        if (toDevice != null) {
            vo.setToDeviceCode(toDevice.getDeviceCode());
            vo.setToDeviceName(toDevice.getDeviceName());
        }
        vo.setRelationType(entity.getRelationType());
        vo.setRelationName(entity.getRelationName());
        vo.setDescription(entity.getDescription());
        vo.setProperties(entity.getProperties());
        vo.setIsActive(entity.getIsActive());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private static Long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

