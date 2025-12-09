package com.weili.iot_portal.service.ingestion.handler;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.dal.dataobject.devicemng.ToolCompensationDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.devicemng.ToolCompensationRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.RealTimeCacheService;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 设备刀具事件处理器
 * 处理 DEVICE_TOOL 事件，写入实时刀具缓存（rt:tool）
 *
 * 事件数据建议字段（eventData）：
 * - toolNumber / toolNo / tool_num
 * - holderNumber / toolHolder / holder_num
 * - offsetX / offsetY / offsetZ / offsetR ...（刀补值）
 * - 其他刀具相关字段将原样透出
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceToolEventHandler implements WebhookEventHandler {

    private static final String EVENT_TYPE = "DEVICE_TOOL";

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final RealTimeCacheService realTimeCacheService;
    private final ToolCompensationRepository toolCompensationRepository;

    @Value("${rt.tool.ttl-millis:300000}")
    private long toolTtlMillis;

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return 30; // 在状态、轴之后处理
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "事件数据不能为空");
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getDeviceId(), "DeviceToolEvent");
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        Long eventTimestamp = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : request.getTimestamp();
        if (eventTimestamp == null) {
            eventTimestamp = System.currentTimeMillis();
        }

        Map<String, String> payload = extractToolFields(eventData);
        if (payload.isEmpty()) {
            log.warn("DEVICE_TOOL 事件未包含刀具相关字段，跳过: deviceInfoId={}", deviceInfoId);
            return;
        }
        String holderNumber = payload.getOrDefault("holderNumber", null);
        if (StringUtils.isBlank(holderNumber)) {
            log.warn("DEVICE_TOOL 事件缺少刀补号(holderNumber/toolHolder)，跳过入库: deviceInfoId={}", deviceInfoId);
        }
        payload.put("updatedAt", String.valueOf(eventTimestamp));
        payload.put("source", "TB");
        if (StringUtils.isNotBlank(request.getMessageId())) {
            payload.put("traceId", request.getMessageId());
        }

        String key = String.format(RedisConstant.RT_TOOL,
                defaultBlank(request.getTenantId()), defaultBlank(orgFactoryId), defaultBlank(deviceInfoId));
        realTimeCacheService.hsetWithTtl(key, payload, toolTtlMillis);

        // 写入刀补补偿表（版本化覆盖）
        if (StringUtils.isNotBlank(holderNumber)) {
            Map<String, Object> compValue = extractCompensationValue(eventData);
            upsertCompensation(request.getTenantId(), deviceInfoId, orgFactoryId, holderNumber, compValue, eventTimestamp);
        }
    }

    /**
     * 提取刀具相关字段
     */
    private Map<String, String> extractToolFields(Map<String, Object> eventData) {
        Map<String, String> map = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            if (key.startsWith("tool") || key.startsWith("holder") || key.startsWith("offset")) {
                map.put(key, String.valueOf(v));
            }
            if ("toolNumber".equalsIgnoreCase(key) || "toolNo".equalsIgnoreCase(key) || "tool_num".equalsIgnoreCase(key)) {
                map.put("toolNumber", String.valueOf(v));
            }
            if ("holderNumber".equalsIgnoreCase(key) || "toolHolder".equalsIgnoreCase(key) || "holder_num".equalsIgnoreCase(key)) {
                map.put("holderNumber", String.valueOf(v));
            }
        });
        return map;
    }

    private String defaultBlank(String value) {
        return StringUtils.defaultIfBlank(value, "none");
    }

    /**
     * 提取补偿值：保留所有 offset/comp/holder/tool 字段原样作为 JSON
     */
    private Map<String, Object> extractCompensationValue(Map<String, Object> eventData) {
        Map<String, Object> map = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            if (key.startsWith("offset") || key.startsWith("comp") || key.startsWith("tool") || key.startsWith("holder")) {
                map.put(key, v);
            }
        });
        return map;
    }

    /**
     * 版本化覆盖：相同值跳过，值变更则关老启新
     */
    private void upsertCompensation(String tenantId, String deviceId, String factoryId,
                                    String holderNumber, Map<String, Object> compValue, Long eventTimestamp) {
        ToolCompensationDO active = toolCompensationRepository.findActive(tenantId, deviceId, holderNumber);
        if (active != null && Objects.equals(active.getCompValueJson(), compValue)) {
            // 相同值，直接跳过
            return;
        }

        long ts = eventTimestamp != null ? eventTimestamp : System.currentTimeMillis() / 1000;
        int nextVersion = 1;
        if (active != null) {
            active.setEndTs(ts);
            active.setActive(0);
            toolCompensationRepository.updateById(active);
            nextVersion = (active.getVersion() != null ? active.getVersion() + 1 : 1);
        }

        ToolCompensationDO record = new ToolCompensationDO();
        record.setTenantUuid(tenantId);
        record.setDeviceInfoId(deviceId);
        record.setOrgFactoryId(factoryId);
        record.setToolHolderNo(holderNumber);
        record.setCompValueJson(compValue);
        record.setVersion(nextVersion);
        record.setStartTs(ts);
        record.setEndTs(null);
        record.setActive(1);
        toolCompensationRepository.insert(record);
    }
}

