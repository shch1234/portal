package com.weili.iot_portal.dal.repository.ingestion.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.mapper.ingestion.WebhookInboxMapper;
import com.weili.iot_portal.dal.repository.ingestion.WebhookInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WebhookInboxRepositoryImpl implements WebhookInboxRepository {

    private final WebhookInboxMapper inboxMapper;

    @Override
    public void insert(WebhookInboxDO inbox) {
        inboxMapper.insert(inbox);
    }

    @Override
    public List<WebhookInboxDO> fetchDue(int limit, LocalDateTime now) {
        return inboxMapper.selectList(
                new LambdaQueryWrapper<WebhookInboxDO>()
                        .in(WebhookInboxDO::getStatus, "PENDING", "FAILED")
                        .and(w -> w.isNull(WebhookInboxDO::getNextRetryTime)
                                .or()
                                .le(WebhookInboxDO::getNextRetryTime, now))
                        .orderByAsc(WebhookInboxDO::getReceivedTime)
                        .last("limit " + limit)
        );
    }

    @Override
    public void update(WebhookInboxDO inbox) {
        inboxMapper.updateById(inbox);
    }
}


