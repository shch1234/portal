package com.weili.iot_portal.common.framework;

import com.weili.basic.authorization.security.SecurityContextUtils;

/**
 * @author luying
 * @className SecurityFrameworkContext
 * @description
 * @date 2025-11-25 09:07
 **/
public class SecurityFrameworkContext {

    public static String getTenantId() {
        return "1";
    }

    public static String getUserId() {
        return SecurityContextUtils.getUserid().toString();
    }

    public static String getLoginTenantId() {
        return "1";
    }

    public static String getLoginFactoryId() {
        return "1";
    }
}
