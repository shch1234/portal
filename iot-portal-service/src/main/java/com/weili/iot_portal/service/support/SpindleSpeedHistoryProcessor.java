package com.weili.iot_portal.service.support;

import com.weili.iot_portal.dal.dataobject.devicemng.SpindleSpeedHistoryDO;
import com.weili.iot_portal.dal.repository.devicemng.SpindleSpeedHistoryRepository;
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

    /**
     * 构建主轴转速历史 DO（对应 device_spindle_speed_history 表的字段）
     */
    private SpindleSpeedHistoryDO buildDO(RealtimeIngestionEvent event, RealtimeCurvePointVO point) {
        SpindleSpeedHistoryDO record = new SpindleSpeedHistoryDO();
        record.setId(UUID.randomUUID().toString());
        record.setDeviceInfoId(event.getDeviceId());
        record.setSampleTs(point.getTs());
        record.setSpeed(point.getValue());
        return record;
    }
}

