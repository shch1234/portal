package com.weili.iot_portal.service.support;

import com.weili.iot_portal.common.enums.ProgramCodeType;
import com.weili.iot_portal.domain.devicemng.ProgramCodeVO;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.processor.RealtimeIngestionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramCodeRealtimeProcessor implements RealtimeIngestionProcessor {

    private final ProgramCodeCache programCodeCache;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.PROGRAM_CODE.equals(eventType);
    }

    @Override
    public void process(RealtimeIngestionEvent event) {
        if (event.getPayload() instanceof ProgramCodeVO code) {
            ProgramCodeType type = code.getType() == null ? ProgramCodeType.G_CODE : code.getType();
            programCodeCache.save(event.getDeviceId(), type, code);
        }
    }
}

