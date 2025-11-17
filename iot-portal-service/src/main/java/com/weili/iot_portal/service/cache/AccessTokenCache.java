package com.weili.iot_portal.service.cache;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.google.common.collect.Lists;
import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.domain.model.AccessTokenModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author luying
 * @className AccessTokenCache
 * @description
 * @date 2025-11-17 11:24
 **/
@Service
@Slf4j
public class AccessTokenCache {

    @Resource
    private RedisClient redisClient;

    public AccessTokenModel get(String accessToken) {
        String redisKey = formatKey(accessToken);
        return JsonUtils.parseObject(redisClient.get(redisKey), AccessTokenModel.class);
    }

    public void set(AccessTokenModel accessTokenDO) {
        String redisKey = formatKey(accessTokenDO.getAccessToken());
        long time = LocalDateTimeUtil.between(LocalDateTime.now(), accessTokenDO.getExpiresTime(), ChronoUnit.SECONDS);
        if (time > 0) {
            redisClient.set(redisKey, JsonUtils.toJsonString(accessTokenDO), time, TimeUnit.SECONDS);
        }
    }

    public void delete(String accessToken) {
        String redisKey = formatKey(accessToken);
        redisClient.delete(redisKey);
    }

    public void deleteList(List<String> accessTokens) {
        List<String> redisKeys = Optional.ofNullable(accessTokens).orElse(Lists.newArrayList())
                .stream().map(AccessTokenCache::formatKey).collect(Collectors.toList());
        redisClient.delete(redisKeys);
    }

    private static String formatKey(String accessToken) {
        return String.format(RedisConstant.OAUTH2_ACCESS_TOKEN, accessToken);
    }
}
