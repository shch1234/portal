package com.weili.iot_portal.common.exception;

import com.weili.basic.common.enums.IEnumBase;

/**
 * @author luying
 * @className IotPortalErrorCode
 * @description IoT Portal 项目错误码枚举
 * @date 2025-12-11 09:14
 **/
public enum IotPortalErrorCode implements IEnumBase {
    // Webhook 相关错误
    WEBHOOK_CATEGORY_NOT_SUPPORTED("不支持的 webhook category"),
    WEBHOOK_MESSAGE_ID_MISSING("缺少 messageId"),
    WEBHOOK_SECRET_VALIDATION_FAILED("Webhook密钥验证失败"),
    WEBHOOK_SIGNATURE_PARAMS_MISSING("Webhook签名参数缺失"),
    WEBHOOK_REQUEST_EXPIRED("Webhook请求已过期"),
    WEBHOOK_DUPLICATE_REQUEST("Webhook重复请求"),
    WEBHOOK_SIGNATURE_VALIDATION_FAILED("Webhook签名验证失败"),
    WEBHOOK_SIGNATURE_ALGORITHM_UNAVAILABLE("签名算法不可用"),
    WEBHOOK_EVENT_TYPE_UNSUPPORTED("不支持的事件类型"),

    // 事件处理相关错误
    EVENT_DATA_EMPTY("事件数据不能为空"),
    EVENT_CURRENT_STATE_EMPTY("当前状态不能为空"),
    EVENT_TIMESTAMP_EMPTY("事件时间戳不能为空"),
    EVENT_DEVICE_STATE_PROCESSING("设备状态正在处理中，请稍后重试"),
    EVENT_TOOL_NUMBER_EMPTY("当前刀号不能为空"),
    EVENT_TOOL_CHANGE_PROCESSING("刀具变更处理中，请稍后重试"),
    EVENT_PRODUCTION_STATUS_INVALID("status 必须为 start/end"),
    EVENT_PRODUCTION_TIMESTAMP_EMPTY("ts 不能为空"),

    // 设备相关错误
    DEVICE_CODE_EMPTY("设备编号不能为空"),
    DEVICE_ID_EMPTY("设备ID不能为空"),
    DEVICE_NOT_FOUND("设备不存在"),
    DEVICE_NOT_ASSOCIATED_FACTORY("设备未关联工厂"),
    DEVICE_NOT_REGISTERED("设备未建档，请检查设备编号或在组织架构中新增设备"),
    DEVICE_NOT_BELONG_TO_FACTORY("设备不属于当前选择的工厂，无权访问"),
    FACTORY_INFO_NOT_FOUND("未获取到工厂信息，请先选择工厂"),
    FACTORY_ID_EMPTY("工厂ID不能为空"),
    SHIFT_CONFIG_EMPTY("班次配置为空"),

    // 设备基础数据相关错误
    DEVICE_INFO_NOT_FOUND("设备信息不存在"),
    DEVICE_CODE_DUPLICATE("设备编号已存在"),
    DEVICE_TB_DEVICE_ID_DUPLICATE("ThingsBoard设备ID已存在"),
    DEVICE_MODEL_NOT_FOUND("设备型号不存在"),
    DEVICE_MODEL_CODE_DUPLICATE("设备型号编码已存在"),
    DEVICE_TYPE_NOT_FOUND("设备类型不存在"),
    DEVICE_TYPE_CODE_DUPLICATE("设备类型编码已存在"),
    DEVICE_TYPE_HAS_CHILDREN("设备类型存在子类型，无法删除"),
    DEVICE_ORG_NOT_FOUND("组织单元不存在"),
    DEVICE_ORG_CODE_DUPLICATE("组织单元编码已存在"),
    DEVICE_ORG_HAS_CHILDREN("组织单元存在子单元，无法删除"),
    DEVICE_ORG_HAS_DEVICES("组织单元下存在设备，无法删除"),
    DEVICE_LOCATION_NOT_FOUND("设备位置信息不存在"),
    DEVICE_NETWORK_CONFIG_NOT_FOUND("设备网络配置不存在"),
    DEVICE_NETWORK_CONFIG_DUPLICATE("设备已存在网络配置"),

    // 默认错误
    DEFAULT_ERROR("系统错误");

    private final String msg;

    IotPortalErrorCode(String msg) {
        this.msg = msg;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return this.msg;
    }
}
