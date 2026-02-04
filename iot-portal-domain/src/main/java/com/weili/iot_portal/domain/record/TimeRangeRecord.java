package com.weili.iot_portal.domain.record;

import java.time.LocalDate;
import java.util.Map;

/**
 * 时间范围记录接口
 * <p>
 * 所有有时间范围（startTs、endTs、durationS）的记录都应该实现此接口
 * 用于统一处理跨班次截断、过期数据、班次限制等逻辑
 * </p>
 * <p>
 * 实现类示例：
 * - {@link com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO}
 * - {@link com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO}
 * - {@link com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO}
 * </p>
 *
 * @author system
 */
public interface TimeRangeRecord {

    /**
     * 获取开始时间戳（毫秒）
     */
    Long getStartTs();

    /**
     * 设置开始时间戳（毫秒）
     */
    void setStartTs(Long startTs);

    /**
     * 获取结束时间戳（毫秒，null表示进行中）
     */
    Long getEndTs();

    /**
     * 设置结束时间戳（毫秒，null表示进行中）
     */
    void setEndTs(Long endTs);

    /**
     * 获取持续时长（毫秒）
     */
    Long getDurationS();

    /**
     * 设置持续时长（毫秒）
     */
    void setDurationS(Long durationS);

    /**
     * 获取班次日期
     */
    LocalDate getShiftDate();

    /**
     * 设置班次日期
     */
    void setShiftDate(LocalDate shiftDate);

    /**
     * 获取班次编码
     */
    Integer getShiftCode();

    /**
     * 设置班次编码
     */
    void setShiftCode(Integer shiftCode);

    /**
     * 获取设备ID
     */
    Long getDeviceInfoId();

    /**
     * 获取工厂ID
     */
    Long getOrgFactoryId();

    /**
     * 获取记录ID（主键）
     * <p>
     * 注意：某些实现类可能没有ID（如临时记录），此时返回null
     * </p>
     *
     * @return 记录ID，如果不存在则返回null
     */
    default Long getId() {
        return null;
    }

    /**
     * 获取扩展属性（可选，用于存储异常标记等信息）
     * <p>
     * 如果记录类型不支持properties，可以返回null
     * </p>
     */
    default Map<String, Object> getProperties() {
        return null;
    }

    /**
     * 设置扩展属性（可选）
     */
    default void setProperties(Map<String, Object> properties) {
        // 默认实现为空，子类可以覆盖
    }
}
