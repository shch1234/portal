package com.weili.iot_portal.dal.dataobject.devicemng;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 设备参数配置 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_parameters_config")
public class DeviceParameterDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1626504502306212875L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private String parameterType;

    private BigDecimal parameterValue;

    private String parameterUnit;

    private String parameterText;

    private Long effectiveStartTs;

    private Long effectiveEndTs;

    private String description;

    private Boolean isActive;

}


