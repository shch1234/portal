package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoListVO;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoUpdateReq;

/**
 * 设备基础信息服务（主数据管理）
 */
public interface DeviceBaseInfoService {

    DeviceBaseInfoVO create(DeviceBaseInfoCreateReq request);

    DeviceBaseInfoVO update(DeviceBaseInfoUpdateReq request);

    DeviceBaseInfoVO getById(String factoryId, String id);

    DeviceBaseInfoVO getByDeviceCode(String deviceCode);

    PageResult<DeviceBaseInfoVO> page(String factoryId, DeviceBaseInfoQueryReq request);

    PageResult<DeviceBaseInfoListVO> list(String factoryId, DeviceBaseInfoQueryReq request);

    boolean delete(String id);
}

