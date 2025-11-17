package com.weili.iot_portal.service.system.impl;

import com.alibaba.fastjson2.JSONObject;
import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.http.OkHttpClientUtils;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.constant.ApolloConstant;
import com.weili.iot_portal.service.system.ISpmRemoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author luying
 * @className SpmRemoteService
 * @description
 * @date 2025-11-17 11:08
 **/
@Service
@Slf4j
public class SpmRemoteService implements ISpmRemoteService {

    @Override
    @ApiInterceptor
    public String loginUser(Integer jobNumber, String password) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("jobNumber", jobNumber);
        jsonObject.put("password", password);
        String message = "";
        try {
            String response = OkHttpClientUtils.doPost(ApolloConstant.getSpmServiceUrl() + "/login", jsonObject.toString());
            JSONObject parseObject = JSONObject.parseObject(response);
            return parseObject.getString("token");
        } catch (Exception e) {
            log.error("e", e);
            throw new ServiceException(ErrorCodeConstants.SPM_ERROR.getCode(), "请求调用中台异常：" + message);
        }
    }
}
