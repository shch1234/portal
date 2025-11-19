package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.request.AxisCoordinateWebhookRequest;

/**
 * 轴坐标 Webhook 服务
 */
public interface AxisCoordinateWebhookService {

    /**
     * 处理TB推送的轴坐标Webhook
     *
     * @param request Webhook请求
     * @param secret  Webhook密钥
     */
    void handleAxisCoordinateWebhook(AxisCoordinateWebhookRequest request, String secret);
}

