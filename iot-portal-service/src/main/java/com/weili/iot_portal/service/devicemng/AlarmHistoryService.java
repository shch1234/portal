package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.AlarmHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.AlarmHistoryQueryReq;

/**
 * 报警历史服务
 */
public interface AlarmHistoryService {

    AlarmHistoryVO getCurrentAlarms(String tenantId, String factoryId, String deviceId);

    AlarmHistoryVO getAlarmHistory(String tenantId, String factoryId, AlarmHistoryQueryReq request);

    /**
     * 检查设备是否有进行中的报警
     * 
     * <p><b>工厂隔离：</b>会验证设备是否属于指定的工厂。
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceId 设备ID
     * @return 是否有进行中的报警
     */
    boolean hasActiveAlarm(String tenantId, String factoryId, String deviceId);
}


