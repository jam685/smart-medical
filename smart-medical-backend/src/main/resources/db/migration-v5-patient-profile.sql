-- =============================================================
-- 迁移 v5：sm_patient 补充患者资料两列
-- 日期：2026-09-19
-- 背景：患者端「我的」页面开发，需要性别与头像。
-- 内容：
--   1. gender  VARCHAR(10)  性别：男/女   （置于 real_name 之后）
--   2. avatar  VARCHAR(500) 头像URL（空则前端用姓名首字母占位）（置于 id_card 之后）
-- 迁移后列顺序与基线 schema.sql 的 sm_patient 完全一致：
--   ... real_name, gender, phone, id_card, avatar, status ...
-- 幂等：逐列检查 information_schema.COLUMNS，列已存在则跳过，脚本可重复执行。
-- 注意：本脚本含 DELIMITER 存储过程语法，请用 mysql 命令行客户端执行，
--       不要经 JDBC 单语句方式执行。
-- =============================================================

DROP PROCEDURE IF EXISTS sm_v5_add_patient_columns;

DELIMITER $$
CREATE PROCEDURE sm_v5_add_patient_columns()
BEGIN
    -- 1. 性别
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sm_patient'
          AND COLUMN_NAME = 'gender'
    ) THEN
        ALTER TABLE sm_patient
            ADD COLUMN gender VARCHAR(10) NULL COMMENT '性别：男/女' AFTER real_name;
    END IF;

    -- 2. 头像URL
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sm_patient'
          AND COLUMN_NAME = 'avatar'
    ) THEN
        ALTER TABLE sm_patient
            ADD COLUMN avatar VARCHAR(500) NULL COMMENT '头像URL（空则前端用首字母占位）' AFTER id_card;
    END IF;
END$$
DELIMITER ;

CALL sm_v5_add_patient_columns();
DROP PROCEDURE IF EXISTS sm_v5_add_patient_columns;
