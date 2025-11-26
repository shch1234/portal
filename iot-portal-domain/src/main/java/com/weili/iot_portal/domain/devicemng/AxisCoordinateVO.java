package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

/**
 * 轴坐标信息
 */
@Data
public class AxisCoordinateVO {

    /**
     * 轴名称（如：X、Y、Z、A、B、C等）
     */
    private String axisName;

    /**
     * 绝对坐标
     */
    private Double absoluteCoordinate;

    /**
     * 相对坐标
     */
    private Double relativeCoordinate;

    /**
     * 机械坐标
     */
    private Double machineCoordinate;

    /**
     * 剩余坐标
     */
    private Double remainingCoordinate;
}

