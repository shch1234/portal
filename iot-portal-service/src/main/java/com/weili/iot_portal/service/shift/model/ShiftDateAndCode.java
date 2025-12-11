package com.weili.iot_portal.service.shift.model;

import java.time.LocalDate;

/**
 * 班次日期和编码
 * 用于记录时间戳对应的班次日期和班次编码
 */
public record ShiftDateAndCode(LocalDate shiftDate, String shiftCode) {
}

