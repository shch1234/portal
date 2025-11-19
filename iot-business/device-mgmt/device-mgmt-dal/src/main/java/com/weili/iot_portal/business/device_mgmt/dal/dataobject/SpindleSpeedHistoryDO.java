package com.weili.iot_portal.business.device_mgmt.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 主轴转速历史 DO
 */
@Data
@TableName("biz_device_mgmt.spindle_speed_history")
public class SpindleSpeedHistoryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private String factoryId;

    /**
     * 点位时间戳
     */
    private Long sampleTs;

    /**
     * 转速
     */
    private Double speed;

    private Long createdTime;
}

