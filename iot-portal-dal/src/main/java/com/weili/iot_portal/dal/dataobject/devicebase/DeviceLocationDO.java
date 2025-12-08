package com.weili.iot_portal.dal.dataobject.devicebase;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Map;

/**
 * 设备物理位置信息 DO（对应 device_location）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_location", autoResultMap = true)
public class DeviceLocationDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("tenant_uuid")
    private String tenantId;

    @TableField("device_info_id")
    private String deviceId;

    private String locationCode;

    private String locationDescription;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> coordinates;

    private Integer floorNo;

    private String areaCode;

    private Double longitude;

    private Double latitude;

    private Long effectiveStartTs;

    private Long effectiveEndTs;

    @TableField("is_active")
    private Boolean active;

    private String description;
}

