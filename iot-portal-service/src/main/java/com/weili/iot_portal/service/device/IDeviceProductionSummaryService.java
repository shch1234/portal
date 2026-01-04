package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.device.req.DeviceProductionStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceProductionStatisticsRespVO;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;

import java.util.List;

/**
 * 设备产量汇总服务接口
 * 负责将 device_production_record 按班次汇总到 device_production_summary
 */
public interface IDeviceProductionSummaryService {
    /**
     * 处理所有设备（带检查点）
     * 
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @param batchSize 每批处理设备数
     * @param timeoutMillis 超时时间（毫秒）
     * @param lookbackDays 处理时间范围（天），只处理当前时间往前推N天内的数据
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis,
            int lookbackDays);

    /**
     * 处理指定工厂的设备（带检查点）
     * 
     * @param factoryId 工厂ID
     * @param devices 设备列表
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @param batchSize 每批处理设备数
     * @param timeoutMillis 超时时间（毫秒）
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis);
}

