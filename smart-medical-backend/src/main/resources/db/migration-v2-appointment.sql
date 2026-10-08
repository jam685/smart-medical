-- =============================================================
-- 迁移 v2：挂号业务前后端联调所需结构与种子数据
-- 日期：2026-09-17
-- 内容：
--   1. sm_doctor 增加职称列 title（挂号费按职称在后端规则计算）
--   2. 给 3 名种子医生补职称
--   3. 修正旧挂号状态语义并为患者张三(patient_id=1)补全各状态挂号
-- 状态枚举：PENDING 待确认 / CONFIRMED 已确认(待就诊) / FINISHED 已完成 / CANCELLED 已取消
-- =============================================================

-- 1. 医生职称列
ALTER TABLE sm_doctor
    ADD COLUMN title VARCHAR(20) NULL COMMENT '职称：主任医师/副主任医师/主治医师' AFTER real_name;

-- 2. 种子医生职称
UPDATE sm_doctor SET title = '主任医师'   WHERE employee_no = 'D001'; -- 李医生 内分泌科
UPDATE sm_doctor SET title = '副主任医师' WHERE employee_no = 'D002'; -- 王医生 心内科
UPDATE sm_doctor SET title = '主治医师'   WHERE employee_no = 'D003'; -- 张医生 呼吸科

-- 3. 旧数据修正：id=1 为张三昨天的号，按时间线应为已完成
UPDATE sm_appointment SET status = 'FINISHED' WHERE id = 1;

-- 4. 张三(patient_id=1) 补充挂号：3 条待就诊 + 2 条已完成(含上面id=1) + 2 条已取消
INSERT INTO sm_appointment (patient_id, doctor_id, department, appointment_time, status, created_at, updated_at) VALUES
(1, 3, '呼吸科',   DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 3 DAY),  INTERVAL 9 HOUR),  'CONFIRMED', NOW(), NOW()),
(1, 1, '内分泌科', DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 5 DAY),  INTERVAL 14 HOUR), 'PENDING',   NOW(), NOW()),
(1, 3, '呼吸科',   DATE_ADD(DATE_ADD(CURDATE(), INTERVAL -10 DAY), INTERVAL 10 HOUR), 'FINISHED',  NOW(), NOW()),
(1, 2, '心内科',   DATE_ADD(DATE_ADD(CURDATE(), INTERVAL -6 DAY),  INTERVAL 15 HOUR), 'CANCELLED', NOW(), NOW()),
(1, 1, '内分泌科', DATE_ADD(DATE_ADD(CURDATE(), INTERVAL -8 DAY),  INTERVAL 11 HOUR), 'CANCELLED', NOW(), NOW());
