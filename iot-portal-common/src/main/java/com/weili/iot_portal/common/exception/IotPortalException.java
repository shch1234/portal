package com.weili.iot_portal.common.exception;

import com.weili.basic.common.enums.IEnumBase;
import com.weili.basic.common.exception.BaseException;

/**
 * @author luying
 * @className IotPortalException
 * @description IoT Portal 项目统一异常类
 * @date 2025-12-11 09:13
 **/
public class IotPortalException extends BaseException {

    public IotPortalException(String code, String message) {
        super(code, message);
    }

    public IotPortalException(IEnumBase errorCode) {
        super(errorCode);
    }

    public IotPortalException(IEnumBase errorCode, String message) {
        super(errorCode.getCode(), message);
    }
}
