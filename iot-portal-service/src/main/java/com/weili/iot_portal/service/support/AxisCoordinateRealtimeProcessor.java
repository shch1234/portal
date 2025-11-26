package com.weili.iot_portal.service.support;

import com.weili.iot_portal.domain.devicemng.AxisCoordinateListVO;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.processor.RealtimeIngestionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 轴坐标实时数据处理器
 */
@Component
@RequiredArgsConstructor
public class AxisCoordinateRealtimeProcessor implements RealtimeIngestionProcessor {

    private final AxisCoordinateCache axisCoordinateCache;

    @Override
    public boolean supports(String eventType) {
        return RealtimeIngestionEventType.AXIS_COORDINATE.equals(eventType);
    }

    @Override
    public void process(RealtimeIngestionEvent event) {
        AxisCoordinateListVO payload = (AxisCoordinateListVO) event.getPayload();
        axisCoordinateCache.save(payload);
    }
}

