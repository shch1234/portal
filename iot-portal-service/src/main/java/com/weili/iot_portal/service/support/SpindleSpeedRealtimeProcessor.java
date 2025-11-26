package com.weili.iot_portal.service.support;

import com.weili.iot_portal.domain.devicemng.RealtimeCurveVO;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.processor.RealtimeIngestionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpindleSpeedRealtimeProcessor implements RealtimeIngestionProcessor {

    private final SpindleSpeedCache spindleSpeedCache;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.SPINDLE_SPEED.equals(eventType);
    }

    @Override
    public void process(RealtimeIngestionEvent event) {
        RealtimeCurveVO curve = (RealtimeCurveVO) event.getPayload();
        spindleSpeedCache.saveLatest(event.getDeviceId(), curve);
    }
}

