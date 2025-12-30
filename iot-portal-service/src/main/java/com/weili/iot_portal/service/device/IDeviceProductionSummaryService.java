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
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 处理指定工厂的设备（带检查点）
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 查询设备产量统计数据
     * @param reqVO 查询请求参数（设备ID和时间范围）
     * @return 产量统计响应数据（当日加工数量 + 趋势图数据）
     */
    DeviceProductionStatisticsRespVO getDeviceProductionStatistics(DeviceProductionStatisticsReqVO reqVO);
}

