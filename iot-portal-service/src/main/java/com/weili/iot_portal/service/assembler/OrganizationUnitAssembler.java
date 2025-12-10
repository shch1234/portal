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

    /**
     * 从创建请求转换为 DO（对应 device_org_relation 表）
     */
    public static OrganizationUnitDO fromCreateReq(OrganizationUnitCreateReq request, String path) {
        OrganizationUnitDO entity = new OrganizationUnitDO();
        entity.setId(UUID.randomUUID().toString());
        entity.setUnitCode(request.getUnitCode());
        entity.setUnitName(request.getUnitName());
        entity.setUnitTypeValue(request.getUnitTypeValue());
        entity.setOrgParentId(request.getOrgParentId());
        entity.setLevelNo(request.getLevelNo());
        entity.setPath(path);
        entity.setDescription(request.getDescription());
        entity.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return entity;
    }

    /**
     * 从 DO 转换为 VO（对应 device_org_relation 表）
     */
    public static OrganizationUnitVO toVO(OrganizationUnitDO entity, String parentName) {
        return new OrganizationUnitVO()
                .setId(entity.getId())
                .setUnitCode(entity.getUnitCode())
                .setUnitName(entity.getUnitName())
                .setUnitTypeValue(entity.getUnitTypeValue())
                .setOrgParentId(entity.getOrgParentId())
                .setParentName(parentName)
                .setLevelNo(entity.getLevelNo())
                .setPath(entity.getPath())
                .setDescription(entity.getDescription())
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

