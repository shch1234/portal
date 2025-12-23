package com.weili.iot_portal.service.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceNetworkConfigDO;
import com.weili.iot_portal.domain.device.req.DeviceNetworkConfigPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceNetworkConfigSaveReqVO;

/**
 * 设备网络配置业务服务接口
 */
public interface IDeviceNetworkConfigBizService {

    /**
     * 创建设备网络配置
     *
     * @param createReqVO 设备网络配置创建请求
     * @return 设备网络配置ID
     */
    Long createDeviceNetworkConfig(DeviceNetworkConfigSaveReqVO createReqVO);

    /**
     * 更新设备网络配置
     *
     * @param updateReqVO 设备网络配置更新请求
     */
    void updateDeviceNetworkConfig(DeviceNetworkConfigSaveReqVO updateReqVO);

    /**
     * 删除设备网络配置
     *
     * @param id 设备网络配置ID
     */
    void deleteDeviceNetworkConfig(Long id);

    /**
     * 根据ID获取设备网络配置
     *
     * @param id 设备网络配置ID
     * @return 设备网络配置
     */
    DeviceNetworkConfigDO getDeviceNetworkConfig(Long id);

    /**
     * 根据设备信息ID获取设备网络配置
     *
     * @param deviceInfoId 设备信息ID
     * @return 设备网络配置
     */
    DeviceNetworkConfigDO getDeviceNetworkConfigByDeviceId(Long deviceInfoId);

    /**
     * 分页查询设备网络配置
     *
     * @param pageReqVO 分页查询请求
     * @return 分页结果
     */
    PageResult<DeviceNetworkConfigDO> getDeviceNetworkConfigPage(DeviceNetworkConfigPageReqVO pageReqVO);
}




