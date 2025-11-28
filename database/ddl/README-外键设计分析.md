# 外键设计分析：id vs device_code

## 一、问题分析

### 1.1 当前设计

**device_info 表**：
- `id` - 主键（CHAR(36)，UUID）
- `device_code` - 业务唯一标识（VARCHAR(100)，租户内唯一）

**device_network_config 和 device_location 表**：
- 当前使用 `device_id` 关联 `device_info.id`（主键）

### 1.2 问题

应该使用 `device_info.id`（主键）还是 `device_info.device_code`（业务键）作为外键？

## 二、方案对比

### 2.1 方案 A：使用主键 id（推荐 ✅）

```sql
-- device_network_config
device_id CHAR(36) NOT NULL,
CONSTRAINT fk_network_config_device 
    FOREIGN KEY (device_id) 
    REFERENCES device_info(id) 
    ON DELETE CASCADE

-- device_location
device_id CHAR(36) NOT NULL,
CONSTRAINT fk_location_device 
    FOREIGN KEY (device_id) 
    REFERENCES device_info(id) 
    ON DELETE CASCADE
```

### 2.2 方案 B：使用业务键 device_code（不推荐 ❌）

```sql
-- device_network_config
device_code VARCHAR(100) NOT NULL,
tenant_id CHAR(36) NOT NULL,
CONSTRAINT fk_network_config_device 
    FOREIGN KEY (tenant_id, device_code) 
    REFERENCES device_info(tenant_id, device_code) 
    ON DELETE CASCADE

-- device_location
device_code VARCHAR(100) NOT NULL,
tenant_id CHAR(36) NOT NULL,
CONSTRAINT fk_location_device 
    FOREIGN KEY (tenant_id, device_code) 
    REFERENCES device_info(tenant_id, device_code) 
    ON DELETE CASCADE
```

## 三、详细对比分析

### 3.1 稳定性对比

| 方面 | 主键 id | 业务键 device_code |
|------|---------|-------------------|
| **稳定性** | ✅ 绝对稳定，不会变化 | ⚠️ 可能因业务需求变更 |
| **唯一性** | ✅ 全局唯一 | ⚠️ 租户内唯一（需要组合键） |
| **变更频率** | ✅ 永不变更 | ⚠️ 可能变更（虽然不常变） |

**结论**：主键更稳定，适合作为外键。

### 3.2 性能对比

| 方面 | 主键 id | 业务键 device_code |
|------|---------|-------------------|
| **索引类型** | ✅ 主键索引（聚簇索引） | ⚠️ 唯一索引（非聚簇索引） |
| **查询性能** | ✅ 最优（直接定位） | ⚠️ 需要二次查找 |
| **JOIN 性能** | ✅ 最优 | ⚠️ 需要组合键 JOIN |
| **存储空间** | ✅ CHAR(36) | ⚠️ VARCHAR(100) + tenant_id |

**结论**：主键索引性能最优。

### 3.3 数据完整性对比

| 方面 | 主键 id | 业务键 device_code |
|------|---------|-------------------|
| **外键约束** | ✅ 简单（单列） | ⚠️ 复杂（组合键） |
| **级联删除** | ✅ 支持 | ✅ 支持 |
| **数据一致性** | ✅ 强一致性 | ⚠️ 如果 device_code 变更，需要级联更新 |

**结论**：主键外键约束更简单，数据一致性更好。

### 3.4 业务场景对比

#### 场景 1：device_code 变更

**使用主键 id**：
```sql
-- device_code 变更不影响外键关系
UPDATE device_info 
SET device_code = 'DEV-0002' 
WHERE id = 'DEVICE-001';

-- 外键关系保持不变
SELECT * FROM device_network_config 
WHERE device_id = 'DEVICE-001';  -- 仍然有效
```

**使用业务键 device_code**：
```sql
-- device_code 变更需要级联更新所有相关表
UPDATE device_info 
SET device_code = 'DEV-0002' 
WHERE device_code = 'DEV-0001';

-- 需要同时更新所有外键表
UPDATE device_network_config 
SET device_code = 'DEV-0002' 
WHERE device_code = 'DEV-0001';

UPDATE device_location 
SET device_code = 'DEV-0002' 
WHERE device_code = 'DEV-0001';
-- ... 其他表也需要更新
```

**结论**：使用主键可以避免级联更新的复杂性。

#### 场景 2：查询性能

**使用主键 id**：
```sql
-- 单列索引，性能最优
SELECT di.*, net.*
FROM device_info di
INNER JOIN device_network_config net ON di.id = net.device_id
WHERE di.device_code = 'DEV-0001';
-- 执行计划：先通过 device_code 索引找到 id，再通过 id 主键索引 JOIN
```

**使用业务键 device_code**：
```sql
-- 组合键索引，需要多列匹配
SELECT di.*, net.*
FROM device_info di
INNER JOIN device_network_config net 
    ON di.tenant_id = net.tenant_id 
    AND di.device_code = net.device_code
WHERE di.device_code = 'DEV-0001';
-- 执行计划：需要组合键索引匹配，性能略差
```

**结论**：使用主键查询性能更好。

## 四、通用做法和最佳实践

### 4.1 数据库设计规范

**通用原则**：
1. ✅ **外键应该引用主键**：这是数据库设计的基本规范
2. ✅ **主键是稳定的**：主键不应该变更，适合作为外键
3. ✅ **业务键可能变更**：业务键可能因业务需求变更，不适合作为外键

### 4.2 行业标准

**参考标准**：
- **SQL 标准**：外键应该引用主键或唯一键
- **数据库设计规范**：推荐使用主键作为外键
- **性能优化**：主键索引性能最优

### 4.3 实际案例

**常见做法**：
- `user_id` → `user.id`（主键）
- `order_id` → `order.id`（主键）
- `product_id` → `product.id`（主键）

**不常见做法**：
- `user_code` → `user.code`（业务键）
- `order_no` → `order.order_no`（业务键）

## 五、当前项目中的其他外键设计

### 5.1 现有外键设计

查看项目中其他表的外键设计：

```sql
-- device_info 表的外键
device_model_id → device_model.id  -- ✅ 使用主键
factory_id → device_org_unit.id    -- ✅ 使用主键
workshop_id → device_org_unit.id   -- ✅ 使用主键
production_line_id → device_org_unit.id  -- ✅ 使用主键

-- device_state_record 表的外键
device_id → device_info.id  -- ✅ 使用主键

-- device_relation 表的外键
from_device_id → device_info.id  -- ✅ 使用主键
to_device_id → device_info.id    -- ✅ 使用主键
```

**结论**：项目中所有外键都使用主键，保持一致性。

### 5.2 设计一致性

**优势**：
- ✅ 设计模式统一
- ✅ 维护成本低
- ✅ 性能一致

**如果使用 device_code**：
- ❌ 破坏设计一致性
- ❌ 增加维护复杂度
- ❌ 性能不一致

## 六、特殊情况分析

### 6.1 什么时候可以使用业务键作为外键？

**适用场景**（不推荐，但可能）：
1. 业务键绝对稳定，永不变更
2. 业务键是全局唯一的（不需要组合键）
3. 业务键是整数类型（性能考虑）

**当前项目**：
- ❌ `device_code` 可能变更（虽然不常变）
- ❌ `device_code` 是租户内唯一（需要组合键）
- ❌ `device_code` 是 VARCHAR(100)（性能不如主键）

**结论**：当前项目不适合使用 `device_code` 作为外键。

### 6.2 如果必须使用 device_code 查询怎么办？

**方案**：使用主键作为外键，但保留 device_code 作为冗余字段（可选）

```sql
-- 方案：外键使用 id，但保留 device_code 作为冗余字段
CREATE TABLE device_network_config (
    id CHAR(36) PRIMARY KEY,
    device_id CHAR(36) NOT NULL,  -- 外键（主键）
    device_code VARCHAR(100),    -- 冗余字段（可选，用于查询优化）
    ...
    CONSTRAINT fk_network_config_device 
        FOREIGN KEY (device_id) 
        REFERENCES device_info(id) 
        ON DELETE CASCADE,
    INDEX idx_network_config_code (device_code)  -- 可选索引
);
```

**优点**：
- ✅ 外键使用主键（稳定、性能好）
- ✅ 可以按 device_code 查询（如果经常需要）

**缺点**：
- ⚠️ 需要维护数据一致性（device_code 变更时需要同步更新）

**建议**：当前项目不需要，因为可以通过 JOIN 查询。

## 七、最终建议

### 7.1 推荐方案：使用主键 id

**理由**：
1. ✅ **符合数据库设计规范**：外键应该引用主键
2. ✅ **稳定性好**：主键不会变更，外键关系稳定
3. ✅ **性能最优**：主键索引性能最好
4. ✅ **维护简单**：不需要级联更新
5. ✅ **设计一致**：与项目中其他外键设计一致

### 7.2 当前设计评估

**当前设计**：使用 `device_id` 关联 `device_info.id`

| 方面 | 评估 | 说明 |
|------|------|------|
| **外键选择** | ✅ **正确** | 使用主键 id |
| **设计规范** | ✅ **符合** | 符合数据库设计规范 |
| **性能** | ✅ **最优** | 主键索引性能最优 |
| **稳定性** | ✅ **稳定** | 主键不会变更 |
| **一致性** | ✅ **一致** | 与项目中其他外键一致 |

**结论**：当前设计是**正确的**，无需修改。

### 7.3 查询示例

**当前设计下的查询**：

```sql
-- 通过 device_code 查询设备及其配置
SELECT di.*, net.*, loc.*
FROM device_info di
LEFT JOIN device_network_config net ON di.id = net.device_id AND net.is_active = 1
LEFT JOIN device_location loc ON di.id = loc.device_id AND loc.is_active = 1
WHERE di.device_code = 'DEV-0001';

-- 执行计划：
-- 1. 通过 device_code 索引找到 device_info 记录（获取 id）
-- 2. 通过 id 主键索引 JOIN device_network_config（最优）
-- 3. 通过 id 主键索引 JOIN device_location（最优）
```

**性能分析**：
- ✅ 第一步：通过 `device_code` 唯一索引快速定位
- ✅ 第二步：通过 `id` 主键索引 JOIN（最优）
- ✅ 总体性能：最优

## 八、总结

### 8.1 核心结论

**外键应该使用 `device_info.id`（主键），而不是 `device_code`（业务键）**

### 8.2 关键要点

1. ✅ **主键更稳定**：主键不会变更，适合作为外键
2. ✅ **主键性能更好**：主键索引性能最优
3. ✅ **符合设计规范**：外键应该引用主键
4. ✅ **维护成本低**：不需要级联更新
5. ✅ **设计一致性**：与项目中其他外键设计一致

### 8.3 当前设计

**当前设计是正确的**：
- ✅ 使用 `device_id` 关联 `device_info.id`
- ✅ 符合数据库设计最佳实践
- ✅ 性能最优
- ✅ 无需修改

### 8.4 如果业务需要按 device_code 查询

**方案**：
- 通过 `device_code` 先查询 `device_info` 获取 `id`
- 再通过 `id` 查询相关配置表
- 或使用 JOIN 查询（性能最优）

**不需要**：
- ❌ 将外键改为 `device_code`
- ❌ 添加 `device_code` 冗余字段（除非查询频率极高）

