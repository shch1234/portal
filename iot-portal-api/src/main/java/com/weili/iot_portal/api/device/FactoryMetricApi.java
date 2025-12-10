package com.weili.iot_portal.api.device;

import com.weili.iot_portal.domain.digital.FactoryMetricsVO;

/**
 * 工厂级效率指标查询API接口（公共接口）
 * 
 * <p>用于其他业务模块（如数字大屏）查询工厂级效率指标
 * 
 * <p>设计原则：
 * - 支持工厂数据隔离
 * - 支持实时和历史数据查询
 */
public interface FactoryMetricApi{

    /**
     * 获取工厂级效率指标（当前班次实时值）
     * 
     * <p>如果当前班次的历史数据已存在，则返回历史数据；
     * 否则实时计算当前班次的指标值。
     * 
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param days 历史趋势天数（可选，默认7天）
     * @return 工厂级效率指标（包含当前值和历史趋势）
     * @throws com.weili.basic.common.exception.ServiceException 如果工厂不存在
     */
    FactoryMetricsVO getCurrentFactoryMetrics(String factoryId, Integer days);

    /**
     * 获取工厂级效率指标历史趋势（过去N天）
     * 
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param days 天数（默认7天）
     * @return 工厂级效率指标（仅包含历史趋势）
     * @throws com.weili.basic.common.exception.ServiceException 如果工厂不存在
     */
    FactoryMetricsVO getFactoryMetricsHistory(String factoryId, Integer days);
}

