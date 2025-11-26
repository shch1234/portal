package com.weili.iot_portal.service.support;

import com.weili.iot_portal.domain.devicemng.RealtimeCurveVO;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.processor.RealtimeIngestionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedRateRealtimeProcessor implements RealtimeIngestionProcessor {

    private final FeedRateCache feedRateCache;
    private final FeedOverrideCache feedOverrideCache;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.FEED_RATE.equals(eventType);
    }

    @Override
    public void process(RealtimeIngestionEvent event) {
        RealtimeCurveVO curve = (RealtimeCurveVO) event.getPayload();
        feedRateCache.saveLatest(event.getDeviceId(), curve);
        if (curve.getMetric().equals("feedRate") && event.getExtra() instanceof Double overrideValue) {
            feedOverrideCache.save(event.getDeviceId(), overrideValue);
        }
    }
}

