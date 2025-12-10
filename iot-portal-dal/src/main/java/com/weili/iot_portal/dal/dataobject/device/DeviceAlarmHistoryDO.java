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
    private String id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private String deviceInfoId;

    private String orgFactoryId;

    private String alarmCode;

    private String alarmText;

    private String alarmLevel;

    private Long startTs;

    private Long endTs;

    private Integer durationS;

    private Integer isActive;

    private LocalDate startShiftDate;

    private String startShiftCode;

    private LocalDate endShiftDate;

    private String endShiftCode;

    private String properties; // JSON 字符串（简化处理）
}


