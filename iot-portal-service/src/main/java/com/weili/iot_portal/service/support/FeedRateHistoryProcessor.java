package com.weili.iot_portal.service.support;

import com.weili.iot_portal.dal.dataobject.devicemng.FeedRateHistoryDO;
import com.weili.iot_portal.dal.repository.devicemng.FeedRateHistoryRepository;
import com.weili.iot_portal.domain.devicemng.RealtimeCurvePointVO;
import com.weili.iot_portal.domain.devicemng.RealtimeCurveVO;
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

    /**
     * 构建进给率历史 DO（对应 device_feed_rate_history 表的字段）
     */
    private FeedRateHistoryDO buildDO(RealtimeIngestionEvent event, RealtimeCurvePointVO point) {
        FeedRateHistoryDO record = new FeedRateHistoryDO();
        record.setId(UUID.randomUUID().toString());
        record.setTenantUuid(event.getTenantId());
        record.setDeviceInfoId(event.getDeviceId());
        record.setSampleTs(point.getTs());
        record.setFeedRate(point.getValue());
        return record;
    }
}

