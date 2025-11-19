package com.weili.iot_portal.business.device_mgmt.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@TableName("biz_device_mgmt.tool_usage_history")
public class ToolUsageHistoryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private String factoryId;

    private String toolNumber;

    private String toolHolderNumber;

    private Long startTs;

    private Long endTs;

    private Long durationMs;

    private Long createdTime;
}

