-- =============================================================
-- 迁移 v4：sm_doctor 补充医生资料三列
-- 日期：2026-09-19
-- 背景：医生端资料/头像功能开发期间，基线表新增了 gender、avatar、bio，
--       旧库（已执行过 v1 建表 + v2 title 迁移）需要补齐这三列。
-- 内容：
--   1. gender  VARCHAR(10)  性别：男/女        （置于 real_name 之后）
--   2. avatar  VARCHAR(500) 头像URL（空则前端用姓名首字母占位）（置于 department 之后）
--   3. bio     VARCHAR(500) 医生简介/擅长领域/成就（置于 avatar 之后）
-- 迁移后列顺序与基线 schema.sql 的 sm_doctor 完全一致：
--   ... real_name, gender, title(v2已加), phone, department, avatar, bio, online_status ...
-- 幂等：逐列检查 information_schema.COLUMNS，列已存在则跳过，脚本可重复执行。
-- 注意：本脚本含 DELIMITER 存储过程语法，请用 mysql 命令行客户端执行，
--       不要经 JDBC 单语句方式执行。
-- =============================================================

DROP PROCEDURE IF EXISTS sm_v4_add_doctor_columns;

DELIMITER $$
CREATE PROCEDURE sm_v4_add_doctor_columns()
BEGIN
    -- 1. 性别
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sm_doctor'
          AND COLUMN_NAME = 'gender'
    ) THEN
        ALTER TABLE sm_doctor
            ADD COLUMN gender VARCHAR(10) NULL COMMENT '性别：男/女' AFTER real_name;
    END IF;

    -- 2. 头像URL
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sm_doctor'
          AND COLUMN_NAME = 'avatar'
    ) THEN
        ALTER TABLE sm_doctor
            ADD COLUMN avatar VARCHAR(500) NULL COMMENT '头像URL（空则前端用首字母占位）' AFTER department;
    END IF;

    -- 3. 医生简介
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sm_doctor'
          AND COLUMN_NAME = 'bio'
    ) THEN
        ALTER TABLE sm_doctor
            ADD COLUMN bio VARCHAR(500) NULL COMMENT '医生简介/擅长领域/成就' AFTER avatar;
    END IF;
END$$
DELIMITER ;

CALL sm_v4_add_doctor_columns();
DROP PROCEDURE IF EXISTS sm_v4_add_doctor_columns;
