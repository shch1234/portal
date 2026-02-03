package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.mapper.device.DeviceStateRecordMapper;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 设备状态时间线仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceStateRecordRepositoryImpl implements DeviceStateRecordRepository {

    private final DeviceStateRecordMapper deviceStateRecordMapper;

    @Override
    public List<DeviceStateRecordDO> selectByRange(Long deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = baseQuery(deviceId);
        
        if (startTs != null && endTs != null) {
            // 查询与时间范围 [startTs, endTs] 有交集的记录
            // 交集条件：记录的开始时间 < 查询范围结束时间 且 (记录未结束 或 记录结束时间 > 查询范围开始时间)
            // SQL: start_ts < endTs AND (end_ts IS NULL OR end_ts > startTs)
            wrapper.lt(DeviceStateRecordDO::getStartTs, endTs)
                   .and(w -> w.isNull(DeviceStateRecordDO::getEndTs)
                           .or(wr -> wr.gt(DeviceStateRecordDO::getEndTs, startTs)));
        } else if (startTs != null) {
            // 查询开始时间 >= startTs 的所有记录
            wrapper.ge(DeviceStateRecordDO::getStartTs, startTs);
        } else if (endTs != null) {
            // 查询开始时间 < endTs 的所有记录
            wrapper.lt(DeviceStateRecordDO::getStartTs, endTs);
        }
        
        wrapper.orderByAsc(DeviceStateRecordDO::getStartTs);
        return deviceStateRecordMapper.selectList(wrapper);
    }

    @Override
    public List<DeviceStateRecordDO> selectRecent(Long deviceId, Long startTs, int limit) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = baseQuery(deviceId);
        if (startTs != null) {
            wrapper.ge(DeviceStateRecordDO::getStartTs, startTs);
        }
        wrapper.orderByDesc(DeviceStateRecordDO::getStartTs)
                .last("limit " + limit);
        List<DeviceStateRecordDO> records = deviceStateRecordMapper.selectList(wrapper);
        records.sort((o1, o2) -> Long.compare(o1.getStartTs(), o2.getStartTs()));
        return records;
    }

    @Override
    public Optional<DeviceStateRecordDO> findLatestState(Long deviceId) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = baseQuery(deviceId);

        // 优先查询进行中的状态（end_ts IS NULL）
        wrapper.isNull(DeviceStateRecordDO::getEndTs)
                .orderByDesc(DeviceStateRecordDO::getStartTs)
                .last("limit 1");

        DeviceStateRecordDO record = deviceStateRecordMapper.selectOne(wrapper);
        if (record != null) {
            return Optional.of(record);
        }

        // 如果没有进行中的状态，查询最近结束的状态
        wrapper.isNotNull(DeviceStateRecordDO::getEndTs)
                .orderByDesc(DeviceStateRecordDO::getEndTs)
                .orderByDesc(DeviceStateRecordDO::getStartTs)
                .last("limit 1");

        record = deviceStateRecordMapper.selectOne(wrapper);
        return Optional.ofNullable(record);
    }

    @Override
    public void insert(DeviceStateRecordDO record) {
        deviceStateRecordMapper.insert(record);
    }

    @Override
    public void insertBatch(List<DeviceStateRecordDO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        
        // 重要：确保记录按 startTs 排序（防御性编程）
        // 虽然 createStateRecords 返回的记录通常已按时间顺序排列，
        // 但为了确保数据一致性，在批量插入前进行排序
        // 这对于跨班次拆分的记录尤其重要，因为时间顺序直接影响业务逻辑
        records.sort((r1, r2) -> {
            Long startTs1 = r1.getStartTs();
            Long startTs2 = r2.getStartTs();
            if (startTs1 == null && startTs2 == null) {
                return 0;
            }
            if (startTs1 == null) {
                return 1; // null 排在后面
            }
            if (startTs2 == null) {
                return -1; // null 排在后面
            }
            return Long.compare(startTs1, startTs2);
        });
        
        // 使用循环插入（保证顺序）
        // 注意：虽然循环插入性能略低于真正的批量插入，但可以保证：
        // 1. 插入顺序与列表顺序一致
        // 2. 每条记录插入后立即获得ID（如果使用自增ID）
        // 3. 如果某条记录插入失败，可以立即知道是哪条记录
        // 
        // 如果将来要使用真正的批量插入（如 MyBatis-Plus 的批量执行器），
        // 需要确保数据库和ORM框架保证插入顺序，或者使用事务+排序来保证顺序
        for (DeviceStateRecordDO record : records) {
            deviceStateRecordMapper.insert(record);
        }
    }

    @Override
    public void update(DeviceStateRecordDO record) {
        deviceStateRecordMapper.updateById(record);
    }

    @Override
    public void deleteById(Long id) {
        deviceStateRecordMapper.deleteById(id);
    }

    @Override
    public List<DeviceStateRecordDO> selectByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = baseQuery(deviceId);
        
        // 使用班次维度查询，可以利用 (device_info_id, shift_date, shift_code) 索引
        if (shiftDate != null) {
            wrapper.eq(DeviceStateRecordDO::getShiftDate, shiftDate);
        }
        if (shiftCode != null) {
            wrapper.eq(DeviceStateRecordDO::getShiftCode, shiftCode);
        }
        
        wrapper.orderByAsc(DeviceStateRecordDO::getStartTs);
        return deviceStateRecordMapper.selectList(wrapper);
    }

    @Override
    public List<DeviceStateRecordRepository.DeviceShiftKey> findDistinctDeviceShifts(LocalDate startDate, LocalDate endDate) {
        // 查询指定日期范围内有状态记录的所有设备+班次组合
        // 使用 DISTINCT 去重，只返回唯一的 (device_info_id, org_factory_id, shift_date, shift_code) 组合
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(
                DeviceStateRecordDO::getDeviceInfoId,
                DeviceStateRecordDO::getOrgFactoryId,
                DeviceStateRecordDO::getShiftDate,
                DeviceStateRecordDO::getShiftCode
        );
        
        if (startDate != null) {
            wrapper.ge(DeviceStateRecordDO::getShiftDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(DeviceStateRecordDO::getShiftDate, endDate);
        }
        
        // 过滤掉 shift_date 或 shift_code 为 null 的记录（这些记录可能来自历史数据）
        wrapper.isNotNull(DeviceStateRecordDO::getShiftDate)
                .isNotNull(DeviceStateRecordDO::getShiftCode)
                .isNotNull(DeviceStateRecordDO::getDeviceInfoId);
        
        // 使用 DISTINCT 去重
        wrapper.last("GROUP BY device_info_id, org_factory_id, shift_date, shift_code");
        
        List<DeviceStateRecordDO> records = deviceStateRecordMapper.selectList(wrapper);
        
        // 转换为 DeviceShiftKey 列表
        return records.stream()
                .map(r -> new DeviceStateRecordRepository.DeviceShiftKey(
                        r.getDeviceInfoId(),
                        r.getOrgFactoryId(),
                        r.getShiftDate(),
                        r.getShiftCode()
                ))
                .distinct()
                .toList();
    }

    @Override
    public List<DeviceStateRecordDO> selectByShiftDateRange(Long deviceId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(DeviceStateRecordDO::getDeviceInfoId, deviceId);
        }
        if (startDate != null) {
            wrapper.ge(DeviceStateRecordDO::getShiftDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(DeviceStateRecordDO::getShiftDate, endDate);
        }
        // 重要：甘特图需要按实际时间顺序排序，确保状态段按时间先后正确显示
        wrapper.orderByAsc(DeviceStateRecordDO::getStartTs);
        return deviceStateRecordMapper.selectList(wrapper);
    }

    @Override
    public List<DeviceStateRecordDO> findAllOngoing(Long startTsAfter, Integer limit) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = new LambdaQueryWrapper<>();
        
        // 只查询进行中的记录（end_ts IS NULL）
        wrapper.isNull(DeviceStateRecordDO::getEndTs);
        
        // 性能优化：只查询最近的数据（避免扫描过旧的数据）
        // 通常只需要检查最近24-48小时内的记录，因为超过这个时间应该已经被处理或过期
        if (startTsAfter != null) {
            wrapper.ge(DeviceStateRecordDO::getStartTs, startTsAfter);
        }
        
        // 按开始时间升序排列，优先处理较早的记录
        wrapper.orderByAsc(DeviceStateRecordDO::getStartTs);
        
        // 限制返回数量，用于分批处理
        if (limit != null && limit > 0) {
            wrapper.last("limit " + limit);
        }
        
        return deviceStateRecordMapper.selectList(wrapper);
    }

    /**
     * 构建基础查询条件（对应 device_state_record 表的字段）
     */
    private LambdaQueryWrapper<DeviceStateRecordDO> baseQuery(Long deviceId) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(DeviceStateRecordDO::getDeviceInfoId, deviceId);
        }
        return wrapper;
    }
}


