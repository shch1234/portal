package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备状态明细数据对象（对应 device_state_record 表）
 * 用于状态时间线与甘特图
 */
@Data
@TableName(value = "device_state_record", autoResultMap = true)
public class DeviceStateRecordDO implements Serializable {

    @Serial
    private static final long serialVersionUID = -5709800285274273654L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private Long deviceInfoId;

    /**
     * 所属厂区ID（关联 device_org_relation.id，冗余字段，优化查询性能，对应 org_factory_id 列）
     */
    private Long orgFactoryId;

    /**
     * 设备状态编码（对应 state_code 列，TINYINT UNSIGNED）
     * 编码映射：0-SHUTDOWN（关机） 1-WORKING（加工中） 2-STANDBY（待机） 3-FAULT（故障） 255-UNKNOWN（未知）
     */
    private Integer stateCode;

    /**
     * 状态开始时间戳（毫秒，Unix时间戳，对应 start_ts 列）
     */
    private Long startTs;

    /**
     * 状态结束时间戳（毫秒，Unix时间戳，NULL表示进行中，对应 end_ts 列）
     */
    private Long endTs;

    /**
     * 持续时长（毫秒，对应 duration_s 列）
     * 注意：时间戳和持续时长都以毫秒为单位存储
     */
    private Long durationS;

    /**
     * 所属班次日期（对应 shift_date 列）
     */
    private LocalDateTime shiftDate;

    /**
     * 班次编码（对应 shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer shiftCode;

    /**
     * 扩展属性（JSON，对应 properties 列）
     * 如：故障代码、工件号等
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;

    /**
     * 是否完整片段：1-完整 0-跨班切分或数据缺失（对应 is_complete 列）
     */
    private Boolean isComplete;
}


