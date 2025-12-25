package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 设备班次配置数据对象（对应 device_shift_config 表）
 * 存储设备级班次定义（支持2班制/3班制）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_shift_config", autoResultMap = true)
public class DeviceShiftConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private String deviceInfoId;

    /**
     * 所属厂区ID（关联 device_org_relation.id，冗余字段，优化查询性能，对应 org_factory_id 列）
     */
    private String orgFactoryId;

    /**
     * 班次数量：2-2班制 3-3班制（对应 shift_mode 列）
     */
    private Integer shiftMode;

    /**
     * 班次定义（JSON数组，计算字段，不对应数据库列）
     */
    @TableField(exist = false)
    private List<DeviceShiftDefinition> shifts;

    /**
     * 班次1编码：1（对应 shift_1_code 列，TINYINT UNSIGNED）
     */
    @TableField("shift_1_code")
    private Integer shift1Code;

    /**
     * 班次1名称：一班/早班（对应 shift_1_name 列）
     */
    @TableField("shift_1_name")
    private String shift1Name;

    /**
     * 班次1开始时间：08:00:00（对应 shift_1_start_time 列）
     */
    @TableField("shift_1_start_time")
    private String shift1StartTime;

    /**
     * 班次1结束时间：16:00:00（对应 shift_1_end_time 列）
     */
    @TableField("shift_1_end_time")
    private String shift1EndTime;

    /**
     * 班次1时长（秒，对应 shift_1_duration_s 列）
     */
    @TableField("shift_1_duration_s")
    private Integer shift1DurationS;

    /**
     * 班次2编码：2（对应 shift_2_code 列，TINYINT UNSIGNED）
     */
    @TableField("shift_2_code")
    private Integer shift2Code;

    /**
     * 班次2名称：二班/中班（对应 shift_2_name 列）
     */
    @TableField("shift_2_name")
    private String shift2Name;

    /**
     * 班次2开始时间：16:00:00（对应 shift_2_start_time 列）
     */
    @TableField("shift_2_start_time")
    private String shift2StartTime;

    /**
     * 班次2结束时间：00:00:00（对应 shift_2_end_time 列）
     */
    @TableField("shift_2_end_time")
    private String shift2EndTime;

    /**
     * 班次2时长（秒，对应 shift_2_duration_s 列）
     */
    @TableField("shift_2_duration_s")
    private Integer shift2DurationS;

    /**
     * 班次3编码：3（仅3班制时使用，对应 shift_3_code 列，TINYINT UNSIGNED）
     */
    @TableField("shift_3_code")
    private Integer shift3Code;

    /**
     * 班次3名称：三班/晚班（仅3班制时使用，对应 shift_3_name 列）
     */
    @TableField("shift_3_name")
    private String shift3Name;

    /**
     * 班次3开始时间（仅3班制时使用，对应 shift_3_start_time 列）
     */
    @TableField("shift_3_start_time")
    private String shift3StartTime;

    /**
     * 班次3结束时间（仅3班制时使用，对应 shift_3_end_time 列）
     */
    @TableField("shift_3_end_time")
    private String shift3EndTime;

    /**
     * 班次3时长（秒，仅3班制时使用，对应 shift_3_duration_s 列）
     */
    @TableField("shift_3_duration_s")
    private Integer shift3DurationS;

    /**
     * 生效开始时间戳（秒，Unix时间戳，对应 effective_start_ts 列）
     */
    private Long effectiveStartTs;

    /**
     * 生效结束时间戳（秒，Unix时间戳，NULL表示当前生效，对应 effective_end_ts 列）
     */
    private Long effectiveEndTs;

    /**
     * 是否启用：1-启用 0-停用（对应 is_active 列）
     */
    private Boolean isActive;
}

