package com.weili.iot_portal.dal.dataobject.devicemng;

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

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantUuid;

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


