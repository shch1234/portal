package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.domain.ingestion.MetricsCalculationResult;
import com.weili.iot_portal.domain.ingestion.RealtimeMetricSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 设备实时指标计算服务接口
 * 负责计算设备的OEE相关指标（时间开动率、性能率、可用率、故障率、OEE等）
 */
public interface IDeviceMetricsService {

    /**
     * 批量处理工厂设备的指标计算（带检查点机制）
     * 包含：检查点管理、分批处理、超时控制、结果统计
     *
     * @param factoryId             工厂ID
     * @param devices               设备列表
     * @param calculationTimeSeconds 计算时间点（秒）
     * @param batchSize             批处理大小
     * @param timeoutMillis         超时时间（毫秒）
     * @return 处理结果
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long calculationTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 处理全部设备的指标计算（带检查点，内部按工厂分组+分批）
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long calculationTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 批量计算所有设备的实时指标（简化版本，不包含检查点）
     *
     * @return 处理结果，包含成功和失败数量
     */
    MetricsCalculationResult calculateAllDevicesMetrics();

    /**
     * 计算单个设备的实时指标
     *
     * @param device 设备信息
     */
    void calculateDeviceMetrics(DeviceInfoDO device);

    /**
     * 获取设备实时指标快照（从Redis读取）
     *
     * @param factoryId 工厂ID（可为空，用于key维度）
     * @param deviceId  设备ID
     * @return 指标快照，可为空（缓存不存在）
     */
    Optional<RealtimeMetricSnapshot> getDeviceRealtimeMetrics(Long factoryId, Long deviceId);

    /**
     * 批量获取设备实时指标快照（从Redis批量读取）
     * <p>
     * 性能优化：使用批量读取减少 Redis 网络往返次数
     *
     * @param factoryId 工厂ID（可为空，用于key维度）
     * @param deviceIds 设备ID列表
     * @return 设备ID到指标快照的Map，只包含存在数据的设备
     */
    java.util.Map<Long, RealtimeMetricSnapshot> batchGetDeviceRealtimeMetrics(
            Long factoryId, List<Long> deviceIds);
}

