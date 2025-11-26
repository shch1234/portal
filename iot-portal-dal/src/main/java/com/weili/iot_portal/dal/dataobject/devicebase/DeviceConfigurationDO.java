package com.weili.iot_portal.dal.dataobject.devicebase;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Map;

/**
 * 设备配置数据对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_device_base.device_configuration", autoResultMap = true)
public class DeviceConfigurationDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 7639847559023457653L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private String ipAddress;

    private Integer port;

    private String macAddress;

    private String gateway;

    private String subnetMask;

    private String protocol;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> connectionParams;

    private String locationCode;

    private String locationDescription;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> coordinates;

    private String updatedBy;
}

