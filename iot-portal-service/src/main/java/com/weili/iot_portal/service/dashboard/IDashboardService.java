package com.weili.iot_portal.service.dashboard;

import com.weili.iot_portal.domain.dashboard.AlarmDurationTopRespVO;
import com.weili.iot_portal.domain.dashboard.DeviceListRespVO;
import com.weili.iot_portal.domain.dashboard.DeviceStateStatisticsRespVO;
import com.weili.iot_portal.domain.dashboard.MetricTrendRespVO;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard业务服务接口
 *
 * @author luying
 */
public interface IDashboardService {

    /**
     * 获取设备状态统计数据
     *
     * @param factoryId 工厂ID
     * @return 设备状态统计
     */
    DeviceStateStatisticsRespVO getDeviceStateStatistics(Long factoryId);

    /**
     * 获取报警时长TOP N
     *
     * @param factoryId 工厂ID
     * @param topN TOP N数量
     * @return 报警时长TOP N列表
     */
    List<AlarmDurationTopRespVO> getAlarmDurationTop(Long factoryId, Integer topN);

    /**
     * 获取指标趋势数据
     *
     * @param factoryId 工厂ID
     * @param metricType 指标类型：OEE-平均OEE, UTILIZATION-平均设备利用率
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 指标趋势数据
     */
    MetricTrendRespVO getMetricTrend(Long factoryId, String metricType, LocalDate startDate, LocalDate endDate);

    /**
     * 获取工厂下的所有设备列表（包含实时状态）
     *
     * @param factoryId 工厂ID
     * @return 设备列表
     */
    List<DeviceListRespVO> getDeviceList(Long factoryId);
}
