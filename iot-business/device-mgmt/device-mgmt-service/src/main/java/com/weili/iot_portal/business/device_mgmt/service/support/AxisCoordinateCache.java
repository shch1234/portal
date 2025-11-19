package com.weili.iot_portal.business.device_mgmt.service.support;

import com.weili.iot_portal.business.device_mgmt.domain.model.AxisCoordinateListVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.AxisCoordinateVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 轴坐标数据缓存（仅保留最新一次推送）
 */
@Component
public class AxisCoordinateCache {

    private final ConcurrentHashMap<String, AxisCoordinateListVO> cache = new ConcurrentHashMap<>();

    /**
     * 保存最新的轴坐标数据
     *
     * @param data 最新数据
     */
    public void save(AxisCoordinateListVO data) {
        cache.put(data.getDeviceId(), deepCopy(data));
    }

    /**
     * 获取指定设备的最新轴坐标数据
     *
     * @param deviceId 设备ID
     * @return 轴坐标数据
     */
    public Optional<AxisCoordinateListVO> get(String deviceId) {
        return Optional.ofNullable(cache.get(deviceId)).map(this::deepCopy);
    }

    private AxisCoordinateListVO deepCopy(AxisCoordinateListVO source) {
        AxisCoordinateListVO target = new AxisCoordinateListVO();
        target.setDeviceId(source.getDeviceId());
        target.setQueryTime(source.getQueryTime());
        if (source.getAxes() != null) {
            List<AxisCoordinateVO> axes = source.getAxes()
                    .stream()
                    .map(this::copyAxis)
                    .collect(Collectors.toList());
            target.setAxes(axes);
        }
        return target;
    }

    private AxisCoordinateVO copyAxis(AxisCoordinateVO source) {
        AxisCoordinateVO axis = new AxisCoordinateVO();
        axis.setAxisName(source.getAxisName());
        axis.setAbsoluteCoordinate(source.getAbsoluteCoordinate());
        axis.setRelativeCoordinate(source.getRelativeCoordinate());
        axis.setMachineCoordinate(source.getMachineCoordinate());
        axis.setRemainingCoordinate(source.getRemainingCoordinate());
        return axis;
    }
}

