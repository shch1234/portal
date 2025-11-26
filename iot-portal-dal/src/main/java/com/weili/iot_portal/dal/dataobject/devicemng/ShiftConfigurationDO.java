package com.weili.iot_portal.dal.dataobject.devicemng;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 班次配置 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_shift_configuration", autoResultMap = true)
public class ShiftConfigurationDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    /**
     * 班次模式：2（2班制）、3（3班制）
     */
    private Integer shiftMode;

    /**
     * 班次定义（JSON数组）
     */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<ShiftDefinition> shifts;

    /**
     * 生效开始时间
     */
    private Long effectiveStartTs;

    /**
     * 生效结束时间（NULL表示当前生效）
     */
    private Long effectiveEndTs;

    /**
     * 是否启用
     */
    private Boolean isActive;

    private String createdBy;

    private String updatedBy;

    /**
     * 班次定义内部类
     */
    @Data
    public static class ShiftDefinition {
        /**
         * 班次编码：SHIFT_1、SHIFT_2、SHIFT_3
         */
        private String code;

        /**
         * 班次名称：早班、中班、晚班
         */
        private String name;

        /**
         * 开始时间：HH:mm:ss
         */
        private String startTime;

        /**
         * 结束时间：HH:mm:ss
         */
        private String endTime;

        /**
         * 持续时长（小时）
         */
        private Integer durationHours;

        /**
         * 是否跨天
         */
        private Boolean crossDay;
    }
}

