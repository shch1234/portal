package com.weili.iot_portal.web.realtime;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.domain.realtime.RealTimeMetricsVO;
import com.weili.iot_portal.domain.realtime.RealTimeOnlineVO;
import com.weili.iot_portal.domain.realtime.RealTimeAxisVO;
import com.weili.iot_portal.domain.realtime.RealTimeToolVO;
import com.weili.iot_portal.domain.realtime.RealTimeProgramVO;
import com.weili.iot_portal.domain.realtime.RealTimeStateVO;
import com.weili.iot_portal.service.cache.RealTimeCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "实时数据")
@RestController
@RequestMapping("/realtime")
@RequiredArgsConstructor
public class RealTimeDataController {

    private final RealTimeCacheService realTimeCacheService;

    @Value("${rt.stale.threshold-millis:120000}")
    private long staleThresholdMillis;

    @Operation(summary = "获取设备当前状态（实时缓存）")
    @GetMapping("/state")
    public CommonResult<RealTimeStateVO> getState(@RequestParam("factoryId") String factoryId,
                                                  @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String key = formatStateKey(tenantId, factoryId, deviceId);
        Map<String, String> payload = realTimeCacheService.getHash(key);
        if (payload == null || payload.isEmpty()) {
            return CommonResult.success(null);
        }

        RealTimeStateVO vo = new RealTimeStateVO();
        vo.setTenantId(tenantId);
        vo.setFactoryId(factoryId);
        vo.setDeviceId(deviceId);
        vo.setState(payload.get("state"));
        vo.setStateCode(payload.get("stateCode"));
        vo.setSource(payload.get("source"));
        vo.setTraceId(payload.get("traceId"));
        Long updatedAt = parseLong(payload.get("updatedAt"));
        vo.setUpdatedAt(updatedAt);
        vo.setStale(isStale(updatedAt));

        // 其余字段作为 extra 返回
        Map<String, String> extra = new HashMap<>(payload);
        extra.keySet().removeAll(
                java.util.Arrays.asList("state", "stateCode", "updatedAt", "source", "traceId"));
        vo.setExtra(extra);

        return CommonResult.success(vo);
    }

    @Operation(summary = "获取设备指标/数值（实时缓存）")
    @GetMapping("/metrics")
    public CommonResult<RealTimeMetricsVO> getMetrics(@RequestParam("factoryId") String factoryId,
                                                      @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String key = formatMetricKey(tenantId, factoryId, deviceId);
        Map<String, String> payload = realTimeCacheService.getHash(key);
        if (payload == null || payload.isEmpty()) {
            return CommonResult.success(null);
        }

        RealTimeMetricsVO vo = new RealTimeMetricsVO();
        vo.setTenantId(tenantId);
        vo.setFactoryId(factoryId);
        vo.setDeviceId(deviceId);
        vo.setSource(payload.get("source"));
        vo.setTraceId(payload.get("traceId"));
        Long updatedAt = parseLong(payload.get("updatedAt"));
        vo.setUpdatedAt(updatedAt);
        vo.setStale(isStale(updatedAt));

        Map<String, String> metrics = payload.entrySet().stream()
                .filter(e -> e.getKey().startsWith("metric."))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        vo.setMetrics(metrics);

        return CommonResult.success(vo);
    }

    @Operation(summary = "获取设备在线状态（实时缓存）")
    @GetMapping("/online")
    public CommonResult<RealTimeOnlineVO> getOnline(@RequestParam("factoryId") String factoryId,
                                                    @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String key = formatOnlineKey(tenantId, factoryId, deviceId);
        boolean online = realTimeCacheService.hasKey(key);
        Long ttl = realTimeCacheService.getTtlSeconds(key);

        RealTimeOnlineVO vo = new RealTimeOnlineVO();
        vo.setTenantId(tenantId);
        vo.setFactoryId(factoryId);
        vo.setDeviceId(deviceId);
        vo.setOnline(online);
        vo.setTtlSeconds(ttl);
        return CommonResult.success(vo);
    }

    @Operation(summary = "获取设备轴坐标（实时缓存）")
    @GetMapping("/axis")
    public CommonResult<RealTimeAxisVO> getAxis(@RequestParam("factoryId") String factoryId,
                                                @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String key = formatAxisKey(tenantId, factoryId, deviceId);
        Map<String, String> payload = realTimeCacheService.getHash(key);
        if (payload == null || payload.isEmpty()) {
            return CommonResult.success(null);
        }

        RealTimeAxisVO vo = new RealTimeAxisVO();
        vo.setTenantId(tenantId);
        vo.setFactoryId(factoryId);
        vo.setDeviceId(deviceId);
        vo.setSource(payload.get("source"));
        vo.setTraceId(payload.get("traceId"));
        Long updatedAt = parseLong(payload.get("updatedAt"));
        vo.setUpdatedAt(updatedAt);
        vo.setStale(isStale(updatedAt));

        vo.setAxes(payload);
        return CommonResult.success(vo);
    }

    @Operation(summary = "获取设备刀具信息（实时缓存）")
    @GetMapping("/tool")
    public CommonResult<RealTimeToolVO> getTool(@RequestParam("factoryId") String factoryId,
                                                @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String key = String.format(RedisConstant.RT_TOOL,
                defaultBlank(tenantId), defaultBlank(factoryId), defaultBlank(deviceId));
        Map<String, String> payload = realTimeCacheService.getHash(key);
        if (payload == null || payload.isEmpty()) {
            return CommonResult.success(null);
        }

        RealTimeToolVO vo = new RealTimeToolVO();
        vo.setTenantId(tenantId);
        vo.setFactoryId(factoryId);
        vo.setDeviceId(deviceId);
        vo.setSource(payload.get("source"));
        vo.setTraceId(payload.get("traceId"));
        Long updatedAt = parseLong(payload.get("updatedAt"));
        vo.setUpdatedAt(updatedAt);
        vo.setStale(isStale(updatedAt));
        vo.setToolData(payload);
        return CommonResult.success(vo);
    }

    @Operation(summary = "获取设备程序信息（实时缓存）")
    @GetMapping("/program")
    public CommonResult<RealTimeProgramVO> getProgram(@RequestParam("factoryId") String factoryId,
                                                      @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String key = String.format(RedisConstant.RT_PROGRAM,
                defaultBlank(tenantId), defaultBlank(factoryId), defaultBlank(deviceId));
        Map<String, String> payload = realTimeCacheService.getHash(key);
        if (payload == null || payload.isEmpty()) {
            return CommonResult.success(null);
        }

        RealTimeProgramVO vo = new RealTimeProgramVO();
        vo.setTenantId(tenantId);
        vo.setFactoryId(factoryId);
        vo.setDeviceId(deviceId);
        vo.setProgramName(payload.get("programName"));
        vo.setProgramPath(payload.get("programPath"));
        vo.setGCode(payload.get("gCode"));
        vo.setMCode(payload.get("mCode"));
        Long updatedAt = parseLong(payload.get("updatedAt"));
        vo.setUpdatedAt(updatedAt);
        vo.setStale(isStale(updatedAt));
        Map<String, String> extra = new HashMap<>(payload);
        extra.keySet().removeAll(java.util.Arrays.asList("programName", "programPath", "gCode", "mCode", "updatedAt"));
        vo.setExtra(extra);
        return CommonResult.success(vo);
    }

    private boolean isStale(Long updatedAt) {
        if (updatedAt == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        return now - updatedAt > staleThresholdMillis;
    }

    private Long parseLong(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String defaultBlank(String v) {
        return StringUtils.defaultIfBlank(v, "none");
    }

    private String formatStateKey(String tenantId, String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_STATE, defaultBlank(tenantId), defaultBlank(factoryId), defaultBlank(deviceId));
    }

    private String formatMetricKey(String tenantId, String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_METRIC, defaultBlank(tenantId), defaultBlank(factoryId), defaultBlank(deviceId));
    }

    private String formatOnlineKey(String tenantId, String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_ONLINE, defaultBlank(tenantId), defaultBlank(factoryId), defaultBlank(deviceId));
    }

    private String formatAxisKey(String tenantId, String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_AXIS, defaultBlank(tenantId), defaultBlank(factoryId), defaultBlank(deviceId));
    }
}

