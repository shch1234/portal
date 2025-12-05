package com.weili.iot_portal.dal.mapper.ingestion;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WebhookInboxMapper extends BaseMapper<WebhookInboxDO> {

    @Select("select * from webhook_inbox where message_id = #{messageId} limit 1")
    WebhookInboxDO findByMessageId(@Param("messageId") String messageId);
}

