-- =============================================
-- 智能医疗系统 DDL (数据库设计 V1.3 · MySQL 9.x, utf8mb4)
-- 共 21 张表，前缀 sm_
-- 命名：表名前缀 sm_，字段小写下划线；角色用 Java 枚举（PATIENT/DOCTOR/ADMIN），不建角色表
-- 说明：sm_patient 按后端 V2.3 的 PAT-04/PAT-05 保留 gender、avatar 字段
--      （V1.3 该表字段清单遗漏此二列，但"更新性别""上传头像"接口明确需要，故保留一致）
-- =============================================

-- 1. sm_patient 患者表
CREATE TABLE IF NOT EXISTS sm_patient (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
    password VARCHAR(128) NOT NULL COMMENT '密码，BCrypt 加密存储',
    real_name VARCHAR(50) COMMENT '真实姓名',
    gender VARCHAR(10) COMMENT '性别：男/女',
    phone VARCHAR(20) COMMENT '手机号',
    id_card VARCHAR(20) COMMENT '身份证，展示时后端脱敏',
    avatar VARCHAR(500) COMMENT '头像URL（空则前端用首字母占位）',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. sm_doctor 医生表
CREATE TABLE IF NOT EXISTS sm_doctor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    employee_no VARCHAR(50) NOT NULL UNIQUE COMMENT '工号',
    password VARCHAR(128) NOT NULL COMMENT '密码（BCrypt 加密）',
    real_name VARCHAR(50) COMMENT '姓名',
    gender VARCHAR(10) COMMENT '性别：男/女',
    title VARCHAR(20) COMMENT '职称：主任医师/副主任医师/主治医师',
    phone VARCHAR(20) COMMENT '手机号',
    department VARCHAR(50) COMMENT '科室',
    avatar VARCHAR(500) COMMENT '头像URL',
    bio VARCHAR(500) COMMENT '医生简介/擅长领域',
    online_status VARCHAR(20) DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_employee_no (employee_no),
    INDEX idx_department (department),
    INDEX idx_online_status (online_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. sm_admin 管理员表
CREATE TABLE IF NOT EXISTS sm_admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    employee_no VARCHAR(50) COMMENT '工号',
    phone VARCHAR(20) COMMENT '手机号',
    id_card VARCHAR(20) COMMENT '身份证',
    real_name VARCHAR(50) COMMENT '姓名',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_employee_no (employee_no),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. sm_appointment 挂号表
CREATE TABLE IF NOT EXISTS sm_appointment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    patient_id BIGINT NOT NULL COMMENT '患者ID',
    doctor_id BIGINT NOT NULL COMMENT '医生ID',
    department VARCHAR(50) COMMENT '科室',
    appointment_time TIMESTAMP COMMENT '挂号时间',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/CANCELLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. sm_visit 就诊进度表
CREATE TABLE IF NOT EXISTS sm_visit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    appointment_id BIGINT COMMENT '挂号ID',
    patient_id BIGINT NOT NULL COMMENT '患者ID',
    doctor_id BIGINT NOT NULL COMMENT '医生ID',
    department VARCHAR(50) COMMENT '科室',
    status VARCHAR(20) DEFAULT 'WAITING' COMMENT 'WAITING/IN_PROGRESS/FINISHED/RESTARTED',
    progress_nodes JSON COMMENT '进度节点',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. sm_report 报告表
CREATE TABLE IF NOT EXISTS sm_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    patient_id BIGINT NOT NULL COMMENT '患者ID',
    report_type VARCHAR(50) COMMENT '报告类型',
    image_url VARCHAR(500) COMMENT '图片地址',
    image_meta JSON COMMENT '图片元数据',
    uploaded_by BIGINT COMMENT '上传人ID',
    status VARCHAR(20) DEFAULT 'UPLOADED' COMMENT '状态',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_patient (patient_id),
    INDEX idx_report_type (report_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. sm_medical_note 病历表
CREATE TABLE IF NOT EXISTS sm_medical_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    consult_id BIGINT COMMENT '关联问诊ID',
    patient_id BIGINT NOT NULL COMMENT '患者ID',
    doctor_id BIGINT NOT NULL COMMENT '医生ID',
    draft TEXT COMMENT '草稿内容',
    status VARCHAR(20) DEFAULT 'PREVIEW' COMMENT 'PREVIEW/DRAFT/SIGNED/VOID',
    modified_count INT DEFAULT 0 COMMENT '修改次数，最大3次，超过作废重开',
    signed_at TIMESTAMP NULL COMMENT '签名时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_consult (consult_id),
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. sm_medical_note_version 病历版本表（V1.3 精简）
CREATE TABLE IF NOT EXISTS sm_medical_note_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    note_id BIGINT NOT NULL COMMENT '病历ID',
    version INT COMMENT '版本号',
    modified_by BIGINT COMMENT '修改人ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_note (note_id),
    INDEX idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. sm_audit_log 审计日志表（V1.3 扩展敏感审计字段）
CREATE TABLE IF NOT EXISTS sm_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT COMMENT '用户ID',
    role VARCHAR(20) COMMENT '角色',
    action VARCHAR(100) COMMENT '操作',
    target_id BIGINT COMMENT '目标ID',
    detail TEXT COMMENT '详情',
    sensitive_level VARCHAR(20) COMMENT '敏感等级',
    review_status VARCHAR(20) COMMENT '审核状态',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_created (created_at),
    INDEX idx_review_status (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. sm_data_quality 数据质量表
CREATE TABLE IF NOT EXISTS sm_data_quality (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    data_count INT COMMENT '数据量',
    labeled_count INT COMMENT '标注量',
    missing_rate DECIMAL(5,4) COMMENT '缺失率',
    abnormal_rate DECIMAL(5,4) COMMENT '异常率',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. sm_ai_analysis AI分析记录表（V1.3 字段精简）
CREATE TABLE IF NOT EXISTS sm_ai_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    consult_id BIGINT NOT NULL COMMENT '关联线上面诊记录ID',
    patient_id BIGINT COMMENT '患者ID',
    doctor_id BIGINT COMMENT '医生ID',
    condition_analysis TEXT COMMENT '病情分析',
    diagnosis_suggestion TEXT COMMENT '诊断建议',
    differential_diagnosis TEXT COMMENT '鉴别提示',
    medication_suggestion TEXT COMMENT '用药建议',
    risk_level VARCHAR(20) COMMENT '风险等级',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_consult (consult_id),
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. sm_online_consult_record 线上面诊记录表（原 sm_online_consult 改名+精简）
CREATE TABLE IF NOT EXISTS sm_online_consult_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    patient_id BIGINT NOT NULL COMMENT '患者ID',
    doctor_id BIGINT NOT NULL COMMENT '医生ID',
    department VARCHAR(50) COMMENT '科室',
    status VARCHAR(30) COMMENT '面诊状态：PENDING/REPLIED/CLOSED',
    start_time TIMESTAMP NULL COMMENT '开始时间',
    end_time TIMESTAMP NULL COMMENT '结束时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. sm_consult_message 问诊消息表（V1.3 精简，去掉敏感标记）
CREATE TABLE IF NOT EXISTS sm_consult_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    consult_id BIGINT NOT NULL COMMENT '关联 sm_online_consult_record.id',
    sender VARCHAR(20) NOT NULL COMMENT 'PATIENT/DOCTOR/SYSTEM',
    message_type VARCHAR(20) COMMENT 'TEXT/IMAGE',
    content TEXT COMMENT '文本内容',
    image_url VARCHAR(500) COMMENT '图片地址',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_consult (consult_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. sm_pre_consult 智能预问诊表（V1.3 新增，挂号前 AI 对话）
CREATE TABLE IF NOT EXISTS sm_pre_consult (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    patient_id BIGINT NOT NULL COMMENT '患者ID',
    session_id VARCHAR(100) NOT NULL COMMENT '会话标识',
    sender VARCHAR(20) NOT NULL COMMENT 'PATIENT/AI/SYSTEM',
    message_type VARCHAR(20) COMMENT 'TEXT/IMAGE',
    content TEXT COMMENT '文字内容',
    image_url VARCHAR(500) COMMENT '图片地址',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_patient (patient_id),
    INDEX idx_session (session_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. sm_model_config 模型配置表（V1.3 重新设计）
CREATE TABLE IF NOT EXISTS sm_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    config_name VARCHAR(100) COMMENT '配置名称',
    type VARCHAR(20) COMMENT '类型：向量模型/聊天模型',
    provider VARCHAR(100) COMMENT '服务商',
    model_name VARCHAR(100) COMMENT '模型名称',
    api_url VARCHAR(500) COMMENT '接口地址',
    api_key VARCHAR(500) COMMENT 'API Key',
    max_tokens INT COMMENT '最大 token 量',
    enabled TINYINT(1) DEFAULT 1 COMMENT '启用状态',
    remark VARCHAR(500) COMMENT '备注',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. sm_prompt_template 提示词模板表（V1.3 新增）
CREATE TABLE IF NOT EXISTS sm_prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_code VARCHAR(100) UNIQUE COMMENT '模板编码',
    template_name VARCHAR(200) COMMENT '模板名称',
    scene VARCHAR(20) COMMENT '使用场景：PATIENT/DOCTOR/ADMIN',
    variables TEXT COMMENT '变量名称，如 {doctorId}{patientId}',
    prompt_content TEXT COMMENT 'prompt 内容',
    enabled TINYINT(1) DEFAULT 1 COMMENT '启用状态',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 17. sm_prescription 处方表（V1.3 与明细表合并，按 prescription_no 分组）
CREATE TABLE IF NOT EXISTS sm_prescription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    prescription_no VARCHAR(50) COMMENT '处方编号（分组）',
    visit_id BIGINT COMMENT '关联就诊进度ID',
    appointment_id BIGINT COMMENT '关联挂号单ID',
    patient_id BIGINT NOT NULL COMMENT '患者ID',
    doctor_id BIGINT NOT NULL COMMENT '开方医生ID',
    department VARCHAR(50) COMMENT '开方科室',
    diagnosis VARCHAR(500) COMMENT '诊断',
    advice VARCHAR(1000) COMMENT '医嘱',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT/ISSUED',
    drug_name VARCHAR(200) NOT NULL COMMENT '药品名称',
    specification VARCHAR(200) COMMENT '规格',
    dosage VARCHAR(100) COMMENT '单次剂量',
    frequency VARCHAR(100) COMMENT '频次（如每日3次）',
    days INT COMMENT '用药天数',
    quantity VARCHAR(100) COMMENT '数量',
    usage_method VARCHAR(200) COMMENT '用法（口服/外用/静滴等）',
    remark VARCHAR(500) COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_prescription_no (prescription_no),
    INDEX idx_visit (visit_id),
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 18. sm_medical_knowledge_base 医学知识库表（V1.3 新增）
CREATE TABLE IF NOT EXISTS sm_medical_knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(200) COMMENT '知识库名称',
    type VARCHAR(20) COMMENT 'CLINICAL临床指南库/DEPARTMENT科室介绍库',
    description VARCHAR(500) COMMENT '知识库说明',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 19. sm_knowledge_document 知识文档库表（V1.3 新增）
CREATE TABLE IF NOT EXISTS sm_knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    document_name VARCHAR(200) COMMENT '文档名称',
    type VARCHAR(50) COMMENT '文档类型',
    knowledge_base_id BIGINT COMMENT '所属知识库',
    parse_status VARCHAR(20) COMMENT '解析状态',
    chunk_count INT COMMENT '片段数量',
    original_text LONGTEXT COMMENT '原文',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_base (knowledge_base_id),
    INDEX idx_parse_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 20. sm_knowledge_chunk 知识片段表（V1.3 新增）
CREATE TABLE IF NOT EXISTS sm_knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    document_id BIGINT NOT NULL COMMENT '来源文档ID',
    chunk_index INT COMMENT '片段序号',
    chunk_title VARCHAR(200) COMMENT '片段标题',
    content TEXT COMMENT '片段具体内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_document (document_id),
    INDEX idx_chunk_index (chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 21. sm_vector_index 向量化与检索表（V1.3 新增，不存向量数据）
CREATE TABLE IF NOT EXISTS sm_vector_index (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    chunk_id BIGINT COMMENT '关联知识片段ID',
    chunk_title VARCHAR(200) COMMENT '片段标题',
    source_document_id BIGINT COMMENT '来源文档ID',
    vector_model VARCHAR(100) COMMENT '向量模型',
    dimension INT COMMENT '维度',
    status VARCHAR(20) COMMENT '状态',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_chunk (chunk_id),
    INDEX idx_source_document (source_document_id),
    INDEX idx_vector_model (vector_model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;