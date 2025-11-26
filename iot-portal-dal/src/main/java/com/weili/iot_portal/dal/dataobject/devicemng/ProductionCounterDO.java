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
 * 班次产量统计 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_production_counter", autoResultMap = true)
public class ProductionCounterDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = 9035405367945829902L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private LocalDate shiftDate;

    private String shiftCode;

    private Long shiftStartTs;

    private Long shiftEndTs;

    private Integer partCount;

    private Integer qualifiedCount;

    private Integer defectCount;

    private String countMethod;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;

    private Boolean isFinalized;

    private Long calculatedTime;
}


