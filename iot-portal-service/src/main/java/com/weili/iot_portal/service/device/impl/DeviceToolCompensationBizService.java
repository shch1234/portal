package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import com.weili.iot_portal.domain.device.req.DeviceToolCompensationQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolCompensationRespVO;
import com.weili.iot_portal.service.device.IDeviceToolCompensationBizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 设备刀具补偿业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceToolCompensationBizService implements IDeviceToolCompensationBizService {

    private final DeviceToolCompensationRepository deviceToolCompensationRepository;

    @Override
    public DeviceToolCompensationRespVO getDeviceToolCompensation(DeviceToolCompensationQueryReqVO queryReqVO) {
        Long deviceId = queryReqVO.getDeviceInfoId();
        Long factoryId = queryReqVO.getOrgFactoryId();

        // 查询设备的所有有效刀补记录
        List<DeviceToolCompensationDO> compensationList = deviceToolCompensationRepository
                .findActiveByDevice(factoryId, String.valueOf(deviceId));

        DeviceToolCompensationRespVO respVO = new DeviceToolCompensationRespVO();
        List<DeviceToolCompensationRespVO.CompensationItem> items = new ArrayList<>();

        for (DeviceToolCompensationDO compensation : compensationList) {
            DeviceToolCompensationRespVO.CompensationItem item = buildCompensationItem(compensation);
            items.add(item);
        }

        respVO.setCompensationList(items);
        return respVO;
    }

    /**
     * 构建刀具补偿项
     */
    private DeviceToolCompensationRespVO.CompensationItem buildCompensationItem(DeviceToolCompensationDO compensation) {
        Map<String, Object> compValueJson = compensation.getCompValueJson();

        // 提取几何补偿数据
        DeviceToolCompensationRespVO.GeometryCompensation geometry = extractGeometry(compValueJson);

        // 提取磨损补偿数据
        DeviceToolCompensationRespVO.WearCompensation wear = extractWear(compValueJson);

        return DeviceToolCompensationRespVO.CompensationItem.builder()
                .toolHolderNo(compensation.getToolHolderNo())
                .geometry(geometry)
                .wear(wear)
                .version(compensation.getVersion())
                .startTs(compensation.getStartTs())
                .build();
    }

    /**
     * 提取几何补偿数据
     *
     * JSON结构：
     * {
     *   "geom": {
     *     "offsetX": 0.5,
     *     "offsetY": -0.3,
     *     "offsetZ": 10.2,
     *     "offsetR": 0.0
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private DeviceToolCompensationRespVO.GeometryCompensation extractGeometry(Map<String, Object> compValueJson) {
        if (compValueJson == null || !compValueJson.containsKey("geom")) {
            return DeviceToolCompensationRespVO.GeometryCompensation.builder()
                    .offsetX(BigDecimal.ZERO)
                    .offsetY(BigDecimal.ZERO)
                    .offsetZ(BigDecimal.ZERO)
                    .offsetR(BigDecimal.ZERO)
                    .build();
        }

        Map<String, Object> geom = (Map<String, Object>) compValueJson.get("geom");

        return DeviceToolCompensationRespVO.GeometryCompensation.builder()
                .offsetX(parseBigDecimal(geom.get("offsetX")))
                .offsetY(parseBigDecimal(geom.get("offsetY")))
                .offsetZ(parseBigDecimal(geom.get("offsetZ")))
                .offsetR(parseBigDecimal(geom.get("offsetR")))
                .build();
    }

    /**
     * 提取磨损补偿数据
     *
     * JSON结构：
     * {
     *   "wear": {
     *     "compX": 0.1,
     *     "compY": 0.2,
     *     "compZ": 0.0,
     *     "compR": 0.0
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private DeviceToolCompensationRespVO.WearCompensation extractWear(Map<String, Object> compValueJson) {
        if (compValueJson == null || !compValueJson.containsKey("wear")) {
            return DeviceToolCompensationRespVO.WearCompensation.builder()
                    .compX(BigDecimal.ZERO)
                    .compY(BigDecimal.ZERO)
                    .compZ(BigDecimal.ZERO)
                    .compR(BigDecimal.ZERO)
                    .build();
        }

        Map<String, Object> wear = (Map<String, Object>) compValueJson.get("wear");

        return DeviceToolCompensationRespVO.WearCompensation.builder()
                .compX(parseBigDecimal(wear.get("compX")))
                .compY(parseBigDecimal(wear.get("compY")))
                .compZ(parseBigDecimal(wear.get("compZ")))
                .compR(parseBigDecimal(wear.get("compR")))
                .build();
    }

    /**
     * 转换为BigDecimal
     */
    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            log.warn("转换BigDecimal失败: {}", value);
            return BigDecimal.ZERO;
        }
    }
}
