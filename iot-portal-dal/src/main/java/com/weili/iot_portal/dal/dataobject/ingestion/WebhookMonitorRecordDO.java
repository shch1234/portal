package com.weili.iot_portal.dal.dataobject.ingestion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * Webhook 监控记录（对应 webhook_monitor_record 表）
 * 记录 webhook 事件匹配、处理成功/失败等指标到数据库，便于持久化观测。
 */
@Data
@EqualsAndHashCode
@TableName("webhook_monitor_record")
public class WebhookMonitorRecordDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 4812234234234234234L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 匹配到的处理器名称（未匹配时为空）
     */
    private String handlerName;

    /**
     * 状态：MATCHED / UNMATCHED / SUCCESS / FAILURE
     */
    private String status;

    /**
     * 处理耗时（毫秒），仅成功/失败时记录
     */
    private Long elapsedMs;

    /**
     * 错误信息（失败时记录）
     */
    private String errorMessage;

    /**
     * 失败时是否会重试
     */
    private Boolean willRetry;
}



