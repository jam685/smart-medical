-- =============================================================
-- 迁移 v3：处方业务（医生接诊开方）新增两张表
-- 日期：2026-09-19
-- 内容：
--   1. sm_prescription        处方主表（一次就诊一张处方）
--   2. sm_prescription_item   处方药品明细表（一张处方一至多条明细）
-- 状态枚举（sm_prescription.status）：DRAFT 草稿 / ISSUED 已开具
-- 关联说明：visit_id -> sm_visit.id、appointment_id -> sm_appointment.id、
--           patient_id -> sm_patient.id、doctor_id -> sm_doctor.id、
--           sm_prescription_item.prescription_id -> sm_prescription.id
--           以上均为逻辑关联，不建物理外键（与全库约定一致）
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行
-- =============================================================
-- 注意：V1.3 数据库设计已将「处方表与明细表合并」，
--       药品明细按 prescription_no 分组直接存于 sm_prescription（每行一条药品明细）。
--       下方 sm_prescription_item 为 v3 时代的过渡设计，V1.3 起废弃，不再创建。

-- 1. 处方主表
CREATE TABLE IF NOT EXISTS sm_prescription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT COMMENT '关联就诊进度ID',
    appointment_id BIGINT COMMENT '关联挂号单ID',
    patient_id BIGINT NOT NULL COMMENT '患者ID',
    doctor_id BIGINT NOT NULL COMMENT '开方医生ID',
    department VARCHAR(50) COMMENT '开方科室',
    diagnosis VARCHAR(500) COMMENT '诊断',
    advice VARCHAR(1000) COMMENT '医嘱',
    status VARCHAR(20) DEFAULT 'ISSUED' COMMENT 'DRAFT草稿/ISSUED已开具',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_visit (visit_id),
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 处方药品明细表【已废弃：V1.3 起药品明细按 prescription_no 合并进 sm_prescription】
--    历史参考定义（不再创建）：
--    CREATE TABLE IF NOT EXISTS sm_prescription_item (
--        id BIGINT AUTO_INCREMENT PRIMARY KEY,
--        prescription_id BIGINT NOT NULL COMMENT '处方ID',
--        drug_name VARCHAR(200) NOT NULL COMMENT '药品名称',
--        specification VARCHAR(200) COMMENT '规格',
--        dosage VARCHAR(100) COMMENT '单次剂量',
--        frequency VARCHAR(100) COMMENT '频次（如每日3次）',
--        days INT COMMENT '用药天数',
--        quantity VARCHAR(100) COMMENT '数量',
--        usage_method VARCHAR(200) COMMENT '用法（口服/外用/静滴等）',
--        remark VARCHAR(500) COMMENT '备注',
--        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--        INDEX idx_prescription (prescription_id)
--    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
