package com.weili.iot_portal.service.ingestion.model;

import lombok.experimental.UtilityClass;

/**
 * 通用事件类型
 */
@UtilityClass
public class RealtimeIngestionEventType {

    public static final String AXIS_COORDINATE = "axisCoordinate";
    public static final String SPINDLE_SPEED = "spindleSpeed";
    public static final String FEED_RATE = "feedRate";
    public static final String TOOL_INFO = "toolInfo";
    public static final String TOOL_COMPENSATION = "toolCompensation";
    public static final String PROGRAM_INFO = "programInfo";
    public static final String PROGRAM_CODE = "programCode";

    // 其他事件类型可在此扩展
}

