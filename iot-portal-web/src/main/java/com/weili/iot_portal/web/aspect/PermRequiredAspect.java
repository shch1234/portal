package com.weili.iot_portal.web.aspect;

import com.weili.basic.common.exception.BaseException;
import com.weili.iot_portal.web.annotation.PermRequired;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/19 14:49
 */
@Slf4j
@Component
@Aspect
@Order(-1)
public class PermRequiredAspect {

    @Resource
    private IPermRequiredVerifier menuRoleRequiredVerifier;

    @Around("@annotation(com.weili.iot_portal.web.annotation.PermRequired)")
    public Object handlePermission(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        //类名
        String className = method.getDeclaringClass().getName();
        //方法名
        String methodName = method.getName();
        Object[] args = pjp.getArgs();
        try {
            PermRequired[] permRequiredAnnotations = method.getAnnotationsByType(PermRequired.class);
            for (PermRequired permRequired : permRequiredAnnotations) {
                menuRoleRequiredVerifier.verifyPerm(pjp, args, permRequired);
            }
            return pjp.proceed();
        } catch (BaseException e) {
            log.warn(String.format("request exception: class=%s, method=%s, errorCode=%s, exception=%s", className, methodName, e.getCode(), e.getMessage()), e);
            throw new BaseException(e.getCode(), e.getMessage());
        }
    }
}
