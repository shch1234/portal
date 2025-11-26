package com.weili.iot_portal.domain.digital;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 厂区布局图数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryLayoutVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String factoryId;

    private String factoryName;

    private List<FactoryLayoutDeviceVO> devices;
}


