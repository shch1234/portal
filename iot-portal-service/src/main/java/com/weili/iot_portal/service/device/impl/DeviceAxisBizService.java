package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.AggregationType;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.device.CurvePointWithTs;
import com.weili.iot_portal.domain.device.req.DeviceAxisQueryReqVO;
import com.weili.iot_portal.domain.device.resp.*;
import com.weili.iot_portal.service.cache.DeviceAxisCacheService;
import com.weili.iot_portal.service.device.IDeviceAxisBizService;
import com.weili.iot_portal.service.device.IDeviceInfoBizService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceAxisEventFields;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备轴标签信息业务服务实现
 */
@Slf4j
@Service
public class DeviceAxisBizService implements IDeviceAxisBizService {


    @Resource
    private IDeviceInfoBizService deviceInfoBizService;
    @Resource
    private DeviceAxisCacheService deviceAxisCacheService;


    @Override
    public DeviceAxisRespVO getDeviceAxisInfo(DeviceAxisQueryReqVO queryReqVO) {
        Long deviceInfoId = queryReqVO.getDeviceId();
        DeviceInfoDO deviceInfo = deviceInfoBizService.getDeviceInfo(deviceInfoId);
        if (deviceInfo == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND, "设备不存在");
        }
        // 获取组织工厂ID
        Long orgFactoryId = deviceInfo.getOrgFactoryId();
        // 获取聚合类型
        AggregationType aggregationType = AggregationType.fromString(queryReqVO.getAggregationType());

        // 1. 获取轴坐标数据
        Map<Object, Object> axisData = deviceAxisCacheService.getAxisData(orgFactoryId, deviceInfoId);
        if (axisData == null || axisData.isEmpty()) {
            return buildEmptyResponse();
        }

        // 2. 解析轴坐标数据
        List<AxisCoordinate> axisCoordinates = parseAxisCoordinates(axisData);

        // 3. 提取倍率值
        Integer ratio = extractRatio(axisData);

        // 4. 获取曲线数据（使用指定的聚合类型）
        SpindleInfo spindleInfo = buildSpindleInfo(orgFactoryId, deviceInfoId, aggregationType);
        return DeviceAxisRespVO.builder()
                .spindleInfo(spindleInfo)
                .axisCoordinates(axisCoordinates)
                .ratio(ratio)
                .build();
    }

    /**
     * 构建空响应
     */
    private DeviceAxisRespVO buildEmptyResponse() {
        return DeviceAxisRespVO.builder()
                .spindleInfo(SpindleInfo.builder().build())
                .axisCoordinates(Collections.emptyList())
                .build();
    }

    /**
     * 解析轴坐标数据（适配优化后的字段名）
     * 优化后格式：X.abs, X.rel, X.mach, X.rem
     */
    private List<AxisCoordinate> parseAxisCoordinates(Map<Object, Object> axisData) {
        // 按轴名称分组：X.abs -> X
        Map<String, Map<String, BigDecimal>> axisMap = new TreeMap<>();

        axisData.forEach((key, value) -> {
            String fieldName = String.valueOf(key);
            // 跳过元数据字段
            if (fieldName.equals(DeviceAxisEventFields.UPDATED_AT) ||
                    fieldName.equals(DeviceAxisEventFields.SOURCE) ||
                    fieldName.equals(DeviceAxisEventFields.TRACE_ID) ||
                    fieldName.equals(DeviceAxisEventFields.RATIO)) {
                return;
            }

            // 解析字段名：X.abs -> [X, abs]
            String[] parts = fieldName.split("\\.");
            if (parts.length == 2) {
                String axisName = parts[0];  // X, Y, Z, A...
                String coordType = parts[1]; // abs, rel, mach, rem

                // 还原完整类型名
                String fullType = expandCoordType(coordType);

                axisMap.putIfAbsent(axisName, new HashMap<>());
                axisMap.get(axisName).put(fullType, parseBigDecimal(value));
            }
        });

        // 转换为响应对象列表
        return axisMap.entrySet().stream()
                .map(entry -> {
                    String axisName = entry.getKey();
                    Map<String, BigDecimal> coords = entry.getValue();
                    return AxisCoordinate.builder()
                            .axisName(axisName)
                            .absolute(roundToThreeDecimals(coords.get("absolute")))
                            .relative(roundToThreeDecimals(coords.get("relative")))
                            .machine(roundToThreeDecimals(coords.get("machine")))
                            .remaining(roundToThreeDecimals(coords.get("remaining")))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 将 BigDecimal 值四舍五入到三位小数
     *
     * @param value 原始值
     * @return 保留三位小数的值，如果原始值为 null 则返回 null
     */
    private BigDecimal roundToThreeDecimals(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 还原坐标类型简称
     * abs -> absolute
     * rel -> relative
     * mach -> machine
     * rem -> remaining
     */
    private String expandCoordType(String shortType) {
        return switch (shortType) {
            case "abs" -> "absolute";
            case "rel" -> "relative";
            case "mach" -> "machine";
            case "rem" -> "remaining";
            default -> shortType;
        };
    }

    /**
     * 构建主轴信息（包含曲线数据）
     */
    private SpindleInfo buildSpindleInfo(Long orgFactoryId, Long deviceInfoId, AggregationType aggregationType) {
        // 获取三条曲线数据，使用指定的聚合类型
        CurveData loadCurve = getCurveData(orgFactoryId, deviceInfoId, DeviceAxisEventFields.METRIC_LOAD, aggregationType);
        CurveData rpmCurve = getCurveData(orgFactoryId, deviceInfoId, DeviceAxisEventFields.METRIC_RPM, aggregationType);
        CurveData feedCurve = getCurveData(orgFactoryId, deviceInfoId, DeviceAxisEventFields.METRIC_FEED, aggregationType);

        return SpindleInfo.builder()
                .loadCurve(loadCurve)
                .rpmCurve(rpmCurve)
                .feedCurve(feedCurve)
                .build();
    }

    /**
     * 获取曲线数据（带时间聚合）
     */
    private CurveData getCurveData(Long orgFactoryId, Long deviceInfoId, String metric, AggregationType aggregationType) {
        // 从Redis获取曲线点（倒序，最新的在前面）
        List<String> curvePoints = deviceAxisCacheService.getCurvePoints(orgFactoryId, deviceInfoId, metric, 0, -1);

        if (curvePoints == null || curvePoints.isEmpty()) {
            return CurveData.builder()
                    .points(Collections.emptyList())
                    .build();
        }

        // 解析为数据点对象（包含ts和value）
        List<CurvePointWithTs> allPoints = curvePoints.stream()
                .map(this::parseCurvePointWithTs)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 按时间间隔聚合
        List<CurvePoint> aggregatedPoints = aggregateCurveByTime(allPoints, aggregationType);

        // 获取当前值（最新值，即第一个点）
        BigDecimal currentValue = allPoints.isEmpty() ? null : allPoints.get(0).value;

        return CurveData.builder()
                .points(aggregatedPoints)
                .currentValue(currentValue)
                .build();
    }

    /**
     * 按时间间隔聚合曲线数据
     * MINUTE: 按分钟聚合，5分钟=5个点
     * TEN_SECONDS: 按10秒聚合，5分钟=30个点
     * 注意：time 字段永远有值，value 可以为 null
     */
    private List<CurvePoint> aggregateCurveByTime(List<CurvePointWithTs> points, AggregationType aggregationType) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }

        int intervalMillis = aggregationType.getIntervalSeconds() * 1000;
        Map<Long, List<CurvePointWithTs>> buckets = new LinkedHashMap<>();

        // 按时间桶分组
        for (CurvePointWithTs point : points) {
            long bucketKey = (point.ts / intervalMillis) * intervalMillis;
            buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(point);
        }

        // 对每个桶计算平均值，并格式化时间
        List<CurvePoint> result = new ArrayList<>();
        for (Map.Entry<Long, List<CurvePointWithTs>> entry : buckets.entrySet()) {
            Long bucketTs = entry.getKey();
            List<CurvePointWithTs> bucketPoints = entry.getValue();

            // 计算平均值（过滤掉 value 为 null 的点）
            List<BigDecimal> validValues = bucketPoints.stream()
                    .map(p -> p.value)
                    .filter(Objects::nonNull).toList();

            BigDecimal avgValue = null;
            if (!validValues.isEmpty()) {
                avgValue = validValues.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(validValues.size()), 2, RoundingMode.HALF_UP);
            }

            // 格式化时间（time 字段永远有值）
            String formattedTime = formatTime(bucketTs, aggregationType);

            result.add(CurvePoint.builder()
                    .time(formattedTime)
                    .value(avgValue)  // value 可以为 null
                    .build());
        }

        // 反转，使最新的在前
        Collections.reverse(result);
        return result;
    }

    /**
     * 格式化时间
     * MINUTE: "HH:mm" (例如 "14:30")
     * TEN_SECONDS: "HH:mm:ss" (例如 "14:30:15")
     */
    private String formatTime(Long timestampMillis, AggregationType aggregationType) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestampMillis),
                ZoneId.systemDefault()
        );

        if (aggregationType == AggregationType.MINUTE) {
            return dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            return dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }


    /**
     * 解析曲线点（带时间戳）
     * 支持 value 为 null 的情况
     */
    private CurvePointWithTs parseCurvePointWithTs(String compactFormat) {
        try {
            String[] parts = compactFormat.split(":");
            if (parts.length == 2) {
                Long ts = Long.parseLong(parts[0]);
                // 支持 value 为 null、空字符串或 "null" 字符串的情况
                BigDecimal value = null;
                if (parts[1] != null && !parts[1].trim().isEmpty() && !"null".equalsIgnoreCase(parts[1].trim())) {
                    value = new BigDecimal(parts[1]);
                }
                return new CurvePointWithTs(ts, value);
            }
        } catch (Exception e) {
            log.warn("解析曲线点紧凑格式失败: {}", compactFormat, e);
        }
        return null;
    }

    /**
     * 提取倍率值
     *
     * @param axisData 轴数据映射
     * @return 倍率值（整数，百分比），如果不存在返回 null
     */
    private Integer extractRatio(Map<Object, Object> axisData) {
        Object ratioObj = axisData.get(DeviceAxisEventFields.RATIO);
        if (ratioObj == null) {
            return null;
        }
        try {
            // 支持多种数据类型：Integer, Long, String, BigDecimal
            if (ratioObj instanceof Integer) {
                return (Integer) ratioObj;
            }
            if (ratioObj instanceof Long) {
                return ((Long) ratioObj).intValue();
            }
            if (ratioObj instanceof Number) {
                return ((Number) ratioObj).intValue();
            }
            // 字符串类型，尝试解析
            String ratioStr = String.valueOf(ratioObj).trim();
            if (ratioStr.isEmpty()) {
                return null;
            }
            // 如果是小数，转换为整数（例如 50.0 -> 50）
            BigDecimal ratioDecimal = new BigDecimal(ratioStr);
            return ratioDecimal.intValue();
        } catch (Exception e) {
            log.warn("解析倍率值失败: {}", ratioObj, e);
            return null;
        }
    }

    /**
     * 转换为BigDecimal
     */
    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
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
            log.debug("转换BigDecimal失败: {}", value);
            return null;
        }
    }
}
