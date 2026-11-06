-- ============================================================================
-- migration-v7-new-depts.sql
-- 新增耳鼻喉科、肛肠科、精神科 3 个科室：每科 1 名医生 + 科室介绍文档（4 chunk）
-- 医生统一密码 123456（BCrypt hash 已硬编码）
-- 知识库：科室介绍库 id=2，所有新文档归属 knowledge_base_id=2
-- ============================================================================

-- -------------------- 1. 医生 --------------------
INSERT INTO sm_doctor
  (employee_no, password, real_name, gender, title, phone, department, bio, online_status, status)
VALUES
  ('D016', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
   '徐医生', '女', '副主任医师', '13900000016', '耳鼻喉科',
   '副主任医师，擅长慢性鼻炎、中耳炎、过敏性鼻炎、鼻息肉、咽喉炎的诊治，对儿童耳鼻喉常见病有丰富经验。',
   'ONLINE', 'ACTIVE'),
  ('D018', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
   '朱医生', '男', '主治医师', '13900000018', '肛肠科',
   '主治医师，擅长痔疮、肛裂、肛周脓肿、肛瘘、慢性便秘、直肠炎等肛肠常见病的保守与手术治疗。',
   'ONLINE', 'ACTIVE'),
  ('D019', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
   '秦医生', '女', '副主任医师', '13900000019', '精神科',
   '副主任医师，擅长抑郁症、焦虑症、失眠障碍、双相情感障碍等常见精神心理疾病的评估、药物治疗与心理干预。',
   'ONLINE', 'ACTIVE');

-- -------------------- 2. 科室介绍文档 --------------------
INSERT INTO sm_knowledge_document
  (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text)
VALUES
  ('耳鼻喉科就诊指引', '科室', 2, 'PARSED', 4,
   '耳鼻喉科（ENT）是诊治耳、鼻、咽、喉、头颈部相关疾病的临床科室，涵盖鼻炎、中耳炎、咽喉炎、扁桃体炎、鼻息肉、眩晕等常见病。'),
  ('肛肠科就诊指引',   '科室', 2, 'PARSED', 4,
   '肛肠科是诊治肛门、直肠、结肠下段及周围组织疾病的科室，常见疾病包括痔疮、肛裂、肛瘘、肛周脓肿、直肠脱垂、慢性便秘等。'),
  ('精神科就诊指引',   '科室', 2, 'PARSED', 4,
   '精神科（精神心理科）是诊治各类精神心理障碍的科室，包括抑郁症、焦虑症、失眠障碍、双相情感障碍、精神分裂症等。');

-- -------------------- 3. 每个文档 4 个 chunk --------------------
-- 耳鼻喉科
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content) VALUES
  ((SELECT id FROM sm_knowledge_document WHERE document_name='耳鼻喉科就诊指引'), 1, '典型症状+分诊规则',
   '典型症状：鼻塞流涕、打喷嚏、鼻痒、耳鸣、听力下降、耳道流脓、咽痛、声音嘶哑、咽部异物感、眩晕、头痛伴随耳部不适。出现这类症状优先推荐耳鼻喉科。
metadata：{"deptName":"耳鼻喉科","docId":"doc_ent_001","chunkType":"典型症状"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='耳鼻喉科就诊指引'), 2, '接诊范围',
   '耳鼻喉科诊治耳、鼻、咽、喉、头颈部相关疾病，包括：慢性鼻炎、过敏性鼻炎、鼻窦炎、鼻息肉、中耳炎、外耳道炎、眩晕（耳源性）、慢性咽炎、扁桃体炎、声带小结、急性喉炎、耳聋、耳鸣等。
metadata：{"deptName":"耳鼻喉科","docId":"doc_ent_001","chunkType":"接诊范围"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='耳鼻喉科就诊指引'), 3, '科室医生擅长',
   '耳鼻喉科-徐医生，副主任医师，擅长慢性鼻炎、过敏性鼻炎、中耳炎、鼻息肉、咽喉炎，对儿童耳鼻喉常见病有丰富经验。出现鼻塞、喷嚏、耳鸣、咽痛等症状可优先选择徐医生。
metadata：{"deptName":"耳鼻喉科","docId":"doc_ent_001","chunkType":"医生擅长"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='耳鼻喉科就诊指引'), 4, '不接诊范围',
   '耳鼻喉科不接诊发热咳嗽、胸痛、腹痛、单纯皮肤皮疹、四肢骨折等非头颈部五官相关病症。
metadata：{"deptName":"耳鼻喉科","docId":"doc_ent_001","chunkType":"不接诊范围"}');

-- 肛肠科
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content) VALUES
  ((SELECT id FROM sm_knowledge_document WHERE document_name='肛肠科就诊指引'), 1, '典型症状+分诊规则',
   '典型症状：大便出血（鲜红色、附着于表面或滴下）、肛门疼痛、肛门瘙痒、肛门肿物脱出、肛门周围红肿热痛、肛门失禁、慢性便秘、排便困难、排便不尽感。出现这类症状优先推荐肛肠科。
metadata：{"deptName":"肛肠科","docId":"doc_procto_001","chunkType":"典型症状"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='肛肠科就诊指引'), 2, '接诊范围',
   '肛肠科诊治肛门、直肠、结肠下段及周围组织疾病，包括：内痔、外痔、混合痔、肛裂、肛瘘、肛周脓肿、直肠脱垂、直肠息肉、直肠炎、慢性便秘、出口梗阻型便秘、肛门失禁等。
metadata：{"deptName":"肛肠科","docId":"doc_procto_001","chunkType":"接诊范围"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='肛肠科就诊指引'), 3, '科室医生擅长',
   '肛肠科-朱医生，主治医师，擅长痔疮、肛裂、肛周脓肿、肛瘘、慢性便秘、直肠炎的保守治疗与微创手术。出现肛门出血、疼痛、肿物脱出等症状可优先选择朱医生。
metadata：{"deptName":"肛肠科","docId":"doc_procto_001","chunkType":"医生擅长"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='肛肠科就诊指引'), 4, '不接诊范围',
   '肛肠科不接诊单纯腹痛、腹泻伴发热（先考虑消化内科或感染性肠炎）、便血伴黑便或咖啡样物（需优先消化内科排查上消化道出血）、结直肠癌根治术后随访（建议联合普外科/肿瘤科）。
metadata：{"deptName":"肛肠科","docId":"doc_procto_001","chunkType":"不接诊范围"}');

-- 精神科
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content) VALUES
  ((SELECT id FROM sm_knowledge_document WHERE document_name='精神科就诊指引'), 1, '典型症状+分诊规则',
   '典型症状：持续情绪低落、对事物无兴趣、入睡困难或早醒、焦虑不安、心慌胸闷（排除器质性原因后）、情绪极端波动、出现幻觉或妄想、社交退缩、注意力严重下降、自伤或自杀念头。出现这类症状且持续两周以上，建议推荐精神科。
metadata：{"deptName":"精神科","docId":"doc_psych_001","chunkType":"典型症状"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='精神科就诊指引'), 2, '接诊范围',
   '精神科诊治各类精神心理障碍，包括：抑郁症、焦虑症、失眠障碍、双相情感障碍、精神分裂症、强迫障碍（OCD）、惊恐障碍、社交焦虑障碍、创伤后应激障碍（PTSD）、认知障碍（痴呆）、儿童青少年行为与情绪障碍等。
metadata：{"deptName":"精神科","docId":"doc_psych_001","chunkType":"接诊范围"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='精神科就诊指引'), 3, '科室医生擅长',
   '精神科-秦医生，副主任医师，擅长抑郁症、焦虑症、失眠障碍、双相情感障碍的评估、药物治疗与心理干预。情绪持续低落、失眠、焦虑不安者可优先选择秦医生。
metadata：{"deptName":"精神科","docId":"doc_psych_001","chunkType":"医生擅长"}'),
  ((SELECT id FROM sm_knowledge_document WHERE document_name='精神科就诊指引'), 4, '不接诊范围',
   '精神科不接诊明确的器质性精神症状首发患者（需先神经内科排查癫痫、脑肿瘤、痴呆等）、急性酒精/药物中毒、高热谵妄（先急诊）。儿童青少年发育障碍可联合儿科/儿保科会诊。
metadata：{"deptName":"精神科","docId":"doc_psych_001","chunkType":"不接诊范围"}');
