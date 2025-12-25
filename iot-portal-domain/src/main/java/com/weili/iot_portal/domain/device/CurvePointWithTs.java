package com.weili.iot_portal.domain.device;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 带时间戳的曲线点
 * 用于设备轴数据的曲线解析
 * <p>
 * 注意：字段使用 public 访问控制，便于在数据处理中直接访问
 * </p>
 */
@NoArgsConstructor
@AllArgsConstructor
public class CurvePointWithTs {
    public Long ts;
    public BigDecimal value;
}

