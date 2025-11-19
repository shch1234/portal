package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceBaseInfoListVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoCreateReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoUpdateReq;

/**
 * 设备基础信息服务
 */
public interface DeviceBaseInfoService {

    DeviceBaseInfoVO create(String tenantId, String operator, DeviceBaseInfoCreateReq request);

    DeviceBaseInfoVO update(String tenantId, String operator, DeviceBaseInfoUpdateReq request);

    DeviceBaseInfoVO getById(String tenantId, String factoryId, String id);

    /**
     * 按设备编号查询设备基础信息
     * 
     * @param tenantId 租户ID
     * @param deviceCode 设备编号
     * @return 设备基础信息
     */
    DeviceBaseInfoVO getByDeviceCode(String tenantId, String deviceCode);

    PageResult<DeviceBaseInfoVO> page(String tenantId, String factoryId, DeviceBaseInfoQueryReq request);

    /**
     * 设备列表查询（用于列表页面展示）
     * 包含报警状态等实时信息
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（必填，用于数据隔离）
     * @param request 查询请求
     */
    PageResult<DeviceBaseInfoListVO> list(String tenantId, String factoryId, DeviceBaseInfoQueryReq request);

    boolean delete(String tenantId, String id);
}


