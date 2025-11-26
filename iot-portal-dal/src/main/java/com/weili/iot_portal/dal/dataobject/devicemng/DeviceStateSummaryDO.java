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
 * 设备状态汇总 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_state_summary", autoResultMap = true)
public class DeviceStateSummaryDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -1505674693530597958L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private LocalDate summaryDate;

    private String shiftCode;

    private Long shiftStartTs;

    private Long shiftEndTs;

    private Long shiftDurationMs;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> stateStatistics;

    private Long workingDurationMs;

    private Long standbyDurationMs;

    private Long faultDurationMs;

    private Long shutdownDurationMs;

    private Double workingRatio;

    private Double standbyRatio;

    private Double faultRatio;

    private Double shutdownRatio;

    private Boolean isFinalized;

    private Long calculatedTime;

    private Double dataCompleteness;

    private Long missingDataMs;
}


