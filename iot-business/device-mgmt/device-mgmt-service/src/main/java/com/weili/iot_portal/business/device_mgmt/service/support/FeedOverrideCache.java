package com.weili.iot_portal.business.device_mgmt.service.support;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeMetricValueVO;
import org.springframework.stereotype.Component;

@Component
public class FeedOverrideCache {

    private final ConcurrentHashMap<String, RealtimeMetricValueVO> cache = new ConcurrentHashMap<>();

    public void save(String deviceId, Double overrideValue) {
        if (overrideValue == null) {
            return;
        }
        RealtimeMetricValueVO vo = new RealtimeMetricValueVO();
        vo.setMetric("feedOverride");
        vo.setValue(overrideValue);
        vo.setTs(System.currentTimeMillis());
        cache.put(deviceId, vo);
    }

    public Optional<RealtimeMetricValueVO> get(String deviceId) {
        return Optional.ofNullable(cache.get(deviceId));
    }
}

