package com.weili.iot_portal.service.ingestion.processor;

import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;

/**
 * 实时摄取事件处理器
 */
public interface RealtimeIngestionProcessor {

    /**
     * 是否支持指定事件类型
     */
    boolean supports(String eventType);

    /**
     * 处理事件
     */
    void process(RealtimeIngestionEvent event);
}

