package com.weili.iot_portal.task.device.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备状态跨班次拆分任务配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "device.state.shift.split")
public class DeviceStateShiftSplitConfig {

    /**
     * 每批处理的记录数量（默认100）
     */
    private int batchSize = 100;

    /**
     * 只处理开始时间在此时间之后的记录（小时，默认24小时前）
     * 用于性能优化，避免扫描过旧的数据
     */
    private Long startTsAfterHours = 24L;

    /**
     * 班次边界时间窗口（分钟）
     * 在班次边界时间前后多少分钟内频繁执行
     * 例如：如果设置为30，则在8:00前后30分钟内（7:30-8:30）频繁执行
     * 默认10分钟
     */
    private int boundaryWindowMinutes = 10;

    /**
     * 班次边界时间列表（HH:mm格式，多个用逗号分隔）
     * 例如：08:00,20:00 表示在8:00和20:00前后频繁执行
     * 默认：08:00,20:00（两班制的常见边界时间）
     */
    private String boundaryTimes = "08:00,20:00";

    /**
     * 正常执行频率下的快速返回阈值（分钟）
     * 如果当前时间不在班次边界窗口内，且距离上次执行时间小于此阈值，可以快速返回
     * 默认5分钟（避免频繁执行）
     */
    private int quickReturnThresholdMinutes = 5;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 获取开始时间阈值（毫秒）
     */
    public Long getStartTsAfter() {
        if (startTsAfterHours == null || startTsAfterHours <= 0) {
            return null;
        }
        return System.currentTimeMillis() - (startTsAfterHours * 60 * 60 * 1000L);
    }

    /**
     * 获取班次边界时间列表
     *
     * @return 班次边界时间列表（LocalTime）
     */
    public List<LocalTime> getBoundaryTimeList() {
        List<LocalTime> times = new ArrayList<>();
        if (boundaryTimes == null || boundaryTimes.trim().isEmpty()) {
            return times;
        }

        String[] timeStrs = boundaryTimes.split(",");
        for (String timeStr : timeStrs) {
            try {
                LocalTime time = LocalTime.parse(timeStr.trim(), TIME_FORMATTER);
                times.add(time);
            } catch (Exception e) {
                // 忽略解析失败的时间
            }
        }
        return times;
    }

    /**
     * 判断当前时间是否在班次边界时间窗口内
     * <p>
     * 窗口定义：边界时间前后N分钟
     * 例如：边界时间08:00，窗口30分钟，则窗口为07:30-08:30
     * </p>
     *
     * @param currentTime 当前时间（LocalTime）
     * @return true 如果在窗口内
     */
    public boolean isNearBoundaryTime(LocalTime currentTime) {
        List<LocalTime> boundaryTimes = getBoundaryTimeList();
        if (boundaryTimes.isEmpty()) {
            return false; // 如果没有配置边界时间，默认不在窗口内
        }

        for (LocalTime boundaryTime : boundaryTimes) {
            // 计算当前时间与边界时间的差值（分钟，考虑跨天）
            int minutesDiff = calculateMinutesDifference(currentTime, boundaryTime);
            
            // 如果在窗口内（前后boundaryWindowMinutes分钟）
            if (Math.abs(minutesDiff) <= boundaryWindowMinutes) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算两个时间的差值（分钟）
     * 考虑跨天的情况，返回绝对值最小的差值
     * <p>
     * 示例：
     * - 当前时间23:00，边界时间08:00：差值为 -9小时（-540分钟），绝对值540分钟
     * - 当前时间01:00，边界时间08:00：差值为 -7小时（-420分钟），绝对值420分钟
     * - 当前时间07:30，边界时间08:00：差值为 -30分钟，绝对值30分钟（在窗口内）
     * - 当前时间08:30，边界时间08:00：差值为 30分钟，绝对值30分钟（在窗口内）
     * </p>
     */
    private int calculateMinutesDifference(LocalTime currentTime, LocalTime boundaryTime) {
        int currentMinutes = currentTime.toSecondOfDay() / 60;
        int boundaryMinutes = boundaryTime.toSecondOfDay() / 60;
        
        // 正向差值（不跨天）
        int diff1 = currentMinutes - boundaryMinutes;
        
        // 反向差值（跨天）
        int diff2;
        if (currentMinutes >= boundaryMinutes) {
            // 当前时间 >= 边界时间，跨天是向前跨
            diff2 = currentMinutes - (boundaryMinutes + 24 * 60);
        } else {
            // 当前时间 < 边界时间，跨天是向后跨
            diff2 = (currentMinutes + 24 * 60) - boundaryMinutes;
        }
        
        // 取绝对值较小的差值
        if (Math.abs(diff2) < Math.abs(diff1)) {
            return diff2;
        }
        
        return diff1;
    }
}
