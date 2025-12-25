package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.ingestion.CheckpointData;

import java.util.List;
import java.util.Set;

/**
 * 通用检查点服务接口
 * 用于批量处理任务的进度保存和恢复
 *
 * @param <T> 检查点数据类型
 */
public interface ICheckpointService<T extends CheckpointData> {

    /**
     * 加载检查点
     *
     * @param factoryId   工厂ID
     * @param timeSeconds 时间点（秒）
     * @return 检查点数据，如果不存在则返回null
     */
    T loadCheckpoint(Long factoryId, long timeSeconds);

    /**
     * 保存检查点
     *
     * @param factoryId          工厂ID
     * @param timeSeconds        时间点（秒）
     * @param processedDeviceIds 已处理的设备ID列表
     */
    void saveCheckpoint(Long factoryId, long timeSeconds, List<Long> processedDeviceIds);

    /**
     * 清除检查点
     *
     * @param factoryId   工厂ID
     * @param timeSeconds 时间点（秒）
     */
    void clearCheckpoint(Long factoryId, long timeSeconds);

    /**
     * 获取已处理的设备ID集合
     *
     * @param factoryId   工厂ID
     * @param timeSeconds 时间点（秒）
     * @return 已处理的设备ID集合
     */
    Set<Long> getProcessedDeviceIds(Long factoryId, long timeSeconds);
}

