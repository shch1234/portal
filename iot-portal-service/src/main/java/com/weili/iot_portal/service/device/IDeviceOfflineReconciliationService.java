package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.ingestion.BatchProcessResult;

/**
 * 设备离线纠正服务
 * <p>
 * 基于心跳状态扫描设备：
 * - 对离线设备结束正在进行中的状态片段（标记为异常结束）；
 * - 插入一条 UNKNOWN(255) 的进行中状态，表示离线期间的状态；
 * </p>
 */
public interface IDeviceOfflineReconciliationService {

    /**
     * 扫描所有被监控设备，基于心跳状态执行离线纠正逻辑。
     *
     * @param currentTimeMillis 当前时间（毫秒）
     * @param batchSize         每批处理设备数量
     * @param timeoutMillis     任务超时时间（毫秒）
     * @return 批处理结果
     */
    BatchProcessResult processAllDevices(long currentTimeMillis, int batchSize, long timeoutMillis);
}

