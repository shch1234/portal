package com.weili.iot_portal.service.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.device.req.DeviceAlarmHistoryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.AlarmHistoryRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceAlarmHistoryRespVO;

/**
 * 设备告警历史业务接口
 */
public interface IDeviceAlarmHistoryBizService {

    /**
     * 分页查询设备告警历史
     *
     * @param queryReqVO 查询请求参数
     * @return 分页结果
     */
    DeviceAlarmHistoryRespVO getDeviceAlarmHistory(DeviceAlarmHistoryQueryReqVO queryReqVO);

    /**
     * 查询报警管理列表
     *
     * @param queryReqVO 查询请求参数
     * @return 分页结果
     */
    PageResult<AlarmHistoryRespVO> queryAlarmManageList(DeviceAlarmHistoryQueryReqVO queryReqVO);
}
