package com.weili.iot_portal.common.enums;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备状态枚举
 * <p>
 * 支持的状态及编码：
 * - SHUTDOWN: 0（关机）
 * - WORKING: 1（加工中）
 * - STANDBY: 2（待机）
 * - FAULT: 3（故障）
 * - UNKNOWN: 255（未知状态）
 * </p>
 * <p>
 */
public enum DeviceStateEnum {

    @JsonPropertyDescription("关机")
    SHUTDOWN(0, "关机"),

    @JsonPropertyDescription("加工中")
    WORKING(1, "加工中"),

    @JsonPropertyDescription("待机")
    STANDBY(2, "待机"),

    @JsonPropertyDescription("故障")
    FAULT(3, "故障"),

    @JsonPropertyDescription("未知")
    UNKNOWN(255, "未知");

    /**
     * 状态编码
     */
    private final int code;

    /**
     * 状态描述
     */
    private final String description;

    /**
     * 编码到枚举的映射（缓存）
     */
    private static final Map<Integer, DeviceStateEnum> CODE_TO_ENUM = new HashMap<>();

    /**
     * 所有状态名称列表（缓存，不可变）
     */
    private static final List<String> ALL_STATE_NAMES = Collections.unmodifiableList(
            Arrays.stream(values()).map(DeviceStateEnum::name).collect(Collectors.toList())
    );

    /**
     * 合法状态名称列表（不包括UNKNOWN，缓存，不可变）
     */
    private static final List<String> VALID_STATE_NAMES = Collections.unmodifiableList(
            Arrays.stream(values())
                    .filter(state -> state != UNKNOWN)
                    .map(DeviceStateEnum::name)
                    .collect(Collectors.toList())
    );

    static {
        // 初始化编码到枚举的映射
        for (DeviceStateEnum state : values()) {
            CODE_TO_ENUM.put(state.code, state);
        }
    }

    DeviceStateEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取状态编码
     *
     * @return 状态编码（0-255）
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取状态描述
     *
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 从编码转换为枚举
     *
     * @param code 状态编码
     * @return 对应的枚举值，未知编码返回UNKNOWN
     */
    public static DeviceStateEnum fromCode(int code) {
        DeviceStateEnum state = CODE_TO_ENUM.get(code);
        return state != null ? state : UNKNOWN;
    }

    /**
     * 从字符串值转换为枚举
     * <p>
     * 兼容性方法：支持从字符串（如"WORKING"）转换为枚举
     * 如果值不在合法集合中，返回UNKNOWN
     * </p>
     *
     * @param value 状态字符串值（不区分大小写）
     * @return 对应的枚举值，未知状态返回UNKNOWN
     */
    public static DeviceStateEnum of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }
        String upper = value.toUpperCase().trim();
        try {
            return DeviceStateEnum.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }

    /**
     * 从字符串或编码转换为枚举
     * <p>
     * 优先尝试作为编码解析（数字），如果失败则作为字符串解析
     * </p>
     *
     * @param value 状态值（可以是字符串如"WORKING"或数字字符串如"1"）
     * @return 对应的枚举值，未知状态返回UNKNOWN
     */
    public static DeviceStateEnum fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }
        String trimmed = value.trim();
        
        // 尝试作为数字编码解析
        try {
            int code = Integer.parseInt(trimmed);
            return fromCode(code);
        } catch (NumberFormatException e) {
            // 不是数字，作为字符串解析
            return of(trimmed);
        }
    }

    /**
     * 获取所有状态名称列表（包括UNKNOWN）
     * 用于统计和报表场景
     *
     * @return 所有状态名称列表（不可变）
     */
    public static List<String> getAllStateNames() {
        return ALL_STATE_NAMES;
    }

    /**
     * 获取所有合法状态名称列表（不包括UNKNOWN）
     * 用于统计和报表场景
     *
     * @return 合法状态名称列表（不可变）
     */
    public static List<String> getValidStateNames() {
        return VALID_STATE_NAMES;
    }

    /**
     * 判断是否为合法状态（不包括UNKNOWN）
     *
     * @return true表示是合法状态，false表示是UNKNOWN
     */
    public boolean isValidState() {
        return this != UNKNOWN;
    }
}
