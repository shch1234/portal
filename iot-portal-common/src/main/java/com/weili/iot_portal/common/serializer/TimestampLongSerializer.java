package com.weili.iot_portal.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Long类型时间戳序列化器
 * 将Long类型的时间戳（毫秒）转换为格式化的日期时间字符串（yyyy-MM-dd HH:mm:ss）
 * 使用方式：
 * <pre>
 * &#64;JsonSerialize(using = TimestampLongSerializer.class)
 * private Long startTime;  // 内部Long类型，返回给前端时自动转为 "2024-11-13 08:00:00"
 * </pre>
 *
 * @author luying
 * @date 2025-12-24 09:40
 */
public class TimestampLongSerializer extends JsonSerializer<Long> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        // 将毫秒时间戳转换为 LocalDateTime
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(value),
                ZoneId.systemDefault()
        );

        // 格式化为字符串并写入
        gen.writeString(dateTime.format(FORMATTER));
    }
}