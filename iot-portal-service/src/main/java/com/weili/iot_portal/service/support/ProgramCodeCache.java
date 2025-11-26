package com.weili.iot_portal.service.support;

import com.weili.iot_portal.common.enums.ProgramCodeType;
import com.weili.iot_portal.domain.devicemng.ProgramCodeVO;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProgramCodeCache {

    private final ConcurrentHashMap<String, ProgramCodeVO> gCodeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProgramCodeVO> mCodeCache = new ConcurrentHashMap<>();

    public void save(String deviceId, ProgramCodeType type, ProgramCodeVO code) {
        if (type == ProgramCodeType.M_CODE) {
            mCodeCache.put(deviceId, code);
        } else {
            gCodeCache.put(deviceId, code);
        }
    }

    public Optional<ProgramCodeVO> get(String deviceId, ProgramCodeType type) {
        return Optional.ofNullable(type == ProgramCodeType.M_CODE
                ? mCodeCache.get(deviceId)
                : gCodeCache.get(deviceId));
    }
}

