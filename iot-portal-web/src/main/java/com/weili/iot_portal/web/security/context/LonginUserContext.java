package com.weili.iot_portal.web.security.context;

import cn.hutool.core.map.MapUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @author luying
 * @className LonginUserContext
 * @description
 * @date 2025-07-06 12:07
 **/
@Getter
@Setter
public class LonginUserContext {

    public static final String INFO_KEY_DEPT_ID = "deptId";
    public static final String INFO_KEY_USERNAME = "username";

    /**
     * 用户编号
     */
    private Long id;

    private String accessToken;
    /**
     * 额外的用户信息
     */
    private Map<String, String> info;
    /**
     * 过期时间
     */
    private LocalDateTime expiresTime;

    // ========== 上下文 ==========
    /**
     * 上下文字段，不进行持久化
     * <p>
     * 1. 用于基于 LoginUser 维度的临时缓存
     */
    @JsonIgnore
    private Map<String, Object> context;

    public void setContext(String key, Object value) {
        if (context == null) {
            context = new HashMap<>();
        }
        context.put(key, value);
    }

    public <T> T getContext(String key, Class<T> type) {
        return MapUtil.get(context, key, type);
    }
}
