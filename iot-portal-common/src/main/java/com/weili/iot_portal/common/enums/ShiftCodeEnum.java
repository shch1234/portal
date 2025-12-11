package com.weili.iot_portal.common.enums;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.HashMap;
import java.util.Map;

/**
 * 班次编码枚举
 * <p>
 * 用于表示班次编码，使用数字编码（1-3）存储，节省存储空间
 * </p>
 */
public enum ShiftCodeEnum {
    @JsonPropertyDescription("一班")
    SHIFT_1(1, "一班"),

    @JsonPropertyDescription("二班")
    SHIFT_2(2, "二班"),

    @JsonPropertyDescription("三班")
    SHIFT_3(3, "三班");

    private final int code;
    private final String description;

    // 用于从编码快速查找枚举的映射
    private static final Map<Integer, ShiftCodeEnum> CODE_TO_ENUM = new HashMap<>();

    static {
        for (ShiftCodeEnum shift : values()) {
            CODE_TO_ENUM.put(shift.code, shift);
        }
    }

    ShiftCodeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取枚举
     *
     * @param code 班次编码（1-3）
     * @return 对应的枚举，如果不存在返回 null
     */
    public static ShiftCodeEnum fromCode(int code) {
        return CODE_TO_ENUM.get(code);
    }

    /**
     * 根据编码或字符串值获取枚举
     * <p>
     * 支持输入：
     * - 数字编码：1, 2, 3
     * - 字符串编码："1", "2", "3"
     * - 字符串名称："SHIFT_1", "SHIFT_2", "SHIFT_3"
     * </p>
     *
     * @param value 班次值（数字或字符串）
     * @return 对应的枚举，如果无法识别返回 null
     */
    public static ShiftCodeEnum fromValue(Object value) {
        if (value == null) {
            return null;
        }

        // 尝试作为数字解析
        if (value instanceof Number) {
            return fromCode(((Number) value).intValue());
        }

        // 尝试作为字符串解析
        String s = value.toString().trim();
        try {
            // 尝试解析为数字字符串
            int code = Integer.parseInt(s);
            return fromCode(code);
        } catch (NumberFormatException e) {
            // 不是数字字符串，尝试作为枚举名称解析
            try {
                return ShiftCodeEnum.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }
}

