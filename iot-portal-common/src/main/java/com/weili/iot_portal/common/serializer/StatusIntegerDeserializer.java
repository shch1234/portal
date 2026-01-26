package com.weili.iot_portal.common.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Status Integer 反序列化器
 * 支持从整数或对象中提取 status 值
 * <p>
 * 支持的格式：
 * <ul>
 *     <li>整数：0 或 1</li>
 *     <li>对象：{"status": 0, "name": "开启"} 或 {"status": 1, "name": "关闭"}</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 * &#64;JsonDeserialize(using = StatusIntegerDeserializer.class)
 * private Integer status;
 * </pre>
 */
public class StatusIntegerDeserializer extends JsonDeserializer<Integer> {

    public static final StatusIntegerDeserializer INSTANCE = new StatusIntegerDeserializer();

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        
        // 如果是整数，直接返回
        if (node.isInt() || node.isNumber()) {
            return node.asInt();
        }
        
        // 如果是对象，尝试提取 status 字段
        if (node.isObject()) {
            JsonNode statusNode = node.get("status");
            if (statusNode != null && (statusNode.isInt() || statusNode.isNumber())) {
                return statusNode.asInt();
            }
            // 如果没有 status 字段，尝试提取 value 字段（某些前端可能使用 value）
            JsonNode valueNode = node.get("value");
            if (valueNode != null && (valueNode.isInt() || valueNode.isNumber())) {
                return valueNode.asInt();
            }
        }
        
        // 如果是字符串，尝试解析为整数
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText());
            } catch (NumberFormatException e) {
                throw new IOException("无法将字符串解析为整数: " + node.asText(), e);
            }
        }
        
        // 如果都不匹配，抛出异常
        throw new IOException("无法反序列化 status 字段，期望整数或包含 status/value 字段的对象，实际收到: " + node.getNodeType());
    }
}
