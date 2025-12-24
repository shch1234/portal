package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import com.weili.iot_portal.domain.device.req.DeviceToolCompensationQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolCompensationRespVO;
import com.weili.iot_portal.service.device.IDeviceInfoBizService;
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

    private final IDeviceInfoBizService deviceInfoBizService;
    private final DeviceToolCompensationRepository deviceToolCompensationRepository;

    @Override
    public List<DeviceToolCompensationRespVO> getDeviceToolCompensation(DeviceToolCompensationQueryReqVO queryReqVO) {
        Long deviceId = queryReqVO.getDeviceId();
        DeviceInfoDO deviceInfoDO = deviceInfoBizService.getDeviceInfo(deviceId);
        if (deviceInfoDO == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND, "设备不存在");
        }
        Long factoryId = deviceInfoDO.getOrgFactoryId();
        List<DeviceToolCompensationDO> compensationList = deviceToolCompensationRepository
                .findActiveByDevice(factoryId, String.valueOf(deviceId));

        List<DeviceToolCompensationRespVO> items = new ArrayList<>();

        for (DeviceToolCompensationDO compensation : compensationList) {
            try {
                DeviceToolCompensationRespVO item = buildCompensationItem(compensation);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                // 对于不规范的JSON结构，记录日志并跳过该条记录
                log.warn("解析刀具补偿数据失败，刀补号: {}, deviceId: {}, 原因: {}",
                        compensation.getToolHolderNo(), deviceId, e.getMessage());
            }
        }

        return items;
    }

    /**
     * 构建刀具补偿项
     *
     * @throws ClassCastException 当JSON结构不符合预期时抛出
     * @throws NullPointerException 当必要字段为null时抛出
     */
    private DeviceToolCompensationRespVO buildCompensationItem(DeviceToolCompensationDO compensation) {
        Map<String, Object> compValueJson = compensation.getCompValueJson();

        // 验证JSON基本结构
        if (compValueJson == null || compValueJson.isEmpty()) {
            log.warn("刀具补偿JSON为空，刀补号: {}", compensation.getToolHolderNo());
            return null;
        }

        // 提取几何补偿数据（容错处理）
        DeviceToolCompensationRespVO.GeometryCompensation geometry = extractGeometry(compValueJson, compensation.getToolHolderNo());

        // 提取磨损补偿数据（容错处理）
        DeviceToolCompensationRespVO.WearCompensation wear = extractWear(compValueJson, compensation.getToolHolderNo());

        return DeviceToolCompensationRespVO.builder()
                .toolHolderNo(compensation.getToolHolderNo())
                .geometry(geometry)
                .wear(wear)
                .build();
    }

    /**
     * 提取几何补偿数据
     * <p>
     * JSON结构：
     * {
     * "geom": {
     * "offsetX": 0.5,
     * "offsetY": -0.3,
     * "offsetZ": 10.2,
     * "offsetR": 0.0
     * }
     * }
     *
     * @param compValueJson 补偿值JSON
     * @param toolHolderNo 刀补号（用于日志）
     * @return 几何补偿对象
     */
    @SuppressWarnings("unchecked")
    private DeviceToolCompensationRespVO.GeometryCompensation extractGeometry(Map<String, Object> compValueJson, String toolHolderNo) {
        if (compValueJson == null || !compValueJson.containsKey("geom")) {
            log.debug("刀具补偿JSON缺少geom字段，刀补号: {}, 返回默认值", toolHolderNo);
            return buildDefaultGeometry();
        }

        try {
            Object geomObj = compValueJson.get("geom");

            // 验证geom字段类型
            if (!(geomObj instanceof Map)) {
                log.warn("刀具补偿JSON的geom字段不是Map类型，实际类型: {}, 刀补号: {}, 返回默认值",
                        geomObj.getClass().getSimpleName(), toolHolderNo);
                return buildDefaultGeometry();
            }

            Map<String, Object> geom = (Map<String, Object>) geomObj;

            return DeviceToolCompensationRespVO.GeometryCompensation.builder()
                    .offsetX(parseBigDecimal(geom.get("offsetX")))
                    .offsetY(parseBigDecimal(geom.get("offsetY")))
                    .offsetZ(parseBigDecimal(geom.get("offsetZ")))
                    .offsetR(parseBigDecimal(geom.get("offsetR")))
                    .build();
        } catch (ClassCastException e) {
            log.warn("刀具补偿JSON的geom字段结构异常，刀补号: {}, 错误: {}, 返回默认值",
                    toolHolderNo, e.getMessage());
            return buildDefaultGeometry();
        }
    }

    /**
     * 构建默认几何补偿对象
     */
    private DeviceToolCompensationRespVO.GeometryCompensation buildDefaultGeometry() {
        return DeviceToolCompensationRespVO.GeometryCompensation.builder()
                .offsetX(BigDecimal.ZERO)
                .offsetY(BigDecimal.ZERO)
                .offsetZ(BigDecimal.ZERO)
                .offsetR(BigDecimal.ZERO)
                .build();
    }

    /**
     * 提取磨损补偿数据
     * <p>
     * JSON结构：
     * {
     * "wear": {
     * "compX": 0.1,
     * "compY": 0.2,
     * "compZ": 0.0,
     * "compR": 0.0
     * }
     * }
     *
     * @param compValueJson 补偿值JSON
     * @param toolHolderNo 刀补号（用于日志）
     * @return 磨损补偿对象
     */
    @SuppressWarnings("unchecked")
    private DeviceToolCompensationRespVO.WearCompensation extractWear(Map<String, Object> compValueJson, String toolHolderNo) {
        if (compValueJson == null || !compValueJson.containsKey("wear")) {
            log.debug("刀具补偿JSON缺少wear字段，刀补号: {}, 返回默认值", toolHolderNo);
            return buildDefaultWear();
        }

        try {
            Object wearObj = compValueJson.get("wear");

            // 验证wear字段类型
            if (!(wearObj instanceof Map)) {
                log.warn("刀具补偿JSON的wear字段不是Map类型，实际类型: {}, 刀补号: {}, 返回默认值",
                        wearObj.getClass().getSimpleName(), toolHolderNo);
                return buildDefaultWear();
            }

            Map<String, Object> wear = (Map<String, Object>) wearObj;

            return DeviceToolCompensationRespVO.WearCompensation.builder()
                    .compX(parseBigDecimal(wear.get("compX")))
                    .compY(parseBigDecimal(wear.get("compY")))
                    .compZ(parseBigDecimal(wear.get("compZ")))
                    .compR(parseBigDecimal(wear.get("compR")))
                    .build();
        } catch (ClassCastException e) {
            log.warn("刀具补偿JSON的wear字段结构异常，刀补号: {}, 错误: {}, 返回默认值",
                    toolHolderNo, e.getMessage());
            return buildDefaultWear();
        }
    }

    /**
     * 构建默认磨损补偿对象
     */
    private DeviceToolCompensationRespVO.WearCompensation buildDefaultWear() {
        return DeviceToolCompensationRespVO.WearCompensation.builder()
                .compX(BigDecimal.ZERO)
                .compY(BigDecimal.ZERO)
                .compZ(BigDecimal.ZERO)
                .compR(BigDecimal.ZERO)
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
