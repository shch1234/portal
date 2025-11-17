package com.weili.iot_portal.web.aspect;

import com.weili.iot_portal.web.annotation.PermRequired;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/19 14:56
 */
public interface IPermRequiredVerifier {

    void verifyPerm(ProceedingJoinPoint pjp, Object[] args, PermRequired permRequired) throws Throwable;

}
