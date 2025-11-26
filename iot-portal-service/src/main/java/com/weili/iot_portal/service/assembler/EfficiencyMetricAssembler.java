package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.common.enums.EfficiencyMetricType;
import com.weili.iot_portal.dal.dataobject.efficiency.DeviceEfficiencyMetricDO;
import com.weili.iot_portal.domain.efficiency.DeviceEfficiencyMetricVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 效率指标装配器
 */
public class EfficiencyMetricAssembler {

    /**
     * DO转VO
     */
    public static DeviceEfficiencyMetricVO toVO(DeviceEfficiencyMetricDO item) {
        if (item == null) {
            return null;
        }
        DeviceEfficiencyMetricVO vo = new DeviceEfficiencyMetricVO();
        vo.setDeviceId(item.getDeviceId());
        vo.setDeviceCode(item.getDeviceCode());
        vo.setDeviceType(item.getDeviceTypeName());
        vo.setDeviceSubType(item.getDeviceSubTypeName());
        vo.setWorkshopId(item.getWorkshopId());
        vo.setWorkshopName(item.getWorkshopName());
        vo.setShiftDate(item.getShiftDate());
        vo.setShiftCode(item.getShiftCode());
        vo.setMetricCode(item.getMetricCode());
        vo.setMetricValue(item.getMetricValue());
        vo.setIsFinalized(item.getIsFinalized());
        
        // 根据指标代码获取指标名称和单位
        EfficiencyMetricType metricType = EfficiencyMetricType.of(item.getMetricCode());
        if (metricType != null) {
            vo.setMetricName(metricType.getDisplayName());
            vo.setMetricUnit(metricType.getUnit());
        } else {
            vo.setMetricName(item.getMetricCode());
            vo.setMetricUnit("%");
        }
        
        return vo;
    }

    /**
     * DO列表转VO列表
     */
    public static List<DeviceEfficiencyMetricVO> toVOList(List<DeviceEfficiencyMetricDO> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(EfficiencyMetricAssembler::toVO)
                .collect(Collectors.toList());
    }
}

