package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.ToolCompensationDO;
import com.weili.iot_portal.dal.repository.devicemng.ToolCompensationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ToolCompensationQueryService {

    private final ToolCompensationRepository toolCompensationRepository;

    /**
     * 查询设备的所有有效刀补记录（可按工厂过滤）
     */
    public List<ToolCompensationDO> listActive(String factoryId, String deviceId) {
        return toolCompensationRepository.findActiveByDevice(factoryId, deviceId);
    }
}


