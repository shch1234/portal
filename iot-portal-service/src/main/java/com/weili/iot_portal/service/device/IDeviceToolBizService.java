package com.weili.iot_portal.service.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.device.req.DeviceToolCompensationQueryReqVO;
import com.weili.iot_portal.domain.device.req.DeviceToolRecordQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolCompensationRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolRecordRespVO;


/**
 * 设备刀具补偿业务接口
 */
public interface IDeviceToolBizService {

    /**
     * 获取设备的刀具使用记录列表、当前刀具实时使用记录
     *
     * @param queryReqVO 查询请求参数
     * @return 刀具记录列表
     */
    DeviceToolRecordRespVO getDeviceToolRecords(DeviceToolRecordQueryReqVO queryReqVO);

    /**
     * 获取设备的刀具补偿列表
     *
     * @param queryReqVO 查询请求参数
     * @return 刀具补偿响应VO
     */
    PageResult<DeviceToolCompensationRespVO> getDeviceToolCompensation(DeviceToolCompensationQueryReqVO queryReqVO);
}
