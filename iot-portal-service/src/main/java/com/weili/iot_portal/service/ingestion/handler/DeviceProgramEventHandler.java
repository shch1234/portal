package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceProgramCacheService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceProgramEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备程序信息事件处理器
 * <p>
 * 处理 DEVICE_PROGRAM 事件，写入实时程序信息缓存（rt:program）
 * </p>
 * <p>
 * 事件数据要求：
 * - payload.eventData 内包含程序相关字段：programName、programPath、gCode、mCode 等
 * - 支持字段别名：program/programName、program_path/programPath、gcode/gCode、mcode/mCode
 * - 所有以 program/gCode/mCode 开头的字段都会被提取并缓存
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceProgramEventHandler implements WebhookEventHandler {

    private final WebhookHandlerUtils webhookHandlerUtils;
    private final DeviceProgramCacheService deviceProgramCacheService;

    @Override
    public boolean supports(String eventType) {
        return DeviceProgramEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_PROGRAM;
    }

    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 实时直接处理：只写Redis缓存，不需要持久化
        return WebhookProcessingStrategy.REALTIME_DIRECT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        // REALTIME_DIRECT策略：inbox参数不使用，直接调用实时处理方法
        handleRealtime(request);
    }

    /**
     * 实时处理程序信息事件
     * <p>
     * REALTIME_DIRECT策略：只写Redis缓存，不需要持久化
     * </p>
     *
     * @param request Webhook请求对象
     * @throws Exception 处理异常
     */
    @Override
    public void handleRealtime(WebhookRequest request) throws Exception {
        try {
            Map<String, Object> eventData = request.getEventData();
            
            // 解析设备标识（按 deviceCode / deviceId 解析为 portal 的 deviceInfoId / factoryId）
            DeviceIdentity identity = 
                    webhookHandlerUtils.resolveDeviceIdentity(request);
            Long deviceInfoId = identity.deviceInfoId();
            Long orgFactoryId = identity.orgFactoryId();

            // 解析时间戳
            Long eventTimestamp = request.getDataTimestamp() != null
                    ? request.getDataTimestamp()
                    : request.getTimestamp();
            if (eventTimestamp == null) {
                eventTimestamp = System.currentTimeMillis();
            }

            // 提取程序相关字段
            Map<String, Object> programFields = extractProgramFields(eventData);
            
            if (programFields.isEmpty()) {
                log.warn("[DeviceProgramEventHandler] DEVICE_PROGRAM 事件未包含程序字段，跳过写入: deviceInfoId={}, eventDataKeys={}", 
                        deviceInfoId, eventData != null ? eventData.keySet() : "null");
                return;
            }

            // 转换为String类型的Map（Redis Hash需要String类型）
            Map<String, String> programData = new HashMap<>();
            programFields.forEach((k, v) -> programData.put(k, String.valueOf(v)));

            // 如果只有programPath而没有programName，从programPath中提取文件名作为programName
            if (!programData.containsKey(DeviceProgramEventFields.PROGRAM_NAME) 
                    && programData.containsKey(DeviceProgramEventFields.PROGRAM_PATH)) {
                String programPath = programData.get(DeviceProgramEventFields.PROGRAM_PATH);
                String extractedProgramName = extractProgramNameFromPath(programPath);
                if (extractedProgramName != null) {
                    programData.put(DeviceProgramEventFields.PROGRAM_NAME, extractedProgramName);
                }
            }
            
            deviceProgramCacheService.saveProgram(orgFactoryId, deviceInfoId, programData,
                    eventTimestamp, DeviceProgramEventFields.SOURCE_TB, request.getMessageId());
        } catch (Exception e) {
            log.error("[DeviceProgramEventHandler] 处理DEVICE_PROGRAM事件异常: messageId={}, error={}", 
                    request != null ? request.getMessageId() : null, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 提取程序相关字段
     * <p>
     * 提取规则（与TB侧保持一致）：
     * 1. 统一映射程序名称字段名（programName/program -> programName）
     * 2. 统一映射程序路径字段名（programPath/program_path -> programPath）
     * 3. 统一映射G代码字段名（gCode/gcode -> gCode）
     * 4. 统一映射M代码字段名（mCode/mcode -> mCode）
     * 5. 提取所有以 program/gCode/mCode 开头的字段，保留原始字段名
     * </p>
     *
     * @param eventData 事件数据
     * @return 程序字段映射（保留原始字段名，但统一关键字段名）
     */
    private Map<String, Object> extractProgramFields(Map<String, Object> eventData) {
        Map<String, Object> programMap = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            
            // 统一程序名称字段名
            if (DeviceProgramEventFields.isProgramNameField(key)) {
                programMap.put(DeviceProgramEventFields.PROGRAM_NAME, v);
            }
            // 统一程序路径字段名
            else if (DeviceProgramEventFields.isProgramPathField(key)) {
                programMap.put(DeviceProgramEventFields.PROGRAM_PATH, v);
            }
            // 统一G代码字段名
            else if (DeviceProgramEventFields.isGCodeField(key)) {
                programMap.put(DeviceProgramEventFields.G_CODE, v);
            }
            // 统一M代码字段名
            else if (DeviceProgramEventFields.isMCodeField(key)) {
                programMap.put(DeviceProgramEventFields.M_CODE, v);
            }
            // 提取所有程序相关字段（program/gCode/mCode 开头），保留原始字段名
            else if (DeviceProgramEventFields.isProgramRelatedField(key)) {
                programMap.put(key, v);
            }
        });
        return programMap;
    }

    /**
     * 从程序路径中提取程序名称
     * <p>
     * 例如：
     * - "/path/to/program.nc" -> "program.nc"
     * - "C:\\path\\to\\program.nc" -> "program.nc"
     * - "program.nc" -> "program.nc"
     * </p>
     *
     * @param programPath 程序路径
     * @return 程序名称，如果无法提取则返回null
     */
    private String extractProgramNameFromPath(String programPath) {
        if (programPath == null || programPath.trim().isEmpty()) {
            return null;
        }
        
        // 处理Windows路径（反斜杠）和Unix路径（正斜杠）
        String normalizedPath = programPath.replace('\\', '/');
        
        // 提取最后一个斜杠后的文件名
        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < normalizedPath.length() - 1) {
            return normalizedPath.substring(lastSlashIndex + 1);
        }
        
        // 如果没有斜杠，直接返回原字符串（可能是文件名）
        return normalizedPath;
    }
}