package com.weili.iot_portal.service.support;

import com.weili.iot_portal.domain.devicemng.CurrentToolInfoVO;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.processor.RealtimeIngestionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ToolInfoRealtimeProcessor implements RealtimeIngestionProcessor {

    private final ToolInfoCache toolInfoCache;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.TOOL_INFO.equals(eventType);
    }

    @Override
    public void process(RealtimeIngestionEvent event) {
        CurrentToolInfoVO payload = (CurrentToolInfoVO) event.getPayload();
        toolInfoCache.save(event.getDeviceId(), payload);
    }
}

