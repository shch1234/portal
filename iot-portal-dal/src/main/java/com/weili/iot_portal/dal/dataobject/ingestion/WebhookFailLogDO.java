package com.weili.iot_portal.dal.dataobject.ingestion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * Webhook失败日志数据对象（对应 webhook_fail_log 表）
 * 记录处理失败或需人工介入的消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "webhook_fail_log", autoResultMap = true)
public class WebhookFailLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 2113456789012345679L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 消息唯一ID（对应 message_id 列）
     */
    private String messageId;

    /**
     * 租户UUID（对应 tenant_id 列）
     */
    private String tenantUuid;

    /**
     * TB设备ID（对应 device_id 列）
     */
    private String tbDeviceId;

    /**
     * 设备编号（对应 device_code 列）
     */
    private String deviceCode;

    /**
     * 事件类型（对应 event_type 列）
     */
    private String eventType;

    /**
     * 完整载荷（JSON，冗余，对应 payload 列）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /**
     * 错误类型：PROCESS/RETRY/VALIDATION等（对应 error_type 列）
     */
    private String errorType;

    /**
     * 错误详情（对应 error_message 列）
     */
    private String errorMessage;

    /**
     * 是否需要人工处理（对应 need_manual 列）
     */
    private Boolean needManual;

    /**
     * 已重试次数（对应 retry_count 列）
     */
    private Integer retryCount;

    /**
     * 是否已恢复/重新入箱（对应 recovered 列）
     */
    private Boolean recovered;

    /**
     * 失败时间（对应 failed_time 列）
     */
    private LocalDateTime failedTime;
}

