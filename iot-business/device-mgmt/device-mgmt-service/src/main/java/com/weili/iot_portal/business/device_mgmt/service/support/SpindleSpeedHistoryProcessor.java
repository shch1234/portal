package com.weili.iot_portal.business.device_mgmt.service.support;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.SpindleSpeedHistoryDO;
import com.weili.iot_portal.business.device_mgmt.dal.repository.SpindleSpeedHistoryRepository;
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
public class SpindleSpeedHistoryProcessor implements RealtimeIngestionProcessor {

    private final SpindleSpeedHistoryRepository spindleSpeedHistoryRepository;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.SPINDLE_SPEED.equals(eventType);
    }

    @Override
    public void process(RealtimeIngestionEvent event) {
        RealtimeCurveVO curve = (RealtimeCurveVO) event.getPayload();
        List<SpindleSpeedHistoryDO> batch = curve.getPoints().stream()
                .map(point -> buildDO(event, point))
                .collect(Collectors.toList());
        spindleSpeedHistoryRepository.insertBatch(batch);
    }

    private SpindleSpeedHistoryDO buildDO(RealtimeIngestionEvent event, RealtimeCurvePointVO point) {
        SpindleSpeedHistoryDO record = new SpindleSpeedHistoryDO();
        record.setId(UUID.randomUUID().toString());
        record.setTenantId(event.getTenantId());
        record.setDeviceId(event.getDeviceId());
        record.setFactoryId(event.getFactoryId());
        record.setSampleTs(point.getTs());
        record.setSpeed(point.getValue());
        record.setCreatedTime(System.currentTimeMillis());
        return record;
    }
}

