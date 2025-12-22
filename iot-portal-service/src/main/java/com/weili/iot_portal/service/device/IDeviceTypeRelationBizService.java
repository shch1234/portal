package com.weili.iot_portal.service.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.domain.device.req.DeviceTypeRelationPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceTypeRelationSaveReqVO;

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
     * 根据设备类型编码获取设备类型
     *
     * @param typeCode 设备类型编码
     * @return 设备类型
     */
    DeviceTypeRelationDO getDeviceTypeRelationByCode(String typeCode);


    /**
     * 根据设备类型父编码获取设备类型
     *
     * @param typeCode 设备类型编码
     * @return 设备类型
     */
    List<DeviceTypeRelationDO> getDeviceTypeRelationByParentCode(String typeCode);


    /**
     * 分页查询设备类型
     *
     * @param pageReqVO 分页查询请求
     * @return 分页结果
     */
    PageResult<DeviceTypeRelationDO> getDeviceTypeRelationPage(DeviceTypeRelationPageReqVO pageReqVO);
}




