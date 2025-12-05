package com.weili.iot_portal.dal.dataobject.ingestion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook 收件箱
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "webhook_inbox", autoResultMap = true)
public class WebhookInboxDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 2113456789012345678L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String messageId;

    private String tenantId;

    private String deviceId;

    private String deviceCode;

    private String eventType;

    /**
     * BUSINESS / REALTIME
     */
    private String webhookCategory;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    private String status;

    private Integer processCount;

    private LocalDateTime nextRetryTime;

    private String lastError;

    private LocalDateTime receivedTime;

    private LocalDateTime processedTime;

    /**
     * 最近更新（处理/重试）时间，用于业务更新的时间戳记录
     */
    private LocalDateTime updatedTime;
}

