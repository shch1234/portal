package com.weili.iot_portal.service.support;

import com.weili.iot_portal.common.enums.ToolCompensationDimension;
import com.weili.iot_portal.domain.devicemng.ToolCompensationUpdateItem;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.processor.RealtimeIngestionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ToolCompensationRealtimeProcessor implements RealtimeIngestionProcessor {

    private final ToolCompensationCache toolCompensationCache;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.TOOL_COMPENSATION.equals(eventType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void process(RealtimeIngestionEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof List<?> list)) {
            return;
        }
        for (Object itemObj : list) {
            if (itemObj instanceof ToolCompensationUpdateItem item) {
                ToolCompensationDimension dimension =
                        item.getDimension() == null ? ToolCompensationDimension.LENGTH : item.getDimension();
                toolCompensationCache.save(
                        event.getDeviceId(),
                        dimension,
                        item.getSlot(),
                        item.getShapeValue(),
                        item.getWearValue(),
                        item.getTs());
            }
        }
    }
}

