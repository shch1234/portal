package com.weili.iot_portal.service.ingestion.model;

import lombok.Builder;
import lombok.Data;

/**
 * 通用实时摄取事件
 */
@Data
@Builder
public class RealtimeIngestionEvent {

    /**
     * 事件类型（如：axisCoordinate）
     */
    private String eventType;

    /**
     * 幂等ID
     */
    private String messageId;

    private String tenantId;
    private String factoryId;
    private String deviceId;

    /**
     * 数据产生时间
     */
    private Long timestamp;

    /**
     * 负载（由具体处理器自行转换）
     */
    private Object payload;

    /**
     * 额外信息，可选
     */
    private Object extra;
}

