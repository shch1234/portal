-- ============================================================
-- 威力 IoT Portal - MySQL 设备基础数据初始化脚本
-- 说明：
--   1. 先根据环境替换占位符：:TENANT_ID
--   2. 包含字典数据、组织结构、设备类型、型号、设备示例
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 字典数据（system_dict_type / system_dict_data）
-- ============================================================
INSERT INTO system_dict_type (id, name, type, status, remark)
VALUES 
    (1001, '组织单元类型', 'org_unit_type', 0, '厂区/车间/产线'),
    (1002, '设备主类型', 'device_type', 0, '主类型枚举'),
    (1003, '设备子类型', 'device_sub_type', 0, '子类型枚举')
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status), remark = VALUES(remark);

INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    (1101, 1, '厂区', 'FACTORY', 'org_unit_type', 0, ''),
    (1102, 2, '车间', 'WORKSHOP', 'org_unit_type', 0, ''),
    (1103, 3, '产线', 'PRODUCTION_LINE', 'org_unit_type', 0, '')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);

-- 设备主类型（device_type）
INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    (601, 1, '机床', 'MACHINE_TOOL', 'device_type', 0, ''),
    (602, 2, '机器人', 'ROBOT', 'device_type', 0, ''),
    (603, 3, 'PLC', 'PLC', 'device_type', 0, '')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);

INSERT INTO system_dict_type (id, name, type, status, remark)
VALUES (148, '设备子类型', 'device_sub_type', 0, '设备子类型');

-- 设备子类型（device_sub_type）- 技术类型分类（主要用于机床、机器人等）
INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    -- 机床子类型
    (701, 1, '五轴铣车中心', 'CNC_5AXIS', 'device_sub_type', 0, '机床-五轴铣车中心'),
    (702, 2, '立式加工中心', 'CNC_VERTICAL', 'device_sub_type', 0, '机床-立式加工中心'),
    (703, 3, '卧式加工中心', 'CNC_HORIZONTAL', 'device_sub_type', 0, '机床-卧式加工中心'),
    (704, 4, '车床', 'LATHE', 'device_sub_type', 0, '机床-车床'),
    (705, 5, '铣床', 'MILLING', 'device_sub_type', 0, '机床-铣床'),
    (706, 6, '磨床', 'GRINDING', 'device_sub_type', 0, '机床-磨床'),
    -- 机器人子类型
    (801, 7, 'Fanuc机器人', 'FANUC_ROBOT', 'device_sub_type', 0, '机器人-发那科机器人'),
    (802, 8, 'ABB机器人', 'ABB_ROBOT', 'device_sub_type', 0, '机器人-ABB机器人'),
    (803, 9, '广数机器人', 'GS_ROBOT', 'device_sub_type', 0, '机器人-广数机器人'),
    -- PLC子类型
    (901, 10, '西门子', 'SIEMENS', 'device_sub_type', 0, 'PLC-西门子'),
    (902, 11, '三菱', 'MITSUBISHI', 'device_sub_type', 0, 'PLC-三菱'),
    (903, 12, '欧姆龙', 'OMRON', 'device_sub_type', 0, 'PLC-欧姆龙'),
    (904, 13, '施耐德', 'SCHNEIDER', 'device_sub_type', 0, 'PLC-施耐德'),
    (905, 14, 'ABB', 'ABB', 'device_sub_type', 0, 'PLC-ABB'),
    (906, 15, '台达', 'DELTA', 'device_sub_type', 0, 'PLC-台达')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);

-- 设备品牌（device_brand）- 品牌分类（主要用于PLC、变频器等按品牌分类的设备）
INSERT INTO system_dict_type (id, name, type, status, remark)
VALUES 
    (1006, '设备品牌', 'device_brand', 0, '设备品牌字典')
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status), remark = VALUES(remark);

INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    -- PLC品牌
    (1601, 1, '西门子', 'SIEMENS', 'device_brand', 0, ''),
    (1602, 2, '三菱', 'MITSUBISHI', 'device_brand', 0, ''),
    (1603, 3, '欧姆龙', 'OMRON', 'device_brand', 0, ''),
    (1604, 4, '施耐德', 'SCHNEIDER', 'device_brand', 0, ''),
    (1605, 5, 'ABB', 'ABB', 'device_brand', 0, ''),
    (1606, 6, '台达', 'DELTA', 'device_brand', 0, ''),
    -- 机床品牌
    (1607, 7, 'DMG MORI', 'DMG_MORI', 'device_brand', 0, ''),
    (1608, 8, 'FANUC', 'FANUC', 'device_brand', 0, ''),
    (1609, 9, '海德汉', 'HEIDENHAIN', 'device_brand', 0, ''),
    (1610, 10, '纽威数控', 'NEWAY', 'device_brand', 0, '')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);

-- ============================================================
-- 1.2 工厂字典数据（factory_code）
-- ============================================================
INSERT INTO system_dict_type (id, name, type, status, remark)
VALUES (201, '工厂编码', 'factory_code', 0, '工厂字典编码');

INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    (201, 1, '威力传动工厂', '1100', 'factory_code', 0, ''),
    (202, 2, '威力增速器工厂', '1300', 'factory_code', 0, ''),
    (203, 3, '威力无价值工厂', '1199', 'factory_code', 0, ''),
    (204, 4, '威马电机工厂', '2100', 'factory_code', 0, ''),
    (205, 5, '威马无价值工厂', '2199', 'factory_code', 0, ''),
    (206, 6, '威润传动工厂', '4100', 'factory_code', 0, ''),
    (207, 7, '威润无价值工厂', '4199', 'factory_code', 0, ''),
    (208, 8, '威驰传动工厂', '5100', 'factory_code', 0, ''),
    (209, 9, '威驰传动无价值工厂', '5199', 'factory_code', 0, '')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);

-- ============================================================
-- 1.3 车间字典数据（workshop_code）
-- ============================================================
INSERT INTO system_dict_type (id, name, type, status, remark)
VALUES (202, '车间编码', 'workshop_code', 0, '车间字典编码');

INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    (301, 1, '机加车间', 'Z01', 'workshop_code', 0, ''),
    (302, 2, '装配车间', 'Z02', 'workshop_code', 0, ''),
    (303, 3, '后处理车间', 'Z03', 'workshop_code', 0, ''),
    (304, 4, '总装车间', 'Z04', 'workshop_code', 0, ''),
    (305, 5, '电气车间', 'Z05', 'workshop_code', 0, ''),
    (306, 6, '产品维修车间', 'Z06', 'workshop_code', 0, ''),
    (307, 7, '扁线车间', 'Z07', 'workshop_code', 0, ''),
    (308, 8, '增速器车间', 'Z71', 'workshop_code', 0, ''),
    (309, 9, '增速器机加车间', 'Z72', 'workshop_code', 0, ''),
    (310, 10, '威润后处理车间', 'Z41', 'workshop_code', 0, ''),
    (311, 11, '试验车间', 'Z99', 'workshop_code', 0, '')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);

-- ============================================================
-- 1.4 产线字典数据（production_line_code）
-- ============================================================
INSERT INTO system_dict_type (id, name, type, status, remark)
VALUES (203, '产线编码', 'production_line_code', 0, '产线字典编码');

INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    (401, 1, '机加产线01', 'Z01-01', 'production_line_code', 0, ''),
    (402, 2, '机加产线02', 'Z01-02', 'production_line_code', 0, ''),
    (403, 3, '装配产线01', 'Z02-01', 'production_line_code', 0, ''),
    (404, 4, '装配产线02', 'Z02-02', 'production_line_code', 0, ''),
    (405, 5, '增速器机加产线01', 'Z72-01', 'production_line_code', 0, '')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);


-- ============================================================
-- 2. 设备组织结构（厂区 → 车间 → 产线）
-- 说明：组织单元为全局共享配置，不按租户隔离
-- ============================================================

-- 厂区（level_no=1）
-- 说明：ID 使用雪花算法生成的 bigint 类型，格式：1000000000000000000 + 工厂编码
INSERT INTO device_org_relation (id, unit_code, unit_name, unit_type_value, org_parent_id, level_no, path, is_active, sort_order)
VALUES 
    -- 威力传动工厂
    (1000000000000001100, '1100', '威力传动工厂', 'FACTORY', NULL, 1, '/1100', 1, 1),
    -- 威力增速器工厂
    (1000000000000001300, '1300', '威力增速器工厂', 'FACTORY', NULL, 1, '/1300', 1, 2),
    -- 威力无价值工厂
    (1000000000000001199, '1199', '威力无价值工厂', 'FACTORY', NULL, 1, '/1199', 1, 3),
    -- 威马电机工厂
    (1000000000000002100, '2100', '威马电机工厂', 'FACTORY', NULL, 1, '/2100', 1, 4),
    -- 威马无价值工厂
    (1000000000000002199, '2199', '威马无价值工厂', 'FACTORY', NULL, 1, '/2199', 1, 5),
    -- 威润传动工厂
    (1000000000000004100, '4100', '威润传动工厂', 'FACTORY', NULL, 1, '/4100', 1, 6),
    -- 威润无价值工厂
    (1000000000000004199, '4199', '威润无价值工厂', 'FACTORY', NULL, 1, '/4199', 1, 7),
    -- 威驰传动工厂
    (1000000000000005100, '5100', '威驰传动工厂', 'FACTORY', NULL, 1, '/5100', 1, 8),
    -- 威驰传动无价值工厂
    (1000000000000005199, '5199', '威驰传动无价值工厂', 'FACTORY', NULL, 1, '/5199', 1, 9)
ON DUPLICATE KEY UPDATE unit_name = VALUES(unit_name), unit_type_value = VALUES(unit_type_value), org_parent_id = VALUES(org_parent_id), path = VALUES(path);

-- 车间（level_no=2）
-- 说明：ID 使用雪花算法生成的 bigint 类型，格式：2000000000000000000 + 车间编码数值部分
INSERT INTO device_org_relation (id, unit_code, unit_name, unit_type_value, org_parent_id, level_no, path, is_active, sort_order)
VALUES 
    -- 威力传动工厂(1100) 下的车间：Z01-Z07
    (2000000000000000001, 'Z01', '机加车间', 'WORKSHOP', 1000000000000001100, 2, '/1100/Z01', 1, 1),
    (2000000000000000002, 'Z02', '装配车间', 'WORKSHOP', 1000000000000001100, 2, '/1100/Z02', 1, 2),
    (2000000000000000003, 'Z03', '后处理车间', 'WORKSHOP', 1000000000000001100, 2, '/1100/Z03', 1, 3),
    (2000000000000000004, 'Z04', '总装车间', 'WORKSHOP', 1000000000000001100, 2, '/1100/Z04', 1, 4),
    (2000000000000000005, 'Z05', '电气车间', 'WORKSHOP', 1000000000000001100, 2, '/1100/Z05', 1, 5),
    (2000000000000000006, 'Z06', '产品维修车间', 'WORKSHOP', 1000000000000001100, 2, '/1100/Z06', 1, 6),
    (2000000000000000007, 'Z07', '扁线车间', 'WORKSHOP', 1000000000000001100, 2, '/1100/Z07', 1, 7),
    -- 威力增速器工厂(1300) 下的车间：Z71、Z72
    (2000000000000000071, 'Z71', '增速器车间', 'WORKSHOP', 1000000000000001300, 2, '/1300/Z71', 1, 1),
    (2000000000000000072, 'Z72', '增速器机加车间', 'WORKSHOP', 1000000000000001300, 2, '/1300/Z72', 1, 2)
ON DUPLICATE KEY UPDATE unit_name = VALUES(unit_name), unit_type_value = VALUES(unit_type_value), org_parent_id = VALUES(org_parent_id), path = VALUES(path);

-- 产线（level_no=3）- 示例数据
-- 说明：ID 使用雪花算法生成的 bigint 类型，格式：3000000000000000000 + 序号
INSERT INTO device_org_relation (id, unit_code, unit_name, unit_type_value, org_parent_id, level_no, path, is_active, sort_order)
VALUES 
    -- 机加车间下的产线
    (3000000000000000001, 'Z01-01', '机加产线01', 'PRODUCTION_LINE', 2000000000000000001, 3, '/1100/Z01/Z01-01', 1, 1),
    (3000000000000000002, 'Z01-02', '机加产线02', 'PRODUCTION_LINE', 2000000000000000001, 3, '/1100/Z01/Z01-02', 1, 2),
    -- 装配车间下的产线
    (3000000000000000003, 'Z02-01', '装配产线01', 'PRODUCTION_LINE', 2000000000000000002, 3, '/1100/Z02/Z02-01', 1, 1),
    (3000000000000000004, 'Z02-02', '装配产线02', 'PRODUCTION_LINE', 2000000000000000002, 3, '/1100/Z02/Z02-02', 1, 2),
    -- 增速器机加车间下的产线
    (3000000000000000005, 'Z72-01', '增速器机加产线01', 'PRODUCTION_LINE', 2000000000000000072, 3, '/1300/Z72/Z72-01', 1, 1)
ON DUPLICATE KEY UPDATE unit_name = VALUES(unit_name), unit_type_value = VALUES(unit_type_value), org_parent_id = VALUES(org_parent_id), path = VALUES(path);

-- ============================================================
-- 3. 设备类型实例（device_type_relation）
-- 说明：设备类型为全局共享配置，不按租户隔离
-- ID 使用雪花算法生成的 bigint 类型，格式：6000000000000000000 + 序号
-- 
-- 分类方式说明：
--   方式1（技术类型分类）：主类型 -> 技术子类型 -> 具体型号
--     例如：机床 -> 五轴铣车中心 -> CNC_5AXIS
--   方式2（品牌分类）：主类型 -> 品牌 -> 具体型号
--     例如：PLC -> 西门子 -> s7-1200
-- ============================================================
-- Level 1: 主类型
INSERT INTO device_type_relation (id, type_code, type_dict_value, parent_type_id, parent_type_code, parent_dict_value, level_no, path, category, description, is_active, sort_order)
VALUES 
    (6000000000000000001, 'MACHINE_TOOL', 'MACHINE_TOOL',  NULL, NULL, NULL, 1, '/MACHINE_TOOL', 'MACHINE_TOOL', '机床主类型',        1, 1),
    (6000000000000000002, 'ROBOT',        'ROBOT',         NULL, NULL, NULL, 1, '/ROBOT',        'ROBOT',        '机器人主类型',      1, 2),
    (6000000000000000003, 'PLC',          'PLC',           NULL, NULL, NULL, 1, '/PLC',          'PLC',          'PLC 主类型',        1, 3)
ON DUPLICATE KEY UPDATE category = VALUES(category), description = VALUES(description), type_dict_value = VALUES(type_dict_value);

-- Level 2: 子类型
-- 说明：
--   机床：按技术类型分类（CNC_5AXIS, CNC_VERTICAL等）
--   机器人：按品牌分类（FANUC_ROBOT, ABB_ROBOT等）
--   PLC：按品牌分类（SIEMENS, MITSUBISHI等）
INSERT INTO device_type_relation (id, type_code, type_dict_value, parent_type_id, parent_type_code, parent_dict_value, level_no, path, category, description, is_active, sort_order)
VALUES 
    -- 机床子类型（技术类型分类）
    (6000000000000000011, 'CNC_5AXIS',        'CNC_5AXIS', 6000000000000000001, 'MACHINE_TOOL', 'MACHINE_TOOL', 2, '/MACHINE_TOOL/CNC_5AXIS',        'MACHINE_TOOL', '五轴铣车中心', 1, 1),
    (6000000000000000012, 'CNC_VERTICAL',     'CNC_VERTICAL', 6000000000000000001, 'MACHINE_TOOL', 'MACHINE_TOOL', 2, '/MACHINE_TOOL/CNC_VERTICAL',      'MACHINE_TOOL', '立式加工中心', 1, 2),
    (6000000000000000013, 'CNC_HORIZONTAL',   'CNC_HORIZONTAL', 6000000000000000001, 'MACHINE_TOOL', 'MACHINE_TOOL', 2, '/MACHINE_TOOL/CNC_HORIZONTAL',    'MACHINE_TOOL', '卧式加工中心', 1, 3),
    (6000000000000000014, 'LATHE',            'LATHE', 6000000000000000001, 'MACHINE_TOOL', 'MACHINE_TOOL', 2, '/MACHINE_TOOL/LATHE',            'MACHINE_TOOL', '车床', 1, 4),
    (6000000000000000015, 'MILLING',          'MILLING', 6000000000000000001, 'MACHINE_TOOL', 'MACHINE_TOOL', 2, '/MACHINE_TOOL/MILLING',          'MACHINE_TOOL', '铣床', 1, 5),
    (6000000000000000016, 'GRINDING',         'GRINDING', 6000000000000000001, 'MACHINE_TOOL', 'MACHINE_TOOL', 2, '/MACHINE_TOOL/GRINDING',         'MACHINE_TOOL', '磨床', 1, 6),
    -- 机器人子类型（品牌分类）
    (6000000000000000021, 'FANUC_ROBOT',      'FANUC_ROBOT', 6000000000000000002, 'ROBOT', 'ROBOT', 2, '/ROBOT/FANUC_ROBOT',      'ROBOT', 'Fanuc机器人', 1, 1),
    (6000000000000000022, 'ABB_ROBOT',        'ABB_ROBOT', 6000000000000000002, 'ROBOT', 'ROBOT', 2, '/ROBOT/ABB_ROBOT',        'ROBOT', 'ABB机器人', 1, 2),
    (6000000000000000023, 'GS_ROBOT',         'GS_ROBOT', 6000000000000000002, 'ROBOT', 'ROBOT', 2, '/ROBOT/GS_ROBOT',         'ROBOT', '广数机器人', 1, 3),
    -- PLC子类型（品牌分类）
    (6000000000000000031, 'SIEMENS',          'SIEMENS', 6000000000000000003, 'PLC', 'PLC', 2, '/PLC/SIEMENS',          'PLC', 'PLC-西门子', 1, 1),
    (6000000000000000032, 'MITSUBISHI',       'MITSUBISHI', 6000000000000000003, 'PLC', 'PLC', 2, '/PLC/MITSUBISHI',       'PLC', 'PLC-三菱', 1, 2),
    (6000000000000000033, 'OMRON',            'OMRON', 6000000000000000003, 'PLC', 'PLC', 2, '/PLC/OMRON',            'PLC', 'PLC-欧姆龙', 1, 3),
    (6000000000000000034, 'SCHNEIDER',        'SCHNEIDER', 6000000000000000003, 'PLC', 'PLC', 2, '/PLC/SCHNEIDER',        'PLC', 'PLC-施耐德', 1, 4),
    (6000000000000000035, 'ABB',              'ABB', 6000000000000000003, 'PLC', 'PLC', 2, '/PLC/ABB',              'PLC', 'PLC-ABB', 1, 5),
    (6000000000000000036, 'DELTA',            'DELTA', 6000000000000000003, 'PLC', 'PLC', 2, '/PLC/DELTA',            'PLC', 'PLC-台达', 1, 6)
ON DUPLICATE KEY UPDATE category = VALUES(category), description = VALUES(description),
    parent_type_id = VALUES(parent_type_id), path = VALUES(path), type_dict_value = VALUES(type_dict_value), parent_dict_value = VALUES(parent_dict_value);

-- ============================================================
-- 4. 设备型号与设备示例
-- 说明：设备型号为全局共享配置，不按租户隔离；device_info 通过 tb_device_id 从 ThingsBoard 获取租户信息
-- device_type_code 关联到 device_type_relation.type_code（Level 2 的子类型）
-- ============================================================
-- 设备型号 ID 使用雪花算法生成的 bigint 类型，格式：4000000000000000000 + 序号
INSERT INTO device_model (id, model_code, model_name, device_type_code, manufacturer, specifications, type_specific_attrs, is_active)
VALUES 
    -- 机床型号示例（技术类型分类）
    -- 机床/五轴铣车中心/CNC_5AXIS -> device_type_code = 'CNC_5AXIS'
    -- 增速器机加设备型号（根据统计表）
    -- 数控立式车床 CK5112*10/5
    (4000000000000000005, 'CK5112', 'CK5112*10/5 数控立式车床', 'LATHE',
     '通用技术齐齐哈尔二机机床有限公司',
     JSON_OBJECT('specification', 'CK5112*10/5'),
     JSON_OBJECT('cncSystem', 'SINUMERIK 828D', 'cncBrand', 'SIEMENS', 'systemType', 'CNC'),
     1),
    -- 数控卧式车床 NL1250H
    (4000000000000000006, 'NL1250H', 'NL1250H 数控卧式车床', 'LATHE',
     '纽威数控装备(苏州)股份有限公司',
     JSON_OBJECT('specification', 'NL1250H'),
     JSON_OBJECT('cncSystem', 'SINUMERIK 828D', 'cncBrand', 'SIEMENS', 'systemType', 'CNC'),
     1),
    -- 数控立式车床 CK5120F
    (4000000000000000007, 'CK5120F', 'CK5120F 数控立式车床', 'LATHE',
     '通用技术齐齐哈尔二机机床有限公司',
     JSON_OBJECT('specification', 'CK5120F'),
     JSON_OBJECT('cncSystem', 'SINUMERIK 828D', 'cncBrand', 'SIEMENS', 'systemType', 'CNC'),
     1),
    -- 数控立式车床 CK5235F
    (4000000000000000008, 'CK5235F', 'CK5235F 数控立式车床', 'LATHE',
     '通用技术齐齐哈尔二机机床有限公司',
     JSON_OBJECT('specification', 'CK5235F'),
     JSON_OBJECT('cncSystem', 'SINUMERIK 828D', 'cncBrand', 'SIEMENS', 'systemType', 'CNC'),
     1),
    -- 数控卧式车床 NL502SA
    (4000000000000000009, 'NL502SA', 'NL502SA 数控卧式车床', 'LATHE',
     '纽威数控装备(苏州)股份有限公司',
     JSON_OBJECT('specification', 'NL502SA'),
     JSON_OBJECT('cncSystem', 'FANUC i seris', 'cncBrand', 'FANUC', 'systemType', 'CNC'),
     1),
    -- 数控立式车床 V4C
    (4000000000000000010, 'V4C', 'V4C 数控立式车床', 'LATHE',
     '通用技术集团沈阳机床有限责任公司',
     JSON_OBJECT('specification', 'V4C'),
     JSON_OBJECT('cncSystem', 'Series Oi-TF PLUS', 'cncBrand', 'FANUC', 'systemType', 'CNC'),
     1),
    -- 数控立式车床 V6C
    (4000000000000000011, 'V6C', 'V6C 数控立式车床', 'LATHE',
     '通用技术集团沈阳机床有限责任公司',
     JSON_OBJECT('specification', 'V6C'),
     JSON_OBJECT('cncSystem', 'Series Oi-TF PLUS', 'cncBrand', 'FANUC', 'systemType', 'CNC'),
     1),
    -- 数控立式车床 V6S
    (4000000000000000012, 'V6S', 'V6S 数控立式车床', 'LATHE',
     '通用技术集团沈阳机床有限责任公司',
     JSON_OBJECT('specification', 'V6S'),
     JSON_OBJECT('cncSystem', 'Series Oi-TF PLUS', 'cncBrand', 'FANUC', 'systemType', 'CNC'),
     1),
    -- 立式加工中心 DNM655/50
    (4000000000000000013, 'DNM655', 'DNM655/50 立式加工中心', 'CNC_VERTICAL',
     '迪恩机床(中国)有限公司',
     JSON_OBJECT('specification', 'DNM655/50'),
     JSON_OBJECT('cncSystem', 'FANUC i seris PLUS', 'cncBrand', 'FANUC', 'systemType', 'CNC'),
     1),
    -- 数控卧车 LG63
    (4000000000000000014, 'LG63', 'LG63 数控卧车', 'LATHE',
     '宁夏新瑞长城机床有限公司',
     JSON_OBJECT('specification', 'LG63'),
     JSON_OBJECT('cncSystem', 'FANUC seris Oi-TD', 'cncBrand', 'FANUC', 'systemType', 'CNC'),
     1)
ON DUPLICATE KEY UPDATE manufacturer = VALUES(manufacturer), specifications = VALUES(specifications), type_specific_attrs = VALUES(type_specific_attrs);

-- 设备信息表：租户信息通过 tb_device_id 从 ThingsBoard 获取
-- 设备信息 ID 使用雪花算法生成的 bigint 类型，格式：5000000000000000000 + 序号
-- 说明：增速器机加设备属于威力增速器工厂(1300)的增速器机加车间(Z72)
SET @FACTORY_1300 = 1000000000000001300;  -- 威力增速器工厂
SET @WORKSHOP_Z72 = 2000000000000000072;  -- 增速器机加车间

INSERT INTO device_info (id, tb_device_id, device_code, device_name, device_type_code, device_model_id,
                         org_factory_id, org_workshop_id, org_production_line_id, device_status)
VALUES 
    -- 示例设备
    (5000000000000000001, 'TB-DEVICE-001', 'DEV-0001', '威力传动工厂-机加车间-五轴机01',
     'CNC_5AXIS', 4000000000000000001, 1000000000000001100, 2000000000000000001, 3000000000000000001, 'ACTIVE'),
    -- 增速器机加设备（根据统计表）
    -- 1. CK5112*10/5 数控立式车床，6台 (JQ001-JQ006)
    (5000000000000000002, 'TB-WL-S21-JQ001', 'WL-S21-JQ001', 'CK5112*10/5 数控立式车床-001', 'LATHE', 4000000000000000005, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    (5000000000000000003, 'TB-WL-S21-JQ002', 'WL-S21-JQ002', 'CK5112*10/5 数控立式车床-002', 'LATHE', 4000000000000000005, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    (5000000000000000004, 'TB-WL-S21-JQ003', 'WL-S21-JQ003', 'CK5112*10/5 数控立式车床-003', 'LATHE', 4000000000000000005, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    (5000000000000000005, 'TB-WL-S21-JQ004', 'WL-S21-JQ004', 'CK5112*10/5 数控立式车床-004', 'LATHE', 4000000000000000005, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    (5000000000000000006, 'TB-WL-S21-JQ005', 'WL-S21-JQ005', 'CK5112*10/5 数控立式车床-005', 'LATHE', 4000000000000000005, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    (5000000000000000007, 'TB-WL-S21-JQ006', 'WL-S21-JQ006', 'CK5112*10/5 数控立式车床-006', 'LATHE', 4000000000000000005, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 2. NL1250H 数控卧式车床，1台 (JQ007)
    (5000000000000000008, 'TB-WL-S21-JQ007', 'WL-S21-JQ007', 'NL1250H 数控卧式车床', 'LATHE', 4000000000000000006, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 3. CK5120F 数控立式车床，2台 (JQ008, JQ009)
    (5000000000000000009, 'TB-WL-S21-JQ008', 'WL-S21-JQ008', 'CK5120F 数控立式车床-001', 'LATHE', 4000000000000000007, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    (5000000000000000010, 'TB-WL-S21-JQ009', 'WL-S21-JQ009', 'CK5120F 数控立式车床-002', 'LATHE', 4000000000000000007, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 4. CK5235F 数控立式车床，2台 (JQ010, JQ011)
    (5000000000000000011, 'TB-WL-S21-JQ010', 'WL-S21-JQ010', 'CK5235F 数控立式车床-001', 'LATHE', 4000000000000000008, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    (5000000000000000012, 'TB-WL-S21-JQ011', 'WL-S21-JQ011', 'CK5235F 数控立式车床-002', 'LATHE', 4000000000000000008, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 5. NL502SA 数控卧式车床，1台 (JQ012)
    (5000000000000000013, 'TB-WL-S21-JQ012', 'WL-S21-JQ012', 'NL502SA 数控卧式车床', 'LATHE', 4000000000000000009, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 6. V4C 数控立式车床，2台 (JQ013, JQ016)
    (5000000000000000014, 'TB-WL-S21-JQ013', 'WL-S21-JQ013', 'V4C 数控立式车床-001', 'LATHE', 4000000000000000010, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    (5000000000000000015, 'TB-WL-S21-JQ016', 'WL-S21-JQ016', 'V4C 数控立式车床-002', 'LATHE', 4000000000000000010, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 7. V6C 数控立式车床，1台 (JQ014)
    (5000000000000000016, 'TB-WL-S21-JQ014', 'WL-S21-JQ014', 'V6C 数控立式车床', 'LATHE', 4000000000000000011, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 8. V6S 数控立式车床，1台 (JQ015)
    (5000000000000000017, 'TB-WL-S21-JQ015', 'WL-S21-JQ015', 'V6S 数控立式车床', 'LATHE', 4000000000000000012, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 9. DNM655/50 立式加工中心，1台 (JQ017)
    (5000000000000000018, 'TB-WL-S21-JQ017', 'WL-S21-JQ017', 'DNM655/50 立式加工中心', 'CNC_VERTICAL', 4000000000000000013, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE'),
    -- 10. LG63 数控卧车，1台 (JQ019)
    (5000000000000000019, 'TB-WL-S21-JQ019', 'WL-S21-JQ019', 'LG63 数控卧车', 'LATHE', 4000000000000000014, @FACTORY_1300, @WORKSHOP_Z72, NULL, 'ACTIVE')
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name), org_factory_id = VALUES(org_factory_id), org_workshop_id = VALUES(org_workshop_id), org_production_line_id = VALUES(org_production_line_id);

SET FOREIGN_KEY_CHECKS = 1;

