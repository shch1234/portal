package com.weili.iot_portal.dal.dataobject.devicemng;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 主轴转速历史 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_spindle_speed_history")
public class SpindleSpeedHistoryDO extends BaseSimpleDO {

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
}

