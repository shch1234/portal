package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.domain.devicebase.req.DeviceTypeRelationPageReqVO;
import com.weili.iot_portal.domain.devicebase.req.DeviceTypeRelationSaveReqVO;

import java.util.List;

/**
 * 设备类型业务服务接口
 */
public interface IDeviceTypeRelationBizService {

    /**
     * 创建设备类型
     *
     * @param createReqVO 设备类型创建请求
     * @return 设备类型ID
     */
    String createDeviceTypeRelation(DeviceTypeRelationSaveReqVO createReqVO);

    /**
     * 更新设备类型
     *
     * @param updateReqVO 设备类型更新请求
     */
    void updateDeviceTypeRelation(DeviceTypeRelationSaveReqVO updateReqVO);

    /**
     * 删除设备类型
     *
     * @param id 设备类型ID
     */
    void deleteDeviceTypeRelation(String id);

    /**
     * 根据ID获取设备类型
     *
     * @param id 设备类型ID
     * @return 设备类型
     */
    DeviceTypeRelationDO getDeviceTypeRelation(String id);

    /**
     * 根据父级ID获取子类型列表
     *
     * @param parentTypeId 父类型ID
     * @return 子类型列表
     */
    List<DeviceTypeRelationDO> getDeviceTypeRelationByParentId(String parentTypeId);

    /**
     * 分页查询设备类型
     *
     * @param pageReqVO 分页查询请求
     * @return 分页结果
     */
    PageResult<DeviceTypeRelationDO> getDeviceTypeRelationPage(DeviceTypeRelationPageReqVO pageReqVO);
}



