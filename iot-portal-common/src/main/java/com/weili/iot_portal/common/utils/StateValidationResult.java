package com.weili.iot_portal.common.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态验证结果
 * <p>
 * 用于记录状态验证和转换的信息
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StateValidationResult {
    /**
     * 验证后的状态值
     */
    private String validatedState;

    /**
     * 是否进行了转换
     */
    private boolean converted;

    /**
     * 原始状态值
     */
    private String originalState;
}

