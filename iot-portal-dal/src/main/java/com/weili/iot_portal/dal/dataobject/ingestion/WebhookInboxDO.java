package com.weili.iot_portal.dal.dataobject.ingestion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook收件箱数据对象（对应 webhook_inbox 表）
 * 业务数据入箱等待异步处理
 */
@Data
@EqualsAndHashCode
@TableName(value = "webhook_inbox", autoResultMap = true)
public class WebhookInboxDO {

    @Serial
    private static final long serialVersionUID = 2113456789012345678L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 消息唯一ID（幂等，对应 message_id 列）
     */
    private String messageId;

    /**
     * TB设备ID
     */
    private String tbDeviceId;

    /**
     * 设备编号（必填，对应 device_code 列）
     */
    private String deviceCode;

    /**
     * 事件类型（对应 event_type 列）
     */
    private String eventType;

    /**
     * 分类：BUSINESS/REALTIME（对应 webhook_category 列）
     */
    private String webhookCategory;

    /**
     * 事件载荷（JSON，对应 payload 列）
     * eventData/telemetryData/metadata/transactionInfo
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /**
     * 状态：PENDING/PROCESSING/SUCCESS/FAILED（对应 status 列）
     * @see com.weili.iot_portal.common.enums.InboxStatusEnum
     */
    private String status;

    /**
     * 处理次数（对应 process_count 列）
     */
    private Integer processCount;

    /**
     * 下一次重试时间（对应 next_retry_time 列）
     */
    private LocalDateTime nextRetryTime;

    /**
     * 最后一次错误信息（对应 last_error 列）
     */
    private String lastError;

    /**
     * 接收时间（对应 received_time 列）
     */
    private LocalDateTime receivedTime;

    /**
     * 处理完成时间（对应 processed_time 列）
     */
    private LocalDateTime processedTime;
}

