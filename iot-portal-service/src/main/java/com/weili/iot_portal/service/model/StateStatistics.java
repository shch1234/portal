package com.weili.iot_portal.service.model;

import lombok.Data;

/**
 * @author luying
 * @className StateStatistics
 * @description 状态统计结果
 * @date 2025-12-25 08:45
 **/
@Data
public class StateStatistics {

    public final String stateCode;
    public long durationSeconds;
    public int fragmentCount;
    public java.math.BigDecimal ratio = java.math.BigDecimal.ZERO;

    public StateStatistics(String stateCode, long durationSeconds, int fragmentCount) {
        this.stateCode = stateCode;
        this.durationSeconds = durationSeconds;
        this.fragmentCount = fragmentCount;
    }
}
