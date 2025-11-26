package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.dal.dataobject.devicebase.OrganizationUnitDO;
import com.weili.iot_portal.domain.devicebase.OrganizationUnitVO;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitCreateReq;

import java.time.ZoneId;
import java.util.UUID;

/**
 * 组织单元装配器
 */
public final class OrganizationUnitAssembler {

    private OrganizationUnitAssembler() {
    }

    public static OrganizationUnitDO fromCreateReq(String tenantId,OrganizationUnitCreateReq request, String path) {
        OrganizationUnitDO entity = new OrganizationUnitDO();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(tenantId);
        entity.setUnitCode(request.getUnitCode());
        entity.setUnitName(request.getUnitName());
        entity.setUnitType(request.getUnitType());
        entity.setParentId(request.getParentId());
        entity.setLevel(request.getLevel());
        entity.setPath(path);
        entity.setDescription(request.getDescription());
        entity.setLocation(request.getLocation());
        entity.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return entity;
    }

    public static OrganizationUnitVO toVO(OrganizationUnitDO entity, String parentName) {
        return new OrganizationUnitVO()
                .setId(entity.getId())
                .setUnitCode(entity.getUnitCode())
                .setUnitName(entity.getUnitName())
                .setUnitType(entity.getUnitType())
                .setParentId(entity.getParentId())
                .setParentName(parentName)
                .setLevel(entity.getLevel())
                .setPath(entity.getPath())
                .setDescription(entity.getDescription())
                .setLocation(entity.getLocation())
                .setIsActive(entity.getIsActive())
                .setSortOrder(entity.getSortOrder())
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime());
    }

    private static Long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

