package com.weili.iot_portal.service.ingestion.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceToolCacheService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
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
    private final DeviceToolCompensationRepository deviceToolCompensationRepository;
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
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        // REALTIME_WITH_PERSISTENCE策略：inbox参数不使用，直接调用实时处理方法
        handleRealtime(request);
    }

    /**
     * 实时处理刀具事件（需要持久化）
     * <p>
     * REALTIME_WITH_PERSISTENCE策略：直接处理，Handler内部有@Transactional保证数据一致性
     * </p>
     *
     * @param request Webhook请求对象
     * @throws Exception 处理异常
     */
    @Override
    public void handleRealtime(WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        DeviceIdentity identity =
                webhookHandlerUtils.resolveDeviceIdentity(request);
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
            if (!compValue.isEmpty()) {
                // 写入刀补补偿表（版本化覆盖）
                upsertCompensation(deviceInfoId, orgFactoryId, holderNumber, compValue, eventTimestamp);
            } else {
                log.warn("[DeviceToolEventHandler] 刀补号存在但补偿数据为空，跳过刀补补偿表写入: deviceId={}, holderNumber={}", 
                        deviceInfoId, holderNumber);
            }
        }
        // 保证实时缓存中的 toolMagazineNo（holderNumber）与后续入库一致：
        // 如果 holderNumber 为空但补偿对象中包含 HOLDER_NUMBER，则使用之填充
        if (StringUtils.isBlank(holderNumber) && compValue != null && !compValue.isEmpty()) {
            Object holderFromComp = compValue.get(DeviceToolEventFields.HOLDER_NUMBER);
            if (holderFromComp != null) {
                String holderStr = String.valueOf(holderFromComp).trim();
                if (!holderStr.isEmpty() && !isZeroValue(holderStr)) {
                    holderNumber = holderStr;
                }
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
     * 提取补偿值：仅支持结构化补偿对象格式
     * <p>
     * 支持的数据格式：
     * 补偿对象：{"compensation": {"geom": {"offsetX": 0.5, "offsetY": -0.3}, "wear": {"compX": 0.1}}}
     * </p>
     *
     * @param eventData 事件数据
     * @return 补偿值映射（结构化格式），如果不存在补偿数据返回空Map
     */
    private Map<String, Object> extractCompensationValue(Map<String, Object> eventData) {
        Map<String, Object> compensation = new HashMap<>();
        
        // 查找结构化补偿字段（compensation）
        Object compensationObj = eventData.get(DeviceToolEventFields.COMPENSATION_FIELD);
        
        if (compensationObj == null) {
            log.warn("[DeviceToolEventHandler] 未找到补偿数据（compensation字段），跳过刀补补偿表写入");
            return compensation;
        }
        
        // 处理结构化补偿数据
        if (compensationObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> compMap = (Map<String, Object>) compensationObj;
            compensation.putAll(compMap);
            log.debug("[DeviceToolEventHandler] 使用补偿对象格式: {}", compensation.keySet());
        } else if (compensationObj instanceof String) {
            // 如果补偿数据是JSON字符串，需要先解析
            try {
                String jsonStr = ((String) compensationObj).trim();
                if (jsonStr.isEmpty()) {
                    log.warn("[DeviceToolEventHandler] 补偿数据字符串为空");
                    return compensation;
                }
                
                // 解析为对象格式
                if (jsonStr.startsWith("{")) {
                    try {
                        Map<String, Object> compMap = JsonUtils.parseObject(jsonStr, new TypeReference<Map<String, Object>>() {});
                        if (compMap != null && !compMap.isEmpty()) {
                            compensation.putAll(compMap);
                            log.debug("[DeviceToolEventHandler] 从JSON字符串解析补偿对象格式: {}", compensation.keySet());
                        } else {
                            log.warn("[DeviceToolEventHandler] 解析补偿对象为空: {}", jsonStr);
                        }
                    } catch (Exception e) {
                        log.warn("[DeviceToolEventHandler] 解析补偿对象JSON字符串失败: {}, error={}", 
                                jsonStr, e.getMessage());
                    }
                } else {
                    log.warn("[DeviceToolEventHandler] 补偿数据字符串格式不正确，期望对象格式: {}", jsonStr);
                }
            } catch (Exception e) {
                log.warn("[DeviceToolEventHandler] 处理补偿数据字符串时发生异常: {}, error={}", 
                        compensationObj, e.getMessage());
            }
        } else {
            log.warn("[DeviceToolEventHandler] 补偿数据格式不正确，期望Map或String，实际类型: {}", 
                    compensationObj.getClass().getName());
        }
        
        return compensation;
    }
    

    /**
     * 版本化覆盖：刀补补偿数据的写入逻辑
     * <p>
     * 业务规则：
     * 1. 一个设备可以有多个刀补号（deviceId + toolHolderNo 唯一标识）
     * 2. 一个刀补号对应一组刀补数据（compValueJson）
     * 3. 如果表中没有该设备的该刀补号，就写入新记录（版本号=1）
     * 4. 如果已经有该刀补号，但刀补值不一样，则做版本管理：
     *    - 关闭旧记录（设置 endTs 和 active=0）
     *    - 创建新记录（版本号=旧版本号+1）
     * 5. 如果已经有该刀补号，且刀补值相同，则跳过（不做任何操作）
     * </p>
     * <p>
     * 性能优化：
     * - 先查Redis缓存，如果缓存命中且值相同，直接跳过（减少数据库查询）
     * - 如果缓存未命中或值不同，再查数据库
     * - 写入数据库后，同步更新缓存
     * </p>
     * 
     * @param deviceId 设备ID
     * @param factoryId 工厂ID
     * @param holderNumber 刀补号
     * @param compValue 刀补值（JSON Map）
     * @param eventTimestamp 事件时间戳（毫秒）
     */
    private void upsertCompensation(Long deviceId, Long factoryId,
                                    String holderNumber, Map<String, Object> compValue, Long eventTimestamp) {
        // 1. 先查Redis缓存（性能优化：减少数据库查询）
        Map<String, Object> cachedCompValue = deviceToolCacheService.getActiveCompensation(deviceId, holderNumber);
        if (cachedCompValue != null && Objects.equals(cachedCompValue, compValue)) {
            log.debug("[DeviceToolEventHandler] 刀补值未变化（缓存命中），跳过写入: deviceId={}, holderNumber={}", 
                    deviceId, holderNumber);
            return;
        }
        
        // 2. 缓存未命中或值不同，查询数据库
        DeviceToolCompensationDO active = deviceToolCompensationRepository.findActive(deviceId, holderNumber);
        
        // 3. 如果找到活跃记录且补偿值相同，更新缓存并跳过写入
        if (active != null && Objects.equals(active.getCompValueJson(), compValue)) {
            // 缓存可能过期或不存在，更新缓存
            deviceToolCacheService.cacheActiveCompensation(deviceId, holderNumber, compValue);
            log.debug("[DeviceToolEventHandler] 刀补值未变化（数据库确认），跳过写入: deviceId={}, holderNumber={}", 
                    deviceId, holderNumber);
            return;
        }

        // 时间戳使用毫秒（数据库存储单位为毫秒）
        long ts = eventTimestamp != null 
                ? eventTimestamp 
                : System.currentTimeMillis();
        
        int nextVersion = DeviceToolEventFields.INITIAL_VERSION;
        
        // 4. 如果找到活跃记录但补偿值不同，关闭旧记录
        if (active != null) {
            log.debug("[DeviceToolEventHandler] 刀补值变化，关闭旧记录并创建新记录: deviceId={}, holderNumber={}, oldVersion={}", 
                    deviceId, holderNumber, active.getVersion());
            // 使用 LambdaUpdateWrapper 仅更新 active 和 end_ts 字段，避免更新其他字段导致唯一约束冲突
            deviceToolCompensationRepository.deactivateById(active.getId(), ts, DeviceToolEventFields.ACTIVE_STATUS_DISABLED);
            nextVersion = (active.getVersion() != null ? active.getVersion() + 1 : DeviceToolEventFields.INITIAL_VERSION);
            // 删除旧缓存（补偿值已变化）
            deviceToolCacheService.deleteActiveCompensation(deviceId, holderNumber);
        } else {
            log.debug("[DeviceToolEventHandler] 首次写入刀补数据: deviceId={}, holderNumber={}", 
                    deviceId, holderNumber);
        }

        // 5. 创建新记录
        DeviceToolCompensationDO record = new DeviceToolCompensationDO();
        record.setDeviceInfoId(deviceId);
        record.setOrgFactoryId(factoryId);
        record.setToolHolderNo(holderNumber);
        record.setCompValueJson(compValue);
        record.setVersion(nextVersion);
        record.setStartTs(ts);
        record.setEndTs(null);  // NULL 表示当前有效
        record.setActive(DeviceToolEventFields.ACTIVE_STATUS_ENABLED);
        deviceToolCompensationRepository.insert(record);
        
        // 6. 同步更新缓存（写入成功后）
        deviceToolCacheService.cacheActiveCompensation(deviceId, holderNumber, compValue);
        
        log.info("[DeviceToolEventHandler] 刀补数据写入成功: deviceId={}, holderNumber={}, version={}", 
                deviceId, holderNumber, nextVersion);
    }
}

