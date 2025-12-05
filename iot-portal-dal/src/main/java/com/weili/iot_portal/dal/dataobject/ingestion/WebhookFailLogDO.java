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
 * Webhook 失败日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "webhook_fail_log", autoResultMap = true)
public class WebhookFailLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 2113456789012345679L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String messageId;

    private String tenantId;

    private String deviceId;

    private String deviceCode;

    private String eventType;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    private String errorType;

    private String errorMessage;

    private Boolean needManual;

    private Integer retryCount;

    private Boolean recovered;

    private LocalDateTime failedTime;
}

