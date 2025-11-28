# device_config 表拆分说明

## 一、拆分概述

### 1.1 拆分原因

原 `device_config` 表混合了两种不同的业务概念：
- **网络配置**：设备通信连接参数（技术配置）
- **位置信息**：设备物理位置（物理属性）

拆分后职责更清晰，便于独立管理和维护。

### 1.2 拆分结果

| 原表 | 拆分后 | 业务分类 |
|------|--------|---------|
| `device_config` | `device_network_config` | 网络配置（通信参数） |
| | `device_location` | 位置信息（物理位置） |

## 二、新表结构

### 2.1 device_network_config（设备网络配置表）

**业务职责**：管理设备网络连接配置

**核心字段**：
- `ip_address` - IP地址
- `port` - 端口号
- `mac_address` - MAC地址
- `gateway` - 网关地址
- `subnet_mask` - 子网掩码
- `protocol` - 通信协议
- `connection_params` - 连接参数（JSON）

**历史版本支持**：
- `effective_start_ts` - 生效开始时间戳
- `effective_end_ts` - 生效结束时间戳（NULL表示当前生效）
- `is_active` - 是否当前生效（1-当前生效，0-历史版本）

**关系**：与 `device_info` 为 **1:N** 关系（一个设备可以有多个历史配置）

### 2.2 device_location（设备位置信息表）

**业务职责**：管理设备物理位置信息

**核心字段**：
- `location_code` - 位置编码
- `location_description` - 位置描述
- `coordinates` - 坐标信息（JSON）
- `floor_no` - 楼层号（冗余字段）
- `area_code` - 区域编码（冗余字段）
- `longitude` - 经度（冗余字段）
- `latitude` - 纬度（冗余字段）

**历史版本支持**：
- `effective_start_ts` - 生效开始时间戳
- `effective_end_ts` - 生效结束时间戳（NULL表示当前生效）
- `is_active` - 是否当前生效（1-当前生效，0-历史版本）

**关系**：与 `device_info` 为 **1:N** 关系（一个设备可以有多个历史位置）

## 三、历史版本功能

### 3.1 设计模式

参考 `device_param_config` 的设计模式，使用时间戳管理历史版本：

```sql
effective_start_ts  BIGINT NOT NULL  -- 生效开始时间戳
effective_end_ts    BIGINT           -- 生效结束时间戳（NULL表示当前生效）
is_active           TINYINT(1)       -- 是否当前生效
```

### 3.2 使用场景

#### 场景 1：网络配置变更

**需求**：记录设备 IP 地址变更历史

```sql
-- 1. 创建初始配置（当前生效）
INSERT INTO device_network_config 
(id, device_id, ip_address, port, protocol, effective_start_ts, is_active)
VALUES 
('NET-001', 'DEVICE-001', '192.168.1.100', 8080, 'MODBUS', 
 UNIX_TIMESTAMP(NOW()) * 1000, 1);

-- 2. IP 地址变更（创建新配置，旧配置标记为历史）
-- 2.1 更新旧配置的结束时间
UPDATE device_network_config 
SET effective_end_ts = UNIX_TIMESTAMP(NOW()) * 1000,
    is_active = 0
WHERE device_id = 'DEVICE-001' AND is_active = 1;

-- 2.2 创建新配置
INSERT INTO device_network_config 
(id, device_id, ip_address, port, protocol, effective_start_ts, is_active, description)
VALUES 
('NET-002', 'DEVICE-001', '192.168.1.200', 8080, 'MODBUS', 
 UNIX_TIMESTAMP(NOW()) * 1000, 1, 'IP地址变更：192.168.1.100 -> 192.168.1.200');
```

#### 场景 2：设备位置变更

**需求**：记录设备搬迁历史

```sql
-- 1. 创建初始位置（当前生效）
INSERT INTO device_location 
(id, device_id, location_code, floor_no, longitude, latitude, effective_start_ts, is_active)
VALUES 
('LOC-001', 'DEVICE-001', 'A区-1层-01号位', 1, 116.3974, 39.9093, 
 UNIX_TIMESTAMP(NOW()) * 1000, 1);

-- 2. 设备搬迁（创建新位置，旧位置标记为历史）
-- 2.1 更新旧位置的结束时间
UPDATE device_location 
SET effective_end_ts = UNIX_TIMESTAMP(NOW()) * 1000,
    is_active = 0
WHERE device_id = 'DEVICE-001' AND is_active = 1;

-- 2.2 创建新位置
INSERT INTO device_location 
(id, device_id, location_code, floor_no, longitude, latitude, effective_start_ts, is_active, description)
VALUES 
('LOC-002', 'DEVICE-001', 'B区-2层-05号位', 2, 116.3980, 39.9100, 
 UNIX_TIMESTAMP(NOW()) * 1000, 1, '设备搬迁：A区-1层 -> B区-2层');
```

### 3.3 查询示例

#### 查询当前生效的配置

```sql
-- 查询设备当前网络配置
SELECT * FROM device_network_config 
WHERE device_id = 'DEVICE-001' 
  AND is_active = 1;

-- 查询设备当前位置
SELECT * FROM device_location 
WHERE device_id = 'DEVICE-001' 
  AND is_active = 1;
```

#### 查询配置历史

```sql
-- 查询设备网络配置历史（按时间倒序）
SELECT * FROM device_network_config 
WHERE device_id = 'DEVICE-001' 
ORDER BY effective_start_ts DESC;

-- 查询设备位置历史（按时间倒序）
SELECT * FROM device_location 
WHERE device_id = 'DEVICE-001' 
ORDER BY effective_start_ts DESC;
```

#### 查询指定时间点的配置

```sql
-- 查询设备在指定时间点的网络配置
SELECT * FROM device_network_config 
WHERE device_id = 'DEVICE-001' 
  AND effective_start_ts <= 1609459200000  -- 2021-01-01 00:00:00
  AND (effective_end_ts IS NULL OR effective_end_ts > 1609459200000);

-- 查询设备在指定时间点的位置
SELECT * FROM device_location 
WHERE device_id = 'DEVICE-001' 
  AND effective_start_ts <= 1609459200000
  AND (effective_end_ts IS NULL OR effective_end_ts > 1609459200000);
```

## 四、索引设计

### 4.1 device_network_config 索引

```sql
-- 租户索引
CREATE INDEX idx_network_config_tenant ON device_network_config (tenant_id);

-- 设备索引
CREATE INDEX idx_network_config_device ON device_network_config (device_id);

-- 当前生效配置查询索引（最常用）
CREATE INDEX idx_network_config_active ON device_network_config (device_id, is_active);

-- 历史版本查询索引
CREATE INDEX idx_network_config_effective ON device_network_config (device_id, effective_start_ts);

-- IP地址查询索引
CREATE INDEX idx_network_config_ip ON device_network_config (ip_address);
```

### 4.2 device_location 索引

```sql
-- 租户索引
CREATE INDEX idx_location_tenant ON device_location (tenant_id);

-- 设备索引
CREATE INDEX idx_location_device ON device_location (device_id);

-- 当前生效位置查询索引（最常用）
CREATE INDEX idx_location_active ON device_location (device_id, is_active);

-- 历史版本查询索引
CREATE INDEX idx_location_effective ON device_location (device_id, effective_start_ts);

-- 位置编码查询索引
CREATE INDEX idx_location_code ON device_location (location_code);

-- 楼层查询索引
CREATE INDEX idx_location_floor ON device_location (floor_no);

-- 区域查询索引
CREATE INDEX idx_location_area ON device_location (area_code);

-- 坐标查询索引（用于地图查询）
CREATE INDEX idx_location_coords ON device_location (longitude, latitude);
```

## 五、数据一致性保证

### 5.1 唯一性约束

**问题**：如何确保每个设备只有一个当前生效的配置？

**方案 A：应用层保证**（当前采用）
- 创建新配置前，先将旧配置标记为历史
- 通过 `is_active = 1` 查询当前生效配置

**方案 B：数据库约束**（MySQL 8.0+）
```sql
-- 使用部分唯一索引（MySQL 8.0+）
CREATE UNIQUE INDEX uk_network_config_active 
    ON device_network_config (device_id) 
    WHERE is_active = 1;

CREATE UNIQUE INDEX uk_location_active 
    ON device_location (device_id) 
    WHERE is_active = 1;
```

**方案 C：触发器保证**（MySQL 5.7）
```sql
-- 创建新配置前，自动将旧配置标记为历史
DELIMITER $$
CREATE TRIGGER trg_network_config_before_insert
BEFORE INSERT ON device_network_config
FOR EACH ROW
BEGIN
    IF NEW.is_active = 1 THEN
        UPDATE device_network_config 
        SET effective_end_ts = NEW.effective_start_ts,
            is_active = 0
        WHERE device_id = NEW.device_id 
          AND is_active = 1;
    END IF;
END$$
DELIMITER ;
```

### 5.2 时间戳验证

**问题**：如何确保时间戳的连续性？

**验证规则**：
1. 新配置的 `effective_start_ts` 应该 >= 旧配置的 `effective_end_ts`
2. 如果旧配置的 `effective_end_ts` 为 NULL，应该先设置为新配置的 `effective_start_ts`

**应用层逻辑**：
```sql
-- 伪代码示例
BEGIN TRANSACTION;

-- 1. 获取当前生效配置
SELECT effective_start_ts, effective_end_ts 
INTO @old_start_ts, @old_end_ts
FROM device_network_config 
WHERE device_id = @device_id AND is_active = 1;

-- 2. 设置新配置的开始时间
SET @new_start_ts = UNIX_TIMESTAMP(NOW()) * 1000;

-- 3. 更新旧配置的结束时间
UPDATE device_network_config 
SET effective_end_ts = @new_start_ts,
    is_active = 0
WHERE device_id = @device_id AND is_active = 1;

-- 4. 创建新配置
INSERT INTO device_network_config 
(..., effective_start_ts, is_active)
VALUES 
(..., @new_start_ts, 1);

COMMIT;
```

## 六、迁移方案

### 6.1 数据迁移

如果已有 `device_config` 表数据，需要迁移到新表：

```sql
-- 1. 迁移网络配置数据
INSERT INTO device_network_config 
(id, tenant_id, device_id, ip_address, port, mac_address, gateway, subnet_mask, protocol, connection_params, 
 effective_start_ts, is_active, creator, create_time, updater, update_time)
SELECT 
    UUID() as id,
    tenant_id,
    device_id,
    ip_address,
    port,
    mac_address,
    gateway,
    subnet_mask,
    protocol,
    connection_params,
    UNIX_TIMESTAMP(create_time) * 1000 as effective_start_ts,
    1 as is_active,
    creator,
    create_time,
    updater,
    update_time
FROM device_config
WHERE ip_address IS NOT NULL OR port IS NOT NULL OR protocol IS NOT NULL;

-- 2. 迁移位置信息数据
INSERT INTO device_location 
(id, tenant_id, device_id, location_code, location_description, coordinates, 
 effective_start_ts, is_active, creator, create_time, updater, update_time)
SELECT 
    UUID() as id,
    tenant_id,
    device_id,
    location_code,
    location_description,
    coordinates,
    UNIX_TIMESTAMP(create_time) * 1000 as effective_start_ts,
    1 as is_active,
    creator,
    create_time,
    updater,
    update_time
FROM device_config
WHERE location_code IS NOT NULL OR coordinates IS NOT NULL;
```

### 6.2 应用层适配

**查询适配**：
```sql
-- 原查询（device_config）
SELECT * FROM device_config WHERE device_id = 'DEVICE-001';

-- 新查询（device_network_config + device_location）
SELECT 
    di.*,
    net.*,
    loc.*
FROM device_info di
LEFT JOIN device_network_config net ON di.id = net.device_id AND net.is_active = 1
LEFT JOIN device_location loc ON di.id = loc.device_id AND loc.is_active = 1
WHERE di.id = 'DEVICE-001';
```

## 七、优势总结

### 7.1 业务优势

1. ✅ **职责清晰**：网络配置和位置信息分离，便于独立管理
2. ✅ **历史追溯**：支持配置和位置变更历史查询
3. ✅ **灵活扩展**：可以独立扩展网络配置或位置信息功能

### 7.2 技术优势

1. ✅ **查询优化**：可以针对不同业务场景优化索引
2. ✅ **数据一致性**：通过时间戳管理历史版本，数据一致性好
3. ✅ **性能优化**：通过 `is_active` 索引快速查询当前配置

### 7.3 维护优势

1. ✅ **独立维护**：网络配置和位置信息可以独立维护
2. ✅ **版本管理**：支持配置和位置的版本管理
3. ✅ **审计追溯**：可以追溯配置和位置的变更历史

## 八、注意事项

### 8.1 数据一致性

1. ⚠️ **确保唯一性**：每个设备只能有一个当前生效的配置/位置
2. ⚠️ **时间戳连续性**：确保历史版本的时间戳连续
3. ⚠️ **应用层保证**：通过应用层逻辑保证数据一致性

### 8.2 查询性能

1. ✅ **使用索引**：查询当前配置时使用 `is_active` 索引
2. ✅ **避免全表扫描**：历史查询时使用 `effective_start_ts` 索引
3. ✅ **冗余字段**：位置信息提取常用字段为独立列，便于查询

### 8.3 业务逻辑

1. ⚠️ **配置变更**：创建新配置前，必须先将旧配置标记为历史
2. ⚠️ **位置变更**：创建新位置前，必须先将旧位置标记为历史
3. ⚠️ **时间戳管理**：使用毫秒级时间戳，确保精度

## 九、总结

### 9.1 拆分结果

- ✅ `device_config` → `device_network_config` + `device_location`
- ✅ 两张表都支持历史版本功能
- ✅ 职责清晰，便于维护

### 9.2 关键特性

- ✅ **历史版本支持**：通过时间戳管理历史版本
- ✅ **当前配置查询**：通过 `is_active` 快速查询
- ✅ **历史追溯**：支持查询任意时间点的配置

### 9.3 使用建议

1. ✅ 查询当前配置：使用 `is_active = 1` 条件
2. ✅ 查询历史版本：使用 `effective_start_ts` 和 `effective_end_ts`
3. ✅ 创建新配置：先更新旧配置，再创建新配置

