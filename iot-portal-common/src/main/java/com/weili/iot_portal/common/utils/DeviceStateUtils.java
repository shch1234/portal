package com.weili.iot_portal.common.utils;

import com.weili.iot_portal.common.constant.DeviceStateConstants;
import com.weili.iot_portal.common.enums.DeviceStateEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态工具类
 * <p>
 * 提供设备状态相关的工具方法，包括状态编码提取、验证、转换等
 * </p>
 */
@Slf4j
public final class DeviceStateUtils {

    private DeviceStateUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 未知状态值
     * 当状态不匹配或出现异常时，使用此状态标记
     * <p>
     * 注意：此常量是为了方便外部使用，实际值来自 {@link DeviceStateEnum#UNKNOWN}
     * </p>
     */
    public static final String UNKNOWN_STATE = DeviceStateEnum.UNKNOWN.name();

    /**
     * 状态验证结果
     * <p>
     * 用于记录状态验证和转换的信息
     * </p>
     */
    public static class StateValidationResult {
        private final String validatedState;
        private final boolean converted;
        private final String originalState;

        public StateValidationResult(String validatedState, boolean converted, String originalState) {
            this.validatedState = validatedState;
            this.converted = converted;
            this.originalState = originalState;
        }

        public String getValidatedState() {
            return validatedState;
        }

        public boolean isConverted() {
            return converted;
        }

        public String getOriginalState() {
            return originalState;
        }
    }

    // ==================== 状态编码提取和验证 ====================

    /**
     * 提取并验证状态编码（数字或数字字符串）
     * <p>
     * Portal 只接受 TB 发送的数字编码（0-3）或数字字符串（"0"-"3"）
     * 直接使用数字编码进行处理，减少不必要的转换
     * </p>
     * <p>
     * 支持输入：
     * - 数字编码（Integer/Long）：0, 1, 2, 3
     * - 数字字符串："0", "1", "2", "3"
     * </p>
     * <p>
     * 容错处理（防御逻辑）：
     * - 如果数字超出 0-3 范围（如 4, 10, 1000, 255 等），返回 255 (UNKNOWN)
     * - 如果字符串无法解析为数字（如 "working", "WORKING" 等），返回 255 (UNKNOWN)
     * - 如果输入为 null，返回 null
     * </p>
     *
     * @param stateValue 状态值（数字编码或数字字符串）
     * @return 状态编码（0-3），如果无法识别或超出范围则返回 255 (UNKNOWN)，如果输入为 null 则返回 null
     */
    public static Integer extractAndValidateStateCode(Object stateValue) {
        if (stateValue == null) {
            return null;
        }

        int code;

        // 如果是数字类型，直接获取整数值
        if (stateValue instanceof Number) {
            code = ((Number) stateValue).intValue();
        } else {
            // 如果是字符串，尝试解析为数字
            String stateStr = stateValue.toString().trim();
            try {
                code = Integer.parseInt(stateStr);
            } catch (NumberFormatException e) {
                // 无法解析为数字的字符串（如 "working", "WORKING" 等），返回 UNKNOWN
                log.warn("[DeviceStateUtils] 无法解析状态值，不是有效的数字（将解析为UNKNOWN）: stateValue={}",
                        stateStr);
                return DeviceStateEnum.UNKNOWN.getCode();
            }
        }

        // 验证编码范围（只接受 0-3）
        if (code >= DeviceStateEnum.SHUTDOWN.getCode() && code <= DeviceStateEnum.FAULT.getCode()) {
            return code;
        }

        // 超出范围（如 4, 10, 1000, 255 等），记录警告并返回 UNKNOWN
        log.warn("[DeviceStateUtils] 状态编码超出有效范围 [0-3]（将解析为UNKNOWN）: code={}", code);
        return DeviceStateEnum.UNKNOWN.getCode();
    }

    /**
     * 将状态编码转换为状态名称字符串
     * <p>
     * 用于日志输出、属性设置等需要可读字符串的场景
     * </p>
     *
     * @param stateCode 状态编码（0-3 或 255）
     * @return 状态名称字符串（WORKING, STANDBY, FAULT, SHUTDOWN, UNKNOWN），如果输入为 null 则返回 null
     */
    public static String convertStateCodeToName(Integer stateCode) {
        if (stateCode == null) {
            return null;
        }
        return DeviceStateEnum.fromCode(stateCode).name();
    }

    // ==================== 属性创建 ====================

    /**
     * 创建包含原始状态信息的properties
     * <p>
     * 当状态被转换时，在properties中记录原始值，便于后续追溯和分析
     * </p>
     *
     * @param stateResult 状态验证结果
     * @param existingProperties 已存在的properties（可为null）
     * @param eventTimestamp 事件时间戳（可为null）
     * @return 包含原始状态信息的properties Map，如果不需要添加信息则返回 existingProperties
     */
    public static Map<String, Object> createPropertiesWithOriginalState(StateValidationResult stateResult,
                                                                        Map<String, Object> existingProperties,
                                                                        Long eventTimestamp) {
        if (stateResult == null || !stateResult.isConverted()) {
            return existingProperties;
        }

        Map<String, Object> properties = existingProperties != null
                ? new HashMap<>(existingProperties)
                : new HashMap<>();

        properties.put(DeviceStateConstants.PROP_ORIGINAL_STATE, stateResult.getOriginalState());
        properties.put(DeviceStateConstants.PROP_STATE_CONVERTED, true);
        properties.put(DeviceStateConstants.PROP_CONVERSION_REASON, "未知状态值");
        if (eventTimestamp != null) {
            properties.put(DeviceStateConstants.PROP_CONVERSION_TIMESTAMP, eventTimestamp);
        }

        return properties;
    }

    // ==================== 状态名称列表 ====================

    /**
     * 获取所有状态名称列表（包括UNKNOWN）
     * <p>
     * 用于统计和报表场景，返回不可变列表
     * </p>
     *
     * @return 所有状态名称列表（不可变）
     */
    public static List<String> getAllStateNames() {
        return DeviceStateEnum.getAllStateNames();
    }
}

