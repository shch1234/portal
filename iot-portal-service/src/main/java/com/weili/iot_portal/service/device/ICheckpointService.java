package com.weili.iot_portal.service.device;

import java.util.List;
import java.util.Set;

/**
 * 通用检查点服务接口
 * 用于批量处理任务的进度保存和恢复
 *
 * @param <T> 检查点数据类型
 */
public interface ICheckpointService<T extends ICheckpointService.CheckpointData> {

    /**
     * 检查点数据基类
     */
    class CheckpointData {
        private String factoryId;
        private long timeSeconds;
        private List<String> processedDeviceIds;
        private int totalDeviceCount;
        private long lastUpdateTime;

        public String getFactoryId() {
            return factoryId;
        }

        public void setFactoryId(String factoryId) {
            this.factoryId = factoryId;
        }

        public long getTimeSeconds() {
            return timeSeconds;
        }

        public void setTimeSeconds(long timeSeconds) {
            this.timeSeconds = timeSeconds;
        }

        public List<String> getProcessedDeviceIds() {
            return processedDeviceIds;
        }

        public void setProcessedDeviceIds(List<String> processedDeviceIds) {
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

    /**
     * 加载检查点
     *
     * @param factoryId   工厂ID
     * @param timeSeconds 时间点（秒）
     * @return 检查点数据，如果不存在则返回null
     */
    T loadCheckpoint(String factoryId, long timeSeconds);

    /**
     * 保存检查点
     *
     * @param factoryId          工厂ID
     * @param timeSeconds        时间点（秒）
     * @param processedDeviceIds 已处理的设备ID列表
     */
    void saveCheckpoint(String factoryId, long timeSeconds, List<String> processedDeviceIds);

    /**
     * 清除检查点
     *
     * @param factoryId   工厂ID
     * @param timeSeconds 时间点（秒）
     */
    void clearCheckpoint(String factoryId, long timeSeconds);

    /**
     * 获取已处理的设备ID集合
     *
     * @param factoryId   工厂ID
     * @param timeSeconds 时间点（秒）
     * @return 已处理的设备ID集合
     */
    Set<String> getProcessedDeviceIds(String factoryId, long timeSeconds);
}

