package com.weili.iot_portal.business.device_mgmt.service.support;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.FeedRateHistoryDO;
import com.weili.iot_portal.business.device_mgmt.dal.repository.FeedRateHistoryRepository;
import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurvePointVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurveVO;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.processor.RealtimeIngestionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FeedRateHistoryProcessor implements RealtimeIngestionProcessor {

    private final FeedRateHistoryRepository feedRateHistoryRepository;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.FEED_RATE.equals(eventType);
    }

    @Override
    public void process(RealtimeIngestionEvent event) {
        RealtimeCurveVO curve = (RealtimeCurveVO) event.getPayload();
        List<FeedRateHistoryDO> batch = curve.getPoints().stream()
                .map(point -> buildDO(event, point))
                .collect(Collectors.toList());
        feedRateHistoryRepository.insertBatch(batch);
    }

    private FeedRateHistoryDO buildDO(RealtimeIngestionEvent event, RealtimeCurvePointVO point) {
        FeedRateHistoryDO record = new FeedRateHistoryDO();
        record.setId(UUID.randomUUID().toString());
        record.setTenantId(event.getTenantId());
        record.setDeviceId(event.getDeviceId());
        record.setFactoryId(event.getFactoryId());
        record.setSampleTs(point.getTs());
        record.setFeedRate(point.getValue());
        record.setCreatedTime(System.currentTimeMillis());
        return record;
    }
}

