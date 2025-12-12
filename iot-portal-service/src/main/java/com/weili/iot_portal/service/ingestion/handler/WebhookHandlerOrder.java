package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.impl.LoggingFallbackWebhookHandler;

/**
 * Webhook 事件处理器执行顺序常量
 * <p>
 * 用于统一管理所有 {@link WebhookEventHandler} 的实现类的执行顺序。
 * 数值越小优先级越高，处理器会按照 order 值从小到大执行。
 * </p>
 * <p>
 * 执行顺序说明：
 * <ul>
 *   <li>10 - 设备状态事件（最高优先级，基础状态信息）</li>
 *   <li>20 - 设备轴事件（在状态之后，轴数据依赖状态）</li>
 *   <li>25 - 刀具变更事件 / 生产事件（在轴之后，刀具信息之前）</li>
 *   <li>30 - 刀具事件（刀具信息处理）</li>
 *   <li>35 - 程序事件（在刀具之后）</li>
 *   <li>40 - 报警事件（在状态、轴、刀具之后）</li>
 *   <li>9999 - 兜底处理器（最低优先级，最后执行）</li>
 * </ul>
 * </p>
 *
 * @author system
 */
public final class WebhookHandlerOrder {

    private WebhookHandlerOrder() {
        // 工具类，禁止实例化
    }

    /**
     * 设备状态事件处理器顺序
     * 最高优先级，因为状态是其他事件的基础信息
     * 对应：{@link com.weili.iot_portal.service.ingestion.handler.DeviceStateEventHandler}
     */
    public static final int DEVICE_STATE = 10;

    /**
     * 设备轴事件处理器顺序
     * 在状态事件之后处理，轴数据可能依赖状态信息
     * 对应：{@link com.weili.iot_portal.service.ingestion.handler.DeviceAxisEventHandler}
     */
    public static final int DEVICE_AXIS = 20;

    /**
     * 刀具变更事件处理器顺序
     * 在轴事件之后，刀具信息事件之前处理
     * 对应：{@link com.weili.iot_portal.service.ingestion.handler.DeviceToolChangeEventHandler}
     */
    public static final int DEVICE_TOOL_CHANGE = 25;

    /**
     * 生产事件处理器顺序
     * 在轴事件之后，刀具信息事件之前处理
     * 对应：{@link com.weili.iot_portal.service.ingestion.handler.DeviceProductionEventHandler}
     */
    public static final int DEVICE_PRODUCTION = 25;

    /**
     * 设备加工状态事件处理器顺序
     * 在生产事件之后处理
     * 对应：{@link com.weili.iot_portal.service.ingestion.handler.DeviceWorkingStateEventHandler}
     */
    public static final int DEVICE_WORKING_STATE = 26;

    /**
     * 刀具事件处理器顺序
     * 在状态、轴之后处理，刀具信息依赖基础状态
     * 对应：{@link com.weili.iot_portal.service.ingestion.handler.DeviceToolEventHandler}
     */
    public static final int DEVICE_TOOL = 30;

    /**
     * 程序事件处理器顺序
     * 在轴、状态、刀具之后处理
     * 对应：{@link com.weili.iot_portal.service.ingestion.handler.DeviceProgramEventHandler}
     */
    public static final int DEVICE_PROGRAM = 35;

    /**
     * 报警事件处理器顺序
     * 在状态、轴、刀具之后处理，报警信息依赖设备基础状态
     * 对应：{@link com.weili.iot_portal.service.ingestion.handler.DeviceAlarmEventHandler}
     */
    public static final int DEVICE_ALARM = 40;

    /**
     * 兜底处理器顺序
     * 最低优先级，当没有其他处理器匹配时执行
     * 对应：{@link LoggingFallbackWebhookHandler}
     */
    public static final int FALLBACK = 9999;
}



