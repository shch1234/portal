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
import java.util.Map;

/**
 * 设备产量汇总数据对象（对应 device_production_summary 表）
 * 按班次存储设备产量统计
 */
@Data
@TableName(value = "device_production_summary", autoResultMap = true)
public class DeviceProductionSummaryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 9035405367945829902L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private Long deviceInfoId;

    /**
     * 班次日期（对应 shift_date 列）
     */
    private LocalDate shiftDate;

    /**
     * 班次编码（对应 shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer shiftCode;

    /**
     * 班次开始时间戳（秒，Unix时间戳，对应 shift_start_ts 列）
     */
    private Long shiftStartTs;

    /**
     * 班次结束时间戳（秒，Unix时间戳，对应 shift_end_ts 列）
     */
    private Long shiftEndTs;

    /**
     * 加工数量（对应 part_count 列）
     */
    private Integer partCount;

    /**
     * 合格数量（暂无质量数据时默认等于part_count，对应 qualified_count 列）
     */
    private Integer qualifiedCount;

    /**
     * 不合格数量（对应 defect_count 列）
     */
    private Integer defectCount;

    /**
     * 计数方式（对应 count_method 列）
     * 如：DOOR_SIGNAL-关门信号 CYCLE_SIGNAL-循环信号 MANUAL-手动
     */
    private String countMethod;

    /**
     * 扩展属性（JSON，对应 properties 列）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;

    /**
     * 是否已最终确定：1-已确定 0-待确定（班次结束后由定时任务确定，对应 is_finalized 列）
     */
    private Boolean isFinalized;

    /**
     * 计算时间戳（秒，Unix时间戳，对应 calculated_time 列）
     */
    private Long calculatedTime;
}


