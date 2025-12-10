package com.weili.iot_portal.service.alarm;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.alarm.AlarmItemVO;
import com.weili.iot_portal.domain.alarm.request.AlarmListQueryReq;

/**
 * 报警列表服务
 */
public interface AlarmListService {

    /**
     * 查询报警列表
     * 
     * <p>对应需求：4.3.2 报警列表
     * - 筛选：设备编号、时间范围、是否报警中
     * - 字段：设备编号、设备类型/子类型、报警号、报警内容、开始时间、结束时间、持续时间、是否报警中
     * - 行为：
     *   - "是否报警中"=是：仅显示未结束记录
     *   - 进行中报警：结束时间显示"-"，持续时间实时累加
     * 
     * @param request 查询请求
     * @return 分页结果
     */
    PageResult<AlarmItemVO> getAlarmList(AlarmListQueryReq request);
}

