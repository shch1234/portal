package com.weili.iot_portal.service.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.weili.basic.common.http.OkHttpClientUtils;
import com.weili.iot_portal.common.constant.ApolloConstant;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ThingsBoard Telemetry 客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TbTelemetryClient {

    private static final String TIMESERIES_PATH = "/api/plugins/telemetry/DEVICE/{deviceId}/values/timeseries";
    private static final String LATEST_PATH = "/api/plugins/telemetry/DEVICE/{deviceId}/values/latest";


    public List<TelemetryPoint> queryTelemetry(String deviceId, String key,
                                               Long startTs, Long endTs, Integer limit) {
        String serviceUrl = ApolloConstant.getTBServiceUrl();
        if (serviceUrl == null) {
            log.warn("ThingsBoard API 未配置，返回空结果");
            return List.of();
        }
        String url = serviceUrl + TIMESERIES_PATH.replace("{deviceId}", deviceId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("keys", key);
        if (startTs != null) {
            builder.queryParam("startTs", startTs);
        }
        if (endTs != null) {
            builder.queryParam("endTs", endTs);
        }
        if (limit != null) {
            builder.queryParam("limit", limit);
        }

        Map<String, String> headers = new HashMap<>();
        String tbToken = ApolloConstant.getTBToken();
        if (tbToken != null) {
            headers.put("X-Authorization", "Bearer " + tbToken);
        }
        try {
            String response = OkHttpClientUtils.doGet(builder.toUriString(), headers);
            if (StringUtils.isBlank(response)) {
                return List.of();
            }
            JSONObject root = JSON.parseObject(response);
            JSONArray array = root.getJSONArray(key);
            List<TelemetryPoint> points = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    long ts = item.getLongValue("ts");
                    double value = decodeValue(item.getString("value"));
                    points.add(new TelemetryPoint(ts, value));
                }
            }
            return points;

        } catch (IOException e) {
            log.error("查询ThingsBoard遥测失败", e);
            return List.of();
        }
    }

    private double decodeValue(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    /**
     * 查询设备的最新遥测值（单个键）
     *
     * @param deviceId ThingsBoard 设备ID
     * @param key      遥测键名
     * @return 最新值，如果不存在返回 null
     */
    public Double queryLatestValue(String deviceId, String key) {
        String serviceUrl = ApolloConstant.getTBServiceUrl();
        if (serviceUrl == null) {
            log.warn("ThingsBoard API 未配置，返回空结果");
            return null;
        }
        String url = serviceUrl + LATEST_PATH.replace("{deviceId}", deviceId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("keys", key);

        Map<String, String> headers = new HashMap<>();
        String tbToken = ApolloConstant.getTBToken();
        if (tbToken != null) {
            headers.put("X-Authorization", "Bearer " + tbToken);
        }

        try {
            String response = OkHttpClientUtils.doGet(builder.toUriString(), headers);
            if (StringUtils.isBlank(response)) {
                return null;
            }
            JSONObject root = JSON.parseObject(response);
            JSONObject valueObject = root.getJSONObject(key);
            if (valueObject == null) {
                return null;
            }
            // ThingsBoard latest API 返回格式: {"key": {"value": 100.5, "ts": 1234567890}}
            String valueStr = valueObject.getString("value");
            if (valueStr == null) {
                return null;
            }
            return decodeValue(valueStr);
        } catch (Exception ex) {
            log.error("查询ThingsBoard最新遥测值失败: deviceId={}, key={}", deviceId, key, ex);
            return null;
        }
    }


    /**
     * 批量查询设备的最新遥测值（多个键）
     *
     * @param deviceId ThingsBoard 设备ID
     * @param keys     遥测键名列表
     * @return 键值对映射
     */
    public java.util.Map<String, Double> queryLatestValues(String deviceId, List<String> keys) {
        String serviceUrl = ApolloConstant.getTBServiceUrl();
        if (serviceUrl == null || CollectionUtils.isEmpty(keys)) {
            log.warn("ThingsBoard API 未配置或键列表为空，返回空结果");
            return java.util.Collections.emptyMap();
        }
        String url = serviceUrl + LATEST_PATH.replace("{deviceId}", deviceId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("keys", String.join(",", keys));

        Map<String, String> headers = new HashMap<>();
        String tbToken = ApolloConstant.getTBToken();
        if (tbToken != null) {
            headers.put("X-Authorization", "Bearer " + tbToken);
        }

        try {
            String response = OkHttpClientUtils.doGet(builder.toUriString(), headers);
            if (StringUtils.isBlank(response)) {
                return java.util.Collections.emptyMap();
            }
            JSONObject root = JSON.parseObject(response);
            java.util.Map<String, Double> result = new java.util.HashMap<>();
            for (String key : keys) {
                JSONObject valueObject = root.getJSONObject(key);
                if (valueObject != null) {
                    // ThingsBoard latest API 返回格式: {"key": {"value": 100.5, "ts": 1234567890}}
                    String valueStr = valueObject.getString("value");
                    if (valueStr != null) {
                        result.put(key, decodeValue(valueStr));
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            log.error("批量查询ThingsBoard最新遥测值失败: deviceId={}, keys={}", deviceId, keys, ex);
            return java.util.Collections.emptyMap();
        }
    }

    @Data
    public static class TelemetryPoint {
        private final long ts;
        private final double value;
    }
}


