package com.weili.iot_portal.service.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceOrgRelationDO;
import com.weili.iot_portal.domain.device.req.DeviceOrgRelationPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceOrgRelationSaveReqVO;

/**
 * 设备组织单元业务服务接口
 */
public interface IDeviceOrgRelationBizService {

    /**
     * 创建设备组织单元
     *
     * @param createReqVO 设备组织单元创建请求
     * @return 设备组织单元ID
     */
    String createDeviceOrgRelation(DeviceOrgRelationSaveReqVO createReqVO);

    /**
     * 更新设备组织单元
     *
     * @param updateReqVO 设备组织单元更新请求
     */
    void updateDeviceOrgRelation(DeviceOrgRelationSaveReqVO updateReqVO);

    /**
     * 删除设备组织单元
     *
     * @param id 设备组织单元ID
     */
    void deleteDeviceOrgRelation(String id);

    /**
     * 根据ID获取设备组织单元
     *
     * @param id 设备组织单元ID
     * @return 设备组织单元
     */
    DeviceOrgRelationDO getDeviceOrgRelation(String id);

    /**
     * 分页查询设备组织单元
     *
     * @param pageReqVO 分页查询请求
     * @return 分页结果
     */
    PageResult<DeviceOrgRelationDO> getDeviceOrgRelationPage(DeviceOrgRelationPageReqVO pageReqVO);
}




