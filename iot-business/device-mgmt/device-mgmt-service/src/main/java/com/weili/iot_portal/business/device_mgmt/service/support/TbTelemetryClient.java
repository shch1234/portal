package com.weili.iot_portal.business.device_mgmt.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weili.iot_portal.business.device_mgmt.service.config.TbTelemetryProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * ThingsBoard Telemetry 客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TbTelemetryClient {

    private static final String TIMESERIES_PATH = "/api/plugins/telemetry/DEVICE/{deviceId}/values/timeseries";
    private static final String LATEST_PATH = "/api/plugins/telemetry/DEVICE/{deviceId}/values/latest";

    private final TbTelemetryProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public List<TelemetryPoint> queryTelemetry(String deviceId, String key,
                                               Long startTs, Long endTs, Integer limit) {
        if (properties.getUrl() == null) {
            log.warn("ThingsBoard API 未配置，返回空结果");
            return List.of();
        }
        String url = properties.getUrl() + TIMESERIES_PATH.replace("{deviceId}", deviceId);
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getToken() != null) {
            headers.set("X-Authorization", "Bearer " + properties.getToken());
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(builder.toUriString(),
                    HttpMethod.GET, entity, String.class);
            if (!response.hasBody() || response.getBody() == null) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode array = root.path(key);
            List<TelemetryPoint> points = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode item : array) {
                    long ts = item.path("ts").asLong();
                    double value = decodeValue(item.path("value").asText());
                    points.add(new TelemetryPoint(ts, value));
                }
            }
            return points;
        } catch (Exception ex) {
            log.error("查询ThingsBoard遥测失败", ex);
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
     * @param key 遥测键名
     * @return 最新值，如果不存在返回 null
     */
    public Double queryLatestValue(String deviceId, String key) {
        if (properties.getUrl() == null) {
            log.warn("ThingsBoard API 未配置，返回空结果");
            return null;
        }
        String url = properties.getUrl() + LATEST_PATH.replace("{deviceId}", deviceId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("keys", key);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getToken() != null) {
            headers.set("X-Authorization", "Bearer " + properties.getToken());
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(builder.toUriString(),
                    HttpMethod.GET, entity, String.class);
            if (!response.hasBody() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode valueNode = root.path(key);
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                return null;
            }
            // ThingsBoard latest API 返回格式: {"key": {"value": 100.5, "ts": 1234567890}}
            JsonNode value = valueNode.path("value");
            if (value.isMissingNode() || value.isNull()) {
                return null;
            }
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                return decodeValue(value.asText());
            }
            return null;
        } catch (Exception ex) {
            log.error("查询ThingsBoard最新遥测值失败: deviceId={}, key={}", deviceId, key, ex);
            return null;
        }
    }

    /**
     * 批量查询设备的最新遥测值（多个键）
     *
     * @param deviceId ThingsBoard 设备ID
     * @param keys 遥测键名列表
     * @return 键值对映射
     */
    public java.util.Map<String, Double> queryLatestValues(String deviceId, List<String> keys) {
        if (properties.getUrl() == null || CollectionUtils.isEmpty(keys)) {
            log.warn("ThingsBoard API 未配置或键列表为空，返回空结果");
            return java.util.Collections.emptyMap();
        }
        String url = properties.getUrl() + LATEST_PATH.replace("{deviceId}", deviceId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("keys", String.join(",", keys));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getToken() != null) {
            headers.set("X-Authorization", "Bearer " + properties.getToken());
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(builder.toUriString(),
                    HttpMethod.GET, entity, String.class);
            if (!response.hasBody() || response.getBody() == null) {
                return java.util.Collections.emptyMap();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            java.util.Map<String, Double> result = new java.util.HashMap<>();
            for (String key : keys) {
                JsonNode valueNode = root.path(key);
                if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                    // ThingsBoard latest API 返回格式: {"key": {"value": 100.5, "ts": 1234567890}}
                    JsonNode value = valueNode.path("value");
                    if (!value.isMissingNode() && !value.isNull()) {
                        double doubleValue = 0.0;
                        if (value.isNumber()) {
                            doubleValue = value.asDouble();
                        } else if (value.isTextual()) {
                            doubleValue = decodeValue(value.asText());
                        }
                        result.put(key, doubleValue);
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


