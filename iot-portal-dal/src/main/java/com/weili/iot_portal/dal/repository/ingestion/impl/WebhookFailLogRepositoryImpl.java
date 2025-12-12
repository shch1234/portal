package com.weili.iot_portal.dal.repository.ingestion.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookFailLogDO;
import com.weili.iot_portal.dal.mapper.ingestion.WebhookFailLogMapper;
import com.weili.iot_portal.dal.repository.ingestion.WebhookFailLogRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WebhookFailLogRepositoryImpl implements WebhookFailLogRepository {

    private final WebhookFailLogMapper failLogMapper;

    @Override
    public void insert(WebhookFailLogDO failLog) {
        failLogMapper.insert(failLog);
    }

    @Override
    public List<WebhookFailLogDO> findUnrecovered(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<WebhookFailLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookFailLogDO::getRecovered, false)
                .eq(WebhookFailLogDO::getNeedManual, false)
                .orderByAsc(WebhookFailLogDO::getFailedTime)
                .last("LIMIT " + limit);
        return failLogMapper.selectList(wrapper);
    }

    @Override
    public Optional<WebhookFailLogDO> findById(String id) {
        return Optional.ofNullable(failLogMapper.selectById(id));
    }

    @Override
    public void update(WebhookFailLogDO failLog) {
        failLogMapper.updateById(failLog);
    }

    @Override
    public Optional<WebhookFailLogDO> findLatestByMessageId(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return Optional.empty();
        }
        LambdaQueryWrapper<WebhookFailLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookFailLogDO::getMessageId, messageId)
                .orderByDesc(WebhookFailLogDO::getFailedTime)
                .last("LIMIT 1");
        return Optional.ofNullable(failLogMapper.selectOne(wrapper));
    }
}




