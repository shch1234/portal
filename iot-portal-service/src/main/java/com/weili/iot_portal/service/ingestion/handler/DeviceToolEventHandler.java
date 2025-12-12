package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.cache.DeviceToolCacheService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
import com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils;

import static com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils.DeviceIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 设备刀具事件处理器
 * <p>
 * 处理 DEVICE_TOOL 事件，写入实时刀具缓存（rt:tool）
 * </p>
 * <p>
 * 事件数据建议字段（eventData）：
 * - toolNumber / toolNo / tool_num - 刀具编号
 * - holderNumber / toolHolder / holder_num - 刀架号/刀补号
 * - offsetX / offsetY / offsetZ / offsetR ... - 刀补值（各轴向补偿）
 * - 其他刀具相关字段将原样透出
 * </p>
 * <p>
 * 字段定义请参考：{@link DeviceToolEventFields}
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceToolEventHandler implements WebhookEventHandler {

    private final WebhookHandlerUtils webhookHandlerUtils;
    private final DeviceToolCacheService deviceToolCacheService;
    private final DeviceToolCompensationRepository deviceToolCompensationRepository;

    @Override
    public boolean supports(String eventType) {
        return DeviceToolEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_TOOL;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        DeviceIdentity identity = 
                webhookHandlerUtils.resolveDeviceIdentity(request);
        String deviceInfoId = identity.deviceInfoId();
        String orgFactoryId = identity.orgFactoryId();

        Long eventTimestamp = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : request.getTimestamp();
        if (eventTimestamp == null) {
            eventTimestamp = System.currentTimeMillis();
        }

        Map<String, String> payload = extractToolFields(eventData);
        if (payload.isEmpty()) {
            log.warn("{} 事件未包含刀具相关字段，跳过: deviceInfoId={}", DeviceToolEventFields.EVENT_TYPE, deviceInfoId);
            return;
        }
        String holderNumber = payload.getOrDefault(DeviceToolEventFields.HOLDER_NUMBER, null);
        if (StringUtils.isBlank(holderNumber)) {
            log.warn("{} 事件缺少刀补号({}/{})，跳过入库: deviceInfoId={}",
                    DeviceToolEventFields.EVENT_TYPE,
                    DeviceToolEventFields.HOLDER_NUMBER,
                    DeviceToolEventFields.TOOL_HOLDER,
                    deviceInfoId);
        }
        payload.put(DeviceToolEventFields.UPDATED_AT, String.valueOf(eventTimestamp));
        payload.put(DeviceToolEventFields.SOURCE, DeviceToolEventFields.SOURCE_TB);
        if (StringUtils.isNotBlank(request.getMessageId())) {
            payload.put(DeviceToolEventFields.TRACE_ID, request.getMessageId());
        }

        deviceToolCacheService.saveTool(orgFactoryId, deviceInfoId, payload,
                eventTimestamp, DeviceToolEventFields.SOURCE_TB, request.getMessageId());

        // 写入刀补补偿表（版本化覆盖）
        if (StringUtils.isNotBlank(holderNumber)) {
            Map<String, Object> compValue = extractCompensationValue(eventData);
            upsertCompensation(deviceInfoId, orgFactoryId, holderNumber, compValue, eventTimestamp);
        }
    }

    /**
     * 提取刀具相关字段
     * <p>
     * 提取规则：
     * 1. 提取所有以 tool/holder/offset 开头的字段
     * 2. 将刀具编号的多种别名统一映射为 toolNumber
     * 3. 将刀架号的多种别名统一映射为 holderNumber
     * </p>
     *
     * @param eventData 事件数据
     * @return 提取后的字段映射
     */
    private Map<String, String> extractToolFields(Map<String, Object> eventData) {
        Map<String, String> map = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();

            // 提取所有刀具相关字段（tool/holder/offset 开头）
            if (DeviceToolEventFields.isToolRelatedField(key)) {
                map.put(key, String.valueOf(v));
            }

            // 统一刀具编号字段名
            if (DeviceToolEventFields.isToolNumberField(key)) {
                map.put(DeviceToolEventFields.TOOL_NUMBER, String.valueOf(v));
            }

            // 统一刀架号字段名
            if (DeviceToolEventFields.isHolderNumberField(key)) {
                map.put(DeviceToolEventFields.HOLDER_NUMBER, String.valueOf(v));
            }
        });
        return map;
    }


    /**
     * 提取补偿值：保留所有 offset/comp/holder/tool 字段原样作为 JSON
     * <p>
     * 用于写入刀补补偿表，保留原始字段名和值，不做统一映射
     * </p>
     *
     * @param eventData 事件数据
     * @return 补偿值映射（保留原始字段名）
     */
    private Map<String, Object> extractCompensationValue(Map<String, Object> eventData) {
        Map<String, Object> map = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            // 提取所有补偿相关字段（offset/comp/tool/holder 开头）
            if (DeviceToolEventFields.isCompensationField(key)) {
                map.put(key, v);
            }
        });
        return map;
    }

    /**
     * 版本化覆盖：相同值跳过，值变更则关老启新
     */
    private void upsertCompensation(String deviceId, String factoryId,
                                    String holderNumber, Map<String, Object> compValue, Long eventTimestamp) {
        DeviceToolCompensationDO active = deviceToolCompensationRepository.findActive(deviceId, holderNumber);
        if (active != null && Objects.equals(active.getCompValueJson(), compValue)) {
            // 相同值，直接跳过
            return;
        }

        long ts = eventTimestamp != null ? eventTimestamp : System.currentTimeMillis() / DeviceToolEventFields.MILLIS_TO_SECONDS;
        int nextVersion = DeviceToolEventFields.INITIAL_VERSION;
        if (active != null) {
            active.setEndTs(ts);
            active.setActive(DeviceToolEventFields.ACTIVE_STATUS_DISABLED);
            deviceToolCompensationRepository.updateById(active);
            nextVersion = (active.getVersion() != null ? active.getVersion() + 1 : DeviceToolEventFields.INITIAL_VERSION);
        }

        DeviceToolCompensationDO record = new DeviceToolCompensationDO();
        record.setDeviceInfoId(deviceId);
        record.setOrgFactoryId(factoryId);
        record.setToolHolderNo(holderNumber);
        record.setCompValueJson(compValue);
        record.setVersion(nextVersion);
        record.setStartTs(ts);
        record.setEndTs(null);
        record.setActive(DeviceToolEventFields.ACTIVE_STATUS_ENABLED);
        deviceToolCompensationRepository.insert(record);
    }
}

