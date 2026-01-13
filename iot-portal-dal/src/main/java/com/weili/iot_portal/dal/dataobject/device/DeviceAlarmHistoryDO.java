package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;

/**
 * 设备报警历史记录（device_alarm_history）
 */
@Data
@TableName("device_alarm_history")
public class DeviceAlarmHistoryDO implements Serializable {

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
     * 所属厂区ID（关联 org_factory.id，对应 org_factory_id 列）
     */
    private Long orgFactoryId;

    /**
     * 报警编码
     */
    private String alarmCode;

    /**
     * 报警内容
     */
    private String alarmText;

    /**
     * 报警级别：INFO-信息 WARNING-警告 ERROR-错误 CRITICAL-严重
     */
    private String alarmLevel;

    /**
     * 报警开始时间戳（毫秒，Unix时间戳）
     */
    private Long startTs;

    /**
     * 报警结束时间戳（毫秒，Unix时间戳，NULL表示报警中）
     */
    private Long endTs;

    /**
     * 持续时长（豪秒）
     */
    private Integer durationS;

    /**
     * 是否报警中：1-报警中 0-已解除
     */
    private Integer isActive;

    /**
     * 报警开始班次日期
     */
    @TableField(jdbcType = JdbcType.DATE)
    private LocalDate startShiftDate;

    /**
     * 报警开始班次编码（对应 start_shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer startShiftCode;

    /**
     * 报警结束班次日期
     */
    @TableField(jdbcType = JdbcType.DATE)
    private LocalDate endShiftDate;

    /**
     * 报警结束班次编码（对应 end_shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班（NULL表示报警中）
     */
    private Integer endShiftCode;

    /**
     * 报警属性（JSON，对应 properties 列）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;
}


