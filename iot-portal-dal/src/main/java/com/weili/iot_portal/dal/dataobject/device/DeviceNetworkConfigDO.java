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
 * device_network_config 表对应的 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_network_config", autoResultMap = true)
public class DeviceNetworkConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 7639847559023457653L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备信息ID（对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private Long deviceInfoId;

    /**
     * 所属厂区ID（关联 device_org_relation.id，冗余字段，优化查询性能，对应 org_factory_id 列）
     */
    private Long orgFactoryId;

    private String ipAddress;

    private Integer port;

    private String macAddress;

    private String gateway;

    private String subnetMask;

    private String protocol;

    /**
     * 连接参数（JSON，对应 connection_params 列）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> connectionParams;

    private Long effectiveStartTs;

    private Long effectiveEndTs;

    private Boolean isActive;

    private String description;

}

