-- =============================================
-- 智能医疗系统 种子数据
-- =============================================

-- 患者 (密码均为 123456, BCrypt hash)
INSERT INTO sm_patient (username, password, real_name, gender, phone, id_card, status) VALUES
('zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张三', '男', '13800138001', '110101199001011234', 'ACTIVE'),
('lisi',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李四', '女', '13800138002', '110101199202022345', 'ACTIVE'),
('wangwu',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王五', '男', '13800138003', '110101198803033456', 'ACTIVE');

-- 医生（密码均为 123456, BCrypt hash）
-- 现有 3 位医生补充 gender/avatar/bio，并新增儿科、妇科、外科、神经外科、骨科、皮肤科、眼科、急诊科、中医科共 13 位医生
INSERT INTO sm_doctor (employee_no, password, real_name, gender, title, phone, department, avatar, bio, online_status, status) VALUES
('D001', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李医生', '女', '主任医师',   '13900139001', '内分泌科', NULL, '从事内分泌临床工作 20 年，擅长糖尿病、甲状腺疾病、骨质疏松的诊治，发表 SCI 论文 12 篇。', 'ONLINE',  'ACTIVE'),
('D002', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王医生', '男', '副主任医师', '13900139002', '心内科',   NULL, '擅长冠心病、高血压、心律失常的综合治疗，完成冠脉介入手术 500 余例。',           'ONLINE',  'ACTIVE'),
('D003', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张医生', '男', '主治医师',   '13900139003', '呼吸科',   NULL, '擅长慢性咳嗽、哮喘、慢阻肺的诊治，对肺部感染性疾病有丰富经验。',             'ONLINE',  'ACTIVE'),
('D004', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '陈医生', '女', '主任医师',   '13900139004', '儿科',     NULL, '儿科主任医师，从业 25 年，擅长儿童呼吸系统疾病、生长发育评估、新生儿保健。',   'ONLINE',  'ACTIVE'),
('D005', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵医生', '男', '副主任医师', '13900139005', '儿科',     NULL, '擅长儿童消化系统疾病、过敏性疾病及儿童保健，主持省级课题 2 项。',             'ONLINE',  'ACTIVE'),
('D006', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '刘医生', '女', '主任医师',   '13900139006', '妇科',     NULL, '妇产科主任医师，擅长妇科肿瘤、盆底疾病、生殖内分泌及更年期综合征诊治。',         'ONLINE',  'ACTIVE'),
('D007', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '孙医生', '女', '主治医师',   '13900139007', '妇科',     NULL, '擅长妇科炎症、月经失调、孕前检查及孕期保健，亲和力强。',                       'ONLINE',  'ACTIVE'),
('D008', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '周医生', '男', '主任医师',   '13900139008', '外科',     NULL, '普外科主任医师，擅长胃肠外科、肝胆外科及腹腔镜微创手术，年手术量 800+ 例。',     'ONLINE',  'ACTIVE'),
('D009', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '吴医生', '男', '副主任医师', '13900139009', '外科',     NULL, '擅长甲状腺、乳腺疾病及疝外科，微创手术经验丰富。',                               'ONLINE',  'ACTIVE'),
('D010', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '郑医生', '男', '主任医师',   '13900139010', '神经外科', NULL, '神经外科主任医师，擅长脑肿瘤、脑血管病及颅脑外伤的手术治疗，主刀手术 2000+ 例。','ONLINE',  'ACTIVE'),
('D011', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '钱医生', '男', '副主任医师', '13900139011', '神经外科', NULL, '擅长功能神经外科（癫痫、帕金森）及脊柱脊髓疾病的微创治疗。',                     'ONLINE',  'ACTIVE'),
('D012', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '冯医生', '男', '主任医师',   '13900139012', '骨科',     NULL, '骨科主任医师，擅长关节置换、脊柱外科及运动损伤，发表核心期刊论文 30 余篇。',     'ONLINE',  'ACTIVE'),
('D013', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '蒋医生', '女', '副主任医师', '13900139013', '皮肤科',   NULL, '擅长痤疮、湿疹、荨麻疹及皮肤美容激光治疗，皮肤镜诊断经验丰富。',                 'ONLINE',  'ACTIVE'),
('D014', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '沈医生', '男', '主任医师',   '13900139014', '眼科',     NULL, '眼科主任医师，擅长白内障超声乳化、青光眼手术及近视激光矫正，年手术 1500+ 例。',  'ONLINE',  'ACTIVE'),
('D015', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '韩医生', '男', '主任医师',   '13900139015', '急诊科',   NULL, '急诊医学科主任医师，擅长急危重症抢救、多发伤救治及中毒处理，抢救成功率 95%+。',   'ONLINE',  'ACTIVE'),
('D016', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '杨医生', '女', '副主任医师', '13900139016', '中医科',   NULL, '擅长中医内科调理、针灸推拿、慢性病康复及亚健康调理，师从国家级名老中医。',       'ONLINE',  'ACTIVE');

-- 管理员 (Demo简化无密码登录)
INSERT INTO sm_admin (employee_no, phone, id_card, real_name, status) VALUES
('A001', '13700137001', '110101198001010001', '系统管理员', 'ACTIVE');

-- 挂号（张三7条覆盖各状态；状态：PENDING待确认/CONFIRMED待就诊/FINISHED已完成/CANCELLED已取消）
INSERT INTO sm_appointment (patient_id, doctor_id, department, appointment_time, status) VALUES
(1, 1, '内分泌科', NOW() - INTERVAL 10 DAY, 'FINISHED'),
(1, 1, '内分泌科', NOW() - INTERVAL 8 DAY,  'CANCELLED'),
(1, 2, '心内科',   NOW() - INTERVAL 6 DAY,  'CANCELLED'),
(1, 1, '内分泌科', NOW() - INTERVAL 2 DAY,  'FINISHED'),
(2, 1, '内分泌科', NOW() - INTERVAL 1 DAY,  'CONFIRMED'),
(1, 2, '心内科',   NOW() + INTERVAL 1 DAY,  'PENDING'),
(1, 3, '呼吸科',   NOW() + INTERVAL 3 DAY,  'CONFIRMED'),
(1, 1, '内分泌科', NOW() + INTERVAL 5 DAY,  'PENDING');

-- 就诊进度
INSERT INTO sm_visit (appointment_id, patient_id, doctor_id, department, status, progress_nodes) VALUES
(1, 1, 1, '内分泌科', 'FINISHED',  '[{"name":"挂号","time":"2026-09-14 09:00:00"},{"name":"候诊","time":"2026-09-14 09:30:00"},{"name":"就诊","time":"2026-09-14 10:00:00"},{"name":"完成","time":"2026-09-14 10:45:00"}]'),
(2, 2, 1, '内分泌科', 'WAITING',   '[{"name":"挂号","time":"2026-09-15 08:30:00"},{"name":"候诊","time":"2026-09-15 09:00:00"}]'),
(NULL, 1, 1, '内分泌科', 'IN_PROGRESS', '[{"name":"AI问诊","time":"2026-09-15 14:00:00"},{"name":"医生接诊","time":"2026-09-15 14:15:00"}]');

-- 报告
INSERT INTO sm_report (patient_id, report_type, image_url, uploaded_by, status) VALUES
(1, '血糖报告', 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=400', 1, 'UPLOADED'),
(1, '血脂报告', 'https://images.unsplash.com/photo-1559757148-5c350d0d3c56?w=400', 1, 'UPLOADED'),
(2, '血常规',   'https://images.unsplash.com/photo-1582719471384-894fbb16e074?w=400', 2, 'UPLOADED');

-- 报告AI解读
INSERT INTO sm_report_interpretation (report_id, recognized_content, analysis, abnormal_items, reference_ranges, confidence, `references`, need_human, disclaimer) VALUES
(1, '空腹血糖 7.2 mmol/L, 餐后2小时血糖 9.1 mmol/L', '空腹血糖偏高，提示可能存在糖代谢异常，建议进一步检查糖化血红蛋白。', '[{"item":"空腹血糖","value":"7.2","unit":"mmol/L","status":"偏高"}]', '[{"item":"空腹血糖","range":"3.9-6.1","unit":"mmol/L"}]', 0.8621, '["中国2型糖尿病防治指南2020版"]', 0, '本解读仅供参考，不能替代医生诊断，请及时就医。'),
(2, '总胆固醇 5.8 mmol/L, 甘油三酯 2.3 mmol/L', '血脂偏高，建议调整饮食结构，增加运动，必要时药物干预。', '[{"item":"总胆固醇","value":"5.8","unit":"mmol/L","status":"偏高"},{"item":"甘油三酯","value":"2.3","unit":"mmol/L","status":"偏高"}]', '[{"item":"总胆固醇","range":"<5.2","unit":"mmol/L"},{"item":"甘油三酯","range":"<1.7","unit":"mmol/L"}]', 0.8456, '["中国成人血脂异常防治指南2016年修订版"]', 0, '本解读仅供参考，不能替代医生诊断，请及时就医。');

-- 线上面诊
INSERT INTO sm_online_consult (patient_id, doctor_id, department, chief_complaint, description, status, supplement_count) VALUES
(1, 1, '内分泌科', '血糖偏高', '最近体检发现血糖偏高，空腹7.2，想咨询一下需要注意什么。', 'AI_ANALYZED', 1),
(2, 1, '内分泌科', '甲状腺结节', '体检发现甲状腺有结节，想了解情况是否严重。', 'DOCTOR_REVIEWING', 0),
(1, 3, '呼吸科', '咳嗽两周', '干咳持续两周无好转，无发热无痰。', 'REPLIED', 2);

-- 问诊消息
INSERT INTO sm_consult_message (consult_id, sender, message_type, content, sensitive_flag) VALUES
(1, 'PATIENT', 'TEXT', '李医生您好，我最近空腹血糖7.2，有点担心。', 0),
(1, 'DOCTOR',  'TEXT', '您好，方便发一下报告吗？我帮您看看。', 0),
(1, 'PATIENT', 'IMAGE', NULL, 0),
(1, 'PATIENT', 'TEXT', '已经上传了血糖报告，请查看。', 0),
(2, 'PATIENT', 'TEXT', '医生好，我体检发现甲状腺结节，该怎么办？', 0),
(3, 'PATIENT', 'TEXT', '咳嗽两周了，干咳无痰无发热。', 0),
(3, 'DOCTOR',  'TEXT', '建议您来医院拍个胸片排除一下肺部问题。', 0);

-- 医生回复
INSERT INTO sm_consult_reply (consult_id, doctor_id, ai_draft, final_reply, replied_at) VALUES
(3, 3, '根据您的描述，可能是上呼吸道感染或支气管炎...', '您好，根据您咳嗽两周、干咳无痰无发热的情况，建议您到医院呼吸科就诊，拍个胸片排除肺部问题。近期注意多休息，多喝温水，避免刺激性食物。如果有发热、咳痰、呼吸困难等症状请及时就医。', NOW());

-- 病历
INSERT INTO sm_medical_note (consult_id, patient_id, doctor_id, draft, status, modified_count, signed_at) VALUES
(NULL, 1, 1, '患者张三，主诉血糖偏高。空腹血糖7.2mmol/L。建议控制饮食，适量运动，复查糖化血红蛋白。', 'SIGNED', 0, NOW() - INTERVAL 1 DAY),
(3,    1, 3, '患者张三，主诉咳嗽两周。干咳无痰无发热。建议胸片检查，注意休息。', 'DRAFT', 1, NULL);

-- 病历版本
INSERT INTO sm_medical_note_version (note_id, version, content, modified_by) VALUES
(1, 1, '患者张三，主诉血糖偏高。建议进一步检查。', 1),
(2, 1, '患者张三，主诉咳嗽两周。建议到院就诊。', 3),
(2, 2, '患者张三，主诉咳嗽两周。干咳无痰无发热。建议胸片检查，注意休息。', 3);

-- 知识库
INSERT INTO sm_knowledge_base (title, type, content, source) VALUES
('2型糖尿病诊疗指南', '指南', '糖尿病是一种以高血糖为特征的代谢性疾病。诊断标准：空腹血糖≥7.0mmol/L或餐后2小时血糖≥11.1mmol/L。治疗包括饮食控制、运动、口服药物和胰岛素。', '中国2型糖尿病防治指南2020版'),
('高血压用药指南', '药品', 'ACEI类药物适用于合并糖尿病或肾病的高血压患者。注意监测肾功能和血钾水平。', '中国高血压防治指南2018年修订版'),
('甲状腺结节诊疗路径', '路径', '甲状腺结节评估流程：超声检查→TI-RADS分级→可疑结节建议穿刺活检→根据病理结果决定治疗方案。', '中华医学会内分泌学分会');

-- 模型配置
INSERT INTO sm_model_config (model_name, version, deploy_type, status) VALUES
('smart-medical-ai', 'v1.2.0', '本地', '运行中'),
('ocr-model', 'v2.0.1', '外部',  '运行中');

-- 数据质量
INSERT INTO sm_data_quality (data_count, labeled_count, missing_rate, abnormal_rate) VALUES
(1280, 960, 0.0231, 0.0156);

-- AI会话
INSERT INTO sm_ai_chat_session (user_id, user_role, session_key, status) VALUES
(1, 'PATIENT', 'sess_p_001', 'CLOSED'),
(2, 'PATIENT', 'sess_p_002', 'ACTIVE');

-- AI消息
INSERT INTO sm_ai_chat_message (session_id, sender, content, confidence, need_human) VALUES
(1, 'USER',  '我最近血糖偏高怎么办？', NULL, 0),
(1, 'AI',    '您好！血糖偏高建议：1.控制饮食，减少精制碳水化合物摄入；2.每天30分钟中等强度运动；3.定期监测血糖。请问您有做过糖化血红蛋白检查吗？', 0.89, 0),
(2, 'USER',  '甲状腺结节需要手术吗？', NULL, 0);

-- AI分析记录
INSERT INTO sm_ai_analysis (consult_id, patient_id, analysis_type, possible_causes, differential, diagnosis_suggestions, medication_suggestions, required_materials, risk_level, confidence, `references`, disclaimer) VALUES
(1, 1, 'CLINICAL',
 '["2型糖尿病","糖耐量受损"]',
 '["甲状腺功能异常","应激性高血糖"]',
 '["建议复查空腹血糖和糖化血红蛋白","建议进行OGTT试验"]',
 '["暂不建议用药，先生活方式干预"]',
 '["近3天血糖监测记录","糖化血红蛋白检查结果"]',
 'MEDIUM', 0.8512,
 '["中国2型糖尿病防治指南2020版"]',
 '本分析仅供医生参考，不能替代医生诊断。');

-- 敏感内容 (模拟1条待审核)
INSERT INTO sm_sensitive_content (user_id, role, content, sensitive_type, sensitive_level, action_taken, review_status) VALUES
(1, 'PATIENT', '这里测试一条敏感内容', '测试内容', 'LOW', 'BLOCK', 'PENDING');

-- 审计日志
INSERT INTO sm_audit_log (user_id, role, action, target_type, target_id, detail, ip, action_taken) VALUES
(1, 'PATIENT', 'LOGIN',    'patient',  1, '患者登录成功',     '127.0.0.1', 'ALLOW'),
(1, 'PATIENT', 'APPOINT',  'appointment', 2, '创建挂号',    '127.0.0.1', 'ALLOW'),
(1, 'PATIENT', 'UPLOAD',   'report',   1, '上传报告',         '127.0.0.1', 'ALLOW'),
(1, 'PATIENT', 'AI_CHAT',  'ai_chat',  1, '调用AI客服',       '127.0.0.1', 'ALLOW'),
(1, 'PATIENT', 'CONSULT',  'consult',  1, '发起问诊',         '127.0.0.1', 'ALLOW');
