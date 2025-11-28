# 层级路径 path 字段说明

## 一、概述

`path` 字段采用**物化路径（Materialized Path）模式**，用于存储从根节点到当前节点的完整层级路径。这种设计可以高效地查询子节点和祖先节点，避免递归查询。

## 二、路径组成规则

### 2.1 基本格式

- **分隔符**：使用 `/`（斜杠）作为层级分隔符
- **起始符**：路径以 `/` 开头
- **路径元素**：使用 `unit_code`（组织单元编码）或 `type_code`（类型编码）作为路径元素
- **格式示例**：`/FACTORY_A/WORKSHOP_A1/LINE_A1_01`

### 2.2 为什么使用 unit_code 而不是 id？

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **unit_code**（业务编码） | ✅ 可读性好，便于调试<br>✅ 业务唯一标识，稳定<br>✅ 便于日志和排查 | ⚠️ 如果修改 unit_code 需要同步更新 path | ⭐⭐⭐⭐⭐ **推荐** |
| **id**（主键ID） | ✅ 绝对稳定，不会变化 | ❌ 可读性差（UUID 很长）<br>❌ 不便于调试和日志 | ⭐⭐⭐ |
| **unit_name**（名称） | ✅ 最直观 | ❌ 可能重复<br>❌ 名称可能修改 | ⭐⭐ |

**结论**：使用 `unit_code` 是最佳实践，因为：
1. `unit_code` 是业务唯一标识，通常不会频繁修改
2. 可读性好，便于开发和运维
3. 即使需要修改，也可以通过触发器或应用层逻辑同步更新 path

### 2.3 路径生成规则

#### 规则 1：根节点
- 根节点的 `parent_id` 为 `NULL`
- 根节点的 `path` = `/` + `unit_code`
- 示例：`/FACTORY_A`

#### 规则 2：子节点
- 子节点的 `path` = 父节点的 `path` + `/` + 当前节点的 `unit_code`
- 公式：`path = CONCAT(parent_path, '/', unit_code)`
- 示例：
  - 父节点：`/FACTORY_A`
  - 当前节点 `unit_code` = `WORKSHOP_A1`
  - 结果：`/FACTORY_A/WORKSHOP_A1`

#### 规则 3：多层级
- 逐级拼接，从根到叶子
- 示例：
  ```
  厂区：/FACTORY_A
  车间：/FACTORY_A/WORKSHOP_A1
  产线：/FACTORY_A/WORKSHOP_A1/LINE_A1_01
  ```

## 三、路径维护

### 3.1 创建节点时

**应用层逻辑**（推荐）：
```sql
-- 伪代码示例
IF parent_id IS NULL THEN
    SET path = CONCAT('/', unit_code);
ELSE
    SELECT path INTO parent_path FROM device_org_unit WHERE id = parent_id;
    SET path = CONCAT(parent_path, '/', unit_code);
END IF;
```

**数据库触发器**（备选）：
```sql
DELIMITER $$
CREATE TRIGGER trg_device_org_unit_path
BEFORE INSERT ON device_org_unit
FOR EACH ROW
BEGIN
    IF NEW.parent_id IS NULL THEN
        SET NEW.path = CONCAT('/', NEW.unit_code);
    ELSE
        SELECT path INTO @parent_path FROM device_org_unit WHERE id = NEW.parent_id;
        SET NEW.path = CONCAT(@parent_path, '/', NEW.unit_code);
    END IF;
END$$
DELIMITER ;
```

### 3.2 修改 unit_code 时

**重要**：如果修改了 `unit_code`，必须同步更新：
1. 当前节点的 `path`
2. 所有子节点的 `path`（级联更新）

**应用层逻辑**（推荐）：
```sql
-- 伪代码示例
-- 1. 更新当前节点 path
UPDATE device_org_unit 
SET path = CONCAT(IFNULL(parent_path, '/'), '/', new_unit_code)
WHERE id = current_id;

-- 2. 更新所有子节点 path（替换路径前缀）
UPDATE device_org_unit 
SET path = REPLACE(path, CONCAT(old_path, '/'), CONCAT(new_path, '/'))
WHERE path LIKE CONCAT(old_path, '/%');
```

### 3.3 移动节点时

**重要**：移动节点（修改 `parent_id`）时，必须：
1. 更新当前节点的 `path`
2. 更新所有子节点的 `path`（级联更新）

**应用层逻辑**：
```sql
-- 伪代码示例
-- 1. 获取新的父节点 path
SELECT path INTO new_parent_path FROM device_org_unit WHERE id = new_parent_id;

-- 2. 更新当前节点 path
UPDATE device_org_unit 
SET path = CONCAT(new_parent_path, '/', unit_code)
WHERE id = current_id;

-- 3. 更新所有子节点 path（替换路径前缀）
UPDATE device_org_unit 
SET path = REPLACE(path, old_path, new_path)
WHERE path LIKE CONCAT(old_path, '/%');
```

## 四、路径查询示例

### 4.1 查询所有子节点

```sql
-- 查询 FACTORY_A 下的所有子节点（包括车间、产线）
SELECT * FROM device_org_unit 
WHERE path LIKE '/FACTORY_A/%'
ORDER BY path;
```

### 4.2 查询直接子节点

```sql
-- 查询 FACTORY_A 的直接子节点（仅车间，不包括产线）
SELECT * FROM device_org_unit 
WHERE parent_id = 'FACTORY-A';
-- 或使用 path（需要知道层级深度）
SELECT * FROM device_org_unit 
WHERE path LIKE '/FACTORY_A/%' 
  AND path NOT LIKE '/FACTORY_A/%/%';  -- 排除二级子节点
```

### 4.3 查询所有祖先节点

```sql
-- 查询 LINE_A1_01 的所有祖先节点
-- 路径：/FACTORY_A/WORKSHOP_A1/LINE_A1_01
-- 需要解析路径，提取每个层级
SELECT * FROM device_org_unit 
WHERE unit_code IN ('FACTORY_A', 'WORKSHOP_A1', 'LINE_A1_01')
ORDER BY level_no;
```

### 4.4 查询同级节点

```sql
-- 查询与 WORKSHOP_A1 同级的其他车间（同一父节点下的其他节点）
SELECT * FROM device_org_unit 
WHERE parent_id = (SELECT parent_id FROM device_org_unit WHERE unit_code = 'WORKSHOP_A1')
  AND id != (SELECT id FROM device_org_unit WHERE unit_code = 'WORKSHOP_A1');
```

### 4.5 查询路径深度

```sql
-- 查询路径层级深度（通过计算分隔符数量）
SELECT 
    unit_code,
    path,
    (LENGTH(path) - LENGTH(REPLACE(path, '/', ''))) AS depth
FROM device_org_unit;
```

## 五、最佳实践

### 5.1 路径生成时机

- ✅ **创建时**：在 INSERT 时自动生成
- ✅ **修改 unit_code 时**：同步更新 path 和所有子节点
- ✅ **移动节点时**：同步更新 path 和所有子节点

### 5.2 路径验证

- ✅ 确保路径以 `/` 开头
- ✅ 确保路径元素与 `unit_code` 一致
- ✅ 确保路径与 `parent_id` 关系一致
- ✅ 确保路径层级与 `level_no` 一致

### 5.3 性能优化

- ✅ 在 `path` 字段上创建索引（已创建：`idx_device_org_unit_path`）
- ✅ 使用 `LIKE 'path%'` 查询时，MySQL 可以利用索引前缀匹配
- ✅ 避免使用 `LIKE '%path%'`（无法使用索引）

## 六、示例数据

```sql
-- 示例：三层级组织结构
INSERT INTO device_org_unit (id, tenant_id, unit_code, unit_name, unit_type_id, parent_id, level_no, path)
VALUES 
    -- 厂区（根节点）
    ('FACTORY-A', @TENANT_ID, 'FACTORY_A', 'A 厂区', @DICT_FACTORY, NULL, 1, '/FACTORY_A'),
    
    -- 车间（二级节点）
    ('WORKSHOP-A1', @TENANT_ID, 'WORKSHOP_A1', 'A1 车间', @DICT_WORKSHOP, 'FACTORY-A', 2, '/FACTORY_A/WORKSHOP_A1'),
    ('WORKSHOP-A2', @TENANT_ID, 'WORKSHOP_A2', 'A2 车间', @DICT_WORKSHOP, 'FACTORY-A', 2, '/FACTORY_A/WORKSHOP_A2'),
    
    -- 产线（三级节点）
    ('LINE-A1-01', @TENANT_ID, 'LINE_A1_01', 'A1-01 产线', @DICT_LINE, 'WORKSHOP-A1', 3, '/FACTORY_A/WORKSHOP_A1/LINE_A1_01'),
    ('LINE-A1-02', @TENANT_ID, 'LINE_A1_02', 'A1-02 产线', @DICT_LINE, 'WORKSHOP-A1', 3, '/FACTORY_A/WORKSHOP_A1/LINE_A1_02'),
    ('LINE-A2-01', @TENANT_ID, 'LINE_A2_01', 'A2-01 产线', @DICT_LINE, 'WORKSHOP-A2', 3, '/FACTORY_A/WORKSHOP_A2/LINE_A2_01');
```

## 七、注意事项

1. **路径长度限制**：`path` 字段定义为 `VARCHAR(500)`，确保足够存储深层级路径
2. **路径唯一性**：路径在租户内应该是唯一的（通过 `unit_code` 唯一性保证）
3. **路径一致性**：确保 `path` 与 `parent_id` 关系保持一致，避免数据不一致
4. **级联更新**：修改 `unit_code` 或移动节点时，必须级联更新所有子节点的 `path`

## 八、参考

- **物化路径模式**：Materialized Path Pattern
- **层级数据存储**：Hierarchical Data Storage
- **MySQL 索引优化**：Prefix Index on VARCHAR

