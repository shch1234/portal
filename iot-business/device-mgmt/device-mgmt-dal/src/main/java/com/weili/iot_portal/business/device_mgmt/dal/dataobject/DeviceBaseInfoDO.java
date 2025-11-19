package com.weili.iot_portal.business.device_mgmt.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Map;

/**
 * 设备基础信息数据对象（MyBatis Plus）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_device_base.device_base_info", autoResultMap = true)
public class DeviceBaseInfoDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = 3545101187655639470L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String tbDeviceId;

    private String deviceCode;

    private String deviceName;

    private String deviceTypeId;

    private String deviceModelId;

    private String deviceTypeName;

    private String deviceSubTypeName;

    private String modelName;

    private String manufacturer;

    private String factoryId;

    private String workshopId;

    private String productionLineId;

    private String factoryName;

    private String workshopName;

    private String productionLineName;

    private String deviceStatus;

    private Boolean isMonitored;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraProperties;

    private String remarks;

    private String createdBy;

    private String updatedBy;
}


