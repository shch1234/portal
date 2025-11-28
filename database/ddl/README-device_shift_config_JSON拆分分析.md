# device_shift_config 表 JSON 字段拆分分析

## 一、当前表结构

### 1.1 当前设计

```sql
CREATE TABLE device_shift_config (
    id              CHAR(36) PRIMARY KEY,
    tenant_id       CHAR(36) NOT NULL,
    device_id       CHAR(36) NOT NULL,
    shift_mode      INT NOT NULL DEFAULT 2,    -- 班次数量：2-2班制 3-3班制
    shifts          JSON NOT NULL,             -- 班次定义（JSON数组）
    effective_start_ts BIGINT NOT NULL,
    effective_end_ts BIGINT,
    is_active       TINYINT(1) DEFAULT 1,
    ...
);
```

### 1.2 JSON 数据结构

**shifts 字段示例**：
```json
{
  "shift_mode": 2,
  "shifts": [
    {
      "code": "SHIFT_1",
      "name": "一班",
      "startTime": "08:00:00",
      "endTime": "16:00:00",
      "duration": 28800
    },
    {
      "code": "SHIFT_2",
      "name": "二班",
      "startTime": "16:00:00",
      "endTime": "00:00:00",
      "duration": 28800
    }
  ]
}
```

**或 3 班制**：
```json
{
  "shift_mode": 3,
  "shifts": [
    {
      "code": "SHIFT_1",
      "name": "早班",
      "startTime": "08:00:00",
      "endTime": "16:00:00",
      "duration": 28800
    },
    {
      "code": "SHIFT_2",
      "name": "中班",
      "startTime": "16:00:00",
      "endTime": "00:00:00",
      "duration": 28800
    },
    {
      "code": "SHIFT_3",
      "name": "晚班",
      "startTime": "00:00:00",
      "endTime": "08:00:00",
      "duration": 28800
    }
  ]
}
```

## 二、业务场景分析

### 2.1 班次数量特点

**特点**：
- ✅ **固定数量**：通常只有 2 班制或 3 班制，最多 3 个班次
- ✅ **数量有限**：不会动态扩展到很多班次
- ✅ **结构固定**：每个班次的结构相同（code、name、startTime、endTime、duration）

### 2.2 查询场景分析

#### 场景 1：查询设备的所有班次配置

**当前方式（JSON）**：
```sql
SELECT shifts
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;
```

**拆分后**：
```sql
SELECT 
    shift_1_code, shift_1_name, shift_1_start_time, shift_1_end_time,
    shift_2_code, shift_2_name, shift_2_start_time, shift_2_end_time,
    shift_3_code, shift_3_name, shift_3_start_time, shift_3_end_time
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;
```

**对比**：
- JSON：简单，一次查询获取所有数据
- 拆分：字段多，但查询简单

#### 场景 2：根据时间判定属于哪个班次

**当前方式（JSON）**：
```sql
-- 需要应用层解析 JSON
SELECT shifts
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;
-- 然后在应用层遍历 JSON 数组，判断时间属于哪个班次
```

**拆分后**：
```sql
-- 可以直接在 SQL 中判断
SELECT 
    CASE 
        WHEN TIME('14:30:00') BETWEEN shift_1_start_time AND shift_1_end_time THEN shift_1_code
        WHEN TIME('14:30:00') BETWEEN shift_2_start_time AND shift_2_end_time THEN shift_2_code
        WHEN TIME('14:30:00') BETWEEN shift_3_start_time AND shift_3_end_time THEN shift_3_code
    END as current_shift
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;
```

**对比**：
- JSON：需要在应用层解析
- 拆分：可以在数据库层直接判断，性能更好

#### 场景 3：查询特定班次的开始时间

**当前方式（JSON）**：
```sql
SELECT JSON_EXTRACT(shifts, '$.shifts[0].startTime') as shift1_start
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;
```

**拆分后**：
```sql
SELECT shift_1_start_time
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;
```

**对比**：
- JSON：需要 JSON_EXTRACT，性能较差
- 拆分：直接查询字段，性能好

#### 场景 4：为班次时间创建索引

**当前方式（JSON）**：
```sql
-- MySQL 5.7+ 支持 JSON 索引，但需要创建虚拟列
ALTER TABLE device_shift_config
ADD COLUMN shift_1_start_time_virtual TIME AS (JSON_EXTRACT(shifts, '$.shifts[0].startTime')) VIRTUAL;

CREATE INDEX idx_shift_1_start ON device_shift_config (shift_1_start_time_virtual);
```

**拆分后**：
```sql
-- 直接为字段创建索引
CREATE INDEX idx_shift_1_start ON device_shift_config (shift_1_start_time);
```

**对比**：
- JSON：需要虚拟列，索引维护复杂
- 拆分：直接索引，简单高效

## 三、拆分方案设计

### 3.1 方案 A：扁平化字段（推荐 ⭐⭐⭐⭐⭐）

**设计**：将每个班次的关键字段拆分为独立列

```sql
CREATE TABLE device_shift_config (
    id              CHAR(36) PRIMARY KEY,
    tenant_id       CHAR(36) NOT NULL,
    device_id       CHAR(36) NOT NULL,
    shift_mode      INT NOT NULL DEFAULT 2 COMMENT '班次数量：2-2班制 3-3班制',
    
    -- 班次1（必填）
    shift_1_code        VARCHAR(50) NOT NULL COMMENT '班次1编码：SHIFT_1',
    shift_1_name        VARCHAR(100) NOT NULL COMMENT '班次1名称：一班/早班',
    shift_1_start_time  TIME NOT NULL COMMENT '班次1开始时间：08:00:00',
    shift_1_end_time    TIME NOT NULL COMMENT '班次1结束时间：16:00:00',
    shift_1_duration_s  INT COMMENT '班次1时长（秒）',
    
    -- 班次2（必填）
    shift_2_code        VARCHAR(50) NOT NULL COMMENT '班次2编码：SHIFT_2',
    shift_2_name        VARCHAR(100) NOT NULL COMMENT '班次2名称：二班/中班',
    shift_2_start_time  TIME NOT NULL COMMENT '班次2开始时间：16:00:00',
    shift_2_end_time    TIME NOT NULL COMMENT '班次2结束时间：00:00:00',
    shift_2_duration_s  INT COMMENT '班次2时长（秒）',
    
    -- 班次3（可选，仅3班制时使用）
    shift_3_code        VARCHAR(50) COMMENT '班次3编码：SHIFT_3（仅3班制时使用）',
    shift_3_name        VARCHAR(100) COMMENT '班次3名称：三班/晚班（仅3班制时使用）',
    shift_3_start_time  TIME COMMENT '班次3开始时间（仅3班制时使用）',
    shift_3_end_time    TIME COMMENT '班次3结束时间（仅3班制时使用）',
    shift_3_duration_s  INT COMMENT '班次3时长（秒，仅3班制时使用）',
    
    effective_start_ts BIGINT NOT NULL,
    effective_end_ts   BIGINT,
    is_active          TINYINT(1) DEFAULT 1,
    ...
);
```

**优点**：
- ✅ **查询性能好**：直接查询字段，无需解析 JSON
- ✅ **索引支持好**：可以为时间字段创建索引
- ✅ **SQL 查询简单**：可以直接在 SQL 中判断班次
- ✅ **数据验证简单**：字段类型明确，易于验证

**缺点**：
- ⚠️ **字段较多**：最多 15 个班次相关字段（3个班次 × 5个字段）
- ⚠️ **扩展性差**：如果未来需要 4 班制，需要修改表结构

### 3.2 方案 B：规范化表（备选 ⭐⭐⭐）

**设计**：将班次拆分为独立的班次明细表

```sql
-- 主表：设备班次配置
CREATE TABLE device_shift_config (
    id              CHAR(36) PRIMARY KEY,
    tenant_id       CHAR(36) NOT NULL,
    device_id       CHAR(36) NOT NULL,
    shift_mode      INT NOT NULL DEFAULT 2,
    effective_start_ts BIGINT NOT NULL,
    effective_end_ts BIGINT,
    is_active       TINYINT(1) DEFAULT 1,
    ...
);

-- 明细表：班次定义
CREATE TABLE device_shift_detail (
    id              CHAR(36) PRIMARY KEY,
    shift_config_id CHAR(36) NOT NULL COMMENT '关联 device_shift_config.id',
    shift_order     INT NOT NULL COMMENT '班次顺序：1, 2, 3',
    shift_code      VARCHAR(50) NOT NULL COMMENT '班次编码：SHIFT_1',
    shift_name      VARCHAR(100) NOT NULL COMMENT '班次名称：一班',
    start_time      TIME NOT NULL COMMENT '开始时间：08:00:00',
    end_time        TIME NOT NULL COMMENT '结束时间：16:00:00',
    duration_s      INT COMMENT '时长（秒）',
    CONSTRAINT fk_shift_detail_config FOREIGN KEY (shift_config_id) 
        REFERENCES device_shift_config(id) ON DELETE CASCADE
);

CREATE INDEX idx_shift_detail_config ON device_shift_detail (shift_config_id, shift_order);
CREATE INDEX idx_shift_detail_time ON device_shift_detail (shift_config_id, start_time, end_time);
```

**优点**：
- ✅ **扩展性好**：可以支持任意数量的班次
- ✅ **结构清晰**：主表和明细表分离
- ✅ **查询灵活**：可以单独查询某个班次

**缺点**：
- ⚠️ **查询复杂**：需要 JOIN 查询
- ⚠️ **性能略差**：需要关联查询
- ⚠️ **表数量增加**：需要维护两张表

### 3.3 方案 C：混合方案（不推荐 ⚠️）

**设计**：保留 JSON 字段，同时添加冗余字段用于查询

```sql
CREATE TABLE device_shift_config (
    id              CHAR(36) PRIMARY KEY,
    tenant_id       CHAR(36) NOT NULL,
    device_id       CHAR(36) NOT NULL,
    shift_mode      INT NOT NULL DEFAULT 2,
    shifts          JSON NOT NULL,  -- 保留 JSON 用于完整数据
    
    -- 冗余字段用于查询和索引
    shift_1_start_time  TIME,
    shift_1_end_time    TIME,
    shift_2_start_time  TIME,
    shift_2_end_time    TIME,
    shift_3_start_time  TIME,
    shift_3_end_time    TIME,
    ...
);
```

**优点**：
- ✅ 保留 JSON 的灵活性
- ✅ 冗余字段支持快速查询

**缺点**：
- ❌ **数据冗余**：需要维护 JSON 和字段的一致性
- ❌ **维护复杂**：更新时需要同时更新 JSON 和字段
- ❌ **容易出错**：数据不一致的风险

## 四、性能对比分析

### 4.1 查询性能

#### 场景 1：查询所有班次配置

| 方案 | SQL 复杂度 | 执行时间 | 索引支持 |
|------|-----------|---------|---------|
| **JSON** | 简单 | 中 | 需要虚拟列 |
| **扁平化字段** | 简单 | **快** | ✅ 直接索引 |
| **规范化表** | 复杂（JOIN） | 中 | ✅ 直接索引 |

#### 场景 2：根据时间判定班次

| 方案 | SQL 复杂度 | 执行时间 | 数据库支持 |
|------|-----------|---------|-----------|
| **JSON** | 简单（但需应用层处理） | 慢 | ❌ 需要应用层解析 |
| **扁平化字段** | 简单（SQL 直接判断） | **快** | ✅ SQL 直接判断 |
| **规范化表** | 复杂（JOIN + 条件） | 中 | ✅ SQL 直接判断 |

#### 场景 3：为班次时间创建索引

| 方案 | 索引创建 | 索引维护 | 查询性能 |
|------|---------|---------|---------|
| **JSON** | 需要虚拟列 | 复杂 | 中 |
| **扁平化字段** | 直接索引 | **简单** | **快** |
| **规范化表** | 直接索引 | 简单 | 快 |

### 4.2 存储空间

**JSON 方案**：
- 存储：JSON 字符串，约 200-500 字节
- 索引：需要虚拟列，额外空间

**扁平化字段方案**：
- 存储：约 300-600 字节（15 个字段）
- 索引：直接索引，空间效率高

**规范化表方案**：
- 存储：主表约 100 字节，明细表约 200-300 字节/班次
- 索引：主表 + 明细表索引

**结论**：存储空间差异不大，但扁平化字段方案索引效率更高

## 五、业务扩展性分析

### 5.1 班次数量扩展

**当前需求**：
- 2 班制：2 个班次
- 3 班制：3 个班次

**未来可能需求**：
- 4 班制：4 个班次（可能性低）
- 5 班制：5 个班次（可能性极低）

**各方案扩展性**：

| 方案 | 2班制 | 3班制 | 4班制 | 5班制+ |
|------|-------|-------|-------|--------|
| **JSON** | ✅ 支持 | ✅ 支持 | ✅ 支持 | ✅ 支持 |
| **扁平化字段** | ✅ 支持 | ✅ 支持 | ⚠️ 需要修改表结构 | ❌ 不支持 |
| **规范化表** | ✅ 支持 | ✅ 支持 | ✅ 支持 | ✅ 支持 |

**分析**：
- 4 班制及以上在实际业务中**极其罕见**
- 扁平化字段方案可以满足 99% 的业务需求
- 如果需要支持 4 班制，可以通过 ALTER TABLE 添加字段（虽然不优雅，但可行）

### 5.2 班次属性扩展

**当前属性**：
- code（编码）
- name（名称）
- startTime（开始时间）
- endTime（结束时间）
- duration（时长）

**未来可能属性**：
- breakTime（休息时间）
- overtime（加班时间）
- workDays（工作日）

**各方案扩展性**：

| 方案 | 添加属性难度 | 查询性能影响 |
|------|------------|------------|
| **JSON** | ✅ 容易（直接添加字段） | 无影响 |
| **扁平化字段** | ⚠️ 需要添加列 | 无影响 |
| **规范化表** | ✅ 容易（添加列） | 无影响 |

**分析**：
- 扁平化字段方案添加属性需要修改表结构，但影响不大
- 规范化表方案添加属性最灵活

## 六、代码维护性分析

### 6.1 查询代码

#### JSON 方案

```java
// 查询班次配置
ShiftConfig config = shiftConfigService.getByDeviceId(deviceId);
List<Shift> shifts = JSON.parseArray(config.getShifts(), Shift.class);

// 根据时间判定班次
for (Shift shift : shifts) {
    if (isTimeInRange(currentTime, shift.getStartTime(), shift.getEndTime())) {
        return shift.getCode();
    }
}
```

**问题**：
- 需要解析 JSON
- 需要在应用层循环判断

#### 扁平化字段方案

```java
// 查询班次配置
ShiftConfig config = shiftConfigService.getByDeviceId(deviceId);
List<Shift> shifts = Arrays.asList(
    new Shift(config.getShift1Code(), config.getShift1Name(), 
              config.getShift1StartTime(), config.getShift1EndTime()),
    new Shift(config.getShift2Code(), config.getShift2Name(), 
              config.getShift2StartTime(), config.getShift2EndTime()),
    config.getShiftMode() == 3 ? 
        new Shift(config.getShift3Code(), config.getShift3Name(), 
                  config.getShift3StartTime(), config.getShift3EndTime()) : null
);

// 根据时间判定班次（也可以在 SQL 中完成）
String shiftCode = shiftConfigService.getShiftByTime(deviceId, currentTime);
```

**优势**：
- 无需解析 JSON
- 可以直接在 SQL 中判断

#### 规范化表方案

```java
// 查询班次配置
ShiftConfig config = shiftConfigService.getByDeviceId(deviceId);
List<ShiftDetail> shifts = shiftDetailService.getByConfigId(config.getId());

// 根据时间判定班次
String shiftCode = shiftDetailService.getShiftByTime(config.getId(), currentTime);
```

**优势**：
- 结构清晰
- 查询灵活

### 6.2 更新代码

#### JSON 方案

```java
// 更新班次配置
ShiftConfig config = new ShiftConfig();
config.setShifts(JSON.toJSONString(shifts));  // 需要序列化
shiftConfigService.update(config);
```

#### 扁平化字段方案

```java
// 更新班次配置
ShiftConfig config = new ShiftConfig();
config.setShift1Code(shifts.get(0).getCode());
config.setShift1Name(shifts.get(0).getName());
config.setShift1StartTime(shifts.get(0).getStartTime());
// ... 设置其他字段
shiftConfigService.update(config);
```

#### 规范化表方案

```java
// 更新班次配置
ShiftConfig config = shiftConfigService.getByDeviceId(deviceId);
// 删除旧班次
shiftDetailService.deleteByConfigId(config.getId());
// 插入新班次
for (Shift shift : shifts) {
    ShiftDetail detail = new ShiftDetail();
    detail.setShiftConfigId(config.getId());
    detail.setShiftOrder(shift.getOrder());
    // ... 设置其他字段
    shiftDetailService.insert(detail);
}
```

**对比**：
- JSON 方案：更新简单，但需要序列化
- 扁平化字段方案：更新直接，字段多但清晰
- 规范化表方案：更新复杂，需要事务保证一致性

## 七、最终建议

### 7.1 推荐方案：扁平化字段（⭐⭐⭐⭐⭐）

**理由**：
1. ✅ **性能最优**：直接查询字段，无需解析 JSON
2. ✅ **索引支持好**：可以为时间字段创建索引，查询速度快
3. ✅ **SQL 查询简单**：可以直接在 SQL 中判断班次，减少应用层逻辑
4. ✅ **业务匹配度高**：班次数量固定（2-3个），扁平化字段完全满足需求
5. ✅ **维护简单**：字段明确，易于理解和维护

### 7.2 备选方案：规范化表（⭐⭐⭐）

**适用场景**：
- 如果未来可能需要支持 4 班制及以上
- 如果班次属性需要频繁扩展
- 如果希望表结构更加规范化

### 7.3 不推荐：JSON 方案（⭐⭐）

**问题**：
- ❌ 查询性能差（需要解析 JSON）
- ❌ 索引支持差（需要虚拟列）
- ❌ SQL 查询能力弱（无法直接在 SQL 中判断班次）

**保留场景**：
- 如果班次配置需要存储大量扩展属性
- 如果班次结构需要频繁变化
- 如果查询频率极低

## 八、实施建议

### 8.1 如果选择扁平化字段方案

**步骤 1**：修改表结构
```sql
ALTER TABLE device_shift_config
DROP COLUMN shifts,
ADD COLUMN shift_1_code VARCHAR(50) NOT NULL COMMENT '班次1编码',
ADD COLUMN shift_1_name VARCHAR(100) NOT NULL COMMENT '班次1名称',
ADD COLUMN shift_1_start_time TIME NOT NULL COMMENT '班次1开始时间',
ADD COLUMN shift_1_end_time TIME NOT NULL COMMENT '班次1结束时间',
ADD COLUMN shift_1_duration_s INT COMMENT '班次1时长（秒）',
ADD COLUMN shift_2_code VARCHAR(50) NOT NULL COMMENT '班次2编码',
ADD COLUMN shift_2_name VARCHAR(100) NOT NULL COMMENT '班次2名称',
ADD COLUMN shift_2_start_time TIME NOT NULL COMMENT '班次2开始时间',
ADD COLUMN shift_2_end_time TIME NOT NULL COMMENT '班次2结束时间',
ADD COLUMN shift_2_duration_s INT COMMENT '班次2时长（秒）',
ADD COLUMN shift_3_code VARCHAR(50) COMMENT '班次3编码（仅3班制时使用）',
ADD COLUMN shift_3_name VARCHAR(100) COMMENT '班次3名称（仅3班制时使用）',
ADD COLUMN shift_3_start_time TIME COMMENT '班次3开始时间（仅3班制时使用）',
ADD COLUMN shift_3_end_time TIME COMMENT '班次3结束时间（仅3班制时使用）',
ADD COLUMN shift_3_duration_s INT COMMENT '班次3时长（秒，仅3班制时使用）';
```

**步骤 2**：创建索引
```sql
CREATE INDEX idx_shift_1_time ON device_shift_config (device_id, shift_1_start_time, shift_1_end_time);
CREATE INDEX idx_shift_2_time ON device_shift_config (device_id, shift_2_start_time, shift_2_end_time);
CREATE INDEX idx_shift_3_time ON device_shift_config (device_id, shift_3_start_time, shift_3_end_time);
```

**步骤 3**：数据迁移
```sql
-- 从 JSON 中提取数据并更新到新字段
UPDATE device_shift_config
SET 
    shift_1_code = JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[0].code')),
    shift_1_name = JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[0].name')),
    shift_1_start_time = JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[0].startTime')),
    shift_1_end_time = JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[0].endTime')),
    shift_1_duration_s = JSON_EXTRACT(shifts, '$.shifts[0].duration'),
    shift_2_code = JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[1].code')),
    shift_2_name = JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[1].name')),
    shift_2_start_time = JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[1].startTime')),
    shift_2_end_time = JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[1].endTime')),
    shift_2_duration_s = JSON_EXTRACT(shifts, '$.shifts[1].duration')
WHERE shift_mode = 2;

-- 3 班制的数据迁移类似
```

### 8.2 如果选择规范化表方案

**步骤 1**：创建明细表
```sql
CREATE TABLE device_shift_detail (...);
```

**步骤 2**：数据迁移
```sql
-- 从 JSON 中提取数据并插入到明细表
INSERT INTO device_shift_detail (shift_config_id, shift_order, shift_code, ...)
SELECT 
    id,
    1,
    JSON_UNQUOTE(JSON_EXTRACT(shifts, '$.shifts[0].code')),
    ...
FROM device_shift_config
WHERE shift_mode >= 1;
```

## 九、总结

### 9.1 核心结论

**推荐使用扁平化字段方案**

**理由**：
1. ✅ **性能最优**：直接查询字段，无需解析 JSON
2. ✅ **索引支持好**：可以为时间字段创建索引
3. ✅ **SQL 查询能力强**：可以直接在 SQL 中判断班次
4. ✅ **业务匹配度高**：班次数量固定（2-3个），完全满足需求
5. ✅ **维护简单**：字段明确，易于理解和维护

### 9.2 关键对比

| 方面 | JSON | 扁平化字段 | 规范化表 | 推荐 |
|------|------|-----------|---------|------|
| **查询性能** | ⚠️ 慢 | ✅ **快** | ✅ 快 | **扁平化** |
| **索引支持** | ⚠️ 需要虚拟列 | ✅ **直接索引** | ✅ 直接索引 | **扁平化** |
| **SQL 查询能力** | ❌ 弱 | ✅ **强** | ✅ 强 | **扁平化** |
| **扩展性** | ✅ 好 | ⚠️ 一般 | ✅ 好 | 平手 |
| **维护成本** | ⚠️ 中 | ✅ **低** | ⚠️ 中 | **扁平化** |

### 9.3 设计原则

**数据结构匹配业务需求**：
- 班次数量固定（2-3个）→ 使用扁平化字段
- 班次结构固定（code、name、time）→ 使用独立字段

**性能优先**：
- 班次配置查询频率高 → 需要好的索引支持
- 需要根据时间判定班次 → 需要 SQL 直接查询能力

