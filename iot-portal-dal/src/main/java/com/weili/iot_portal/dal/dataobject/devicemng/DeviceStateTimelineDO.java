package com.weili.iot_portal.dal.dataobject.devicemng;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Map;

/**
 * 设备状态时间线 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_state_timeline", autoResultMap = true)
public class DeviceStateTimelineDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -5709800285274273654L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private String state;

    private Long startTs;

    private Long endTs;

    private Long durationMs;

    private LocalDate shiftDate;

    private String shiftCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;

    private Boolean isComplete;
}


