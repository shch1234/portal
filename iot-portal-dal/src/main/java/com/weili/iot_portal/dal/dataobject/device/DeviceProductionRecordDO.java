package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.iot_portal.domain.record.TimeRangeRecord;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备产量明细（device_production_record）
 */
@Data
@TableName("device_production_record")
public class DeviceProductionRecordDO implements Serializable, TimeRangeRecord {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private Long deviceInfoId;

    private Long orgFactoryId;

    private Long startTs;

    private Long endTs;

    /**
     * 持续时长（毫秒，对应 duration_s 列）
     * 注意：虽然数据库列名为 duration_s，但实际存储的是毫秒值
     * 统一使用 Long 类型，与其他记录表保持一致
     */
    private Long durationS;

    private String workpieceNo;

    private String workpieceType;

    private String batchNo;

    private String programName;

    /**
     * 所属班次日期（对应 shift_date 列）
     */
    private LocalDate shiftDate;

    /**
     * 班次编码（对应 shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer shiftCode;

    private String countSource;

    /**
     * 扩展属性（JSON，对应 properties 列）
     * 用于存储异常标记等信息，如离线异常、时间戳异常等
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;

    /**
     * 扩展属性（用于存储异常标记等信息）
     */
    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}


