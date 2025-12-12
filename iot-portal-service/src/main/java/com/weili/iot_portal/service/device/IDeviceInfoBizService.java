package com.weili.iot_portal.service.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.device.req.DeviceInfoBasePageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceInfoSaveReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceInfoOptionsRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceInfoRespVO;

import java.util.List;

/**
 * 设备信息业务服务接口
 */
public interface IDeviceInfoBizService {

    /**
     * 创建设备信息
     *
     * @param createReqVO 设备信息创建请求
     * @return 设备信息ID
     */
    String createDeviceInfo(DeviceInfoSaveReqVO createReqVO);

    /**
     * 更新设备信息
     *
     * @param updateReqVO 设备信息更新请求
     */
    void updateDeviceInfo(DeviceInfoSaveReqVO updateReqVO);

    /**
     * 删除设备信息
     *
     * @param id 设备信息ID
     */
    void deleteDeviceInfo(String id);

    /**
     * 根据ID获取设备信息
     *
     * @param id 设备信息ID
     * @return 设备信息
     */
    DeviceInfoDO getDeviceInfo(String id);

    /**
     * 根据ID获取设备信息（包含位置和网络配置）
     *
     * @param id 设备信息ID
     * @return 设备信息响应VO
     */
    DeviceInfoRespVO getDeviceInfoWithDetails(String id);

    /**
     * 根据设备编号获取设备信息
     *
     * @param deviceCode 设备编号
     * @return 设备信息
     */
    DeviceInfoDO getDeviceInfoByCode(String deviceCode);

    /**
     * 分页查询设备信息
     *
     * @param pageReqVO 分页查询请求
     * @return 分页结果
     */
    PageResult<DeviceInfoDO> getDeviceInfoPage(DeviceInfoBasePageReqVO pageReqVO);

    /**
     * 获取设备信息选项数据（用于新增/编辑页面）
     * 包含设备类型、组织关系、设备型号等下拉选项
     *
     * @return 设备信息选项数据
     */
    DeviceInfoOptionsRespVO getDeviceInfoOptions();
}

