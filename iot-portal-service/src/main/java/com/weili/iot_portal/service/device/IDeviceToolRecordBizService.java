package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.device.req.DeviceToolRecordQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolRecordRespVO;

import java.util.List;

/**
 * 设备刀具记录业务接口
 */
public interface IDeviceToolRecordBizService {

    /**
     * 获取设备的刀具使用记录列表
     *
     * @param queryReqVO 查询请求参数
     * @return 刀具记录列表
     */
    List<DeviceToolRecordRespVO> getDeviceToolRecords(DeviceToolRecordQueryReqVO queryReqVO);
}
