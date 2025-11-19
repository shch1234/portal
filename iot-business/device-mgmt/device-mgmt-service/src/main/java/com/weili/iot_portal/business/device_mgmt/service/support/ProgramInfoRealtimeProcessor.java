package com.weili.iot_portal.business.device_mgmt.service.support;

import com.weili.iot_portal.business.device_mgmt.domain.model.ProgramInfoVO;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.processor.RealtimeIngestionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramInfoRealtimeProcessor implements RealtimeIngestionProcessor {

    private final ProgramInfoCache programInfoCache;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.PROGRAM_INFO.equals(eventType);
    }

    @Override
    public void process(RealtimeIngestionEvent event) {
        if (event.getPayload() instanceof ProgramInfoVO info) {
            programInfoCache.save(event.getDeviceId(), info);
        }
    }
}

