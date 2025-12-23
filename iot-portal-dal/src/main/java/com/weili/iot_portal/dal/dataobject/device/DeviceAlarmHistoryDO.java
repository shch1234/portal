package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

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

    private Long orgFactoryId;

    private String alarmCode;

    private String alarmText;

    private String alarmLevel;

    private Long startTs;

    private Long endTs;

    private Integer durationS;

    private Integer isActive;

    private LocalDate startShiftDate;

    /**
     * 报警开始班次编码（对应 start_shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer startShiftCode;

    private LocalDate endShiftDate;

    /**
     * 报警结束班次编码（对应 end_shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班（NULL表示报警中）
     */
    private Integer endShiftCode;

    private String properties; // JSON 字符串（简化处理）
}


