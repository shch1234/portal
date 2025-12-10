package com.weili.iot_portal.dal.dataobject.device;

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

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 设备信息ID（对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private String deviceInfoId;

    /**
     * 所属厂区ID（关联 device_org_relation.id，冗余字段，优化查询性能，对应 org_factory_id 列）
     */
    private String orgFactoryId;

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

