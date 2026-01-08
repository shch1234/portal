package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.iot_portal.domain.record.TimeRangeRecord;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;

/**
 * 设备刀具使用记录数据对象（对应 device_tool_record 表）
 */
@Data
@TableName(value = "device_tool_record", autoResultMap = true)
public class DeviceToolRecordDO implements Serializable, TimeRangeRecord {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 刀具编号（刀具唯一标识，用于追踪刀具生命周期，对应 tool_id 列）
     */
    private String toolId;

    /**
     * 刀号（刀具在刀库中的位置号，如T01、T02等，对应 tool_no 列）
     */
    private String toolNo;

    /**
     * 刀套号（对应 tool_magazine_no 列）
     */
    private String toolMagazineNo;

    /**
     * 刀补号（对应 tool_holder_no 列，便于查询）
     */
    private String toolHolderNo;
    /**
     * 刀具类型（对应 tool_type 列）
     * 如：铣刀、钻头、镗刀等
     */
    private String toolType;

    /**
     * 开始使用时间戳（毫秒，Unix时间戳，对应 start_ts 列）
     */
    private Long startTs;

    /**
     * 结束使用时间戳（毫秒，Unix时间戳，NULL表示使用中，对应 end_ts 列）
     */
    private Long endTs;

    /**
     * 使用时长（毫秒，对应 duration_s 列）
     * 注意：虽然数据库列名为 duration_s，但实际存储的是毫秒值
     */
    private Long durationS;

    /**
     * 关联工件号（对应 workpiece_no 列）
     */
    private String workpieceNo;

    /**
     * 关联程序名（对应 program_name 列）
     */
    private String programName;

    /**
     * 使用时的刀具补偿值快照（JSON，对应 compensation_snapshot 列）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> compensationSnapshot;

    /**
     * 所属班次日期（对应 shift_date 列）
     */
    private LocalDate shiftDate;

    /**
     * 班次编码（对应 shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer shiftCode;
}

