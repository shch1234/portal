package com.weili.iot_portal.business.device_mgmt.service.support;

import com.weili.iot_portal.business.device_mgmt.domain.model.ProgramInfoVO;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProgramInfoCache {

    private final ConcurrentHashMap<String, ProgramInfoVO> cache = new ConcurrentHashMap<>();

    public void save(String deviceId, ProgramInfoVO info) {
        cache.put(deviceId, info);
    }

    public Optional<ProgramInfoVO> get(String deviceId) {
        return Optional.ofNullable(cache.get(deviceId));
    }
}

