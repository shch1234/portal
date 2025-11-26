package com.weili.iot_portal.service.efficiency;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.efficiency.DeviceEfficiencyMetricVO;
import com.weili.iot_portal.domain.efficiency.request.EfficiencyMetricQueryReq;

/**
 * 效率指标服务
 * 
 * <p>对应需求：4.4 能效管理
 * - 每个指标独立
 * - 查询指定指标（如OEE）在给定时间点（班次）时，所有设备的相关指标
 * - 支持排序
 */
public interface EfficiencyMetricService {

    /**
     * 查询效率指标列表
     * 
     * <p>查询指定指标在指定班次时，所有设备的指标值
     * 
     * @param tenantId 租户ID
     * @param request 查询请求
     * @return 分页结果
     */
    PageResult<DeviceEfficiencyMetricVO> getDeviceMetrics(String tenantId, EfficiencyMetricQueryReq request);
}

