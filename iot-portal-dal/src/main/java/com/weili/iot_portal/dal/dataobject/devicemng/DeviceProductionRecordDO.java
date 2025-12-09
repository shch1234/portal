package com.weili.iot_portal.dal.dataobject.devicemng;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 设备产量明细（device_production_record）
 */
@Data
@TableName("device_production_record")
public class DeviceProductionRecordDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantUuid;

    private String deviceInfoId;

    private String orgFactoryId;

    private Long startTs;

    private Long endTs;

    private Integer durationS;

    private String workpieceNo;

    private String workpieceType;

    private String batchNo;

    private String programName;

    private LocalDate shiftDate;

    private String shiftCode;

    private String countSource;
}


