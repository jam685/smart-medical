-- =============================================
-- 智能医疗系统 DDL (MySQL 9.x, utf8mb4)
-- 21 张表，前缀 sm_
-- =============================================

-- 1. sm_patient 患者表
CREATE TABLE IF NOT EXISTS sm_patient (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(50),
    gender VARCHAR(10) COMMENT '性别：男/女',
    phone VARCHAR(20),
    id_card VARCHAR(20),
    avatar VARCHAR(500) COMMENT '头像URL（空则前端用首字母占位）',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. sm_doctor 医生表
CREATE TABLE IF NOT EXISTS sm_doctor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_no VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(50),
    gender VARCHAR(10) COMMENT '性别：男/女',
    title VARCHAR(20) COMMENT '职称：主任医师/副主任医师/主治医师',
    phone VARCHAR(20),
    department VARCHAR(50),
    avatar VARCHAR(500) COMMENT '头像URL（空则前端用首字母占位）',
    bio VARCHAR(500) COMMENT '医生简介/擅长领域/成就',
    online_status VARCHAR(20) DEFAULT 'OFFLINE',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_employee_no (employee_no),
    INDEX idx_department (department),
    INDEX idx_online_status (online_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. sm_admin 管理员表
CREATE TABLE IF NOT EXISTS sm_admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_no VARCHAR(50),
    phone VARCHAR(20),
    id_card VARCHAR(20),
    real_name VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_employee_no (employee_no),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. sm_appointment 挂号表
CREATE TABLE IF NOT EXISTS sm_appointment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    department VARCHAR(50),
    appointment_time TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. sm_visit 就诊进度表
CREATE TABLE IF NOT EXISTS sm_visit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    department VARCHAR(50),
    status VARCHAR(20) DEFAULT 'WAITING',
    progress_nodes JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. sm_report 报告表
CREATE TABLE IF NOT EXISTS sm_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    report_type VARCHAR(50),
    image_url VARCHAR(500),
    image_meta JSON,
    uploaded_by BIGINT,
    status VARCHAR(20) DEFAULT 'UPLOADED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_report_type (report_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. sm_report_interpretation 报告AI解读表
CREATE TABLE IF NOT EXISTS sm_report_interpretation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    recognized_content TEXT,
    analysis TEXT,
    abnormal_items JSON,
    reference_ranges JSON,
    confidence DECIMAL(5,4),
    `references` JSON,
    need_human TINYINT(1) DEFAULT 0,
    disclaimer TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. sm_ai_chat_session AI会话表
CREATE TABLE IF NOT EXISTS sm_ai_chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    session_key VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_session_key (session_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. sm_ai_chat_message AI消息表
CREATE TABLE IF NOT EXISTS sm_ai_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    sender VARCHAR(20) NOT NULL,
    content TEXT,
    confidence DECIMAL(5,4),
    need_human TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. sm_ai_analysis AI分析记录表
CREATE TABLE IF NOT EXISTS sm_ai_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consult_id BIGINT NOT NULL,
    patient_id BIGINT,
    analysis_type VARCHAR(50),
    possible_causes JSON,
    differential JSON,
    diagnosis_suggestions JSON,
    medication_suggestions JSON,
    required_materials JSON,
    risk_level VARCHAR(20),
    confidence DECIMAL(5,4),
    `references` JSON,
    disclaimer TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_consult (consult_id),
    INDEX idx_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. sm_online_consult 线上面诊表
CREATE TABLE IF NOT EXISTS sm_online_consult (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    department VARCHAR(50),
    chief_complaint VARCHAR(500),
    description TEXT,
    status VARCHAR(30) DEFAULT 'PENDING',
    supplement_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. sm_consult_message 问诊消息表
CREATE TABLE IF NOT EXISTS sm_consult_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consult_id BIGINT NOT NULL,
    sender VARCHAR(20) NOT NULL,
    message_type VARCHAR(20),
    content TEXT,
    image_url VARCHAR(500),
    sensitive_flag TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_consult (consult_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. sm_consult_supplement 补充材料记录表
CREATE TABLE IF NOT EXISTS sm_consult_supplement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consult_id BIGINT NOT NULL,
    supplement_no INT,
    material_type VARCHAR(50),
    content TEXT,
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_consult (consult_id),
    INDEX idx_supplement_no (supplement_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. sm_consult_reply 医生回复表
CREATE TABLE IF NOT EXISTS sm_consult_reply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consult_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    ai_draft TEXT,
    final_reply TEXT,
    replied_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_consult (consult_id),
    INDEX idx_doctor (doctor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. sm_medical_note 病历表
CREATE TABLE IF NOT EXISTS sm_medical_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consult_id BIGINT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    draft TEXT,
    status VARCHAR(20) DEFAULT 'PREVIEW',
    modified_count INT DEFAULT 0,
    signed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_consult (consult_id),
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. sm_medical_note_version 病历版本表
CREATE TABLE IF NOT EXISTS sm_medical_note_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    note_id BIGINT NOT NULL,
    version INT,
    content TEXT,
    modified_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_note (note_id),
    INDEX idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 17. sm_audit_log 审计日志表
CREATE TABLE IF NOT EXISTS sm_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    role VARCHAR(20),
    action VARCHAR(100),
    target_type VARCHAR(50),
    target_id BIGINT,
    detail TEXT,
    ip VARCHAR(50),
    user_agent VARCHAR(500),
    sensitive_type VARCHAR(50),
    sensitive_level VARCHAR(20),
    action_taken VARCHAR(50),
    review_status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_created (created_at),
    INDEX idx_review_status (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 18. sm_sensitive_content 敏感内容表
CREATE TABLE IF NOT EXISTS sm_sensitive_content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    role VARCHAR(20),
    content TEXT,
    sensitive_type VARCHAR(50),
    sensitive_level VARCHAR(20),
    action_taken VARCHAR(50),
    review_status VARCHAR(20) DEFAULT 'PENDING',
    reviewed_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    INDEX idx_review_status (review_status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 19. sm_knowledge_base 知识库表
CREATE TABLE IF NOT EXISTS sm_knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200),
    type VARCHAR(50),
    content TEXT,
    source VARCHAR(200),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 20. sm_model_config 模型配置表
CREATE TABLE IF NOT EXISTS sm_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100),
    version VARCHAR(50),
    deploy_type VARCHAR(50),
    status VARCHAR(20),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 21. sm_data_quality 数据质量表
CREATE TABLE IF NOT EXISTS sm_data_quality (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_count INT,
    labeled_count INT,
    missing_rate DECIMAL(5,4),
    abnormal_rate DECIMAL(5,4),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 22. sm_prescription 处方主表
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

-- 23. sm_prescription_item 处方药品明细表
CREATE TABLE IF NOT EXISTS sm_prescription_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT NOT NULL COMMENT '处方ID',
    drug_name VARCHAR(200) NOT NULL COMMENT '药品名称',
    specification VARCHAR(200) COMMENT '规格',
    dosage VARCHAR(100) COMMENT '单次剂量',
    frequency VARCHAR(100) COMMENT '频次（如每日3次）',
    days INT COMMENT '用药天数',
    quantity VARCHAR(100) COMMENT '数量',
    usage_method VARCHAR(200) COMMENT '用法（口服/外用/静滴等）',
    remark VARCHAR(500) COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_prescription (prescription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
