package com.weili.iot_portal.service.ingestion.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.utils.WebhookTimestampUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceAlarmEventFields;
import com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.model.ShiftDateAndCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils.DeviceIdentity;

/**
 * 设备报警事件处理器
 * <p>
 * 处理设备报警数组变化事件，更新设备报警历史记录。
 * 支持的事件类型：DEVICE_ALARM
 * </p>
 * 
 * <p>
 * 处理流程：
 * 1. 解析事件数据（previousAlarms、currentAlarms、时间戳等）
 * 2. 获取分布式锁
 * 3. 查询数据库最新活跃报警
 * 4. 根据报警匹配情况处理（首次记录、首次连接、正常匹配、报警不匹配等）
 * 5. 更新报警记录（新增、更新、关闭）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAlarmEventHandler implements WebhookEventHandler {

    private final DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;
    private final WebhookHandlerUtils webhookHandlerUtils;
    private final DeviceLockService deviceLockService;
    private final IShiftCalculationService shiftCalculationService;

    @Override
    public boolean supports(String eventType) {
        return DeviceAlarmEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_ALARM;
    }

    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 业务持久化处理：需要写数据库，经过收件箱，支持重试
        return WebhookProcessingStrategy.BUSINESS_PERSISTENT;
    }

    // ==================== 主处理方法 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        log.info("[Webhook-Handler-DeviceAlarm] 处理设备报警事件: messageId={}, eventType={}, deviceCode={}",
                request.getMessageId(), request.getEventType(), request.getDeviceCode());

        // 1. 解析事件数据
        EventData eventData = parseEventData(request);
        
        // 2. 解析设备信息
        DeviceIdentity identity = webhookHandlerUtils.resolveDeviceIdentity(request);
        
        // 3. 使用分布式锁处理报警更新
        processAlarmTransitionWithLock(eventData, identity, request);
    }

    // ==================== 数据解析 ====================

    /**
     * 解析事件数据
     */
    private EventData parseEventData(WebhookRequest request) {
        Map<String, Object> eventDataMap = request.getEventData();
        if (eventDataMap == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        // 提取上一次报警数组（previousAlarms）
        // 优先级：previousAlarms > oldValue
        Object previousAlarmsObj = eventDataMap.get(DeviceAlarmEventFields.PREVIOUS_ALARMS);
        if (previousAlarmsObj == null) {
            previousAlarmsObj = eventDataMap.get("oldValue");
        }
        List<Map<String, Object>> previousAlarms = extractAlarms(previousAlarmsObj);

        // 提取当前报警数组（currentAlarms > alarms > newValue）
        Object currentAlarmsObj = eventDataMap.get(DeviceAlarmEventFields.CURRENT_ALARMS);
        if (currentAlarmsObj == null) {
            currentAlarmsObj = eventDataMap.get(DeviceAlarmEventFields.ALARMS);
        }
        if (currentAlarmsObj == null) {
            currentAlarmsObj = eventDataMap.get("newValue");
        }
        List<Map<String, Object>> currentAlarms = extractAlarms(currentAlarmsObj);
        
        // 如果currentAlarms为空，记录警告日志
        if (currentAlarms.isEmpty()) {
            log.warn("[DeviceAlarmEventHandler] 当前报警数组为空: eventData keys={}, messageId={}",
                    eventDataMap.keySet(), request.getMessageId());
        }

        // 提取时间戳
        Long eventTimestamp = WebhookTimestampUtils.extractDeviceTimestamp(
                eventDataMap, request.getTelemetryData(), request.getDataTimestamp(), request.getTimestamp());
        if (eventTimestamp == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_TIMESTAMP_EMPTY);
        }

        return new EventData(previousAlarms, currentAlarms, eventTimestamp);
    }

    /**
     * 提取报警数组
     * <p>
     * 支持多种格式：
     * 1. List<Map> - 已解析的列表
     * 2. Map - 单条报警对象
     * 3. String - JSON字符串（需要解析）
     * </p>
     */
    private List<Map<String, Object>> extractAlarms(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        
        // 如果是List类型，直接处理
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
        
        // 如果是Map类型，作为单条报警处理
        if (raw instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) raw;
            Map<String, Object> filtered = new HashMap<>();
            map.forEach((k, v) -> {
                if (k instanceof String) {
                    filtered.put((String) k, v);
                }
            });
            return Collections.singletonList(filtered);
        }
        
        // 如果是String类型，尝试解析为JSON
        if (raw instanceof String) {
            String jsonStr = (String) raw;
            if (StringUtils.isBlank(jsonStr)) {
                return Collections.emptyList();
            }
            
            try {
                // 先尝试解析为List
                List<Object> parsedList = JsonUtils.parseObject(jsonStr, new TypeReference<List<Object>>() {});
                if (parsedList != null && !parsedList.isEmpty()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Object o : parsedList) {
                        if (o instanceof Map<?, ?>) {
                            Map<?, ?> map = (Map<?, ?>) o;
                            Map<String, Object> filtered = new HashMap<>();
                            map.forEach((k, v) -> {
                                if (k instanceof String) {
                                    filtered.put((String) k, v);
                                }
                            });
                            result.add(filtered);
                        }
                    }
                    if (!result.isEmpty()) {
                        log.debug("[DeviceAlarmEventHandler] 从JSON字符串解析报警数组: count={}", result.size());
                        return result;
                    }
                }
                
                // 如果解析为List失败，尝试解析为单个Map对象
                Map<String, Object> parsedMap = JsonUtils.parseObject(jsonStr, new TypeReference<Map<String, Object>>() {});
                if (parsedMap != null && !parsedMap.isEmpty()) {
                    log.debug("[DeviceAlarmEventHandler] 从JSON字符串解析单个报警对象");
                    return Collections.singletonList(parsedMap);
                }
            } catch (Exception e) {
                log.warn("[DeviceAlarmEventHandler] 解析报警数组JSON字符串失败: jsonStr={}, error={}", 
                        jsonStr.length() > 100 ? jsonStr.substring(0, 100) + "..." : jsonStr, e.getMessage());
            }
        }
        
        return Collections.emptyList();
    }

    // ==================== 报警转换处理 ====================

    /**
     * 使用分布式锁处理报警转换
     */
    private void processAlarmTransitionWithLock(EventData eventData, DeviceIdentity identity, WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        
        // 获取分布式锁
        if (deviceLockService.tryLockAlarm(deviceInfoId, DeviceAlarmEventFields.LOCK_TIMEOUT_SECONDS)) {
            log.warn("[Webhook-Handler-DeviceAlarm] 获取设备报警锁失败: deviceInfoId={}, messageId={}",
                    deviceInfoId, request.getMessageId());
            throw new IotPortalException(IotPortalErrorCode.EVENT_DEVICE_STATE_PROCESSING);
        }
        try {
            // 查询数据库最新活跃报警
            List<DeviceAlarmHistoryDO> activeList = deviceAlarmHistoryRepository.findActiveByDevice(
                    identity.orgFactoryId(), deviceInfoId);
            
            // 处理报警转换
            processAlarmTransition(activeList, eventData, identity, request);
        } finally {
            deviceLockService.unlockAlarm(deviceInfoId);
        }
    }

    /**
     * 处理报警转换
     */
    private void processAlarmTransition(List<DeviceAlarmHistoryDO> activeList, EventData eventData,
                                         DeviceIdentity identity, WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();
        
        // 构建活跃报警Map（按alarmCode索引）
        Map<String, DeviceAlarmHistoryDO> activeByCode = activeList.stream()
                .filter(a -> StringUtils.isNotBlank(a.getAlarmCode()))
                .collect(Collectors.toMap(DeviceAlarmHistoryDO::getAlarmCode, a -> a, (a, b) -> a));
        
        // 判断处理场景
        TransitionType transitionType = determineTransitionType(activeByCode, eventData);
        
        log.debug("[DeviceAlarmEventHandler] 处理报警转换: 类型={}, deviceInfoId={}, previousAlarmsCount={}, currentAlarmsCount={}",
                transitionType, deviceInfoId, eventData.previousAlarms().size(), eventData.currentAlarms().size());
        
        switch (transitionType) {
            case FIRST_RECORD:
                // 情况D：数据库无记录（首次记录）
                handleFirstRecord(deviceInfoId, orgFactoryId, eventData);
                break;
                
            case FIRST_CONNECTION:
                // 情况A：首次连接（previousAlarms = NULL）
                // 注意：如果数据库中有未结束的报警记录，需要先结束它们
                if (!activeByCode.isEmpty()) {
                    log.warn("[DeviceAlarmEventHandler] 首次连接但数据库中有未结束的报警: deviceInfoId={}, activeCount={}, 将先结束这些报警",
                            deviceInfoId, activeByCode.size());
                    // 先结束未完成的报警
                    for (DeviceAlarmHistoryDO existing : activeByCode.values()) {
                        closeAlarm(existing, eventData.eventTimestamp(), orgFactoryId);
                    }
                }
                handleFirstConnection(deviceInfoId, orgFactoryId, eventData);
                break;
                
            case ALARMS_UNCHANGED:
                // 报警数组未变化（重复的相同报警事件）
                log.debug("[DeviceAlarmEventHandler] 报警数组未变化，跳过处理");
                break;
                
            case NORMAL_TRANSITION:
                // 情况B：正常匹配（previousAlarms == DB最新活跃报警）
                handleNormalTransition(activeByCode, orgFactoryId, eventData);
                break;
                
            case ALARMS_MISMATCH:
                // 情况C：报警不匹配（异常情况）
                log.warn("[DeviceAlarmEventHandler] 报警不匹配: deviceInfoId={}, DB活跃报警数={}, 事件previousAlarms数={}, 事件currentAlarms数={}",
                        deviceInfoId, activeByCode.size(), eventData.previousAlarms().size(), eventData.currentAlarms().size());
                handleAlarmsMismatch(activeByCode, eventData, identity);
                break;
                
            default:
                throw new IllegalStateException("未知的报警转换类型: " + transitionType);
        }
    }

    /**
     * 判断报警转换类型
     */
    private TransitionType determineTransitionType(Map<String, DeviceAlarmHistoryDO> activeByCode, EventData eventData) {
        if (activeByCode.isEmpty()) {
            log.debug("[DeviceAlarmEventHandler] 判断转换类型: FIRST_RECORD (数据库无记录)");
            return TransitionType.FIRST_RECORD;
        }
        
        // 如果previousAlarms为空，但DB中有活跃报警，需要特殊处理
        if (eventData.previousAlarms().isEmpty()) {
            // 检查currentAlarms是否与DB中的活跃报警有交集
            // 如果有交集，说明不是首次连接，而是TB没有发送previousAlarms（可能是ALWAYS模式或配置问题）
            Set<String> currentCodes = eventData.currentAlarms().stream()
                    .map(a -> toStr(a.get(DeviceAlarmEventFields.ALARM_CODE)))
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            
            Set<String> dbCodes = activeByCode.keySet();
            
            // 如果currentAlarms与DB中的活跃报警有交集，说明不是首次连接
            // 这种情况下，应该基于currentAlarms与DB的匹配情况来处理
            boolean hasIntersection = currentCodes.stream().anyMatch(dbCodes::contains);
            
            if (hasIntersection) {
                // 有交集：说明TB没有发送previousAlarms，但这不是首次连接
                // 这种情况下，应该基于currentAlarms与DB的匹配情况来处理
                // 如果currentAlarms包含了所有DB中的活跃报警，且还有新增，则认为是正常过渡
                // 否则认为是报警不匹配
                if (dbCodes.containsAll(currentCodes) || currentCodes.containsAll(dbCodes)) {
                    log.debug("[DeviceAlarmEventHandler] 判断转换类型: NORMAL_TRANSITION (previousAlarms为空但currentAlarms与DB匹配)");
                    return TransitionType.NORMAL_TRANSITION;
                } else {
                    log.debug("[DeviceAlarmEventHandler] 判断转换类型: ALARMS_MISMATCH (previousAlarms为空且currentAlarms与DB不匹配)");
                    return TransitionType.ALARMS_MISMATCH;
                }
            } else {
                // 无交集：真正的首次连接（currentAlarms与DB中的活跃报警完全不同）
                log.debug("[DeviceAlarmEventHandler] 判断转换类型: FIRST_CONNECTION (previousAlarms为空且currentAlarms与DB无交集)");
                return TransitionType.FIRST_CONNECTION;
            }
        }
        
        // 检查报警数组是否未变化
        if (isAlarmsUnchanged(activeByCode, eventData.previousAlarms(), eventData.currentAlarms())) {
            log.debug("[DeviceAlarmEventHandler] 判断转换类型: ALARMS_UNCHANGED (报警数组未变化)");
            return TransitionType.ALARMS_UNCHANGED;
        }
        
        // 检查是否正常匹配（previousAlarms == DB最新活跃报警）
        if (isAlarmsMatched(activeByCode, eventData.previousAlarms())) {
            log.debug("[DeviceAlarmEventHandler] 判断转换类型: NORMAL_TRANSITION (正常匹配)");
            return TransitionType.NORMAL_TRANSITION;
        }
        
        log.debug("[DeviceAlarmEventHandler] 判断转换类型: ALARMS_MISMATCH (报警不匹配)");
        return TransitionType.ALARMS_MISMATCH;
    }

    /**
     * 检查报警数组是否未变化
     */
    private boolean isAlarmsUnchanged(Map<String, DeviceAlarmHistoryDO> activeByCode,
                                       List<Map<String, Object>> previousAlarms,
                                       List<Map<String, Object>> currentAlarms) {
        // 如果previousAlarms和currentAlarms完全相同，则认为未变化
        if (previousAlarms.size() != currentAlarms.size()) {
            return false;
        }
        
        Set<String> previousCodes = previousAlarms.stream()
                .map(a -> toStr(a.get(DeviceAlarmEventFields.ALARM_CODE)))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        
        Set<String> currentCodes = currentAlarms.stream()
                .map(a -> toStr(a.get(DeviceAlarmEventFields.ALARM_CODE)))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        
        if (!previousCodes.equals(currentCodes)) {
            return false;
        }
        
        // 进一步检查内容是否相同（简化处理：只检查code集合是否相同）
        return true;
    }

    /**
     * 检查previousAlarms是否与DB最新活跃报警匹配
     */
    private boolean isAlarmsMatched(Map<String, DeviceAlarmHistoryDO> activeByCode,
                                     List<Map<String, Object>> previousAlarms) {
        if (activeByCode.size() != previousAlarms.size()) {
            return false;
        }
        
        Set<String> dbCodes = activeByCode.keySet();
        Set<String> previousCodes = previousAlarms.stream()
                .map(a -> toStr(a.get(DeviceAlarmEventFields.ALARM_CODE)))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        
        return dbCodes.equals(previousCodes);
    }

    // ==================== 报警处理场景 ====================

    /**
     * 情况A：首次连接（previousAlarms = NULL）
     */
    private void handleFirstConnection(Long deviceInfoId, Long orgFactoryId, EventData eventData) {
        if (eventData.currentAlarms().isEmpty()) {
            log.info("[DeviceAlarmEventHandler] 首次连接但当前报警数组为空，跳过处理: deviceInfoId={}", deviceInfoId);
            return;
        }
        
        // 处理当前报警数组
        log.debug("[DeviceAlarmEventHandler] 首次连接，创建报警记录: deviceInfoId={}, alarmCount={}",
                deviceInfoId, eventData.currentAlarms().size());
        for (Map<String, Object> alarm : eventData.currentAlarms()) {
            String code = toStr(alarm.get(DeviceAlarmEventFields.ALARM_CODE));
            if (StringUtils.isBlank(code)) {
                log.warn("[DeviceAlarmEventHandler] 报警记录缺少alarmCode，跳过: deviceInfoId={}, alarm={}",
                        deviceInfoId, alarm);
                continue;
            }
            createAlarmRecord(deviceInfoId, orgFactoryId, alarm, eventData.eventTimestamp());
        }
    }

    /**
     * 情况B：正常匹配（DB最新活跃报警 == previousAlarms）
     */
    private void handleNormalTransition(Map<String, DeviceAlarmHistoryDO> activeByCode,
                                        Long orgFactoryId, EventData eventData) {
        // 从activeByCode中获取deviceInfoId（所有记录应该有相同的deviceInfoId）
        Long deviceInfoId = activeByCode.isEmpty() ? null : activeByCode.values().iterator().next().getDeviceInfoId();
        
        Set<String> currentCodes = eventData.currentAlarms().stream()
                .map(a -> toStr(a.get(DeviceAlarmEventFields.ALARM_CODE)))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        
        // 处理新增或更新的报警
        for (Map<String, Object> alarm : eventData.currentAlarms()) {
            String code = toStr(alarm.get(DeviceAlarmEventFields.ALARM_CODE));
            if (StringUtils.isBlank(code)) {
                continue;
            }
            
            DeviceAlarmHistoryDO existing = activeByCode.get(code);
            if (existing == null) {
                // 新报警
                if (deviceInfoId == null) {
                    log.warn("[DeviceAlarmEventHandler] 无法创建新报警记录：deviceInfoId为空");
                    continue;
                }
                createAlarmRecord(deviceInfoId, orgFactoryId, alarm, eventData.eventTimestamp());
            } else {
                // 更新现有报警（如果文本或级别变化）
                updateAlarmIfChanged(existing, alarm);
            }
        }
        
        // 处理消失的报警（关闭）
        for (DeviceAlarmHistoryDO existing : activeByCode.values()) {
            if (!currentCodes.contains(existing.getAlarmCode())) {
                closeAlarm(existing, eventData.eventTimestamp(), orgFactoryId);
            }
        }
    }

    /**
     * 情况C：报警不匹配（异常情况）
     */
    private void handleAlarmsMismatch(Map<String, DeviceAlarmHistoryDO> activeByCode,
                                       EventData eventData, DeviceIdentity identity) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();
        
        // 关闭所有现有活跃报警
        for (DeviceAlarmHistoryDO existing : activeByCode.values()) {
            closeAlarm(existing, eventData.eventTimestamp(), orgFactoryId);
        }
        
        // 创建新的报警记录
        for (Map<String, Object> alarm : eventData.currentAlarms()) {
            String code = toStr(alarm.get(DeviceAlarmEventFields.ALARM_CODE));
            if (StringUtils.isBlank(code)) {
                continue;
            }
            createAlarmRecord(deviceInfoId, orgFactoryId, alarm, eventData.eventTimestamp());
        }
        
        // 记录异常日志
        String errorMessage = String.format("报警不匹配: DB活跃报警数=%d, 事件previousAlarms数=%d, 事件currentAlarms数=%d",
                activeByCode.size(), eventData.previousAlarms().size(), eventData.currentAlarms().size());
        log.warn("[DeviceAlarmEventHandler] {}", errorMessage);
    }

    /**
     * 情况D：数据库无记录（首次记录）
     */
    private void handleFirstRecord(Long deviceInfoId, Long orgFactoryId, EventData eventData) {
        if (!eventData.previousAlarms().isEmpty()) {
            log.warn("[DeviceAlarmEventHandler] 数据库无记录但previousAlarms不为空: deviceInfoId={}, previousAlarmsCount={}",
                    deviceInfoId, eventData.previousAlarms().size());
        }

        if (eventData.currentAlarms().isEmpty()) {
            log.info("[DeviceAlarmEventHandler] 数据库无记录但当前报警数组为空，跳过处理: deviceInfoId={}", deviceInfoId);
            return;
        }

        // 处理当前报警数组
        log.debug("[DeviceAlarmEventHandler] 首次记录，创建报警记录: deviceInfoId={}, alarmCount={}",
                deviceInfoId, eventData.currentAlarms().size());
        for (Map<String, Object> alarm : eventData.currentAlarms()) {
            String code = toStr(alarm.get(DeviceAlarmEventFields.ALARM_CODE));
            if (StringUtils.isBlank(code)) {
                log.warn("[DeviceAlarmEventHandler] 报警记录缺少alarmCode，跳过: deviceInfoId={}, alarm={}",
                        deviceInfoId, alarm);
                continue;
            }
            createAlarmRecord(deviceInfoId, orgFactoryId, alarm, eventData.eventTimestamp());
        }
    }

    // ==================== 报警记录操作方法 ====================

    /**
     * 创建报警记录
     */
    private void createAlarmRecord(Long deviceInfoId, Long orgFactoryId,
                                    Map<String, Object> alarm, Long eventTimestamp) {
        String code = toStr(alarm.get(DeviceAlarmEventFields.ALARM_CODE));
        String text = toStr(alarm.get(DeviceAlarmEventFields.ALARM_TEXT));
        String level = toStr(alarm.get(DeviceAlarmEventFields.ALARM_LEVEL));
        
        DeviceAlarmHistoryDO record = new DeviceAlarmHistoryDO();
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        record.setAlarmCode(code);
        record.setAlarmText(text);
        record.setAlarmLevel(level);
        record.setStartTs(eventTimestamp);
        record.setEndTs(null);
        record.setDurationS(null);
        record.setIsActive(DeviceAlarmEventFields.ACTIVE_STATUS_ENABLED);
        
        // 设置班次信息
        if (eventTimestamp != null) {
            try {
                ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(
                        orgFactoryId, deviceInfoId, eventTimestamp);
                record.setStartShiftDate(shiftInfo.shiftDate());
                record.setStartShiftCode(shiftInfo.shiftCode());
            } catch (Exception e) {
                log.warn("[DeviceAlarmEventHandler] 计算班次信息失败: deviceInfoId={}, startTs={}, error={}",
                        deviceInfoId, eventTimestamp, e.getMessage());
            }
        }
        
        deviceAlarmHistoryRepository.insert(record);
        log.debug("[DeviceAlarmEventHandler] 创建报警记录: alarmCode={}, deviceInfoId={}, startTs={}",
                code, deviceInfoId, eventTimestamp);
    }

    /**
     * 更新报警记录（如果文本或级别变化）
     */
    private void updateAlarmIfChanged(DeviceAlarmHistoryDO existing, Map<String, Object> alarm) {
        String text = toStr(alarm.get(DeviceAlarmEventFields.ALARM_TEXT));
        String level = toStr(alarm.get(DeviceAlarmEventFields.ALARM_LEVEL));
        
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
            log.debug("[DeviceAlarmEventHandler] 更新报警记录: alarmCode={}, deviceInfoId={}",
                    existing.getAlarmCode(), existing.getDeviceInfoId());
        }
    }

    /**
     * 关闭报警记录
     */
    private void closeAlarm(DeviceAlarmHistoryDO existing, Long eventTimestamp, Long orgFactoryId) {
        existing.setEndTs(eventTimestamp);
        if (existing.getStartTs() != null) {
            existing.setDurationS((int) (eventTimestamp - existing.getStartTs()));
        }
        existing.setIsActive(DeviceAlarmEventFields.ACTIVE_STATUS_DISABLED);
        
        // 设置结束班次信息
        if (eventTimestamp != null) {
            try {
                ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(
                        orgFactoryId, existing.getDeviceInfoId(), eventTimestamp);
                existing.setEndShiftDate(shiftInfo.shiftDate());
                existing.setEndShiftCode(shiftInfo.shiftCode());
            } catch (Exception e) {
                log.warn("[DeviceAlarmEventHandler] 计算结束班次信息失败: deviceInfoId={}, endTs={}, error={}",
                        existing.getDeviceInfoId(), eventTimestamp, e.getMessage());
            }
        }
        
        // 如果开始班次信息缺失，补充它
        fillShiftInfoIfMissing(existing, orgFactoryId);
        
        deviceAlarmHistoryRepository.updateById(existing);
        log.debug("[DeviceAlarmEventHandler] 关闭报警记录: alarmCode={}, deviceInfoId={}, endTs={}",
                existing.getAlarmCode(), existing.getDeviceInfoId(), eventTimestamp);
    }

    /**
     * 如果班次信息缺失，根据开始时间补充
     */
    private void fillShiftInfoIfMissing(DeviceAlarmHistoryDO record, Long factoryId) {
        if (record.getStartTs() != null
                && (record.getStartShiftDate() == null || record.getStartShiftCode() == null)) {
            try {
                ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(
                        factoryId, record.getDeviceInfoId(), record.getStartTs());
                if (record.getStartShiftDate() == null) {
                    record.setStartShiftDate(shiftInfo.shiftDate());
                }
                if (record.getStartShiftCode() == null) {
                    record.setStartShiftCode(shiftInfo.shiftCode());
                }
            } catch (Exception e) {
                log.warn("[DeviceAlarmEventHandler] 补充班次信息失败: deviceInfoId={}, startTs={}, error={}",
                        record.getDeviceInfoId(), record.getStartTs(), e.getMessage());
            }
        }
    }

    // ==================== 工具方法 ====================

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    // ==================== 内部类 ====================

    /**
     * 事件数据记录
     */
    private record EventData(
            List<Map<String, Object>> previousAlarms,  // 上一次报警数组
            List<Map<String, Object>> currentAlarms,   // 当前报警数组
            Long eventTimestamp                        // 事件时间戳（毫秒）
    ) {}

    /**
     * 报警转换类型
     */
    private enum TransitionType {
        FIRST_RECORD,        // 情况D：数据库无记录
        FIRST_CONNECTION,    // 情况A：首次连接
        ALARMS_UNCHANGED,   // 报警数组未变化
        NORMAL_TRANSITION,  // 情况B：正常匹配
        ALARMS_MISMATCH     // 情况C：报警不匹配
    }
}
