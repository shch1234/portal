package com.weili.iot_portal.service.efficiency.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.api.shift.ShiftQueryApi;
import com.weili.iot_portal.common.enums.EfficiencyMetricType;
import com.weili.iot_portal.dal.dataobject.efficiency.DeviceEfficiencyMetricDO;
import com.weili.iot_portal.dal.repository.effiency.EfficiencyMetricRepository;
import com.weili.iot_portal.domain.efficiency.DeviceEfficiencyMetricVO;
import com.weili.iot_portal.domain.efficiency.request.EfficiencyMetricQueryReq;
import com.weili.iot_portal.domain.shift.ShiftInfoVO;
import com.weili.iot_portal.service.assembler.EfficiencyMetricAssembler;
import com.weili.iot_portal.service.efficiency.EfficiencyMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 效率指标服务实现
 * 
 * <p>通过公共接口 ShiftQueryApi 获取班次信息，实现模块间解耦
 */
@Service
@RequiredArgsConstructor
public class EfficiencyMetricServiceImpl implements EfficiencyMetricService {

    private final EfficiencyMetricRepository efficiencyMetricRepository;
    private final ShiftQueryApi shiftQueryApi;

    @Override
    public PageResult<DeviceEfficiencyMetricVO> getDeviceMetrics(EfficiencyMetricQueryReq request) {
        // 参数校验
        validateRequest(request);

        // 计算班次日期和编码（如果未提供，则使用当前班次）
        String shiftDate = request.getShiftDate();
        String shiftCode = request.getShiftCode();
        
        if (!StringUtils.hasText(shiftDate) || !StringUtils.hasText(shiftCode)) {
            // 自动计算当前班次（通过公共接口获取班次信息）
            ShiftInfoVO currentShift = shiftQueryApi.getFactoryCurrentShift(
                    request.getFactoryId(), request.getWorkshopId(), null);
            if (!StringUtils.hasText(shiftDate)) {
                shiftDate = currentShift.getShiftDate();
            }
            if (!StringUtils.hasText(shiftCode)) {
                shiftCode = currentShift.getShiftCode();
            }
        }

        // 根据指标类型确定默认排序方向（停机率默认升序，其他默认降序）
        String defaultSortDirection = getDefaultSortDirection(request.getMetricCode());
        String sortDirection = request.getSortDirection();
        if (!StringUtils.hasText(sortDirection)) {
            sortDirection = defaultSortDirection;
        }

        // 查询数据
        PageResult<DeviceEfficiencyMetricDO> pageResult = efficiencyMetricRepository.selectDeviceMetrics(
                request.getFactoryId(),
                request.getWorkshopId(),
                request.getMetricCode(),
                shiftDate,
                shiftCode,
                request.getSortBy() != null ? request.getSortBy() : "metricValue",
                sortDirection,
                request.getPageNo() != null ? request.getPageNo() : 1,
                request.getPageSize() != null ? request.getPageSize() : 10);

        // 转换为VO
        return PageResult.of(
                EfficiencyMetricAssembler.toVOList(pageResult.getList()),
                pageResult.getTotal(),
                pageResult.getPageNo(),
                pageResult.getPageSize());
    }

    /**
     * 根据指标类型获取默认排序方向
     * 
     * <p>停机率默认升序（越低越好），其他指标默认降序（越高越好）
     */
    private String getDefaultSortDirection(String metricCode) {
        if ("downtimeRate".equals(metricCode)) {
            return "ASC";  // 停机率越低越好
        }
        return "DESC";  // 其他指标越高越好
    }

    /**
     * 参数校验
     */
    private void validateRequest(EfficiencyMetricQueryReq request) {
        if (request == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "查询请求不能为空");
        }
        if (!StringUtils.hasText(request.getFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "工厂ID不能为空");
        }
        if (!StringUtils.hasText(request.getMetricCode())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "指标代码不能为空");
        }

        // 验证指标代码是否有效
        EfficiencyMetricType metricType = EfficiencyMetricType.of(request.getMetricCode());
        if (metricType == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                    "无效的指标代码：" + request.getMetricCode() +
                    "，有效值：oee、timeAvailability、performanceRate、equipmentAvailability、downtimeRate");
        }
    }
}

