package com.weili.iot_portal.domain.ingestion;

import java.util.List;

/**
 * @author luying
 * @className CheckpointData
 * @description  检查点数据基类
 * @date 2025-12-25 09:00
 **/
public class CheckpointData {

    private Long factoryId;
    private long timeSeconds;
    private List<Long> processedDeviceIds;
    private int totalDeviceCount;
    private long lastUpdateTime;

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public long getTimeSeconds() {
        return timeSeconds;
    }

    public void setTimeSeconds(long timeSeconds) {
        this.timeSeconds = timeSeconds;
    }

    public List<Long> getProcessedDeviceIds() {
        return processedDeviceIds;
    }

    public void setProcessedDeviceIds(List<Long> processedDeviceIds) {
        this.processedDeviceIds = processedDeviceIds;
    }

    public int getTotalDeviceCount() {
        return totalDeviceCount;
    }

    public void setTotalDeviceCount(int totalDeviceCount) {
        this.totalDeviceCount = totalDeviceCount;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
}
