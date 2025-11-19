package com.weili.iot_portal.business.device_mgmt.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 进给率历史 DO
 */
@Data
@TableName("biz_device_mgmt.feed_rate_history")
public class FeedRateHistoryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private String factoryId;

    private Long sampleTs;

    private Double feedRate;

    private Long createdTime;
}

