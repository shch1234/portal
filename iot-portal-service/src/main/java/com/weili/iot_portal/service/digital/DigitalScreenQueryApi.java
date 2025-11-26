package com.weili.iot_portal.service.digital;


import com.weili.iot_portal.domain.digital.AlarmRankingVO;
import com.weili.iot_portal.domain.digital.FactoryLayoutVO;
import com.weili.iot_portal.domain.digital.FactoryMetricsVO;
import com.weili.iot_portal.domain.digital.FactoryStatusSummaryVO;

import java.util.List;

/**
 * 数字大屏查询API
 */
public interface DigitalScreenQueryApi {

    /**
     * 查询厂区布局图所需的设备数据
     *
     * @param tenantId  租户ID
     * @param factoryId 厂区ID
     * @return 布局图数据
     */
    FactoryLayoutVO getFactoryLayout(String tenantId, String factoryId);

    /**
     * 查询厂区状态监测数据（设备数量与占比）
     *
     * @param tenantId  租户ID
     * @param factoryId 厂区ID
     * @return 状态监测数据
     */
    FactoryStatusSummaryVO getFactoryStatusSummary(String tenantId, String factoryId);

    /**
     * 查询报警排行（当前报警时长Top）
     *
     * @param tenantId  租户ID
     * @param factoryId 厂区ID
     * @param limit     返回数量，可选
     * @return 报警排行
     */
    List<AlarmRankingVO> getAlarmDurationRanking(String tenantId, String factoryId, Integer limit);

    /**
     * 查询工厂级效率指标（平均OEE、平均设备利用率）
     *
     * <p>返回当前班次的实时值和历史趋势
     *
     * @param tenantId  租户ID
     * @param factoryId 厂区ID
     * @param days 历史趋势天数（可选，默认7天）
     * @return 工厂级效率指标（包含当前值和历史趋势）
     */
    FactoryMetricsVO getFactoryMetrics(String tenantId, String factoryId, Integer days);
}


