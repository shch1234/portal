package com.weili.iot_portal.common.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Long类型时间戳反序列化器
 * 将前端传来的日期时间字符串（yyyy-MM-dd HH:mm:ss）转换为Long类型时间戳（毫秒）
 *
 * 使用方式：
 * <pre>
 * &#64;JsonDeserialize(using = TimestampLongDeserializer.class)
 * private Long startTime;  // 前端传 "2024-11-13 08:00:00"，自动转为毫秒时间戳
 * </pre>
 *
 * @author luying
 * @date 2025-12-24 10:00
 */
public class TimestampLongDeserializer extends JsonDeserializer<Long> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateTimeStr = p.getValueAsString();
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }

        // 将字符串解析为 LocalDateTime
        LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr, FORMATTER);

        // 转换为毫秒时间戳
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
