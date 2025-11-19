package com.weili.iot_portal.business.device_mgmt.service.support;

import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurvePointVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurveVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class FeedRateCache {

    private final ConcurrentHashMap<String, RealtimeCurveVO> cache = new ConcurrentHashMap<>();

    public void saveLatest(String deviceId, RealtimeCurveVO data) {
        cache.put(buildKey(deviceId), deepCopy(data));
    }

    public Optional<RealtimeCurveVO> getLatest(String deviceId) {
        return Optional.ofNullable(cache.get(buildKey(deviceId))).map(this::deepCopy);
    }

    private String buildKey(String deviceId) {
        return "feedRate:latest:" + deviceId;
    }

    private RealtimeCurveVO deepCopy(RealtimeCurveVO source) {
        RealtimeCurveVO target = new RealtimeCurveVO();
        target.setMetric(source.getMetric());
        target.setStartTs(source.getStartTs());
        target.setEndTs(source.getEndTs());
        if (source.getPoints() != null) {
            List<RealtimeCurvePointVO> points = source.getPoints().stream()
                    .map(point -> {
                        RealtimeCurvePointVO cp = new RealtimeCurvePointVO();
                        cp.setTs(point.getTs());
                        cp.setValue(point.getValue());
                        return cp;
                    })
                    .collect(Collectors.toList());
            target.setPoints(points);
        }
        return target;
    }
}

