package com.weili.iot_portal.service.ingestion.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.dal.cache.CachedDeviceInfo;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 设备匹配服务：基于 device_code 查询 device_base_info
 * <p>
 * 职责：
 * 1. 根据 deviceCode 匹配设备（带缓存优化）
 * 2. 同步 TB 设备ID（如果需要）
 * </p>
 * <p>
 * 性能优化：
 * - 使用Redis缓存设备信息，减少数据库查询
 * - 缓存TTL：1小时（设备信息变化不频繁）
 * - 设备ID更新时自动清除缓存
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMatchingService {

    /**
     * 未匹配设备 WARN 日志的最小输出间隔（毫秒）
     * <p>
     * 行业通用做法：
     * - 首次发现问题时输出 WARN
     * - 后续在一个时间窗口内（例如 60 秒）只输出一次 WARN
     * - 其余请求可以降级为 DEBUG 或不输出，避免刷屏
     * </p>
     */
    private static final long UNMATCHED_WARN_INTERVAL_MILLIS = Duration.ofSeconds(60).toMillis();

    /**
     * TB设备ID同步锁超时时间（秒）
     * 防止并发更新同一设备的tb_device_id
     */
    private static final long SYNC_TB_DEVICE_ID_LOCK_TIMEOUT_SECONDS = 3L;

    /**
     * TB设备ID频繁切换检测窗口（毫秒）
     * 如果设备在短时间内多次切换tb_device_id，记录警告而不是更新
     */
    private static final long TB_DEVICE_ID_SWITCH_DETECTION_WINDOW_MILLIS = Duration.ofMinutes(5).toMillis();

    private final DeviceInfoRepository deviceInfoRepository;
    private final RedisClient redisClient;

    /**
     * 本地缓存（Caffeine）：二级缓存，减少Redis查询
     * Key: deviceCode
     * Value: DeviceInfoDO
     * 
     * 优势：
     * - 零延迟访问（内存访问）
     * - 减少Redis网络开销
     * - 提高缓存命中率
     */
    private Cache<String, DeviceInfoDO> localCache;

    /**
     * 记录每个未匹配设备上次输出 WARN 日志的时间戳（毫秒）
     * key: deviceCode
     * value: lastWarnTimeMillis
     */
    private final ConcurrentMap<String, Long> unmatchedDeviceLastWarnTime = new ConcurrentHashMap<>();

    /**
     * 记录每个设备最近一次tb_device_id更新的信息
     * key: deviceCode
     * value: TbDeviceIdUpdateInfo (包含更新时间和更新后的tb_device_id)
     */
    private final ConcurrentMap<String, TbDeviceIdUpdateInfo> tbDeviceIdUpdateHistory = new ConcurrentHashMap<>();

    /**
     * Redis缓存TTL（秒）
     * 默认值：1小时（3600秒）
     */
    @Value("${device.matching.redis-cache-ttl-seconds:3600}")
    private long redisCacheTtlSeconds;

    /**
     * 本地缓存最大大小
     * 默认值：10000（支持10000个设备的本地缓存）
     */
    @Value("${device.matching.local-cache-max-size:10000}")
    private int localCacheMaxSize;

    /**
     * 本地缓存TTL（分钟）
     * 默认值：10分钟（比Redis缓存短，作为热点数据缓存）
     */
    @Value("${device.matching.local-cache-ttl-minutes:10}")
    private int localCacheTtlMinutes;

    /**
     * 是否启用缓存预热
     * 默认值：false（预热会增加启动时间，可根据需要开启）
     */
    @Value("${device.matching.cache-warmup-enabled:false}")
    private boolean cacheWarmupEnabled;

    /**
     * 缓存预热设备数量
     * 默认值：100（预热前100个活跃设备）
     */
    @Value("${device.matching.cache-warmup-size:100}")
    private int cacheWarmupSize;

    /**
     * 初始化本地缓存
     */
    @PostConstruct
    public void initLocalCache() {
        localCache = Caffeine.newBuilder()
                .maximumSize(localCacheMaxSize)
                .expireAfterWrite(localCacheTtlMinutes, TimeUnit.MINUTES)
                .recordStats() // 启用统计，便于监控缓存命中率
                .build();
        log.info("[DeviceMatching] 本地缓存初始化完成: maxSize={}, ttl={}分钟, redisCacheTtl={}秒", 
                localCacheMaxSize, localCacheTtlMinutes, redisCacheTtlSeconds);
        
        // 启动缓存统计定时任务（每5分钟输出一次）
        startCacheStatsTask();
        
        // 缓存预热（异步执行，不阻塞启动）
        if (cacheWarmupEnabled) {
            warmupCacheAsync();
        }
    }

    /**
     * 异步缓存预热
     * 预加载热点设备到本地缓存，提高缓存命中率
     */
    private void warmupCacheAsync() {
        java.util.concurrent.ExecutorService executor = 
                java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "device-matching-cache-warmup");
                    t.setDaemon(true);
                    return t;
                });
        
        executor.submit(() -> {
            try {
                log.info("[DeviceMatching] 开始缓存预热: warmupSize={}", cacheWarmupSize);
                long startTime = System.currentTimeMillis();
                
                // 查询活跃且监控中的设备（优先预热热点设备）
                // 注意：这里只预热前N个设备，避免启动时间过长
                List<DeviceInfoDO> activeDevices = deviceInfoRepository.findAllActive();
                int warmedCount = 0;
                
                for (DeviceInfoDO device : activeDevices) {
                    if (warmedCount >= cacheWarmupSize) {
                        break;
                    }
                    
                    // 只预热活跃且监控中的设备（这些设备更可能被访问）
                    if (Boolean.TRUE.equals(device.getIsMonitored()) 
                            && "ACTIVE".equals(device.getDeviceStatus())
                            && !Boolean.TRUE.equals(device.getDeleted())) {
                        String deviceCode = device.getDeviceCode();
                        if (StringUtils.isNotBlank(deviceCode)) {
                            // 写入本地缓存
                            localCache.put(deviceCode, device);
                            // 写入Redis缓存
                            cacheDeviceInfo(deviceCode, device);
                            warmedCount++;
                        }
                    }
                }
                
                long cost = System.currentTimeMillis() - startTime;
                log.info("[DeviceMatching] 缓存预热完成: 预热设备数={}, 总设备数={}, 耗时={}ms", 
                        warmedCount, activeDevices.size(), cost);
            } catch (Exception e) {
                log.warn("[DeviceMatching] 缓存预热失败", e);
            }
        });
    }

    /**
     * 启动缓存统计定时任务
     * 定期输出缓存命中率等统计信息，便于监控和优化
     */
    private void startCacheStatsTask() {
        java.util.concurrent.ScheduledExecutorService scheduler = 
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "device-matching-cache-stats");
                    t.setDaemon(true);
                    return t;
                });
        
        // 每5分钟输出一次缓存统计
        scheduler.scheduleWithFixedDelay(this::logCacheStats, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 输出缓存统计信息
     */
    private void logCacheStats() {
        if (localCache == null) {
            return;
        }
        
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = localCache.stats();
        long hitCount = stats.hitCount();
        long missCount = stats.missCount();
        long requestCount = hitCount + missCount;
        double hitRate = requestCount > 0 ? (double) hitCount / requestCount * 100 : 0.0;
        long evictionCount = stats.evictionCount();
        long size = localCache.estimatedSize();
        
        log.info("[DeviceMatching] 缓存统计: 命中率={:.2f}% (命中={}, 未命中={}, 总请求={}), " +
                "缓存大小={}, 淘汰次数={}, 最大容量={}",
                hitRate, hitCount, missCount, requestCount, size, evictionCount, localCacheMaxSize);
    }

    /**
     * 匹配设备（带二级缓存）
     * <p>
     * 优化后的缓存策略（三级缓存）：
     * 1. 先查本地缓存（Caffeine）- 最快，零延迟
     * 2. 本地缓存未命中，查Redis缓存 - 分布式共享
     * 3. Redis缓存未命中，查数据库 - 最慢，需要优化
     * 4. 查询结果写入本地缓存和Redis缓存
     * </p>
     * <p>
     * 性能提升：
     * - 本地缓存命中：0ms（内存访问）
     * - Redis缓存命中：1-2ms（网络延迟）
     * - 数据库查询：10-50ms（取决于数据库负载）
     * </p>
     *
     * @param deviceCode 设备编号
     * @return 设备信息，如果未匹配则返回空
     */
    public Optional<DeviceInfoDO> match(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            log.warn("[DeviceMatching] device_code 为空，跳过匹配: deviceCode={}", deviceCode);
            return Optional.empty();
        }

        // 1. 先查本地缓存（最快）
        DeviceInfoDO localCached = localCache.getIfPresent(deviceCode);
        if (localCached != null) {
            if (log.isDebugEnabled()) {
                log.debug("[DeviceMatching] 本地缓存命中: deviceCode={}, deviceId={}", 
                        deviceCode, localCached.getId());
            }
            return Optional.of(localCached);
        }

        // 2. 本地缓存未命中，查Redis缓存
        Optional<DeviceInfoDO> redisCached = getFromRedisCache(deviceCode);
        if (redisCached.isPresent()) {
            DeviceInfoDO device = redisCached.get();
            // 写入本地缓存，下次直接命中
            localCache.put(deviceCode, device);
            return redisCached;
        }

        // 3. Redis缓存未命中，查数据库
        Optional<DeviceInfoDO> deviceOpt = queryFromDatabase(deviceCode);
        if (deviceOpt.isEmpty()) {
            return Optional.empty();
        }

        // 4. 写入本地缓存和Redis缓存
        DeviceInfoDO device = deviceOpt.get();
        localCache.put(deviceCode, device);
        cacheDeviceInfo(deviceCode, device);

        return Optional.of(device);
    }

    /**
     * 从Redis缓存获取设备信息
     *
     * @param deviceCode 设备编号
     * @return 设备信息，如果缓存未命中或无效则返回空
     */
    private Optional<DeviceInfoDO> getFromRedisCache(String deviceCode) {
        String cacheKey = buildCacheKey(deviceCode);
        String cached;
        try {
            cached = redisClient.get(cacheKey);
        } catch (org.springframework.dao.QueryTimeoutException | 
                 org.springframework.data.redis.RedisConnectionFailureException e) {
            // Redis 超时或连接失败：降级到数据库查询，不阻塞业务
            log.warn("[DeviceMatching] Redis操作超时或连接失败，降级到数据库查询: deviceCode={}, error={}", 
                    deviceCode, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            // 其他异常：记录日志并降级
            log.warn("[DeviceMatching] Redis操作异常，降级到数据库查询: deviceCode={}, error={}", 
                    deviceCode, e.getMessage());
            return Optional.empty();
        }
        
        if (StringUtils.isBlank(cached)) {
            return Optional.empty();
        }

        try {
            CachedDeviceInfo cachedInfo = JsonUtils.parseObject(cached, CachedDeviceInfo.class);
            if (cachedInfo == null) {
                log.warn("[DeviceMatching] 缓存数据为null: deviceCode={}", deviceCode);
                evictCacheUnchecked(deviceCode);
                return Optional.empty();
            }

            if (!cachedInfo.isValid()) {
                log.warn("[DeviceMatching] 缓存数据无效: deviceCode={}, cachedInfo={}", deviceCode, cached);
                evictCacheUnchecked(deviceCode);
                return Optional.empty();
            }

            DeviceInfoDO device = cachedInfo.toDeviceInfoDO();
            if (log.isDebugEnabled()) {
                log.debug("[DeviceMatching] 缓存命中: deviceCode={}, deviceId={}", deviceCode, device.getId());
            }
            return Optional.of(device);

        } catch (Exception e) {
            log.warn("[DeviceMatching] 解析缓存数据失败: deviceCode={}, cachedLength={}, error={}",
                    deviceCode, cached.length(), e.getMessage(), e);
            evictCacheUnchecked(deviceCode);
            return Optional.empty();
        }
    }

    /**
     * 从数据库查询设备信息
     * <p>
     * 优化：减少不必要的数据库查询
     * - 优先使用 findActiveMonitoredByDeviceCode（单次查询，带条件）
     * - 仅在需要详细日志时（且满足节流条件）才查询详细信息
     * </p>
     *
     * @param deviceCode 设备编号
     * @return 设备信息，如果未匹配则返回空
     */
    private Optional<DeviceInfoDO> queryFromDatabase(String deviceCode) {
        DeviceInfoDO device = deviceInfoRepository.findActiveMonitoredByDeviceCode(deviceCode).orElse(null);
        if (device == null) {
            // 优化：仅在需要输出WARN日志时才查询详细信息，减少数据库查询
            // 使用原子操作确保同一设备在同一时间窗口内只输出一次日志（避免并发竞态条件）
            long now = System.currentTimeMillis();
            
            // 使用 compute 原子性地检查和更新时间戳
            // 返回值是更新前的旧值（如果首次记录则为null）
            Long previousTime = unmatchedDeviceLastWarnTime.compute(deviceCode, (key, oldValue) -> {
                // 如果首次记录或时间间隔已过，更新为当前时间
                if (oldValue == null || (now - oldValue) >= UNMATCHED_WARN_INTERVAL_MILLIS) {
                    return now; // 更新为当前时间
                }
                // 时间间隔未过，保持原值
                return oldValue;
            });
            
            // 判断是否需要输出日志：
            // 1. 如果 previousTime == null，说明这是首次记录，当前线程成功更新了，应该输出
            // 2. 如果 previousTime != null 且 (now - previousTime) >= UNMATCHED_WARN_INTERVAL_MILLIS，
            //    说明时间间隔已过，当前线程成功更新了时间戳，应该输出
            // 3. 如果 previousTime != null 且 (now - previousTime) < UNMATCHED_WARN_INTERVAL_MILLIS，
            //    说明时间间隔未过，compute 返回了旧值，不应该输出
            // 
            // 注意：即使多个线程几乎同时调用 compute，也只有一个线程能成功将时间戳从 oldValue 更新为 now
            // 因为 compute 是原子操作，多个线程会串行执行，后面的线程会看到前面线程已经更新的值
            boolean shouldWarn = previousTime == null 
                    || (now - previousTime) >= UNMATCHED_WARN_INTERVAL_MILLIS;
            
            if (shouldWarn) {
                // 仅在需要输出WARN日志时查询详细信息
                Optional<DeviceInfoDO> rawDevice = deviceInfoRepository.findByDeviceCode(deviceCode);
                if (rawDevice.isPresent()) {
                    DeviceInfoDO raw = rawDevice.get();
                    log.warn("[DeviceMatching] 设备未匹配: deviceCode={}, id={}, deleted={}, deviceStatus={}, isMonitored={}, tbDeviceId={}",
                            deviceCode, raw.getId(), raw.getDeleted(), raw.getDeviceStatus(), raw.getIsMonitored(), raw.getTbDeviceId());

                    // 分析未匹配原因（仅在 WARN 日志输出时一起打印）
                    StringBuilder reasons = new StringBuilder();
                    if (Boolean.TRUE.equals(raw.getDeleted())) {
                        reasons.append("设备已删除(deleted=true); ");
                    }
                    if (!"ACTIVE".equals(raw.getDeviceStatus())) {
                        reasons.append("设备状态不是ACTIVE(deviceStatus=").append(raw.getDeviceStatus()).append("); ");
                    }
                    if (reasons.length() > 0) {
                        log.warn("[DeviceMatching] 设备未匹配原因: deviceCode={}, 原因={}", deviceCode, reasons.toString());
                    }
                } else {
                    log.warn("[DeviceMatching] 设备未匹配: deviceCode={}, 数据库中不存在该设备", deviceCode);
                }
            } else if (log.isDebugEnabled()) {
                // 在节流窗口内，不查询详细信息，仅输出 DEBUG 级别日志
                log.debug("[DeviceMatching] 设备未匹配(节流中，跳过详细查询): deviceCode={}", deviceCode);
            }
            return Optional.empty();
        }

        log.debug("[DeviceMatching] 设备匹配成功: deviceCode={}, id={}, deleted={}, deviceStatus={}, isMonitored={}",
                deviceCode, device.getId(), device.getDeleted(), device.getDeviceStatus(), device.getIsMonitored());
        return Optional.of(device);
    }

    /**
     * 同步 TB 设备ID（如果需要）
     * <p>
     * 优化：优先使用缓存中的tb_device_id，避免频繁更新
     * 更新规则：
     * 1. 如果 TB 传了 deviceId（tbDeviceId 不为空）
     * 2. 先检查缓存中的tb_device_id是否匹配，如果匹配则直接返回（不更新）
     * 3. 如果缓存中不匹配，再检查数据库中的tb_device_id是否匹配
     * 4. 如果数据库中的tb_device_id为空或不匹配，则更新数据库和缓存
     * 5. 否则跳过
     * </p>
     *
     * @param device     设备信息（可能来自缓存）
     * @param tbDeviceId TB传过来的设备ID
     */
    public void syncTbDeviceIdIfNeeded(DeviceInfoDO device, String tbDeviceId) {
        if (StringUtils.isBlank(tbDeviceId)) {
            return;
        }

        String deviceCode = device.getDeviceCode();
        
        // 优化1：先检查本地缓存和Redis缓存中的tb_device_id是否匹配
        // 如果缓存中的tb_device_id与请求的tbDeviceId匹配，直接返回，不更新
        DeviceInfoDO localCached = localCache != null ? localCache.getIfPresent(deviceCode) : null;
        if (localCached != null) {
            String cachedTbDeviceId = localCached.getTbDeviceId();
            if (StringUtils.isNotBlank(cachedTbDeviceId) && tbDeviceId.equals(cachedTbDeviceId)) {
                // 本地缓存中的tb_device_id已匹配，直接返回，不更新
                log.debug("[DeviceMatching] 本地缓存中的tb_device_id已匹配，跳过更新: deviceCode={}, tbDeviceId={}",
                        deviceCode, tbDeviceId);
                return;
            }
        }
        
        // 如果本地缓存未命中，再查Redis缓存
        Optional<DeviceInfoDO> redisCached = getFromRedisCache(deviceCode);
        if (redisCached.isPresent()) {
            String cachedTbDeviceId = redisCached.get().getTbDeviceId();
            if (StringUtils.isNotBlank(cachedTbDeviceId) && tbDeviceId.equals(cachedTbDeviceId)) {
                // Redis缓存中的tb_device_id已匹配，直接返回，不更新
                log.debug("[DeviceMatching] Redis缓存中的tb_device_id已匹配，跳过更新: deviceCode={}, tbDeviceId={}",
                        deviceCode, tbDeviceId);
                return;
            }
        }

        // 优化2：如果缓存中不匹配，再检查传入的device对象（可能来自缓存）
        // 如果传入的device对象的tb_device_id与请求的tbDeviceId匹配，也直接返回
        if (shouldSyncTbDeviceId(device, tbDeviceId)) {
            // 需要更新：调用updateTbDeviceId进行更新
            updateTbDeviceId(device, tbDeviceId);
        } else {
            // 不需要更新：传入的device对象的tb_device_id已匹配
            log.debug("[DeviceMatching] 设备信息中的tb_device_id已匹配，跳过更新: deviceCode={}, tbDeviceId={}",
                    deviceCode, tbDeviceId);
        }
    }

    /**
     * 判断是否需要同步 TB 设备ID
     *
     * @param device     设备信息
     * @param tbDeviceId TB传过来的设备ID
     * @return true 如果需要同步，false 如果不需要
     */
    private boolean shouldSyncTbDeviceId(DeviceInfoDO device, String tbDeviceId) {
        if (StringUtils.isBlank(tbDeviceId)) {
            return false;
        }
        String currentTbDeviceId = device.getTbDeviceId();
        return StringUtils.isBlank(currentTbDeviceId) || !tbDeviceId.equals(currentTbDeviceId);
    }

    /**
     * 更新 TB 设备ID
     * <p>
     * 优化：添加分布式锁，防止并发更新同一设备的tb_device_id
     * 1. 使用分布式锁确保同一设备只有一个线程能更新
     * 2. 锁内重新查询设备信息，检查是否已被其他线程更新
     * 3. 如果已被更新，跳过本次更新，避免重复操作
     * </p>
     *
     * @param device     设备信息
     * @param tbDeviceId TB传过来的设备ID
     */
    private void updateTbDeviceId(DeviceInfoDO device, String tbDeviceId) {
        String deviceCode = device.getDeviceCode();
        String lockKey = buildSyncTbDeviceIdLockKey(deviceCode);

        // 使用分布式锁，防止并发更新
        try {
            boolean lockAcquired = redisClient.tryLock(lockKey, SYNC_TB_DEVICE_ID_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.debug("[DeviceMatching] 获取同步锁失败，跳过更新: deviceCode={}", deviceCode);
                return;
            }

            try {
                // 重新查询设备信息（防止并发修改）
                Optional<DeviceInfoDO> currentDeviceOpt = deviceInfoRepository.findByDeviceCode(deviceCode);
                if (currentDeviceOpt.isEmpty()) {
                    log.warn("[DeviceMatching] 设备不存在，跳过更新: deviceCode={}", deviceCode);
                    return;
                }

                DeviceInfoDO currentDevice = currentDeviceOpt.get();

                // 再次检查是否需要更新（可能已被其他线程更新）
                if (!shouldSyncTbDeviceId(currentDevice, tbDeviceId)) {
                    log.debug("[DeviceMatching] tb_device_id已同步，跳过更新: deviceCode={}, currentTbDeviceId={}",
                            deviceCode, currentDevice.getTbDeviceId());
                    return;
                }

                String currentTbDeviceId = currentDevice.getTbDeviceId();
                
                // 检测频繁切换：如果设备在短时间内多次切换tb_device_id，记录警告而不是更新
                TbDeviceIdUpdateInfo lastUpdate = tbDeviceIdUpdateHistory.get(deviceCode);
                long now = System.currentTimeMillis();
                if (lastUpdate != null 
                        && (now - lastUpdate.updateTime) < TB_DEVICE_ID_SWITCH_DETECTION_WINDOW_MILLIS
                        && !tbDeviceId.equals(lastUpdate.tbDeviceId)
                        && StringUtils.isNotBlank(currentTbDeviceId)
                        && !tbDeviceId.equals(currentTbDeviceId)) {
                    // 检测到频繁切换：在检测窗口内，且新的tb_device_id和上次更新的不同，且和数据库中的也不同
                    log.warn("[DeviceMatching] 检测到tb_device_id频繁切换，跳过更新以避免数据不一致: " +
                            "deviceCode={}, 数据库tbDeviceId={}, 请求tbDeviceId={}, 上次更新tbDeviceId={}, " +
                            "距离上次更新={}ms",
                            deviceCode, currentTbDeviceId, tbDeviceId, lastUpdate.tbDeviceId,
                            now - lastUpdate.updateTime);
                    return;
                }

                // 正常更新：使用DEBUG级别，减少日志输出
                log.debug("[DeviceMatching] 同步tb_device_id: deviceCode={}, oldTbDeviceId={}, newTbDeviceId={}",
                        deviceCode, currentTbDeviceId, tbDeviceId);

                currentDevice.setTbDeviceId(tbDeviceId);
                // 匹配完成并更新tb_device_id时，同时将is_monitored设置为1（监控中）
                currentDevice.setIsMonitored(true);
                deviceInfoRepository.update(currentDevice);

                // 优化：更新本地缓存和Redis缓存，提高下次连接的缓存命中率
                // 这样可以避免下次连接时缓存未命中，减少数据库查询
                if (localCache != null) {
                    localCache.put(deviceCode, currentDevice);
                }
                cacheDeviceInfo(deviceCode, currentDevice);

                // 记录更新历史
                tbDeviceIdUpdateHistory.put(deviceCode, new TbDeviceIdUpdateInfo(now, tbDeviceId));

                log.debug("[DeviceMatching] tb_device_id同步成功，is_monitored已更新为1，缓存已更新");
            } finally {
                redisClient.releaseLock(lockKey);
            }
        } catch (Exception e) {
            log.error("[DeviceMatching] 同步tb_device_id时发生异常: deviceCode={}, error={}",
                    deviceCode, e.getMessage(), e);
            // 异常时也要尝试释放锁
            try {
                redisClient.releaseLock(lockKey);
            } catch (Exception ex) {
                log.warn("[DeviceMatching] 释放锁失败: deviceCode={}, error={}", deviceCode, ex.getMessage());
            }
        }
    }

    /**
     * 构建TB设备ID同步锁键
     *
     * @param deviceCode 设备编号
     * @return 锁键
     */
    private String buildSyncTbDeviceIdLockKey(String deviceCode) {
        return RedisConstant.LOCK_KEY_PREFIX_SYNC_TB_DEVICE_ID + deviceCode;
    }

    /**
     * 缓存设备信息
     *
     * @param deviceCode 设备编号
     * @param device     设备信息
     */
    /**
     * 缓存设备信息到Redis（本地缓存已在match方法中处理）
     *
     * @param deviceCode 设备编号
     * @param device     设备信息
     */
    private void cacheDeviceInfo(String deviceCode, DeviceInfoDO device) {
        try {
            CachedDeviceInfo cachedInfo = CachedDeviceInfo.fromDeviceInfoDO(device);
            String cacheKey = buildCacheKey(deviceCode);
            redisClient.set(cacheKey, JsonUtils.toJsonString(cachedInfo), redisCacheTtlSeconds, TimeUnit.SECONDS);
            if (log.isDebugEnabled()) {
                log.debug("[DeviceMatching] 设备信息已缓存到Redis: deviceCode={}, deviceId={}, ttl={}秒", 
                        deviceCode, device.getId(), redisCacheTtlSeconds);
            }
        } catch (Exception e) {
            log.warn("[DeviceMatching] 缓存设备信息到Redis失败: deviceCode={}, error={}", deviceCode, e.getMessage());
        }
    }

    /**
     * 清除设备信息缓存（公共方法，供其他服务调用）
     * <p>
     * 当设备信息被更新时，应调用此方法清除缓存，确保下次查询获取最新数据
     * </p>
     *
     * @param deviceCode 设备编号
     */
    public void evictCache(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            return;
        }
        evictCacheUnchecked(deviceCode);
    }

    /**
     * 清除设备信息缓存（内部方法，不检查参数）
     * <p>
     * 用于内部调用，参数已由调用方验证
     * 同时清除本地缓存和Redis缓存
     * </p>
     *
     * @param deviceCode 设备编号（非空）
     */
    private void evictCacheUnchecked(String deviceCode) {
        // 清除本地缓存
        if (localCache != null) {
            localCache.invalidate(deviceCode);
        }
        
        // 清除Redis缓存
        String cacheKey = buildCacheKey(deviceCode);
        try {
            redisClient.delete(cacheKey);
        } catch (Exception e) {
            log.warn("[DeviceMatching] 清除Redis缓存失败: deviceCode={}, error={}", deviceCode, e.getMessage());
        }
        
        if (log.isDebugEnabled()) {
            log.debug("[DeviceMatching] 设备信息缓存已清除（本地+Redis）: deviceCode={}", deviceCode);
        }
    }

    /**
     * 构建缓存键
     *
     * @param deviceCode 设备编号
     * @return 缓存键
     */
    private String buildCacheKey(String deviceCode) {
        return String.format(RedisConstant.DEVICE_INFO_MATCH, deviceCode);
    }

    /**
     * TB设备ID更新信息
     * 用于记录设备最近一次tb_device_id更新的时间和值，用于检测频繁切换
     */
    private record TbDeviceIdUpdateInfo(long updateTime, String tbDeviceId) {
    }

}

