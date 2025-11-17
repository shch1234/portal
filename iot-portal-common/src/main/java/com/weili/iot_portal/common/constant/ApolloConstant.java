package com.weili.iot_portal.common.constant;

import com.ctrip.framework.apollo.ConfigService;

/**
 * @author luying
 * @className ApolloConstant
 * @description
 * @date 2025-11-17 11:08
 **/
public class ApolloConstant {

    public static String getSpmServiceUrl() {
        return ConfigService.getAppConfig().getProperty("spm.service.url", "http://192.168.70.124:8080");
    }


    public static Integer accessTokenSeconds() {
        return ConfigService.getAppConfig().getIntProperty("accessToken.seconds", 18000);
    }


    public static String fdsService() {
        return ConfigService.getAppConfig().getProperty("fdfs.download.service", "");
    }

}
