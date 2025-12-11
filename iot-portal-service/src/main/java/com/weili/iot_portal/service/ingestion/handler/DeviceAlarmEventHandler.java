package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备报警事件处理器
 * 支持事件类型：DEVICE_ALARM
 * 事件数据要求：eventData.alarms 为数组，每个元素包含 alarmCode / alarmText / alarmLevel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAlarmEventHandler implements WebhookEventHandler {

    private static final String EVENT_TYPE = "DEVICE_ALARM";

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_ALARM;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        List<Map<String, Object>> alarms = extractAlarms(eventData);
        
        // 解析设备身份
        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getDeviceCode(),
                        request.getDeviceId(), "DeviceAlarmEvent");
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        long eventTs = request.getDataTimestamp() != null ? request.getDataTimestamp()
                : (request.getTimestamp() != null ? request.getTimestamp() : System.currentTimeMillis() / 1000);

        // 当前活跃报警
        List<DeviceAlarmHistoryDO> activeList = deviceAlarmHistoryRepository.findActiveByDevice(orgFactoryId, deviceInfoId);
        Map<String, DeviceAlarmHistoryDO> activeByCode = activeList.stream()
                .filter(a -> StringUtils.isNotBlank(a.getAlarmCode()))
                .collect(Collectors.toMap(DeviceAlarmHistoryDO::getAlarmCode, a -> a, (a, b) -> a));

        Set<String> incomingCodes = new HashSet<>();

        // 新增或更新现有（相同 code 视为同一条报警）
        // 如果alarms为空，incomingCodes也为空，后续会关闭所有活跃报警
        for (Map<String, Object> alarm : alarms) {
            String code = toStr(alarm.get("alarmCode"));
            if (StringUtils.isBlank(code)) {
                continue;
            }
            incomingCodes.add(code);
            DeviceAlarmHistoryDO existing = activeByCode.get(code);
            String text = toStr(alarm.get("alarmText"));
            String level = toStr(alarm.get("alarmLevel"));

            if (existing == null) {
                // 新报警
                DeviceAlarmHistoryDO record = new DeviceAlarmHistoryDO();
                record.setDeviceInfoId(deviceInfoId);
                record.setOrgFactoryId(orgFactoryId);
                record.setAlarmCode(code);
                record.setAlarmText(text);
                record.setAlarmLevel(level);
                record.setStartTs(eventTs);
                record.setEndTs(null);
                record.setDurationS(null);
                record.setIsActive(1);
                deviceAlarmHistoryRepository.insert(record);
                continue;
            }

            // 已存在：如果文本或级别变化，更新之；持续时间在关闭时更新
            boolean needUpdate = false;
            if (!StringUtils.equals(existing.getAlarmText(), text)) {
                existing.setAlarmText(text);
                needUpdate = true;
            }
            if (!StringUtils.equals(existing.getAlarmLevel(), level)) {
                existing.setAlarmLevel(level);
                needUpdate = true;
            }
            if (needUpdate) {
                deviceAlarmHistoryRepository.updateById(existing);
            }
        }

        // 消失的报警：关闭
        // 如果alarms为空（incomingCodes为空），则关闭所有活跃报警
        if (alarms.isEmpty()) {
            log.info("DEVICE_ALARM 事件中无报警数据，关闭所有活跃报警: deviceInfoId={}, activeCount={}", 
                    deviceInfoId, activeByCode.size());
        }
        for (DeviceAlarmHistoryDO existing : activeByCode.values()) {
            if (!incomingCodes.contains(existing.getAlarmCode())) {
                existing.setEndTs(eventTs);
                if (existing.getStartTs() != null) {
                    existing.setDurationS((int) (eventTs - existing.getStartTs()));
                }
                existing.setIsActive(0);
                deviceAlarmHistoryRepository.updateById(existing);
            }
        }
    }

    private List<Map<String, Object>> extractAlarms(Map<String, Object> eventData) {
        Object raw = eventData.get("alarms");
        if (raw instanceof List<?>) {
            List<?> list = (List<?>) raw;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> map) {
                    // 仅保留字符串键
                    Map<String, Object> filtered = new HashMap<>();
                    map.forEach((k, v) -> {
                        if (k instanceof String) {
                            filtered.put((String) k, v);
                        }
                    });
                    result.add(filtered);
                }
            }
            return result;
        }
        // 若是单条对象，也处理
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> filtered = new HashMap<>();
            map.forEach((k, v) -> {
                if (k instanceof String) {
                    filtered.put((String) k, v);
                }
            });
            return Collections.singletonList(filtered);
        }
        return Collections.emptyList();
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }
}


