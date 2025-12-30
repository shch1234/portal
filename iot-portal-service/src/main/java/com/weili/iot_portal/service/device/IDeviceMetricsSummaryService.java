package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;

import java.util.List;

/**
 * 设备班次指标汇总服务
 * 依赖已完成的 device_state_summary，计算班次级指标并写入 device_metrics_summary
 * 注意：所有时间戳参数统一使用毫秒（milliseconds）
 */
public interface IDeviceMetricsSummaryService {

    /**
     * 批量处理所有设备的班次指标汇总（带检查点机制）
     *
     * @param statPointMillis 统计时间点（毫秒），仅处理 shift_end_ts <= statPoint 的已完成汇总
     * @param batchSize       每批处理设备数
     * @param timeoutMillis   超时时间（毫秒）
     * @return 处理结果
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long statPointMillis,
            int batchSize,
            long timeoutMillis);

    /**
     * 批量处理所有设备的班次指标汇总（带检查点机制和时间范围限制）
     *
     * @param statPointMillis 统计时间点（毫秒），仅处理 shift_end_ts <= statPoint 的已完成汇总
     * @param batchSize       每批处理设备数
     * @param timeoutMillis   超时时间（毫秒）
     * @param lookbackDays    处理时间范围（天），只处理当前时间往前推N天内的数据
     * @param dataReadyDelayHours 数据就绪延迟时间（小时），只处理班次结束时间在统计时间点之前至少N小时的班次
     * @return 处理结果
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long statPointMillis,
            int batchSize,
            long timeoutMillis,
            int lookbackDays,
            int dataReadyDelayHours);

    /**
     * 批量处理工厂设备的班次指标汇总（带检查点机制）
     *
     * @param factoryId       工厂ID
     * @param devices         设备列表
     * @param statPointMillis 统计时间点（毫秒）
     * @param batchSize       批大小
     * @param timeoutMillis   超时时间（毫秒）
     * @param dataReadyDelayHours 数据就绪延迟时间（小时），只处理班次结束时间在统计时间点之前至少N小时的班次
     * @return 处理结果
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long statPointMillis,
            int batchSize,
            long timeoutMillis,
            int dataReadyDelayHours);

    /**
     * 向后兼容旧接口（不带检查点），内部可委托带检查点的实现
     */
    default BatchProcessResult processAllDevices(long statPointMillis) {
        return processAllDevicesWithCheckpoint(statPointMillis, 50, 15 * 60 * 1000);
    }
}

