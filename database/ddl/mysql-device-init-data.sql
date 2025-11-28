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

INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    (1201, 1, '机床', 'MACHINE_TOOL', 'device_type', 0, ''),
    (1202, 2, '机器人', 'ROBOT', 'device_type', 0, ''),
    (1203, 3, 'PLC', 'PLC', 'device_type', 0, '')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);

INSERT INTO system_dict_data (id, sort, label, value, dict_type, status, remark)
VALUES 
    (1301, 1, '五轴铣车中心', 'CNC_5AXIS', 'device_sub_type', 0, ''),
    (1302, 2, '立式加工中心', 'CNC_VERTICAL', 'device_sub_type', 0, ''),
    (1303, 3, '卧式加工中心', 'CNC_HORIZONTAL', 'device_sub_type', 0, ''),
    (1304, 4, '协作机器人', 'ROBOT_COLLABORATIVE', 'device_sub_type', 0, '')
ON DUPLICATE KEY UPDATE label = VALUES(label), status = VALUES(status);

-- ============================================================
-- 2. 设备组织结构（厂区 → 车间 → 产线）
-- ============================================================
SET @TENANT_ID = 'TENANT-001';

-- 厂区（level_no=1）
INSERT INTO device_org_relation (id, tenant_uuid, unit_code, unit_name, unit_type_value, org_parent_id, level_no, path, is_active, sort_order)
VALUES 
    -- 威力传动工厂
    ('FACTORY-1100', @TENANT_ID, '1100', '威力传动工厂', 'FACTORY', NULL, 1, '/1100', 1, 1),
    -- 威力增速器工厂
    ('FACTORY-1300', @TENANT_ID, '1300', '威力增速器工厂', 'FACTORY', NULL, 1, '/1300', 1, 2),
    -- 威力无价值工厂
    ('FACTORY-1199', @TENANT_ID, '1199', '威力无价值工厂', 'FACTORY', NULL, 1, '/1199', 1, 3),
    -- 威马电机工厂
    ('FACTORY-2100', @TENANT_ID, '2100', '威马电机工厂', 'FACTORY', NULL, 1, '/2100', 1, 4),
    -- 威马无价值工厂
    ('FACTORY-2199', @TENANT_ID, '2199', '威马无价值工厂', 'FACTORY', NULL, 1, '/2199', 1, 5),
    -- 威润传动工厂
    ('FACTORY-4100', @TENANT_ID, '4100', '威润传动工厂', 'FACTORY', NULL, 1, '/4100', 1, 6),
    -- 威润无价值工厂
    ('FACTORY-4199', @TENANT_ID, '4199', '威润无价值工厂', 'FACTORY', NULL, 1, '/4199', 1, 7),
    -- 威驰传动工厂
    ('FACTORY-5100', @TENANT_ID, '5100', '威驰传动工厂', 'FACTORY', NULL, 1, '/5100', 1, 8),
    -- 威驰传动无价值工厂
    ('FACTORY-5199', @TENANT_ID, '5199', '威驰传动无价值工厂', 'FACTORY', NULL, 1, '/5199', 1, 9)
ON DUPLICATE KEY UPDATE unit_name = VALUES(unit_name), unit_type_value = VALUES(unit_type_value), org_parent_id = VALUES(org_parent_id), path = VALUES(path);

-- 车间（level_no=2）
INSERT INTO device_org_relation (id, tenant_uuid, unit_code, unit_name, unit_type_value, org_parent_id, level_no, path, is_active, sort_order)
VALUES 
    -- 威力传动工厂(1100) 下的车间
    ('WORKSHOP-Z01', @TENANT_ID, 'Z01', '机加车间', 'WORKSHOP', 'FACTORY-1100', 2, '/1100/Z01', 1, 1),
    ('WORKSHOP-Z02', @TENANT_ID, 'Z02', '装配车间', 'WORKSHOP', 'FACTORY-1100', 2, '/1100/Z02', 1, 2),
    ('WORKSHOP-Z03', @TENANT_ID, 'Z03', '后处理车间', 'WORKSHOP', 'FACTORY-1100', 2, '/1100/Z03', 1, 3),
    ('WORKSHOP-Z04', @TENANT_ID, 'Z04', '总装车间', 'WORKSHOP', 'FACTORY-1100', 2, '/1100/Z04', 1, 4),
    ('WORKSHOP-Z05', @TENANT_ID, 'Z05', '电气车间', 'WORKSHOP', 'FACTORY-1100', 2, '/1100/Z05', 1, 5),
    ('WORKSHOP-Z06', @TENANT_ID, 'Z06', '产品维修车间', 'WORKSHOP', 'FACTORY-1100', 2, '/1100/Z06', 1, 6),
    -- 威力增速器工厂(1300) 下的车间
    ('WORKSHOP-Z71', @TENANT_ID, 'Z71', '增速器车间', 'WORKSHOP', 'FACTORY-1300', 2, '/1300/Z71', 1, 1),
    ('WORKSHOP-Z72', @TENANT_ID, 'Z72', '增速器机加车间', 'WORKSHOP', 'FACTORY-1300', 2, '/1300/Z72', 1, 2),
    -- 威马电机工厂(2100) 下的车间
    ('WORKSHOP-Z07', @TENANT_ID, 'Z07', '扁线车间', 'WORKSHOP', 'FACTORY-2100', 2, '/2100/Z07', 1, 1),
    -- 威润传动工厂(4100) 下的车间
    ('WORKSHOP-Z41', @TENANT_ID, 'Z41', '威润后处理车间', 'WORKSHOP', 'FACTORY-4100', 2, '/4100/Z41', 1, 1),
    -- 试验车间（可分配给多个厂区，这里分配给威力传动工厂）
    ('WORKSHOP-Z99', @TENANT_ID, 'Z99', '试验车间', 'WORKSHOP', 'FACTORY-1100', 2, '/1100/Z99', 1, 7)
ON DUPLICATE KEY UPDATE unit_name = VALUES(unit_name), unit_type_value = VALUES(unit_type_value), org_parent_id = VALUES(org_parent_id), path = VALUES(path);

-- 产线（level_no=3）- 示例数据
INSERT INTO device_org_relation (id, tenant_uuid, unit_code, unit_name, unit_type_value, org_parent_id, level_no, path, is_active, sort_order)
VALUES 
    -- 机加车间下的产线
    ('LINE-Z01-01', @TENANT_ID, 'Z01-01', '机加产线01', 'PRODUCTION_LINE', 'WORKSHOP-Z01', 3, '/1100/Z01/Z01-01', 1, 1),
    ('LINE-Z01-02', @TENANT_ID, 'Z01-02', '机加产线02', 'PRODUCTION_LINE', 'WORKSHOP-Z01', 3, '/1100/Z01/Z01-02', 1, 2),
    -- 装配车间下的产线
    ('LINE-Z02-01', @TENANT_ID, 'Z02-01', '装配产线01', 'PRODUCTION_LINE', 'WORKSHOP-Z02', 3, '/1100/Z02/Z02-01', 1, 1),
    ('LINE-Z02-02', @TENANT_ID, 'Z02-02', '装配产线02', 'PRODUCTION_LINE', 'WORKSHOP-Z02', 3, '/1100/Z02/Z02-02', 1, 2),
    -- 增速器机加车间下的产线
    ('LINE-Z72-01', @TENANT_ID, 'Z72-01', '增速器机加产线01', 'PRODUCTION_LINE', 'WORKSHOP-Z72', 3, '/1300/Z72/Z72-01', 1, 1)
ON DUPLICATE KEY UPDATE unit_name = VALUES(unit_name), unit_type_value = VALUES(unit_type_value), org_parent_id = VALUES(org_parent_id), path = VALUES(path);

-- ============================================================
-- 3. 设备类型实例（device_type_relation）
-- ============================================================
INSERT INTO device_type_relation (id, tenant_uuid, type_code, type_dict_value, parent_type_id, parent_type_code, parent_dict_value, level_no, path, category, description, is_active, sort_order)
VALUES 
    ('TYPE-MT',       @TENANT_ID, 'MACHINE_TOOL', 'MACHINE_TOOL',  NULL, NULL, NULL, 1, '/MACHINE_TOOL', 'MACHINE_TOOL', '机床主类型',        1, 1),
    ('TYPE-ROBOT',    @TENANT_ID, 'ROBOT',        'ROBOT',         NULL, NULL, NULL, 1, '/ROBOT',        'ROBOT',        '机器人主类型',      1, 2),
    ('TYPE-PLC',      @TENANT_ID, 'PLC',          'PLC',           NULL, NULL, NULL, 1, '/PLC',          'PLC',          'PLC 主类型',        1, 3)
ON DUPLICATE KEY UPDATE category = VALUES(category), description = VALUES(description), type_dict_value = VALUES(type_dict_value);

INSERT INTO device_type_relation (id, tenant_uuid, type_code, type_dict_value, parent_type_id, parent_type_code, parent_dict_value, level_no, path, category, description, is_active, sort_order)
VALUES 
    ('TYPE-MT-5AXIS', @TENANT_ID, 'CNC_5AXIS',        'CNC_5AXIS', 'TYPE-MT',    'MACHINE_TOOL', 'MACHINE_TOOL', 2, '/MACHINE_TOOL/CNC_5AXIS',        'MACHINE_TOOL', '五轴铣车中心', 1, 1),
    ('TYPE-MT-V',     @TENANT_ID, 'CNC_VERTICAL',     'CNC_VERTICAL', 'TYPE-MT',  'MACHINE_TOOL', 'MACHINE_TOOL', 2, '/MACHINE_TOOL/CNC_VERTICAL',      'MACHINE_TOOL', '立式加工中心', 1, 2),
    ('TYPE-ROBOT-C',  @TENANT_ID, 'ROBOT_COLLABORATIVE', 'ROBOT_COLLABORATIVE', 'TYPE-ROBOT','ROBOT', 'ROBOT', 2, '/ROBOT/ROBOT_COLLABORATIVE', 'ROBOT', '协作机器人', 1, 1)
ON DUPLICATE KEY UPDATE category = VALUES(category), description = VALUES(description),
    parent_type_id = VALUES(parent_type_id), path = VALUES(path), type_dict_value = VALUES(type_dict_value), parent_dict_value = VALUES(parent_dict_value);

-- ============================================================
-- 4. 设备型号与设备示例
-- ============================================================
INSERT INTO device_model (id, tenant_uuid, model_code, model_name, device_type_code, manufacturer, specifications, type_specific_attrs, is_active)
VALUES 
    ('MODEL-DMU50', @TENANT_ID, 'DMU50', 'DMU 50 五轴加工中心', 'CNC_5AXIS',
     'DMG MORI',
     JSON_OBJECT('power', JSON_OBJECT('value', 15, 'unit', 'kW')),
     JSON_OBJECT('cncSystem', 'FANUC 0i-MF', 'axisCount', 5),
     1)
ON DUPLICATE KEY UPDATE manufacturer = VALUES(manufacturer), specifications = VALUES(specifications), type_specific_attrs = VALUES(type_specific_attrs);

INSERT INTO device_info (id, tenant_uuid, tb_device_id, device_code, device_name, device_type_code, device_model_id,
                         org_factory_id, org_workshop_id, org_production_line_id, device_status)
VALUES 
    ('DEVICE-001', @TENANT_ID, 'TB-DEVICE-001', 'DEV-0001', '威力传动工厂-机加车间-五轴机01',
     'CNC_5AXIS', 'MODEL-DMU50', 'FACTORY-1100', 'WORKSHOP-Z01', 'LINE-Z01-01', 'ACTIVE')
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name), org_factory_id = VALUES(org_factory_id), org_workshop_id = VALUES(org_workshop_id), org_production_line_id = VALUES(org_production_line_id);

SET FOREIGN_KEY_CHECKS = 1;

