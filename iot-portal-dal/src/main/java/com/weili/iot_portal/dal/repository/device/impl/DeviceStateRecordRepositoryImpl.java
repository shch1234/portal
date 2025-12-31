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


