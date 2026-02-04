package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 缓存更新请求
 * <p>
 * 用于批量处理缓存更新
 * </p>
 *
 * @author system
 */
@Data
@AllArgsConstructor
public class CacheUpdateRequest {
    /**
     * 设备身份信息
     */
    private DeviceIdentity identity;
    
    /**
     * 当前状态编码
     */
    private Integer currentStateCode;
    
    /**
     * 事件时间戳
     */
    private Long eventTimestamp;
    
    /**
     * 是否需要更新缓存（true-更新状态值，false-只刷新TTL）
     */
    private boolean needUpdateCache;
    
    /**
     * 追踪ID
     */
    private String traceId;
    
    /**
     * 请求创建时间（用于监控）
     */
    private long createTime;
    
    /**
     * 获取设备ID（用于去重）
     */
    public Long getDeviceId() {
        return identity != null ? identity.deviceInfoId() : null;
    }
    
    /**
     * 获取工厂ID
     */
    public Long getFactoryId() {
        return identity != null ? identity.orgFactoryId() : null;
    }
}
