package com.weili.iot_portal.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface PermRequired {

    /**
     * key 在参数列表中得索引序号,从0开始
     */
    int index() default -1;

    /**
     * 需要校验的权限key
     */
    String permission() default "";
    /**
     * 需要校验的权限key列表(必须所有的都满足)；二选一
     */
    String[] permissions() default {};
}
