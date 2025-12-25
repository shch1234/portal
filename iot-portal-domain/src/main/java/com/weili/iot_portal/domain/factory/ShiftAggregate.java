package com.weili.iot_portal.domain.factory;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 班次聚合数据
 * 用于工厂指标汇总时的班次级别聚合
 * <p>
 * 注意：字段使用 public 访问控制，便于在聚合计算中直接访问和修改
 * </p>
 */
@NoArgsConstructor
@AllArgsConstructor
public class ShiftAggregate {
    public LocalDate shiftDate;
    public String shiftCode;
    public Long shiftStartTs;
    public Long shiftEndTs;
    public BigDecimal sumOee = BigDecimal.ZERO;
    public BigDecimal sumUptime = BigDecimal.ZERO;
    public BigDecimal sumPerformance = BigDecimal.ZERO;
    public BigDecimal sumAvailability = BigDecimal.ZERO;
    public BigDecimal sumFault = BigDecimal.ZERO;
    public long sumWeight = 0;
    public int validDevices = 0;

    public ShiftAggregate(LocalDate shiftDate, String shiftCode, Long shiftStartTs, Long shiftEndTs) {
        this.shiftDate = shiftDate;
        this.shiftCode = shiftCode;
        this.shiftStartTs = shiftStartTs;
        this.shiftEndTs = shiftEndTs;
    }
}

