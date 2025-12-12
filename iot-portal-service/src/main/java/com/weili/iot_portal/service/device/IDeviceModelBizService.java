package com.weili.iot_portal.service.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceModelDO;
import com.weili.iot_portal.domain.device.req.DeviceModelPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceModelSaveReqVO;

import java.util.List;

/**
 * 设备型号业务服务接口
 */
public interface IDeviceModelBizService {

    /**
     * 创建设备型号
     *
     * @param createReqVO 设备型号创建请求
     * @return 设备型号ID
     */
    String createDeviceModel(DeviceModelSaveReqVO createReqVO);

    /**
     * 更新设备型号
     *
     * @param updateReqVO 设备型号更新请求
     */
    void updateDeviceModel(DeviceModelSaveReqVO updateReqVO);

    /**
     * 删除设备型号
     *
     * @param id 设备型号ID
     */
    void deleteDeviceModel(String id);

    /**
     * 根据ID获取设备型号
     *
     * @param id 设备型号ID
     * @return 设备型号
     */
    DeviceModelDO getDeviceModel(String id);

    /**
     * 分页查询设备型号
     *
     * @param pageReqVO 分页查询请求
     * @return 分页结果
     */
    PageResult<DeviceModelDO> getDeviceModelPage(DeviceModelPageReqVO pageReqVO);

    /**
     * 获取所有设备型号列表
     *
     * @return 设备型号列表
     */
    List<DeviceModelDO> getDeviceModelList();
}




