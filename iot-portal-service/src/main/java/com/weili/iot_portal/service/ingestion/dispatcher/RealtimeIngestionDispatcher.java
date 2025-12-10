package com.weili.iot_portal.service.ingestion.dispatcher;

import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 实时摄取事件分发器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public abstract class RealtimeIngestionDispatcher {

    private final List<RealtimeIngestionProcessor> processors;

    public void dispatch(RealtimeIngestionEvent event) {
        boolean handled = false;
        for (RealtimeIngestionProcessor processor : processors) {
            if (processor.supports(event.getEventType())) {
                processor.process(event);
                handled = true;
            }
        }
        if (!handled) {
            log.warn("未找到可处理的实时事件处理器, eventType={}, deviceId={}",
                    event.getEventType(), event.getDeviceId());
        }
    }
}

