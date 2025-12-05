package com.weili.iot_portal.dal.mapper.ingestion;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookFailLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookFailLogMapper extends BaseMapper<WebhookFailLogDO> {
}

