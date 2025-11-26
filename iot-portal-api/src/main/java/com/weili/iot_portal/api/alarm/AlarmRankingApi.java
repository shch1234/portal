package com.weili.iot_portal.api.alarm;

import com.weili.iot_portal.domain.alarm.AlarmRankingItemVO;

import java.util.List;

/**
 * 报警排行查询API
 */
public interface AlarmRankingApi {

    /**
     * 查询正在报警且时长最长的若干条记录
     *
     * @param tenantId  租户ID
     * @param factoryId 工厂ID
     * @param limit     返回数量，默认5
     * @return 报警排行列表
     */
    List<AlarmRankingItemVO> getTopActiveAlarms(String tenantId, String factoryId, Integer limit);
}


