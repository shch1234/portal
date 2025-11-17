package com.weili.iot_portal.common.utils;

import cn.hutool.core.util.IdUtil;

/**
 * @author luying
 * @className TokeUtil
 * @description
 * @date 2025-11-17 11:20
 **/
public class TokeUtil {

    public static String generateAccessToken() {
        return IdUtil.fastSimpleUUID();
    }

    public static String generateRefreshToken() {
        return IdUtil.fastSimpleUUID();
    }
}
