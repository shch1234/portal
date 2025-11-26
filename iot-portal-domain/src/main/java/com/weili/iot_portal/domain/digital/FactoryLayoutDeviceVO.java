package com.weili.iot_portal.domain.digital;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 布局图设备展示信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryLayoutDeviceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;

    /**
     * 设备编号（威力编号）
     */
    private String deviceCode;

    private String deviceTypeName;

    private String deviceSubTypeName;

    private String modelName;

    /**
     * 当前状态（加工中/待机/故障/关机/未知）
     */
    private String currentStatus;
}


