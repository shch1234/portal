package com.weili.iot_portal.service.support;

import com.weili.iot_portal.domain.devicemng.CurrentToolInfoVO;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolInfoCache {

    private final ConcurrentHashMap<String, CurrentToolInfoVO> cache = new ConcurrentHashMap<>();

    public void save(String deviceId, CurrentToolInfoVO info) {
        cache.put(deviceId, info);
    }

    public Optional<CurrentToolInfoVO> get(String deviceId) {
        return Optional.ofNullable(cache.get(deviceId));
    }
}

