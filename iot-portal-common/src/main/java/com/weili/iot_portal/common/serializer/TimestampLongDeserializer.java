package com.weili.iot_portal.common.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Long类型时间戳反序列化器
 * 将前端传来的日期时间字符串转换为Long类型时间戳（毫秒）
 * <p>
 * 支持的格式：
 * <ul>
 *     <li>yyyy-MM-dd HH:mm:ss - 完整日期时间</li>
 *     <li>yyyy-MM-dd - 纯日期（默认时间为 00:00:00）</li>
 * </ul>
 *
 * 使用方式：
 * <pre>
 * &#64;JsonDeserialize(using = TimestampLongDeserializer.class)
 * private Long startTime;  // 前端传 "2024-11-13 08:00:00" 或 "2024-11-13"，自动转为毫秒时间戳
 * </pre>
 *
 * @author luying
 * @date 2025-12-24 10:00
 */
public class TimestampLongDeserializer extends JsonDeserializer<Long> {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateTimeStr = p.getValueAsString();
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }

        dateTimeStr = dateTimeStr.trim();

        try {
            // 尝试解析为完整日期时间格式（yyyy-MM-dd HH:mm:ss）
            LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr, DATETIME_FORMATTER);
            return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e1) {
            try {
                // 解析失败，尝试纯日期格式（yyyy-MM-dd），默认时间为 00:00:00
                LocalDate date = LocalDate.parse(dateTimeStr, DATE_FORMATTER);
                LocalDateTime dateTime = date.atStartOfDay();
                return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException e2) {
                // 两种格式都解析失败，抛出异常
                throw new IOException("无法解析日期时间字符串: " + dateTimeStr +
                        "，支持的格式: yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd", e2);
            }
        }
    }
}
