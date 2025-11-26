package com.weili.iot_portal.dal.mapper.alarm;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.alarm.AlarmHistoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报警历史 Mapper
 */
@Mapper
public interface AlarmHistoryMapper extends BaseMapper<AlarmHistoryDO> {
}


