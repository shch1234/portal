package com.weili.iot_portal.business.device_mgmt.service.support;

import com.weili.iot_portal.business.device_mgmt.domain.enums.ToolCompensationDimension;
import com.weili.iot_portal.business.device_mgmt.domain.model.ToolCompensationSlotVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.ToolCompensationVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolCompensationCache {

    private final ConcurrentHashMap<String, DeviceCompensationData> cache = new ConcurrentHashMap<>();

    public void save(String deviceId,
                     ToolCompensationDimension dimension,
                     Integer slot,
                     Double shapeValue,
                     Double wearValue,
                     Long ts) {
        DeviceCompensationData data = cache.computeIfAbsent(deviceId, key -> new DeviceCompensationData());
        ToolCompensationSlotVO slotVO = new ToolCompensationSlotVO();
        slotVO.setSlot(slot);
        slotVO.setShapeValue(shapeValue);
        slotVO.setWearValue(wearValue);
        slotVO.setTs(ts != null ? ts : System.currentTimeMillis());
        data.put(dimension, slot, slotVO);
    }

    public Optional<ToolCompensationVO> get(String deviceId) {
        DeviceCompensationData data = cache.get(deviceId);
        if (data == null) {
            return Optional.empty();
        }
        ToolCompensationVO vo = new ToolCompensationVO();
        vo.setDeviceId(deviceId);
        vo.setLengthSlots(data.toList(ToolCompensationDimension.LENGTH));
        vo.setRadiusSlots(data.toList(ToolCompensationDimension.RADIUS));
        vo.setLastUpdatedTs(data.getLastUpdatedTs());
        return Optional.of(vo);
    }

    private static class DeviceCompensationData {
        private final ConcurrentHashMap<Integer, ToolCompensationSlotVO> lengthSlots = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, ToolCompensationSlotVO> radiusSlots = new ConcurrentHashMap<>();
        private volatile long lastUpdatedTs;

        void put(ToolCompensationDimension dimension, Integer slot, ToolCompensationSlotVO slotVO) {
            if (dimension == ToolCompensationDimension.RADIUS) {
                radiusSlots.put(slot, slotVO);
            } else {
                lengthSlots.put(slot, slotVO);
            }
            lastUpdatedTs = System.currentTimeMillis();
        }

        List<ToolCompensationSlotVO> toList(ToolCompensationDimension dimension) {
            ConcurrentHashMap<Integer, ToolCompensationSlotVO> map =
                    dimension == ToolCompensationDimension.RADIUS ? radiusSlots : lengthSlots;
            List<ToolCompensationSlotVO> list = new ArrayList<>(map.values());
            list.sort(Comparator.comparing(ToolCompensationSlotVO::getSlot, Comparator.nullsLast(Integer::compareTo)));
            return list;
        }

        long getLastUpdatedTs() {
            return lastUpdatedTs;
        }
    }
}

