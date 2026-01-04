package com.weili.iot_portal.common.enums;

/**
 * @EnumName: TransitionType
 * @Description:
 * @Author: luying
 **/
public enum TransitionType {
    FIRST_RECORD,       // 数据库无记录（首次记录）
    NORMAL_CHANGE,      // 正常换刀
    TOOL_UNCHANGED,     // 刀具未变化
    TOOL_MISMATCH,      // 刀具不匹配
    FIRST_CONNECTION    // 首次连接（数据库有记录但previousToolNo为空）
}
