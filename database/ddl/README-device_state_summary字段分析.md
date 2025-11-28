# device_state_summary 表字段分析

## 一、当前表结构

### 1.1 字段清单

| 字段名 | 数据类型 | 说明 | 类型 |
|--------|---------|------|------|
| `state_statistics` | JSON | 状态统计详情（JSON）：每个状态的时长、占比、片段数等 | JSON字段 |
| `working_duration_s` | INT | 加工中时长（秒，冗余字段） | 冗余字段 |
| `standby_duration_s` | INT | 待机时长（秒，冗余字段） | 冗余字段 |
| `fault_duration_s` | INT | 故障时长（秒，冗余字段） | 冗余字段 |
| `shutdown_duration_s` | INT | 关机时长（秒，冗余字段） | 冗余字段 |
| `working_ratio` | DECIMAL(5,4) | 加工中占比（冗余字段） | 冗余字段 |
| `standby_ratio` | DECIMAL(5,4) | 待机占比（冗余字段） | 冗余字段 |
| `fault_ratio` | DECIMAL(5,4) | 故障占比（冗余字段） | 冗余字段 |
| `shutdown_ratio` | DECIMAL(5,4) | 关机占比（冗余字段） | 冗余字段 |

## 二、字段对比分析

### 2.1 state_statistics JSON 字段可能包含的内容

**假设的 JSON 结构**：
```json
{
  "WORKING": {
    "duration_s": 28800,
    "ratio": 0.8,
    "segment_count": 15,
    "avg_segment_duration_s": 1920,
    "max_segment_duration_s": 3600,
    "min_segment_duration_s": 300
  },
  "STANDBY": {
    "duration_s": 3600,
    "ratio": 0.1,
    "segment_count": 8,
    "avg_segment_duration_s": 450,
    "max_segment_duration_s": 900,
    "min_segment_duration_s": 60
  },
  "FAULT": {
    "duration_s": 1800,
    "ratio": 0.05,
    "segment_count": 2,
    "avg_segment_duration_s": 900,
    "max_segment_duration_s": 1200,
    "min_segment_duration_s": 600
  },
  "SHUTDOWN": {
    "duration_s": 1800,
    "ratio": 0.05,
    "segment_count": 1,
    "avg_segment_duration_s": 1800,
    "max_segment_duration_s": 1800,
    "min_segment_duration_s": 1800
  }
}
```

### 2.2 冗余字段包含的内容

**冗余字段**：
- `working_duration_s` = 28800（秒）
- `standby_duration_s` = 3600（秒）
- `fault_duration_s` = 1800（秒）
- `shutdown_duration_s` = 1800（秒）
- `working_ratio` = 0.8
- `standby_ratio` = 0.1
- `fault_ratio` = 0.05
- `shutdown_ratio` = 0.05

### 2.3 对比分析

| 信息类型 | JSON 字段 | 冗余字段 | 说明 |
|---------|----------|---------|------|
| **基本时长** | ✅ 包含 | ✅ 包含 | 冗余字段已覆盖 |
| **基本占比** | ✅ 包含 | ✅ 包含 | 冗余字段已覆盖 |
| **片段数** | ✅ 包含 | ❌ 不包含 | JSON 独有 |
| **平均片段时长** | ✅ 包含 | ❌ 不包含 | JSON 独有 |
| **最大片段时长** | ✅ 包含 | ❌ 不包含 | JSON 独有 |
| **最小片段时长** | ✅ 包含 | ❌ 不包含 | JSON 独有 |
| **扩展状态** | ✅ 支持 | ❌ 不支持 | JSON 更灵活 |
| **查询性能** | ⚠️ 较差 | ✅ 最优 | 冗余字段可索引 |
| **存储空间** | ⚠️ 较大 | ✅ 较小 | 冗余字段更省空间 |

## 三、使用场景分析

### 3.1 场景 A：基本查询（仅需要时长和占比）

**查询需求**：
- 查询设备某个班次的加工时长
- 查询设备某个班次的工作占比
- 按加工时长排序

**使用冗余字段**：
```sql
-- ✅ 性能最优
SELECT device_id, working_duration_s, working_ratio
FROM device_state_summary
WHERE device_id = 'DEVICE-001'
ORDER BY working_duration_s DESC;
```

**使用 JSON 字段**：
```sql
-- ⚠️ 性能较差，需要解析 JSON
SELECT device_id, 
       JSON_EXTRACT(state_statistics, '$.WORKING.duration_s') as working_duration_s,
       JSON_EXTRACT(state_statistics, '$.WORKING.ratio') as working_ratio
FROM device_state_summary
WHERE device_id = 'DEVICE-001'
ORDER BY JSON_EXTRACT(state_statistics, '$.WORKING.duration_s') DESC;
```

**结论**：冗余字段更适合基本查询。

### 3.2 场景 B：详细分析（需要片段数、平均时长等）

**查询需求**：
- 查询设备某个班次的状态片段数
- 查询设备某个班次的平均片段时长
- 分析状态切换频率

**使用 JSON 字段**：
```sql
-- ✅ JSON 字段包含详细信息
SELECT device_id,
       JSON_EXTRACT(state_statistics, '$.WORKING.segment_count') as segment_count,
       JSON_EXTRACT(state_statistics, '$.WORKING.avg_segment_duration_s') as avg_segment_duration_s
FROM device_state_summary
WHERE device_id = 'DEVICE-001';
```

**使用冗余字段**：
```sql
-- ❌ 无法获取片段数等详细信息
-- 需要从 device_state_record 表重新计算
SELECT device_id, COUNT(*) as segment_count, AVG(duration_s) as avg_segment_duration_s
FROM device_state_record
WHERE device_id = 'DEVICE-001' AND state_code = 'WORKING'
GROUP BY device_id;
```

**结论**：JSON 字段包含冗余字段没有的详细信息。

### 3.3 场景 C：扩展状态支持

**需求**：如果未来需要支持更多状态（如 SETUP-设置、MAINTENANCE-维护等）

**使用 JSON 字段**：
```json
{
  "WORKING": {...},
  "STANDBY": {...},
  "FAULT": {...},
  "SHUTDOWN": {...},
  "SETUP": {
    "duration_s": 600,
    "ratio": 0.02,
    "segment_count": 1
  },
  "MAINTENANCE": {
    "duration_s": 300,
    "ratio": 0.01,
    "segment_count": 1
  }
}
```

**使用冗余字段**：
```sql
-- ❌ 需要添加新字段
ALTER TABLE device_state_summary 
ADD COLUMN setup_duration_s INT,
ADD COLUMN setup_ratio DECIMAL(5,4),
ADD COLUMN maintenance_duration_s INT,
ADD COLUMN maintenance_ratio DECIMAL(5,4);
```

**结论**：JSON 字段更灵活，支持动态扩展。

## 四、方案对比

### 4.1 方案 A：保留 state_statistics（推荐 ✅）

**优点**：
- ✅ 包含详细信息（片段数、平均时长等）
- ✅ 支持扩展状态（无需修改表结构）
- ✅ 便于审计和追溯（完整统计信息）
- ✅ 支持复杂分析（片段分析、状态切换频率等）

**缺点**：
- ⚠️ 查询性能较差（需要解析 JSON）
- ⚠️ 存储空间较大
- ⚠️ 无法直接索引

**适用场景**：
- 需要详细分析（片段数、平均时长等）
- 需要支持扩展状态
- 需要完整审计信息

### 4.2 方案 B：删除 state_statistics，仅使用冗余字段

**优点**：
- ✅ 查询性能最优（可索引）
- ✅ 存储空间较小
- ✅ 表结构更简洁

**缺点**：
- ❌ 无法获取片段数等详细信息
- ❌ 无法支持扩展状态（需要修改表结构）
- ❌ 需要从 `device_state_record` 重新计算详细信息

**适用场景**：
- 仅需要基本时长和占比
- 不需要详细分析
- 状态类型固定（不会扩展）

### 4.3 方案 C：保留 state_statistics，但改为可选（推荐 ⭐⭐⭐⭐⭐）

**优点**：
- ✅ 兼顾性能和灵活性
- ✅ 基本查询使用冗余字段（性能好）
- ✅ 详细分析使用 JSON 字段（信息全）
- ✅ 支持扩展状态

**缺点**：
- ⚠️ 需要维护数据一致性（冗余字段和 JSON 字段）

**适用场景**：
- 需要兼顾性能和灵活性
- 基本查询和详细分析都需要

## 五、业务需求分析

### 5.1 典型业务场景

#### 场景 1：设备状态报表（基本需求）

**需求**：显示设备某个班次的工作时长、待机时长、故障时长

**数据来源**：
- ✅ 冗余字段已足够（`working_duration_s`, `standby_duration_s`, `fault_duration_s`）

#### 场景 2：设备状态分析（详细需求）

**需求**：分析设备状态切换频率、平均片段时长、最长片段时长

**数据来源**：
- ✅ JSON 字段包含（`segment_count`, `avg_segment_duration_s`, `max_segment_duration_s`）
- ❌ 冗余字段不包含

#### 场景 3：扩展状态支持（未来需求）

**需求**：支持新状态（如 SETUP-设置、MAINTENANCE-维护）

**数据来源**：
- ✅ JSON 字段支持（无需修改表结构）
- ❌ 冗余字段不支持（需要添加新字段）

### 5.2 数据来源对比

| 信息类型 | 冗余字段 | JSON 字段 | 从 device_state_record 计算 |
|---------|---------|-----------|---------------------------|
| **基本时长** | ✅ 直接查询 | ✅ 解析 JSON | ⚠️ 需要聚合计算 |
| **基本占比** | ✅ 直接查询 | ✅ 解析 JSON | ⚠️ 需要聚合计算 |
| **片段数** | ❌ 无 | ✅ 解析 JSON | ✅ 需要 COUNT 计算 |
| **平均片段时长** | ❌ 无 | ✅ 解析 JSON | ✅ 需要 AVG 计算 |
| **最大/最小片段时长** | ❌ 无 | ✅ 解析 JSON | ✅ 需要 MAX/MIN 计算 |

## 六、性能对比

### 6.1 查询性能

| 查询类型 | 冗余字段 | JSON 字段 | 从 device_state_record 计算 |
|---------|---------|-----------|---------------------------|
| **基本查询** | ✅ 最优（1ms） | ⚠️ 较差（10ms） | ⚠️ 较差（50ms） |
| **详细分析** | ❌ 不支持 | ✅ 较好（10ms） | ⚠️ 较差（100ms） |
| **扩展状态** | ❌ 不支持 | ✅ 支持 | ✅ 支持（但性能差） |

### 6.2 存储空间

| 方案 | 存储空间 | 说明 |
|------|---------|------|
| **仅冗余字段** | 100% | 基准 |
| **仅 JSON 字段** | 120% | JSON 包含键名和结构 |
| **冗余字段 + JSON** | 150% | 数据冗余，但兼顾性能和灵活性 |

## 七、最终建议

### 7.1 推荐方案：保留 state_statistics（方案 A 或 C）

**理由**：
1. ✅ **详细信息**：JSON 字段包含冗余字段没有的详细信息（片段数、平均时长等）
2. ✅ **扩展性**：支持未来扩展新状态，无需修改表结构
3. ✅ **审计追溯**：完整的统计信息便于审计和追溯
4. ✅ **性能权衡**：基本查询使用冗余字段，详细分析使用 JSON 字段

### 7.2 如果删除 state_statistics

**影响**：
- ❌ 无法获取片段数等详细信息
- ❌ 无法支持扩展状态（需要修改表结构）
- ❌ 需要从 `device_state_record` 重新计算（性能差）

**适用条件**：
- ✅ 仅需要基本时长和占比
- ✅ 不需要详细分析
- ✅ 状态类型固定（不会扩展）

### 7.3 优化建议

**如果保留 state_statistics**：

1. **基本查询使用冗余字段**：
   ```sql
   -- ✅ 使用冗余字段（性能好）
   SELECT working_duration_s, working_ratio
   FROM device_state_summary
   WHERE device_id = 'DEVICE-001';
   ```

2. **详细分析使用 JSON 字段**：
   ```sql
   -- ✅ 使用 JSON 字段（信息全）
   SELECT JSON_EXTRACT(state_statistics, '$.WORKING.segment_count') as segment_count
   FROM device_state_summary
   WHERE device_id = 'DEVICE-001';
   ```

3. **数据一致性保证**：
   - 写入时同时更新冗余字段和 JSON 字段
   - 确保冗余字段与 JSON 字段中的基本数据一致

## 八、总结

### 8.1 结论

**建议保留 `state_statistics` JSON 字段**

**理由**：
1. ✅ 包含冗余字段没有的详细信息（片段数、平均时长等）
2. ✅ 支持扩展状态（无需修改表结构）
3. ✅ 便于审计和追溯
4. ✅ 基本查询使用冗余字段，详细分析使用 JSON 字段，兼顾性能和灵活性

### 8.2 使用策略

- **基本查询**：使用冗余字段（`working_duration_s`, `working_ratio` 等）
- **详细分析**：使用 JSON 字段（`state_statistics`）
- **扩展状态**：使用 JSON 字段（无需修改表结构）

### 8.3 如果业务确实不需要详细信息

**可以删除 `state_statistics`，但需要**：
- ✅ 确认不需要片段数等详细信息
- ✅ 确认状态类型固定（不会扩展）
- ✅ 接受从 `device_state_record` 重新计算的性能开销

