# device_shift_config 与 device_param_config 合并分析

## 一、当前表结构对比

### 1.1 device_param_config（设备参数配置表）

```sql
CREATE TABLE device_param_config (
    id                  CHAR(36) PRIMARY KEY,
    tenant_id           CHAR(36) NOT NULL,
    device_id           CHAR(36) NOT NULL,
    parameter_type      VARCHAR(100) NOT NULL,  -- 参数类型：THEORETICAL_CYCLE-理论节拍等
    parameter_value     DECIMAL(10,4),          -- 参数值（数值型）
    parameter_unit      VARCHAR(50),           -- 单位
    parameter_text      VARCHAR(500),           -- 参数值（文本型）
    effective_start_ts  BIGINT NOT NULL,        -- 生效开始时间戳
    effective_end_ts    BIGINT,                -- 生效结束时间戳
    description         TEXT,
    is_active           TINYINT(1) DEFAULT 1,
    -- 审计字段
    creator, create_time, updater, update_time, deleted, deleted_time
);
```

**特点**：
- 用于存储简单的参数配置（数值型或文本型）
- 通过 `parameter_type` 区分不同类型的参数
- 支持历史版本

### 1.2 device_shift_config（设备班次配置表）

```sql
CREATE TABLE device_shift_config (
    id              CHAR(36) PRIMARY KEY,
    tenant_id       CHAR(36) NOT NULL,
    device_id       CHAR(36) NOT NULL,
    shift_mode      INT NOT NULL DEFAULT 2,    -- 班次数量：2-2班制 3-3班制
    shifts          JSON NOT NULL,             -- 班次定义（JSON数组）
    effective_start_ts BIGINT NOT NULL,        -- 生效开始时间戳
    effective_end_ts BIGINT,                    -- 生效结束时间戳
    is_active       TINYINT(1) DEFAULT 1,
    -- 审计字段
    creator, create_time, updater, update_time, deleted, deleted_time
);
```

**特点**：
- 用于存储复杂的班次配置（JSON数组）
- 包含 `shift_mode`（班次数量）和 `shifts`（班次详情）
- 支持历史版本

## 二、功能对比分析

### 2.1 数据结构对比

| 方面 | device_param_config | device_shift_config |
|------|---------------------|---------------------|
| **数据类型** | 简单类型（数值/文本） | 复杂类型（JSON数组） |
| **参数类型** | 通过 `parameter_type` 区分 | 固定为班次配置 |
| **数据存储** | `parameter_value` 或 `parameter_text` | `shifts`（JSON） |
| **历史版本** | ✅ 支持 | ✅ 支持 |
| **唯一性** | 一个设备可以有多个参数类型 | 一个设备只有一个班次配置 |

### 2.2 业务语义对比

**device_param_config**：
- 存储**业务参数**（理论节拍、计划停机时间等）
- 参数是**独立的**，互不关联
- 参数值通常是**单一值**（数值或文本）

**device_shift_config**：
- 存储**班次配置**（班次数量、各班次时间等）
- 配置是**结构化的**（包含多个班次，每个班次有多个属性）
- 配置值是**复合值**（JSON数组）

### 2.3 查询场景对比

#### device_param_config 查询

```sql
-- 查询设备的理论节拍
SELECT parameter_value, parameter_unit
FROM device_param_config
WHERE device_id = 'DEVICE-001'
  AND parameter_type = 'THEORETICAL_CYCLE'
  AND is_active = 1;

-- 查询设备的所有参数
SELECT parameter_type, parameter_value, parameter_unit
FROM device_param_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;
```

#### device_shift_config 查询

```sql
-- 查询设备的班次配置
SELECT shift_mode, shifts
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;

-- 查询设备当前生效的班次配置
SELECT shifts
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1
  AND effective_end_ts IS NULL;
```

## 三、合并方案分析

### 3.1 方案 A：完全合并到 device_param_config

**设计**：
```sql
CREATE TABLE device_param_config (
    id                  CHAR(36) PRIMARY KEY,
    tenant_id           CHAR(36) NOT NULL,
    device_id           CHAR(36) NOT NULL,
    parameter_type      VARCHAR(100) NOT NULL,  -- SHIFT_CONFIG-班次配置
    parameter_value     DECIMAL(10,4),          -- 用于存储 shift_mode
    parameter_unit      VARCHAR(50),
    parameter_text      VARCHAR(500),
    parameter_json      JSON,                    -- 新增：用于存储 shifts（JSON数组）
    effective_start_ts  BIGINT NOT NULL,
    effective_end_ts    BIGINT,
    description         TEXT,
    is_active           TINYINT(1) DEFAULT 1,
    ...
);
```

**优点**：
- ✅ 表数量减少
- ✅ 统一管理所有配置

**缺点**：
- ❌ 数据结构不匹配（班次配置需要 JSON，参数配置主要是数值/文本）
- ❌ 查询逻辑复杂（需要区分参数类型）
- ❌ 索引设计困难（JSON 字段无法直接索引）
- ❌ 语义不清晰（班次配置不是"参数"）

### 3.2 方案 B：保持独立（推荐 ✅）

**设计**：保持两个表独立

**优点**：
- ✅ **语义清晰**：班次配置和参数配置是不同的业务概念
- ✅ **数据结构匹配**：班次配置用 JSON，参数配置用数值/文本
- ✅ **查询简单**：不需要区分参数类型
- ✅ **索引优化**：可以为班次配置创建专门的索引
- ✅ **扩展性好**：班次配置可以独立扩展

**缺点**：
- ⚠️ 表数量增加（但这是合理的分离）

## 四、详细对比分析

### 4.1 数据结构差异

#### device_param_config 的数据结构

**示例数据**：
```sql
-- 理论节拍
parameter_type = 'THEORETICAL_CYCLE'
parameter_value = 120.5
parameter_unit = 'SECOND'

-- 计划停机时间
parameter_type = 'PLANNED_DOWNTIME'
parameter_value = 30
parameter_unit = 'MINUTE'
```

**特点**：
- 简单的键值对
- 一个参数类型对应一个值
- 易于查询和索引

#### device_shift_config 的数据结构

**示例数据**：
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

**特点**：
- 复杂的结构化数据
- 包含多个班次，每个班次有多个属性
- 需要 JSON 存储

### 4.2 查询需求差异

#### device_param_config 查询需求

**典型查询**：
```sql
-- 1. 查询单个参数值（简单）
SELECT parameter_value 
FROM device_param_config
WHERE device_id = 'DEVICE-001' 
  AND parameter_type = 'THEORETICAL_CYCLE'
  AND is_active = 1;

-- 2. 查询所有参数（简单）
SELECT parameter_type, parameter_value, parameter_unit
FROM device_param_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;

-- 3. 按参数类型分组统计（简单）
SELECT parameter_type, COUNT(*) as count
FROM device_param_config
WHERE tenant_id = 'TENANT-001'
GROUP BY parameter_type;
```

**特点**：查询简单，直接通过 `parameter_type` 和 `parameter_value` 即可

#### device_shift_config 查询需求

**典型查询**：
```sql
-- 1. 查询班次配置（需要解析 JSON）
SELECT shifts
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;

-- 2. 查询班次数量（需要提取 JSON）
SELECT JSON_EXTRACT(shifts, '$.shift_mode') as shift_mode
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;

-- 3. 查询特定班次的时间（需要解析 JSON 数组）
SELECT JSON_EXTRACT(shifts, '$.shifts[0].startTime') as shift1_start
FROM device_shift_config
WHERE device_id = 'DEVICE-001'
  AND is_active = 1;
```

**特点**：查询复杂，需要解析 JSON

### 4.3 业务逻辑差异

#### device_param_config 的业务逻辑

**特点**：
- 一个设备可以有**多个参数**（理论节拍、计划停机时间等）
- 每个参数是**独立的**
- 参数之间**没有关联**

**使用场景**：
- 计算 OEE 时使用理论节拍
- 计算可用时间时使用计划停机时间
- 每个参数独立使用

#### device_shift_config 的业务逻辑

**特点**：
- 一个设备只有**一个班次配置**
- 班次配置是**整体性的**（包含多个班次）
- 班次之间**有关联**（时间连续）

**使用场景**：
- 判定产量归属班次
- 计算班次时长
- 班次配置作为整体使用

## 五、合并可行性分析

### 5.1 技术可行性

#### 方案 A：使用 parameter_json 字段

```sql
-- 班次配置存储
parameter_type = 'SHIFT_CONFIG'
parameter_json = '{"shift_mode": 2, "shifts": [...]}'

-- 普通参数存储
parameter_type = 'THEORETICAL_CYCLE'
parameter_value = 120.5
```

**问题**：
- ⚠️ 字段使用不一致（有些用 `parameter_value`，有些用 `parameter_json`）
- ⚠️ 查询逻辑复杂（需要判断 `parameter_type` 决定使用哪个字段）
- ⚠️ 数据验证困难（需要区分参数类型）

#### 方案 B：统一使用 parameter_json

```sql
-- 所有配置都使用 JSON
parameter_type = 'SHIFT_CONFIG'
parameter_json = '{"shift_mode": 2, "shifts": [...]}'

parameter_type = 'THEORETICAL_CYCLE'
parameter_json = '{"value": 120.5, "unit": "SECOND"}'
```

**问题**：
- ⚠️ 简单参数也需要 JSON（过度设计）
- ⚠️ 查询性能差（所有查询都需要解析 JSON）
- ⚠️ 索引困难（无法为简单参数创建索引）

### 5.2 业务可行性

#### 语义清晰度

**device_param_config**：
- ✅ 语义明确：设备参数配置
- ✅ 业务清晰：存储各种业务参数

**device_shift_config**：
- ✅ 语义明确：设备班次配置
- ✅ 业务清晰：存储班次定义

**合并后**：
- ⚠️ 语义模糊：班次配置也是"参数"？
- ⚠️ 业务不清晰：参数配置和班次配置混在一起

#### 使用频率

**device_param_config**：
- 查询频率：中（计算指标时使用）
- 更新频率：低（参数不常变更）

**device_shift_config**：
- 查询频率：高（每次判定班次都需要查询）
- 更新频率：低（班次配置不常变更）

**合并后**：
- ⚠️ 查询逻辑复杂（需要区分参数类型）
- ⚠️ 性能可能下降（JSON 解析开销）

### 5.3 维护可行性

#### 代码维护

**独立表**：
```java
// 查询班次配置（简单）
ShiftConfig config = shiftConfigService.getByDeviceId(deviceId);

// 查询参数配置（简单）
ParamConfig param = paramConfigService.getByDeviceIdAndType(deviceId, "THEORETICAL_CYCLE");
```

**合并表**：
```java
// 查询班次配置（复杂）
ParamConfig config = paramConfigService.getByDeviceIdAndType(deviceId, "SHIFT_CONFIG");
ShiftConfig shiftConfig = JSON.parse(config.getParameterJson());  // 需要解析

// 查询参数配置（复杂）
ParamConfig param = paramConfigService.getByDeviceIdAndType(deviceId, "THEORETICAL_CYCLE");
// 需要判断 parameter_type 决定使用 parameter_value 还是 parameter_json
```

**结论**：独立表代码更简洁，维护更容易

## 六、最终建议

### 6.1 推荐方案：保持独立（⭐⭐⭐⭐⭐）

**理由**：
1. ✅ **语义清晰**：班次配置和参数配置是不同的业务概念
2. ✅ **数据结构匹配**：班次配置用 JSON，参数配置用数值/文本
3. ✅ **查询简单**：不需要区分参数类型
4. ✅ **性能更好**：可以为班次配置创建专门的索引
5. ✅ **维护容易**：代码逻辑清晰，易于维护

### 6.2 如果必须合并

**方案**：使用 `parameter_json` 字段存储班次配置

```sql
-- 修改 device_param_config 表
ALTER TABLE device_param_config 
ADD COLUMN parameter_json JSON COMMENT '参数值（JSON型，用于复杂配置如班次配置）';

-- 班次配置存储
INSERT INTO device_param_config 
(device_id, parameter_type, parameter_json, effective_start_ts, is_active)
VALUES 
('DEVICE-001', 'SHIFT_CONFIG', 
 '{"shift_mode": 2, "shifts": [...]}', 
 UNIX_TIMESTAMP(NOW()), 1);

-- 普通参数存储（保持不变）
INSERT INTO device_param_config 
(device_id, parameter_type, parameter_value, parameter_unit, effective_start_ts, is_active)
VALUES 
('DEVICE-001', 'THEORETICAL_CYCLE', 120.5, 'SECOND', UNIX_TIMESTAMP(NOW()), 1);
```

**问题**：
- ⚠️ 字段使用不一致
- ⚠️ 查询逻辑复杂
- ⚠️ 代码维护困难

### 6.3 关键对比

| 方面 | 独立表 | 合并表 | 推荐 |
|------|--------|--------|------|
| **语义清晰度** | ✅ 清晰 | ⚠️ 模糊 | **独立表** |
| **数据结构匹配** | ✅ 匹配 | ⚠️ 不匹配 | **独立表** |
| **查询简单度** | ✅ 简单 | ⚠️ 复杂 | **独立表** |
| **性能** | ✅ 好 | ⚠️ 差 | **独立表** |
| **维护成本** | ✅ 低 | ⚠️ 高 | **独立表** |
| **表数量** | ⚠️ 2个表 | ✅ 1个表 | 平手 |

## 七、总结

### 7.1 核心结论

**不建议合并，保持两个表独立**

**理由**：
1. ✅ **业务语义不同**：班次配置和参数配置是不同的业务概念
2. ✅ **数据结构不同**：班次配置是复杂 JSON，参数配置是简单值
3. ✅ **查询需求不同**：班次配置需要整体查询，参数配置需要单个查询
4. ✅ **维护成本**：独立表维护成本更低

### 7.2 设计原则

**单一职责原则**：
- `device_param_config`：负责简单的参数配置
- `device_shift_config`：负责复杂的班次配置

**数据结构匹配原则**：
- 简单数据用简单字段（`parameter_value`, `parameter_text`）
- 复杂数据用 JSON 字段（`shifts`）

### 7.3 如果未来需要统一管理

**建议**：
- 保持表结构独立
- 在应用层创建统一的配置管理服务
- 通过服务层统一接口，底层仍然使用不同的表

**优势**：
- ✅ 保持数据结构的合理性
- ✅ 通过服务层提供统一的接口
- ✅ 兼顾灵活性和性能

