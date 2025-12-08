package com.weili.iot_portal.dal.dataobject.devicemng;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 主轴转速历史数据对象（对应 device_spindle_speed_history 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_spindle_speed_history")
public class SpindleSpeedHistoryDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 租户UUID（对应 tenant_uuid 列）
     */
    private String tenantUuid;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     */
    private String deviceInfoId;

    /**
     * 采样时间戳（秒，Unix时间戳，对应 sample_ts 列）
     */
    private Long sampleTs;

    /**
     * 转速（对应 speed 列）
     */
    private Double speed;
}

