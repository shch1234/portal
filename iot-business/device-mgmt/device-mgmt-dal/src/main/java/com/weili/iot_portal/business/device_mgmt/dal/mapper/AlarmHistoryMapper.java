package com.weili.iot_portal.business.device_mgmt.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.AlarmHistoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报警历史 Mapper
 */
@Mapper
public interface AlarmHistoryMapper extends BaseMapper<AlarmHistoryDO> {
}


