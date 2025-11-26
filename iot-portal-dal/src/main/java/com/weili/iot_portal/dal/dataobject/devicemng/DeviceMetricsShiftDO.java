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
 * 设备班次指标 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_metrics_shift", autoResultMap = true)
public class DeviceMetricsShiftDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -1760139988580895094L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private LocalDate shiftDate;

    private String shiftCode;

    private Long shiftStartTs;

    private Long shiftEndTs;

    private Long shiftDurationMs;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> calculationData;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parameterSnapshot;

    private Boolean isFinalized;

    private String calculationStatus;

    private Long calculatedTime;

    private String calculationSource;

    private Double dataCompleteness;

    private Long recalculatedAt;

    private String recalculationReason;

    private Integer recalculationCount;
}


