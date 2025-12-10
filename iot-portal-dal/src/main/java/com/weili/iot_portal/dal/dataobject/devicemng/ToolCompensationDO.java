package com.weili.iot_portal.dal.dataobject.devicemng;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 刀补补偿记录（device_tool_compensation）
 */
@Data
@TableName(value = "device_tool_compensation", autoResultMap = true)
public class ToolCompensationDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private String deviceInfoId;

    /** 所属厂区ID */
    private String orgFactoryId;

    /** 刀补号 */
    private String toolHolderNo;

    /** 补偿值（JSON） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> compValueJson;

    /** 版本号（覆盖时+1） */
    private Integer version;

    /** 生效开始时间（秒） */
    private Long startTs;

    /** 生效结束时间（秒，NULL 表示当前有效） */
    private Long endTs;

    /** 是否当前有效：1-有效 0-历史 */
    private Integer active;
}


