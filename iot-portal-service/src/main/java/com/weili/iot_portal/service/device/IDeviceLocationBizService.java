package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;
import com.weili.iot_portal.domain.device.req.DeviceLocationSaveReqVO;

import java.util.List;

/**
 * 设备位置业务服务接口
 */
public interface IDeviceLocationBizService {

    /**
     * 创建设备位置
     *
     * @param createReqVO 设备位置创建请求
     * @return 设备位置ID
     */
    String createDeviceLocation(DeviceLocationSaveReqVO createReqVO);

    /**
     * 更新设备位置
     *
     * @param updateReqVO 设备位置更新请求
     */
    void updateDeviceLocation(DeviceLocationSaveReqVO updateReqVO);

    /**
     * 根据设备ID获取设备位置
     *
     * @param deviceInfoId 设备信息ID
     * @return 设备位置
     */
    DeviceLocationDO getDeviceLocationByDeviceId(String deviceInfoId);

    /**
     * 根据设备ID列表批量获取设备位置
     *
     * @param deviceInfoIds 设备信息ID列表
     * @return 设备位置列表
     */
    List<DeviceLocationDO> getDeviceLocationsByDeviceIds(List<String> deviceInfoIds);
}




