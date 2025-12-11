package com.weili.iot_portal.common.enums;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * @EnumName: WebHookCategoryType
 * @Description:
 * @Author: luying
 **/
public enum WebHookCategoryType {

    @JsonPropertyDescription("业务类")
    BUSINESS,

    @JsonPropertyDescription("实时类")
    REALTIME
}
