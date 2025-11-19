package com.weili.iot_portal.business.device_base.service;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.domain.model.DeviceBaseInfoListVO;
import com.weili.iot_portal.business.device_base.domain.model.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceBaseInfoCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceBaseInfoUpdateReq;

/**
 * 设备基础信息服务（主数据管理）
 */
public interface DeviceBaseInfoService {

    DeviceBaseInfoVO create(String tenantId, String operator, DeviceBaseInfoCreateReq request);

    DeviceBaseInfoVO update(String tenantId, String operator, DeviceBaseInfoUpdateReq request);

    DeviceBaseInfoVO getById(String tenantId, String factoryId, String id);

    DeviceBaseInfoVO getByDeviceCode(String tenantId, String deviceCode);

    PageResult<DeviceBaseInfoVO> page(String tenantId, String factoryId, DeviceBaseInfoQueryReq request);

    PageResult<DeviceBaseInfoListVO> list(String tenantId, String factoryId, DeviceBaseInfoQueryReq request);

    boolean delete(String tenantId, String id);
}

