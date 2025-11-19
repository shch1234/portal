package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Webhook 安全校验
 */
@Component
public class WebhookSecurityService {

    @Value("${portal.webhook.secret:}")
    private String globalSecret;

    public void validate(String providedSecret) {
        if (StringUtils.isBlank(globalSecret)) {
            return;
        }
        if (!StringUtils.equals(globalSecret, providedSecret)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "Webhook密钥验证失败");
        }
    }
}

