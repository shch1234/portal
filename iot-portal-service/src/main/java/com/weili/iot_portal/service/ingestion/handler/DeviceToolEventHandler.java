package com.weili.iot_portal.service.ingestion.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceToolCacheService;
import com.weili.iot_portal.service.device.DeviceToolCompensationService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备刀具事件处理器
 * <p>
 * 处理 DEVICE_TOOL 事件，写入实时刀具缓存（rt:tool）
 * </p>
 * <p>
 * 事件数据建议字段（eventData）：
 * - toolNo - 刀具编号（统一使用此字段名，与数据库保持一致；允许值为0，0表示"未使用刀具"，这是一个有效的状态）
 * - holderNumber / hNo / toolEdgeNumber / dNo - 刀补号（值为0时表示未使用刀补，不处理）
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
    private final DeviceToolCompensationService deviceToolCompensationService;
    private final DeviceToolRecordRepository deviceToolRecordRepository;

    @Override
    public boolean supports(String eventType) {
        return DeviceToolEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_TOOL;
    }

    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 实时但需持久化：REALTIME类别但需要写数据库（device_tool_compensation表）
        // 直接处理，Handler内部有@Transactional保证数据一致性
        return WebhookProcessingStrategy.REALTIME_WITH_PERSISTENCE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 10)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        // REALTIME_WITH_PERSISTENCE策略：inbox参数不使用，直接调用实时处理方法
        handleRealtime(request);
    }

    /**
     * 实时处理刀具事件（需要持久化）
     * <p>
     * REALTIME_WITH_PERSISTENCE策略：直接处理，Handler内部有@Transactional保证数据一致性
     * </p>
     * <p>
     * 优化：使用 NOT_SUPPORTED 挂起事务，确保 Redis 操作在事务外执行
     * 避免 Redis MULTI 嵌套错误
     * </p>
     *
     * @param request Webhook请求对象
     * @throws Exception 处理异常
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handleRealtime(WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        DeviceIdentity identity = webhookHandlerUtils.resolveDeviceIdentity(request);
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();

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
        
        // 提取toolNo和holderNumber
        String toolNo = payload.get(DeviceToolEventFields.TOOL_NO);
        String holderNumber = payload.getOrDefault(DeviceToolEventFields.HOLDER_NUMBER, null);
        
        // 如果toolNo不存在，跳过处理
        if (StringUtils.isBlank(toolNo)) {
            log.debug("{} 事件toolNo不存在，跳过处理: deviceInfoId={}", 
                    DeviceToolEventFields.EVENT_TYPE, deviceInfoId);
            return;
        }
        
        // 如果刀具号为0（未使用刀具），不写入刀补补偿表
        boolean isUnusedTool = DeviceToolEventFields.isUnusedTool(toolNo);
        if (isUnusedTool) {
            log.debug("{} 事件刀具号为0（未使用刀具），跳过刀补补偿表写入: deviceInfoId={}", 
                    DeviceToolEventFields.EVENT_TYPE, deviceInfoId);
            holderNumber = null;  // 确保为null，不写入补偿表
        }
        
        // 如果holderNumber为0或不存在，不写入刀补补偿表（刀补号0没有意义）
        if (StringUtils.isBlank(holderNumber) || isZeroValue(holderNumber)) {
            log.debug("{} 事件刀补号为0或不存在，跳过刀补补偿表写入: deviceInfoId={}", 
                    DeviceToolEventFields.EVENT_TYPE, deviceInfoId);
            holderNumber = null;  // 确保为null，不写入补偿表
        }
        
        // 提取补偿数据
        Map<String, Object> compValue = null;
        // 只有在刀具号不为0且holderNumber不为空时，才写入刀补补偿表
        if (!isUnusedTool && StringUtils.isNotBlank(holderNumber)) {
            compValue = extractCompensationValue(eventData);
            
            // 优先使用补偿数据中的 holderNumber（如果存在且不为空）
            // 这样可以确保与 DeviceToolChangeEventHandler 使用相同的 holderNumber
            if (compValue != null && !compValue.isEmpty()) {
                Object holderFromComp = compValue.get(DeviceToolEventFields.HOLDER_NUMBER);
                if (holderFromComp != null) {
                    String holderStr = String.valueOf(holderFromComp).trim();
                    if (!holderStr.isEmpty() && !isZeroValue(holderStr)) {
                        // 使用补偿数据中的 holderNumber（优先级更高）
                        log.debug("[DeviceToolEventHandler] 使用补偿数据中的holderNumber: 原值={}, 新值={}", 
                                holderNumber, holderStr);
                        holderNumber = holderStr;
                    }
                }
            }
            
            if (compValue != null && !compValue.isEmpty()) {
                // 写入刀补补偿表（版本化覆盖）
                deviceToolCompensationService.upsertCompensation(deviceInfoId, orgFactoryId, holderNumber, compValue, eventTimestamp,
                        "[DeviceToolEventHandler]");
            } else {
                // 合并警告：包含未找到补偿数据和刀补号存在但补偿数据为空的信息
                log.warn("[DeviceToolEventHandler] 刀补号存在但补偿数据为空（未找到compensation字段或offset/comp字段），跳过刀补补偿表写入: deviceId={}, holderNumber={}", 
                        deviceInfoId, holderNumber);
            }
        }
        
        // 构建包含 toolNo、holderNumber 和 compensation 的完整结构，存入Redis
        deviceToolCacheService.saveTool(orgFactoryId, deviceInfoId, toolNo, holderNumber, compValue,
                eventTimestamp, DeviceToolEventFields.SOURCE_TB, request.getMessageId());
        
        // 更新进行中记录的 toolHolderNo（如果 toolNo 匹配）
        // 注意：刀具记录的创建和变更统一由 DeviceToolChangeEventHandler 处理（DEVICE_TOOL_CHANGE 事件）
        // 这里只负责更新 toolHolderNo，避免与 DeviceToolChangeEventHandler 重复插入
        updateToolHolderNoIfNeeded(deviceInfoId, toolNo, holderNumber);
    }
    
    /**
     * 更新进行中记录的 toolHolderNo（如果需要）
     * <p>
     * 职责说明：
     * 1. 仅更新 toolHolderNo：如果数据库中有进行中的记录且 toolNo 匹配，更新 toolHolderNo
     * 2. 不创建新记录：刀具记录的创建和变更统一由 DeviceToolChangeEventHandler 处理
     * 3. 这样避免与 DeviceToolChangeEventHandler 重复插入，职责更清晰
     * </p>
     */
    private void updateToolHolderNoIfNeeded(Long deviceInfoId, String toolNo, String holderNumber) {
        if (deviceInfoId == null || StringUtils.isBlank(toolNo) || 
            StringUtils.isBlank(holderNumber) || isZeroValue(holderNumber)) {
            return;
        }
        
        try {
            // 检查是否有进行中的刀具记录
            DeviceToolRecordDO latestOngoing = deviceToolRecordRepository.findLatestOngoing(deviceInfoId);
            
            if (latestOngoing != null && toolNo.equals(latestOngoing.getToolNo())) {
                // toolNo 匹配，更新 toolHolderNo（如果需要）
                String currentHolderNo = latestOngoing.getToolHolderNo();
                if (!holderNumber.equals(currentHolderNo)) {
                    latestOngoing.setToolHolderNo(holderNumber);
                    deviceToolRecordRepository.updateById(latestOngoing);
                    log.debug("[DeviceToolEventHandler] 更新进行中记录的toolHolderNo: deviceId={}, toolNo={}, holderNumber={}",
                            deviceInfoId, toolNo, holderNumber);
                }
            }
        } catch (Exception e) {
            log.warn("[DeviceToolEventHandler] 更新toolHolderNo时发生异常: deviceId={}, toolNo={}, error={}",
                    deviceInfoId, toolNo, e.getMessage(), e);
        }
    }

    /**
     * 提取刀具相关字段
     * <p>
     * 提取规则：
     * 1. 提取所有以 offset 开头的字段
     * 2. 将刀具编号统一映射为 toolNo（与数据库保持一致，允许值为0，0表示"未使用刀具"）
     * 3. 将刀补号的多种别名统一映射为 holderNumber
     * 4. 刀补号优先级：hNo > toolEdgeNumber > dNo > holderNumber
     * 5. 刀补号过滤规则：hNo、toolEdgeNumber、dNo、holderNumber 为0时表示未使用刀补，不进行提取
     * </p>
     *
     * @param eventData 事件数据
     * @return 提取后的字段映射
     */
    private Map<String, String> extractToolFields(Map<String, Object> eventData) {
        Map<String, String> map = new HashMap<>();
        // 用于存储候选刀补号（按优先级）
        String hNo = null;
        String toolEdgeNumber = null;
        String dNo = null;
        String holderNumber = null;  // 标准字段

        for (Map.Entry<String, Object> entry : eventData.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (k == null || v == null) {
                continue;
            }
            String key = k.trim();

            // 提取所有刀具相关字段（offset 开头）
            if (DeviceToolEventFields.isToolRelatedField(key)) {
                map.put(key, String.valueOf(v));
            }

            // 统一刀具编号字段名（允许值为0，0表示"未使用刀具"）
            if (DeviceToolEventFields.isToolNumberField(key)) {
                map.put(DeviceToolEventFields.TOOL_NO, String.valueOf(v));
            }

            // 收集刀补号候选值（按优先级，过滤0值）
            if (DeviceToolEventFields.H_NO.equalsIgnoreCase(key)) {
                String hNoValue = String.valueOf(v);
                if (!isZeroValue(hNoValue)) {
                    hNo = hNoValue;
                }
            } else if (DeviceToolEventFields.TOOL_EDGE_NUMBER.equalsIgnoreCase(key)) {
                String toolEdgeNumberValue = String.valueOf(v);
                if (!isZeroValue(toolEdgeNumberValue)) {
                    toolEdgeNumber = toolEdgeNumberValue;
                }
            } else if (DeviceToolEventFields.D_NO.equalsIgnoreCase(key)) {
                String dNoValue = String.valueOf(v);
                if (!isZeroValue(dNoValue)) {
                    dNo = dNoValue;
                }
            } else if (DeviceToolEventFields.HOLDER_NUMBER.equalsIgnoreCase(key)) {
                // 标准刀补号字段（过滤0值）
                String holderNumberValue = String.valueOf(v);
                if (!isZeroValue(holderNumberValue)) {
                    holderNumber = holderNumberValue;
                }
            }
        }

        // 按优先级选择最终的刀补号
        String finalHolderNumber = hNo;
        if (finalHolderNumber == null) {
            finalHolderNumber = toolEdgeNumber;
        }
        if (finalHolderNumber == null) {
            finalHolderNumber = dNo;
        }
        if (finalHolderNumber == null) {
            finalHolderNumber = holderNumber;
        }

        // 统一设置刀补号（如果找到且不为0，刀补号0没有意义）
        if (finalHolderNumber != null) {
            map.put(DeviceToolEventFields.HOLDER_NUMBER, finalHolderNumber);
        }

        return map;
    }

    /**
     * 判断值是否为0（表示未使用）
     * <p>
     * 支持字符串"0"和数字0的判断
     * </p>
     *
     * @param value 值（字符串格式）
     * @return true 如果值为0
     */
    private boolean isZeroValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String trimmed = value.trim();
        // 判断是否为字符串"0"
        if ("0".equals(trimmed)) {
            return true;
        }
        // 尝试解析为数字，判断是否为0
        try {
            double numValue = Double.parseDouble(trimmed);
            return numValue == 0.0;
        } catch (NumberFormatException e) {
            // 不是数字，返回false
            return false;
        }
    }


    /**
     * 提取补偿值：支持结构化补偿对象格式和扁平化格式
     * <p>
     * 支持的数据格式：
     * 1. 结构化格式：{"compensation": {"geom": {"offsetX": 0.5, "offsetY": -0.3}, "wear": {"compX": 0.1}}}
     * 2. 扁平化格式：{"offsetX": 0.5, "offsetY": -0.3, "offsetZ": 0.1, "compX": 0.05, ...}
     * </p>
     * <p>
     * 提取策略（优先级从高到低）：
     * 1. 优先查找 compensation 字段（结构化格式）
     * 2. 如果不存在，降级到扁平化格式提取（offsetX, offsetY, offsetZ, offsetR, compX, compY, compZ, compR 等字段）
     * </p>
     *
     * @param eventData 事件数据
     * @return 补偿值映射，如果不存在补偿数据返回空Map
     */
    private Map<String, Object> extractCompensationValue(Map<String, Object> eventData) {
        Map<String, Object> compensation = new HashMap<>();
        
        if (eventData == null || eventData.isEmpty()) {
            return compensation;
        }
        
        // 调试日志：记录事件数据字段
        log.debug("[DeviceToolEventHandler] 事件数据字段: {}", eventData.keySet());
        
        // 1. 优先查找结构化补偿字段（compensation）
        Object compensationObj = eventData.get(DeviceToolEventFields.COMPENSATION_FIELD);
        
        // 调试日志：记录compensation字段的类型和值
        if (compensationObj != null) {
            log.debug("[DeviceToolEventHandler] compensation字段类型: {}, 值: {}", 
                    compensationObj.getClass().getName(), compensationObj);
        } else {
            log.debug("[DeviceToolEventHandler] compensation字段不存在");
        }
        
        if (compensationObj != null) {
            // 处理结构化补偿数据
            if (compensationObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> compMap = (Map<String, Object>) compensationObj;
                compensation.putAll(compMap);
                log.debug("[DeviceToolEventHandler] 使用补偿对象格式: {}", compensation.keySet());
                return compensation;
            } else if (compensationObj instanceof String) {
                // 如果补偿数据是JSON字符串，需要先解析
                try {
                    String jsonStr = ((String) compensationObj).trim();
                    if (!jsonStr.isEmpty() && jsonStr.startsWith("{")) {
                        Map<String, Object> compMap = JsonUtils.parseObject(jsonStr, new TypeReference<Map<String, Object>>() {});
                        if (compMap != null && !compMap.isEmpty()) {
                            compensation.putAll(compMap);
                            log.debug("[DeviceToolEventHandler] 从JSON字符串解析补偿对象格式: {}", compensation.keySet());
                            return compensation;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[DeviceToolEventHandler] 解析补偿对象JSON字符串失败: {}, error={}", 
                            compensationObj, e.getMessage());
                }
            } else {
                log.warn("[DeviceToolEventHandler] 补偿数据格式不正确，期望Map或String，实际类型: {}", 
                        compensationObj.getClass().getName());
            }
        }
        
        // 2. 降级到扁平化提取：提取所有以 offset/comp 开头的字段
        eventData.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            // 提取所有补偿相关字段（offset/comp 开头）
            if (DeviceToolEventFields.isCompensationField(key)) {
                compensation.put(key, v);
            }
        });
        
        if (!compensation.isEmpty()) {
            log.debug("[DeviceToolEventHandler] 使用扁平化补偿格式: {}", compensation.keySet());
        } else {
            // 降级为debug，避免与后续"刀补号存在但补偿数据为空"的警告重复
            log.debug("[DeviceToolEventHandler] 未找到补偿数据（compensation字段或offset/comp字段）");
        }
        
        // 调试日志：记录最终提取的补偿数据
        log.debug("[DeviceToolEventHandler] 提取的补偿数据: {}, 是否为空: {}", compensation, compensation.isEmpty());
        
        return compensation;
    }
    

}

