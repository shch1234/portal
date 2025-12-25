package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.service.model.BatchProcessResult;
import com.weili.iot_portal.service.model.CompensationResult;
import com.weili.iot_portal.service.model.ProcessResult;
import com.weili.iot_portal.service.model.StateStatistics;
import com.weili.iot_portal.service.model.ShiftTimeRange;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 设备班次汇总服务接口
 * 负责班次相关的计算和汇总逻辑
 */
public interface IDeviceShiftSummaryService {

    /**
     * 检查班次是否应该被统计
     *
     * @param shiftRange           班次时间范围
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @return true-应该统计，false-跳过
     */
    boolean shouldProcessShift(ShiftTimeRange shiftRange, long statisticsTimeSeconds);


    /**
     * 批量处理工厂设备的班次汇总（带检查点机制）
     * 包含：检查点管理、分批处理、超时控制、结果统计
     *
     * @param factoryId            工厂ID
     * @param devices              设备列表
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @param batchSize             批处理大小
     * @param timeoutMillis         超时时间（毫秒）
     * @return 处理结果
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 处理全部设备的班次汇总（带检查点，内部按工厂分组+分批）
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 处理单个设备的班次统计（完整流程）
     * 包含：查询配置、计算班次、查询数据、计算统计、保存汇总
     *
     * @param device                设备信息
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @return 处理结果
     */
    ProcessResult processDeviceShiftComplete(
            DeviceInfoDO device,
            long statisticsTimeSeconds);

    /**
     * 处理单个设备的班次统计（仅保存汇总）
     * 适用于已经准备好所有数据的场景
     *
     * @param device                设备信息
     * @param config                班次配置
     * @param shiftRange            班次时间范围
     * @param shiftDate             班次日期
     * @param stateStats            状态统计结果
     * @return 处理结果
     */
    ProcessResult processDeviceShift(
            DeviceInfoDO device,
            com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO config,
            ShiftTimeRange shiftRange,
            LocalDate shiftDate,
            Map<String, StateStatistics> stateStats);

    /**
     * 强制更新汇总记录（用于补偿任务）
     * 即使记录已统计过，也会强制更新
     *
     * @param deviceId     设备ID
     * @param orgFactoryId 工厂ID
     * @param shiftDate    班次日期
     * @param shiftRange   班次时间范围
     * @param stateStats   状态统计结果
     */
    void forceUpdateSummary(
            Long deviceId,
            Long orgFactoryId,
            LocalDate shiftDate,
            ShiftTimeRange shiftRange,
            Map<String, StateStatistics> stateStats);

    /**
     * 补偿处理最近N天未完成的汇总记录
     *
     * @param compensationDays 补偿天数
     * @return 处理结果
     */
    CompensationResult compensatePendingSummaries(int compensationDays);
}

