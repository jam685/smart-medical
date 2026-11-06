-- =============================================
-- 智能医疗系统 种子数据（数据库设计 V1.3 · 21 张表）
-- =============================================

-- 患者 (密码均为 SmartMed@2026, BCrypt hash；演示环境可用 --spring.profiles.active=demo 走万能口令 123456)
INSERT INTO sm_patient (username, password, real_name, gender, phone, id_card, avatar, status) VALUES
('zhangsan', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '张三', '男', '13800138001', '110101199001011234', NULL, 'ACTIVE'),
('lisi',     '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '李四', '女', '13800138002', '110101199202022345', NULL, 'ACTIVE'),
('wangwu',   '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '王五', '男', '13800138003', '110101198803033456', NULL, 'ACTIVE');

-- 医生（密码均为 123456, BCrypt hash；覆盖多个科室便于挂号/问诊）
INSERT INTO sm_doctor (employee_no, password, real_name, gender, title, phone, department, avatar, bio, online_status, status) VALUES
('D001', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '李医生', '女', '主任医师',   '13900139001', '内分泌科', NULL, '从事内分泌临床工作 20 年，擅长糖尿病、甲状腺疾病、骨质疏松的诊治。', 'ONLINE',  'ACTIVE'),
('D002', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '王医生', '男', '副主任医师', '13900139002', '心内科',   NULL, '擅长冠心病、高血压、心律失常的综合治疗。',                                'ONLINE',  'ACTIVE'),
('D003', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '张医生', '男', '主治医师',   '13900139003', '呼吸科',   NULL, '擅长慢性咳嗽、哮喘、慢阻肺的诊治。',                                      'ONLINE',  'ACTIVE'),
('D004', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '陈医生', '女', '主任医师',   '13900139004', '儿科',     NULL, '擅长儿童呼吸系统疾病、生长发育评估、新生儿保健。',                         'ONLINE',  'ACTIVE'),
('D005', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '赵医生', '男', '副主任医师', '13900139005', '儿科',     NULL, '擅长儿童消化系统疾病、过敏性疾病及儿童保健。',                             'ONLINE',  'ACTIVE'),
('D006', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '刘医生', '女', '主任医师',   '13900139006', '妇科',     NULL, '擅长妇科肿瘤、盆底疾病、生殖内分泌及更年期综合征诊治。',                    'ONLINE',  'ACTIVE'),
('D007', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '孙医生', '女', '主治医师',   '13900139007', '妇科',     NULL, '擅长妇科炎症、月经失调、孕前检查及孕期保健。',                             'ONLINE',  'ACTIVE'),
('D008', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '周医生', '男', '主任医师',   '13900139008', '外科',     NULL, '擅长胃肠外科、肝胆外科及腹腔镜微创手术。',                                 'ONLINE',  'ACTIVE'),
('D009', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '吴医生', '男', '副主任医师', '13900139009', '外科',     NULL, '擅长甲状腺、乳腺疾病及疝外科，微创手术经验丰富。',                         'ONLINE',  'ACTIVE'),
('D010', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '郑医生', '男', '主任医师',   '13900139010', '神经外科', NULL, '擅长脑肿瘤、脑血管病及颅脑外伤的手术治疗。',                               'ONLINE',  'ACTIVE'),
('D011', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '钱医生', '男', '副主任医师', '13900139011', '神经外科', NULL, '擅长功能神经外科及脊柱脊髓疾病的微创治疗。',                               'ONLINE',  'ACTIVE'),
('D012', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '冯医生', '男', '主任医师',   '13900139012', '骨科',     NULL, '擅长关节置换、脊柱外科及运动损伤。',                                       'ONLINE',  'ACTIVE'),
('D013', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '蒋医生', '女', '副主任医师', '13900139013', '皮肤科',   NULL, '擅长痤疮、湿疹、荨麻疹及皮肤美容激光治疗。',                               'ONLINE',  'ACTIVE'),
('D014', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '沈医生', '男', '主任医师',   '13900139014', '眼科',     NULL, '擅长白内障超声乳化、青光眼手术及近视激光矫正。',                           'ONLINE',  'ACTIVE'),
('D015', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '韩医生', '男', '主任医师',   '13900139015', '急诊科',   NULL, '擅长急危重症抢救、多发伤救治及中毒处理。',                                 'ONLINE',  'ACTIVE'),
('D016', '$2a$10$9ZJ7kiVkmxS4fFs6Bc1OZOLLeRnkJTqdLCfjKsXqgzxbmvap.Nxaq', '杨医生', '女', '副主任医师', '13900139016', '中医科',   NULL, '擅长中医内科调理、针灸推拿、慢性病康复及亚健康调理。',                     'ONLINE',  'ACTIVE');

-- 管理员 (Demo简化登录)
INSERT INTO sm_admin (employee_no, phone, id_card, real_name, status) VALUES
('A001', '13700137001', '110101198001010001', '系统管理员', 'ACTIVE');

-- 挂号（张三多条覆盖各状态；PENDING待确认/CONFIRMED待就诊/FINISHED已完成/CANCELLED已取消）
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
(1, 1, 1, '内分泌科', 'FINISHED',    '[{"name":"挂号","time":"2026-09-14 09:00:00"},{"name":"候诊","time":"2026-09-14 09:30:00"},{"name":"就诊","time":"2026-09-14 10:00:00"},{"name":"完成","time":"2026-09-14 10:45:00"}]'),
(2, 2, 1, '内分泌科', 'WAITING',     '[{"name":"挂号","time":"2026-09-15 08:30:00"},{"name":"候诊","time":"2026-09-15 09:00:00"}]'),
(4, 1, 1, '内分泌科', 'IN_PROGRESS','[{"name":"AI问诊","time":"2026-09-15 14:00:00"},{"name":"医生接诊","time":"2026-09-15 14:15:00"}]');

-- 报告
INSERT INTO sm_report (patient_id, report_type, image_url, uploaded_by, status) VALUES
(1, '血糖报告', 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=400', 1, 'UPLOADED'),
(1, '血脂报告', 'https://images.unsplash.com/photo-1559757148-5c350d0d3c56?w=400', 1, 'UPLOADED'),
(2, '血常规',   'https://images.unsplash.com/photo-1582719471384-894fbb16e074?w=400', 2, 'UPLOADED');

-- 线上面诊记录（原 sm_online_consult 改名）
INSERT INTO sm_online_consult_record (patient_id, doctor_id, department, status, start_time, end_time) VALUES
(1, 1, '内分泌科', 'CLOSED',   NOW() - INTERVAL 2 DAY,   NOW() - INTERVAL 2 DAY + INTERVAL 1 HOUR),
(2, 1, '内分泌科', 'PENDING',  NOW() - INTERVAL 1 DAY,   NULL),
(1, 3, '呼吸科',   'CLOSED',   NOW() - INTERVAL 3 DAY,   NOW() - INTERVAL 3 DAY + INTERVAL 2 HOUR);

-- 问诊消息（去掉 sensitive_flag）
INSERT INTO sm_consult_message (consult_id, sender, message_type, content, image_url) VALUES
(1, 'PATIENT', 'TEXT',  '李医生您好，我最近空腹血糖7.2，有点担心。', NULL),
(1, 'DOCTOR',  'TEXT',  '您好，方便发一下报告吗？我帮您看看。', NULL),
(1, 'PATIENT', 'IMAGE', NULL, 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=400'),
(1, 'PATIENT', 'TEXT',  '已经上传了血糖报告，请查看。', NULL),
(2, 'PATIENT', 'TEXT',  '医生好，我体检发现甲状腺结节，该怎么办？', NULL),
(3, 'PATIENT', 'TEXT',  '咳嗽两周了，干咳无痰无发热。', NULL),
(3, 'DOCTOR',  'TEXT',  '建议您来医院拍个胸片排除一下肺部问题。', NULL);

-- 智能预问诊（V1.3 新增）
INSERT INTO sm_pre_consult (patient_id, session_id, sender, message_type, content, image_url) VALUES
(1, 'pre_001', 'PATIENT', 'TEXT', '我最近总觉得口渴、尿多，是不是血糖有问题？', NULL),
(1, 'pre_001', 'AI',      'TEXT', '您好！口渴、多饮、多尿确实可能与血糖异常有关。建议您先到院测一下空腹血糖和糖化血红蛋白。请问最近体重有明显下降吗？为了挂到合适的科室，方便告诉我您的年龄吗？', NULL),
(1, 'pre_001', 'PATIENT', 'TEXT', '35岁，体重好像轻了一点。', NULL),
(1, 'pre_001', 'AI',      'TEXT', '了解。综合您的情况，建议挂内分泌科做进一步检查。请在挂号前先控制近期的饮食，避免剧烈波动。', NULL);

-- 病历
INSERT INTO sm_medical_note (consult_id, patient_id, doctor_id, draft, status, modified_count, signed_at) VALUES
(NULL, 1, 1, '患者张三，主诉血糖偏高。空腹血糖7.2mmol/L。建议控制饮食，适量运动，复查糖化血红蛋白。', 'SIGNED', 0, NOW() - INTERVAL 1 DAY),
(3,    1, 3, '患者张三，主诉咳嗽两周。干咳无痰无发热。建议胸片检查，注意休息。', 'DRAFT', 1, NULL);

-- 病历版本（V1.3 精简：去掉 content，仅有版本轨迹）
INSERT INTO sm_medical_note_version (note_id, version, modified_by) VALUES
(1, 1, 1),
(2, 1, 3),
(2, 2, 3);

-- AI 分析记录（V1.3 字段精简）
INSERT INTO sm_ai_analysis (consult_id, patient_id, doctor_id, condition_analysis, diagnosis_suggestion, differential_diagnosis, medication_suggestion, risk_level) VALUES
(1, 1, 1, '患者诉口渴多尿，体重下降，空腹血糖7.2mmol/L，高度提示可能为糖代谢异常。', '建议复查空腹血糖与糖化血红蛋白，必要时行OGTT试验。', '需鉴别应激性高血糖、甲状腺功能亢进导致的继发性血糖升高。', '暂不建议用药，先以生活方式干预为主，遵医嘱。', 'MEDIUM');

-- 模型配置（V1.3 重新设计）
INSERT INTO sm_model_config (config_name, type, provider, model_name, api_url, api_key, max_tokens, enabled, remark) VALUES
('临床分析聊天模型', '聊天模型', '本地模拟', 'demo-chat', 'http://localhost:8000/chat', 'sk-demo', 2048, 1, 'V2.3 阶段先用本地模拟实现'),
('图片理解向量模型', '向量模型', '本地模拟', 'demo-embed', 'http://localhost:8000/embed', 'sk-demo', NULL, 1, 'V2.3 阶段先用本地模拟实现');

-- 提示词模板（V1.3 新增）
INSERT INTO sm_prompt_template (template_code, template_name, scene, variables, prompt_content, enabled) VALUES
('CLINICAL_ANALYSIS', '病情分析', 'DOCTOR', '{patientId}{consultId}', '你是一位具有多年临床经验的医生。请根据患者病情分析其可能的病因、诊断建议、鉴别诊断和用药建议。', 1),
('REPLY_DRAFT', '辅助回复草稿', 'DOCTOR', '{consultId}', '请根据问诊上下文，为医生生成一段给患者的礼貌、专业的回复草稿。', 1),
('PRE_CONSULT', '预问诊引导', 'PATIENT', '{patientId}', '你是医院智能预问诊助手，只做症状采集与就诊引导，不诊断、不开方。请用提问引导患者描述病情。', 1),
('IMAGE_ANALYSIS', '医疗图片分析', 'DOCTOR', '{imageUrl}', '请分析这张医疗检验报告/影像图片，给出识别内容、异常项、参考范围与免责声明。', 1);

-- 医学知识库（V1.3 新增）
INSERT INTO sm_medical_knowledge_base (name, type, description, status) VALUES
('临床指南库', 'CLINICAL',    '为面诊 agent 提供临床诊疗常规参考', 'ACTIVE'),
('科室介绍库', 'DEPARTMENT',  '为预问诊 agent 提供科室介绍与挂号引导', 'ACTIVE');

-- 知识文档（V1.3 新增）
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text) VALUES
('糖尿病诊疗指南摘要', '指南', 1, 'PARSED', 2, '糖尿病以慢性高血糖为特征。诊断：空腹血糖≥7.0mmol/L或OGTT 2h血糖≥11.1mmol/L。治疗：生活方式干预为基础，必要时口服药或胰岛素。'),
('高血压防治指南摘要', '指南', 1, 'PARSED', 1, '成人血压≥140/90mmHg为高血压。治疗包括限盐、运动与药物，合并糖尿病或肾病者优先ACEI/ARB。'),
('内分泌科就诊指引', '科室', 2, 'PARSED', 1, '内分泌科诊治糖尿病、甲状腺疾病、骨质疏松等。如有糖尿病典型症状建议优先挂内分泌科。');

-- 知识片段（V1.3 新增）
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content) VALUES
(1, 1, '糖尿病诊断标准',  '糖尿病诊断：空腹血糖≥7.0mmol/L 或 OGTT 2h 血糖≥11.1mmol/L，或有糖尿病典型症状且随机血糖≥11.1mmol/L。'),
(1, 2, '糖尿病治疗原则',  '以生活方式干预为基础，控制饮食、规律运动；效果不佳时根据情况使用口服降糖药或胰岛素。'),
(2, 1, '高血压诊断与治疗','成人非同日三次诊室血压≥140/90mmHg 诊断为高血压；治疗包括限盐、运动与药物，合并肾病者优先 ACEI/ARB。'),
(3, 1, '内分泌科就诊指引','内分泌科诊治糖尿病、甲状腺疾病、骨质疏松等。如有糖尿病典型症状（多饮多尿、体重下降）建议优先挂内分泌科。');

-- 向量化检索记录（V1.3 新增，不存向量数据）
INSERT INTO sm_vector_index (chunk_id, chunk_title, source_document_id, vector_model, dimension, status) VALUES
(1, '糖尿病诊断标准',   1, 'demo-embed', 768, 'READY'),
(2, '糖尿病治疗原则',   1, 'demo-embed', 768, 'READY'),
(3, '高血压诊断与治疗', 2, 'demo-embed', 768, 'READY'),
(4, '内分泌科就诊指引', 3, 'demo-embed', 768, 'READY');

-- 处方（V1.3 与明细表合并：同一 prescription_no 多条记录即一组药品明细）
INSERT INTO sm_prescription (prescription_no, visit_id, appointment_id, patient_id, doctor_id, department, diagnosis, advice, status, drug_name, specification, dosage, frequency, days, quantity, usage_method, remark) VALUES
('RX20260915001', 1, 1, 1, 1, '内分泌科', '2型糖尿病（初诊）', '控制饮食、规律运动，监测血糖，定期复查糖化血红蛋白，如有不适及时就医。', 'ISSUED', '二甲双胍片', '0.5g/片', '0.5g', '每日2次', 30, '60片', '口服，早晚饭后', '随餐服用可减少胃肠道反应'),
('RX20260915001', 1, 1, 1, 1, '内分泌科', '2型糖尿病（初诊）', '控制饮食、规律运动，监测血糖，定期复查糖化血红蛋白，如有不适及时就医。', 'ISSUED', '阿卡波糖片', '50mg/片', '50mg', '每日3次', 30, '90片', '口服，进餐时与口食物同嚼服', '未按时进食无需服药');

-- 数据质量
INSERT INTO sm_data_quality (data_count, labeled_count, missing_rate, abnormal_rate) VALUES
(1280, 960, 0.0231, 0.0156);

-- 审计日志
INSERT INTO sm_audit_log (user_id, role, action, target_type, target_id, detail, ip, action_taken) VALUES
(1, 'PATIENT', 'LOGIN',    'patient',      1, '患者登录成功', '127.0.0.1', 'ALLOW'),
(1, 'PATIENT', 'APPOINT',  'appointment',  2, '创建挂号',     '127.0.0.1', 'ALLOW'),
(1, 'PATIENT', 'UPLOAD',   'report',       1, '上传报告',     '127.0.0.1', 'ALLOW'),
(1, 'PATIENT', 'PRE_CONSULT', 'pre_consult', 1, '发起预问诊', '127.0.0.1', 'ALLOW'),
(1, 'PATIENT', 'CONSULT',  'consult',      1, '发起问诊',     '127.0.0.1', 'ALLOW');