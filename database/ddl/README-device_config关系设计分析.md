# device_config 与 device_info 关系设计分析

## 一、当前设计

### 1.1 当前关系

```sql
-- device_info 表（主表）
CREATE TABLE device_info (
    id CHAR(36) PRIMARY KEY,
    device_code VARCHAR(100),
    device_name VARCHAR(255),
    ...
);

-- device_config 表（从表）
CREATE TABLE device_config (
    id CHAR(36) PRIMARY KEY,
    device_id CHAR(36) NOT NULL,  -- 外键
    ...
    CONSTRAINT fk_device_config_device 
        FOREIGN KEY (device_id) 
        REFERENCES device_info(id) 
        ON DELETE CASCADE
);
```

### 1.2 关系类型判断

**问题**：这是一个 **1:1** 还是 **1:N** 关系？

**分析**：
- 从业务角度看：一个设备通常只有一个配置（网络配置 + 位置信息）
- 从技术角度看：如果支持配置历史版本，可能是 1:N 关系

## 二、通用做法分析

### 2.1 一对一（1:1）关系的设计

#### 方案 A：在从表中存储外键（推荐）

```sql
-- 主表：device_info
CREATE TABLE device_info (
    id CHAR(36) PRIMARY KEY,
    ...
);

-- 从表：device_config（存储外键）
CREATE TABLE device_config (
    id CHAR(36) PRIMARY KEY,
    device_id CHAR(36) NOT NULL UNIQUE,  -- 外键 + 唯一约束
    ...
    CONSTRAINT fk_device_config_device 
        FOREIGN KEY (device_id) 
        REFERENCES device_info(id) 
        ON DELETE CASCADE
);
```

**优点**：
- ✅ 符合通用做法（在依赖表中存储外键）
- ✅ 允许设备没有配置（device_config 可以为空）
- ✅ 删除设备时自动删除配置（CASCADE）

**缺点**：
- ⚠️ 如果设备必须有配置，需要应用层保证

#### 方案 B：在主表中存储外键（不推荐）

```sql
-- 主表：device_info（存储外键）
CREATE TABLE device_info (
    id CHAR(36) PRIMARY KEY,
    config_id CHAR(36) UNIQUE,  -- 外键 + 唯一约束
    ...
    CONSTRAINT fk_device_info_config 
        FOREIGN KEY (config_id) 
        REFERENCES device_config(id) 
        ON DELETE SET NULL
);

-- 从表：device_config
CREATE TABLE device_config (
    id CHAR(36) PRIMARY KEY,
    ...
);
```

**缺点**：
- ❌ 不符合通用做法（主表不应该依赖从表）
- ❌ 必须先创建配置，再创建设备（顺序问题）
- ❌ 删除配置时，设备的外键需要设置为 NULL

### 2.2 一对多（1:N）关系的设计

**适用场景**：如果支持配置历史版本

```sql
-- 主表：device_info
CREATE TABLE device_info (
    id CHAR(36) PRIMARY KEY,
    ...
);

-- 从表：device_config（支持多个配置）
CREATE TABLE device_config (
    id CHAR(36) PRIMARY KEY,
    device_id CHAR(36) NOT NULL,  -- 外键（不唯一）
    effective_start_ts BIGINT NOT NULL,  -- 生效开始时间
    effective_end_ts BIGINT,  -- 生效结束时间（NULL表示当前生效）
    is_active TINYINT(1) DEFAULT 1,  -- 是否当前生效
    ...
    CONSTRAINT fk_device_config_device 
        FOREIGN KEY (device_id) 
        REFERENCES device_info(id) 
        ON DELETE CASCADE
);

-- 确保每个设备只有一个当前生效的配置
CREATE UNIQUE INDEX uk_device_config_active 
    ON device_config (device_id, is_active) 
    WHERE is_active = 1;
```

## 三、外键删除策略分析

### 3.1 删除策略对比

| 策略 | SQL 语法 | 行为 | 适用场景 |
|------|---------|------|---------|
| **CASCADE** | `ON DELETE CASCADE` | 删除主表记录时，自动删除从表记录 | 配置完全依赖设备，设备删除时配置无意义 |
| **SET NULL** | `ON DELETE SET NULL` | 删除主表记录时，从表外键设置为 NULL | 配置可以独立存在，但通常不适用 |
| **RESTRICT** | `ON DELETE RESTRICT` | 如果从表有记录，禁止删除主表记录 | 需要先删除配置才能删除设备 |
| **NO ACTION** | `ON DELETE NO ACTION` | 同 RESTRICT（MySQL 中） | 同 RESTRICT |

### 3.2 当前设计评估

**当前设计**：`ON DELETE CASCADE`

**评估**：
- ✅ **合理**：设备删除时，配置也应该删除
- ✅ **符合业务逻辑**：配置完全依赖设备，设备不存在时配置无意义

**对比其他表**：
```sql
-- device_info 与其他表的关系
device_info -> device_model: ON DELETE RESTRICT  -- 设备存在时不能删除型号
device_info -> device_org_unit: ON DELETE SET NULL  -- 组织删除时，设备保留但组织字段为NULL
device_info -> device_config: ON DELETE CASCADE  -- 设备删除时，配置也删除
```

**结论**：删除策略选择合理。

## 四、关系设计建议

### 4.1 推荐方案：1:1 关系 + 唯一约束

**设计**：
```sql
CREATE TABLE device_config (
    id                  CHAR(36) NOT NULL PRIMARY KEY COMMENT '主键ID',
    tenant_id           CHAR(36) NOT NULL COMMENT '租户ID',
    device_id           CHAR(36) NOT NULL COMMENT '设备ID（外键关联 device_info.id，1:1关系）',
    ...
    CONSTRAINT fk_device_config_device 
        FOREIGN KEY (device_id) 
        REFERENCES device_info(id) 
        ON DELETE CASCADE,
    CONSTRAINT uk_device_config_device 
        UNIQUE (device_id)  -- 确保 1:1 关系
) COMMENT='设备配置表：与device_info为1:1关系';
```

**理由**：
1. ✅ 符合通用做法（在从表存储外键）
2. ✅ 确保 1:1 关系（唯一约束）
3. ✅ 允许设备没有配置（可选关系）
4. ✅ 删除策略合理（CASCADE）

### 4.2 如果支持配置历史版本：1:N 关系

**设计**：
```sql
CREATE TABLE device_config (
    id                  CHAR(36) NOT NULL PRIMARY KEY COMMENT '主键ID',
    tenant_id           CHAR(36) NOT NULL COMMENT '租户ID',
    device_id           CHAR(36) NOT NULL COMMENT '设备ID（外键关联 device_info.id，1:N关系）',
    effective_start_ts BIGINT NOT NULL COMMENT '生效开始时间戳（毫秒）',
    effective_end_ts    BIGINT COMMENT '生效结束时间戳（毫秒，NULL表示当前生效）',
    is_active           TINYINT(1) DEFAULT 1 COMMENT '是否当前生效：1-生效 0-历史',
    ...
    CONSTRAINT fk_device_config_device 
        FOREIGN KEY (device_id) 
        REFERENCES device_info(id) 
        ON DELETE CASCADE
) COMMENT='设备配置表：与device_info为1:N关系（支持配置历史）';

-- 确保每个设备只有一个当前生效的配置（MySQL 8.0+ 支持部分索引）
CREATE UNIQUE INDEX uk_device_config_active 
    ON device_config (device_id) 
    WHERE is_active = 1;

-- 或使用触发器保证（MySQL 5.7）
```

**理由**：
1. ✅ 支持配置历史追溯
2. ✅ 可以查询配置变更历史
3. ✅ 通过唯一索引确保只有一个当前生效的配置

## 五、索引设计

### 5.1 当前索引

```sql
CREATE INDEX idx_device_config_tenant ON device_config (tenant_id);
CREATE INDEX idx_device_config_device ON device_config (device_id);
```

### 5.2 优化建议

#### 如果是 1:1 关系

```sql
-- 添加唯一索引（确保 1:1 关系）
CREATE UNIQUE INDEX uk_device_config_device ON device_config (device_id);

-- 如果经常通过设备查询配置，device_id 索引已足够
-- 如果经常通过租户查询，tenant_id 索引已足够
```

#### 如果是 1:N 关系（支持历史版本）

```sql
-- 设备ID索引（已有）
CREATE INDEX idx_device_config_device ON device_config (device_id);

-- 当前生效配置查询索引
CREATE INDEX idx_device_config_active 
    ON device_config (device_id, is_active) 
    WHERE is_active = 1;

-- 历史配置查询索引
CREATE INDEX idx_device_config_history 
    ON device_config (device_id, effective_start_ts, effective_end_ts);
```

## 六、查询性能对比

### 6.1 1:1 关系查询

```sql
-- 查询设备及其配置（JOIN）
SELECT di.*, dc.*
FROM device_info di
LEFT JOIN device_config dc ON di.id = dc.device_id
WHERE di.device_code = 'DEV-0001';

-- 性能：通过 device_id 索引，性能好
```

### 6.2 1:N 关系查询（支持历史版本）

```sql
-- 查询设备当前配置
SELECT dc.*
FROM device_config dc
WHERE dc.device_id = 'DEVICE-001'
  AND dc.is_active = 1;

-- 查询设备配置历史
SELECT dc.*
FROM device_config dc
WHERE dc.device_id = 'DEVICE-001'
ORDER BY dc.effective_start_ts DESC;

-- 性能：通过 device_id + is_active 索引，性能好
```

## 七、最终建议

### 7.1 推荐方案：1:1 关系 + 唯一约束

**SQL 实现**：
```sql
CREATE TABLE device_config (
    id                  CHAR(36) NOT NULL PRIMARY KEY COMMENT '主键ID',
    tenant_id           CHAR(36) NOT NULL COMMENT '租户ID',
    device_id           CHAR(36) NOT NULL COMMENT '设备ID（外键关联 device_info.id，1:1关系）',
    -- 网络配置字段
    ip_address          VARCHAR(50) COMMENT 'IP地址',
    port                INT COMMENT '端口号',
    ...
    -- 位置信息字段
    location_code       VARCHAR(100) COMMENT '位置编码',
    ...
    -- 审计字段
    creator, create_time, updater, update_time, deleted, deleted_time,
    
    -- 外键约束
    CONSTRAINT fk_device_config_device 
        FOREIGN KEY (device_id) 
        REFERENCES device_info(id) 
        ON DELETE CASCADE,
    
    -- 唯一约束（确保 1:1 关系）
    CONSTRAINT uk_device_config_device 
        UNIQUE (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备配置表：与device_info为1:1关系，一个设备只有一个配置';

-- 索引
CREATE INDEX idx_device_config_tenant ON device_config (tenant_id);
-- device_id 的唯一索引已通过 UNIQUE 约束自动创建
```

**优点**：
- ✅ 符合通用做法（在从表存储外键）
- ✅ 确保 1:1 关系（唯一约束）
- ✅ 允许设备没有配置（可选关系）
- ✅ 删除策略合理（CASCADE）
- ✅ 查询性能好（通过 device_id 索引）

### 7.2 如果未来需要支持配置历史

**建议**：
1. 修改为 1:N 关系（移除唯一约束）
2. 添加 `effective_start_ts`、`effective_end_ts`、`is_active` 字段
3. 添加唯一索引确保只有一个当前生效的配置

### 7.3 关键设计原则

1. **外键位置**：在从表（device_config）中存储外键 ✅
2. **关系类型**：1:1 关系，通过唯一约束保证 ✅
3. **删除策略**：CASCADE（设备删除时配置也删除）✅
4. **可选性**：允许设备没有配置（LEFT JOIN）✅

## 八、总结

### 8.1 当前设计评估

| 方面 | 当前设计 | 评估 | 建议 |
|------|---------|------|------|
| **外键位置** | device_config.device_id | ✅ 正确 | 保持 |
| **关系类型** | 未明确（可能是 1:1 或 1:N） | ⚠️ 需明确 | 添加唯一约束 |
| **删除策略** | ON DELETE CASCADE | ✅ 合理 | 保持 |
| **唯一性** | 无唯一约束 | ⚠️ 需添加 | 添加唯一约束 |

### 8.2 优化建议

1. ✅ **添加唯一约束**：确保 1:1 关系
2. ✅ **明确关系类型**：在表注释中说明
3. ✅ **保持删除策略**：CASCADE 合理

### 8.3 最终结论

**当前设计基本正确**，但需要：
- 添加 `UNIQUE (device_id)` 约束，确保 1:1 关系
- 在表注释中明确说明关系类型

