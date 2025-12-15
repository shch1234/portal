package com.weili.iot_portal.service.ingestion.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.cache.DeviceToolCacheService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
import com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils;

import static com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils.DeviceIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
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
            if (!compValue.isEmpty()) {
                upsertCompensation(deviceInfoId, orgFactoryId, holderNumber, compValue, eventTimestamp);
            } else {
                log.warn("[DeviceToolEventHandler] 刀补号存在但补偿数据为空，跳过刀补补偿表写入: deviceId={}, holderNumber={}", 
                        deviceInfoId, holderNumber);
            }
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
     * 提取补偿值：仅支持结构化补偿数据
     * <p>
     * 支持的数据格式（二选一）：
     * 1. 补偿对象：{"compensation": {"shape": {"offsetX": 0.5, "offsetY": -0.3}, "wear": {"compX": 0.1}}}
     * 2. 补偿数组：{"compensations": [{"type": "shape", "offsetX": 0.5, "offsetY": -0.3}, {"type": "wear", "compX": 0.1}]}
     * </p>
     * <p>
     * 注意：不再支持零散的offset/comp字段，必须使用compensation或compensations字段
     * </p>
     *
     * @param eventData 事件数据
     * @return 补偿值映射（结构化格式），如果不存在补偿数据返回空Map
     */
    private Map<String, Object> extractCompensationValue(Map<String, Object> eventData) {
        Map<String, Object> compensation = new HashMap<>();
        
        // 查找结构化补偿字段（compensation 或 compensations）
        Object compensationObj = eventData.get(DeviceToolEventFields.COMPENSATION_FIELD);
        if (compensationObj == null) {
            compensationObj = eventData.get(DeviceToolEventFields.COMPENSATIONS_FIELD);
        }
        
        if (compensationObj == null) {
            log.warn("[DeviceToolEventHandler] 未找到补偿数据（compensation/compensations字段），跳过刀补补偿表写入");
            return compensation;
        }
        
        // 处理结构化补偿数据
        if (compensationObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> compMap = (Map<String, Object>) compensationObj;
            compensation.putAll(compMap);
            log.debug("[DeviceToolEventHandler] 使用补偿对象格式: {}", compensation.keySet());
        } else if (compensationObj instanceof java.util.List) {
            // 数组格式转换为对象格式
            compensation = convertCompensationArrayToMap((java.util.List<Object>) compensationObj);
            log.debug("[DeviceToolEventHandler] 转换补偿数组为对象格式: {}", compensation.keySet());
        } else if (compensationObj instanceof String) {
            // 如果补偿数据是JSON字符串，需要先解析
            // 这通常发生在JSON序列化/反序列化过程中，数组被转换为字符串的情况
            // 或者TB端发送时，List被序列化为JSON字符串
            try {
                String jsonStr = ((String) compensationObj).trim();
                if (jsonStr.isEmpty()) {
                    log.warn("[DeviceToolEventHandler] 补偿数据字符串为空");
                    return compensation;
                }
                
                // 先尝试解析为List（compensations数组格式）
                // 判断是否以 [ 开头，表示数组格式
                if (jsonStr.startsWith("[")) {
                    try {
                        List<Object> compList = JsonUtils.parseObject(jsonStr, new TypeReference<List<Object>>() {});
                        if (compList != null && !compList.isEmpty()) {
                            compensation = convertCompensationArrayToMap(compList);
                            log.debug("[DeviceToolEventHandler] 从JSON字符串解析补偿数组并转换为对象格式: {}", compensation.keySet());
                        } else {
                            log.warn("[DeviceToolEventHandler] 解析补偿数组为空: {}", jsonStr);
                        }
                    } catch (Exception e1) {
                        log.warn("[DeviceToolEventHandler] 解析补偿数组JSON字符串失败: {}, error={}", 
                                jsonStr, e1.getMessage());
                    }
                } else if (jsonStr.startsWith("{")) {
                    // 判断是否以 { 开头，表示对象格式
                    try {
                        Map<String, Object> compMap = JsonUtils.parseObject(jsonStr, new TypeReference<Map<String, Object>>() {});
                        if (compMap != null && !compMap.isEmpty()) {
                            compensation.putAll(compMap);
                            log.debug("[DeviceToolEventHandler] 从JSON字符串解析补偿对象格式: {}", compensation.keySet());
                        } else {
                            log.warn("[DeviceToolEventHandler] 解析补偿对象为空: {}", jsonStr);
                        }
                    } catch (Exception e2) {
                        log.warn("[DeviceToolEventHandler] 解析补偿对象JSON字符串失败: {}, error={}", 
                                jsonStr, e2.getMessage());
                    }
                } else {
                    log.warn("[DeviceToolEventHandler] 补偿数据字符串格式不正确，既不是数组也不是对象: {}", jsonStr);
                }
            } catch (Exception e) {
                log.warn("[DeviceToolEventHandler] 处理补偿数据字符串时发生异常: {}, error={}", 
                        compensationObj, e.getMessage());
            }
        } else {
            log.warn("[DeviceToolEventHandler] 补偿数据格式不正确，期望Map、List或String，实际类型: {}", 
                    compensationObj.getClass().getName());
        }
        
        return compensation;
    }
    
    /**
     * 将补偿数组转换为对象格式
     * <p>
     * 输入：[
     *   {"type": "shape", "offsetX": 0.5, "offsetY": -0.3},
     *   {"type": "wear", "compX": 0.1, "compY": 0.2}
     * ]
     * 输出：{
     *   "shape": {"offsetX": 0.5, "offsetY": -0.3},
     *   "wear": {"compX": 0.1, "compY": 0.2}
     * }
     * </p>
     * 
     * @param compensationArray 补偿数组
     * @return 补偿对象
     */
    private Map<String, Object> convertCompensationArrayToMap(java.util.List<Object> compensationArray) {
        Map<String, Object> result = new HashMap<>();
        for (Object item : compensationArray) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) item;
                Object typeObj = itemMap.get("type");
                if (typeObj != null) {
                    String type = typeObj.toString();
                    // 移除type字段，保留其他字段
                    Map<String, Object> compData = new HashMap<>(itemMap);
                    compData.remove("type");
                    result.put(type, compData);
                } else {
                    // 如果没有type字段，使用默认类型
                    result.put(DeviceToolEventFields.COMP_TYPE_OFFSET, itemMap);
                }
            }
        }
        return result;
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
    private void upsertCompensation(String deviceId, String factoryId,
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
            active.setEndTs(ts);
            active.setActive(DeviceToolEventFields.ACTIVE_STATUS_DISABLED);
            deviceToolCompensationRepository.updateById(active);
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

