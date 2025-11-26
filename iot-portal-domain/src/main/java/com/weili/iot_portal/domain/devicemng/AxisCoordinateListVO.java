package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

import java.util.List;

/**
 * 轴坐标列表响应
 */
@Data
public class AxisCoordinateListVO {

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 查询时间戳（毫秒）
     */
    private Long queryTime;

    /**
     * 轴坐标列表
     */
    private List<AxisCoordinateVO> axes;
}

