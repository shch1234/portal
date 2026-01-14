package com.weili.iot_portal.service.device.util;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import org.slf4j.MDC;

/**
 * 设备日志上下文工具类
 * <p>
 * 用于在设备处理逻辑中设置 MDC，使日志能够显示设备编号
 * </p>
 */
public class DeviceLogContext {

    private static final String DEVICE_CODE_KEY = "deviceCode";

    /**
     * 设置设备编号到 MDC
     *
     * @param deviceCode 设备编号
     */
    public static void setDeviceCode(String deviceCode) {
        if (deviceCode != null && !deviceCode.isEmpty()) {
            MDC.put(DEVICE_CODE_KEY, "[" + deviceCode + "]");
        }
    }

    /**
     * 从设备信息设置设备编号到 MDC
     *
     * @param deviceInfo 设备信息
     */
    public static void setDeviceCode(DeviceInfoDO deviceInfo) {
        if (deviceInfo != null && deviceInfo.getDeviceCode() != null) {
            setDeviceCode(deviceInfo.getDeviceCode());
        }
    }

    /**
     * 从设备ID和设备信息设置设备编号到 MDC
     * <p>
     * 如果 deviceInfo 为 null，则尝试从 deviceId 查询设备信息
     * </p>
     *
     * @param deviceId   设备ID
     * @param deviceInfo 设备信息（可为null）
     */
    public static void setDeviceCode(Long deviceId, DeviceInfoDO deviceInfo) {
        if (deviceInfo != null) {
            setDeviceCode(deviceInfo);
        } else if (deviceId != null) {
            // 如果 deviceInfo 为 null，但 deviceId 不为 null，可以在这里查询
            // 但为了避免循环依赖，这里只设置 deviceId 作为占位符
            // 实际使用时，应该在获取 deviceInfo 后立即调用 setDeviceCode(deviceInfo)
            MDC.put(DEVICE_CODE_KEY, "[deviceId:" + deviceId + "]");
        }
    }

    /**
     * 清除设备编号 MDC
     */
    public static void clearDeviceCode() {
        MDC.remove(DEVICE_CODE_KEY);
    }

    /**
     * 执行带设备编号上下文的操作
     * <p>
     * 自动设置和清除 MDC，确保线程安全
     * </p>
     *
     * @param deviceCode 设备编号
     * @param action     要执行的操作
     * @param <T>        返回值类型
     * @return 操作结果
     */
    public static <T> T withDeviceCode(String deviceCode, java.util.function.Supplier<T> action) {
        setDeviceCode(deviceCode);
        try {
            return action.get();
        } finally {
            clearDeviceCode();
        }
    }

    /**
     * 执行带设备编号上下文的操作（无返回值）
     *
     * @param deviceCode 设备编号
     * @param action     要执行的操作
     */
    public static void withDeviceCode(String deviceCode, Runnable action) {
        setDeviceCode(deviceCode);
        try {
            action.run();
        } finally {
            clearDeviceCode();
        }
    }

    /**
     * 执行带设备编号上下文的操作（从设备信息）
     *
     * @param deviceInfo 设备信息
     * @param action     要执行的操作
     * @param <T>        返回值类型
     * @return 操作结果
     */
    public static <T> T withDeviceCode(DeviceInfoDO deviceInfo, java.util.function.Supplier<T> action) {
        setDeviceCode(deviceInfo);
        try {
            return action.get();
        } finally {
            clearDeviceCode();
        }
    }
}
