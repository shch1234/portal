package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 设备参数配置数据对象（对应 device_param_config 表）
 * 支持历史修订，记录设备参数配置的变更历史
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_param_config")
public class DeviceParamConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1626504502306212875L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private String deviceInfoId;

    /**
     * 参数类型（对应 parameter_type 列）
     * 如：THEORETICAL_CYCLE-理论节拍 PLANNED_DOWNTIME-计划停机时间等
     */
    private String parameterType;

    /**
     * 参数值（数值型，对应 parameter_value 列）
     */
    private BigDecimal parameterValue;

    /**
     * 单位（对应 parameter_unit 列）
     * 如：HOUR-小时 MINUTE-分钟 SECOND-秒 PIECE-件等
     */
    private String parameterUnit;

    /**
     * 参数值（文本型，对应 parameter_text 列）
     */
    private String parameterText;

    /**
     * 生效开始时间戳（秒，Unix时间戳，用于历史修订，对应 effective_start_ts 列）
     */
    private Long effectiveStartTs;

    /**
     * 生效结束时间戳（秒，Unix时间戳，NULL表示当前生效，对应 effective_end_ts 列）
     */
    private Long effectiveEndTs;

    /**
     * 配置说明（对应 description 列）
     */
    private String description;

    /**
     * 是否启用：1-启用 0-停用（对应 is_active 列）
     */
    private Boolean isActive;
}


