package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.service.factory.IFactoryMetricsService;
import com.weili.iot_portal.service.model.BatchProcessResult;

import java.util.List;

/**
 * 设备班次指标汇总服务
 * 依赖已完成的 device_state_summary，计算班次级指标并写入 device_metrics_summary
 */
public interface IDeviceMetricsSummaryService {

    /**
     * 批量处理所有设备的班次指标汇总（带检查点机制）
     *
     * @param statPointSeconds 统计时间点（秒），仅处理 shift_end_ts <= statPoint 的已完成汇总
     * @param batchSize        每批处理设备数
     * @param timeoutMillis    超时时间（毫秒）
     * @return 处理结果
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long statPointSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 批量处理工厂设备的班次指标汇总（带检查点机制）
     *
     * @param factoryId        工厂ID
     * @param statPointSeconds 统计时间点（秒）
     * @param batchSize        批大小
     * @param timeoutMillis    超时时间
     * @return 处理结果
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long statPointSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 向后兼容旧接口（不带检查点），内部可委托带检查点的实现
     */
    default BatchProcessResult processAllDevices(long statPointSeconds) {
        return processAllDevicesWithCheckpoint(statPointSeconds, 50, 15 * 60 * 1000);
    }
}

