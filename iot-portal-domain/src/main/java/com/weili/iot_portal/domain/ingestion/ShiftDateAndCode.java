package com.weili.iot_portal.domain.ingestion;

import java.time.LocalDate;

/**
 * 班次日期和编码
 * 用于记录时间戳对应的班次日期和班次编码
 * <p>
 * 班次编码使用数字编码（1-3）：
 * - 1: 一班
 * - 2: 二班
 * - 3: 三班
 * </p>
 */
public record ShiftDateAndCode(LocalDate shiftDate, Integer shiftCode) {
}

