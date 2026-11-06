-- =====================================================================
-- V1.6 医生 AI 辅助分析种子数据
-- 1) doctor-analyze 提示词模板（sm_prompt_template，管理员端可热更新）
-- 2) 临床指南库（knowledge_base_id=1）常见病指南种子：8 文档 × 2 片段
-- 执行方式：mysql -uroot -p smart_medical < migration-v6-doctor-analyze.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) doctor-analyze 提示词模板
-- ---------------------------------------------------------------------
INSERT INTO sm_prompt_template (template_code, template_name, scene, variables, prompt_content, enabled, created_at, updated_at)
VALUES ('doctor-analyze', '医生问诊分析 Agent', 'DOCTOR',
       'doctorProfile,patientInfo,preConsultSummary,medicalHistory,chatHistory,latestMessage,ragContext',
       '【角色】你是医院医生端的临床辅助分析智能体，服务对象是当前接诊的医生。你的分析仅供医生参考，不直接面向患者，最终诊疗决策由医生做出。

【输入信息】
医生信息：{doctorProfile}
患者基本档案：{patientInfo}
预问诊摘要：{preConsultSummary}
患者历史病历：{medicalHistory}
本次问诊对话记录：
{chatHistory}
患者最新消息：{latestMessage}
{ragContext}

【任务】
1. 结合上述上下文分析患者病情，判断现有信息是否足以给出初步诊断与处置建议：
   - 信息不足（关键症状、病程、体温、既往史、过敏史、用药史等缺失，无法确诊病因或无法给出合理处方建议）时：sufficiency 输出 INSUFFICIENT，在 missingItems 中列出还缺哪些关键信息，并生成一段引导式追问的回复草稿 draftReply，供医生直接发送给患者，语气礼貌专业，一次性集中追问缺失要点。
   - 信息充足时：sufficiency 输出 SUFFICIENT，给出初步诊断 diagnosis（病情与病因分析）、就诊建议 advice（进一步检查、护理要点、复诊时机、需要警惕的危险症状等），并给出处方建议 prescription 列表。
2. 处方建议的药品优先选择临床常用、与诊断相符的药品，每项包含 drugName（药品通用名）、specification（规格，如 0.5g*16片）、dosage（单次剂量，如 0.5g）、frequency（频次，如 每日3次）、days（用药天数，数字）、usageMethod（用法，如 口服/外用）。
3. 无论信息是否充足，都生成一段 draftReply 供医生参考发送：信息不足时是追问草稿，信息充足时是告知患者初步判断与建议的草稿，语气温和专业。
4. 参考临床指南检索结果进行分析，但不要照搬照抄，不要虚构患者未描述的症状、体征与检查结果。

【输出要求】
只输出一个 JSON 对象，不要输出任何其他文字、解释或 markdown 标记。字段结构：
{"sufficiency":"INSUFFICIENT或SUFFICIENT","missingItems":["缺失信息1","缺失信息2"],"draftReply":"给患者的回复草稿","diagnosis":"初步诊断与病因分析（信息不足时为空字符串）","advice":"就诊与护理建议（信息不足时为空字符串）","prescription":[{"drugName":"药品通用名","specification":"规格","dosage":"单次剂量","frequency":"频次","days":3,"usageMethod":"用法"}]}
信息不足时 missingItems 至少一项、prescription 输出空数组 []；信息充足时 missingItems 输出空数组 []。',
       1, NOW(), NOW())
ON DUPLICATE KEY UPDATE prompt_content = VALUES(prompt_content), enabled = 1, updated_at = NOW();

-- ---------------------------------------------------------------------
-- 2) 临床指南库常见病种子（8 文档 × 2 片段）
-- ---------------------------------------------------------------------

-- 感冒（普通感冒/流行性感冒）
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text, created_at, updated_at)
VALUES ('感冒诊疗指南摘要', '指南', 1, 'PARSED', 2,
'感冒（普通感冒/流感）诊断要点：以鼻塞、流涕、咽痛、咳嗽、低热为主要表现，多起病急，病程 5-7 天自限。流感全身症状重：高热（≥38.5℃）、头痛、肌肉酸痛、乏力。需问诊确认：发热程度与持续时间、是否伴咽痛/咳嗽/咳痰性状、有无接触史、既往慢性病史、药物过敏史。鉴别：过敏性鼻炎（喷嚏流清涕为主、无发热）、细菌性扁桃体炎（咽部脓点、高热）、新冠/支原体感染。
感冒治疗原则：对症治疗为主。发热头痛咽痛：对乙酰氨基酚或布洛芬；鼻塞流涕：伪麻黄碱类复方制剂；干咳：右美沙芬；流感确诊或疑似：奥司他韦 75mg 每日2次×5天（发病48小时内最佳）。嘱多饮水、休息。常规不推荐使用抗菌药物。出现高热持续3天不退、呼吸困难、胸痛、意识改变等需及时复诊。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '感冒诊疗指南摘要' AND knowledge_base_id = 1), 1, '感冒诊断要点', '感冒（普通感冒/流感）诊断要点：以鼻塞、流涕、咽痛、咳嗽、低热为主要表现，多起病急，病程 5-7 天自限。流感全身症状重：高热（≥38.5℃）、头痛、肌肉酸痛、乏力。需问诊确认：发热程度与持续时间、是否伴咽痛/咳嗽/咳痰性状、有无接触史、既往慢性病史、药物过敏史。鉴别：过敏性鼻炎（喷嚏流清涕为主、无发热）、细菌性扁桃体炎（咽部脓点、高热）、新冠/支原体感染。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '感冒诊疗指南摘要' AND knowledge_base_id = 1), 2, '感冒治疗原则', '感冒治疗原则：对症治疗为主。发热头痛咽痛：对乙酰氨基酚 0.5g 或布洛芬 0.2-0.4g 口服；鼻塞流涕：含伪麻黄碱复方制剂；干咳：右美沙芬；流感确诊或疑似：奥司他韦 75mg 每日2次×5天（发病48小时内最佳）。嘱多饮水、休息。常规不推荐抗菌药物。红旗症状：高热持续3天不退、呼吸困难、胸痛、意识改变，需及时复诊或面诊。', NOW(), NOW());

-- 急性胃肠炎
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text, created_at, updated_at)
VALUES ('急性胃肠炎诊疗指南摘要', '指南', 1, 'PARSED', 2,
'急性胃肠炎诊断要点：多有不洁饮食史，急性起病，表现为恶心、呕吐、腹痛、腹泻（水样便多见），可伴发热。需问诊确认：起病时间与可疑食物、腹泻次数与性状（有无脓血便）、呕吐次数、能否进食进水、尿量变化、发热、同住者是否发病。鉴别：细菌性痢疾（脓血便、里急后重）、阑尾炎早期（转移性右下腹痛）、肠梗阻（停止排气排便、腹胀）。
急性胃肠炎治疗原则：补液为关键，首选口服补液盐Ⅲ，少量多次；蒙脱石散 3g 每日3次保护肠黏膜；益生菌（双歧杆菌三联活菌等）调节菌群。呕吐明显可暂禁食4-6小时后进流质。多数为病毒性，不需抗菌药物；明确细菌感染时才考虑喹诺酮类。红旗症状：持续呕吐无法进水、少尿、脓血便、高热、剧烈腹痛，提示脱水或严重感染，需面诊。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '急性胃肠炎诊疗指南摘要' AND knowledge_base_id = 1), 1, '急性胃肠炎诊断要点', '急性胃肠炎诊断要点：多有不洁饮食史，急性起病，表现为恶心、呕吐、腹痛、腹泻（水样便多见），可伴发热。需问诊确认：起病时间与可疑食物、腹泻次数与性状（有无脓血便）、呕吐次数、能否进食进水、尿量变化、发热、同住者是否发病。鉴别：细菌性痢疾（脓血便、里急后重）、阑尾炎早期（转移性右下腹痛）、肠梗阻（停止排气排便、腹胀）。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '急性胃肠炎诊疗指南摘要' AND knowledge_base_id = 1), 2, '急性胃肠炎治疗原则', '急性胃肠炎治疗原则：补液为关键，首选口服补液盐Ⅲ，少量多次冲服；蒙脱石散 3g 每日3次口服保护肠黏膜；益生菌（双歧杆菌三联活菌等）调节菌群。呕吐明显可暂禁食4-6小时后进流质。多数为病毒性，不需抗菌药物；明确细菌感染时才考虑诺氟沙星等喹诺酮类（18岁以下禁用）。红旗症状：持续呕吐无法进水、少尿、脓血便、高热、剧烈腹痛，提示脱水或严重感染，需面诊。', NOW(), NOW());

-- 急性支气管炎
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text, created_at, updated_at)
VALUES ('急性支气管炎诊疗指南摘要', '指南', 1, 'PARSED', 2,
'急性支气管炎诊断要点：咳嗽为主要症状，初期干咳，后可有白色黏痰或黄脓痰，常继发于上呼吸道感染，可伴低热、胸骨后不适，双肺呼吸音粗，病程一般1-3周。需问诊确认：咳嗽持续时间、咳痰颜色与量、有无发热、有无喘息气促、吸烟史、基础肺病（哮喘/慢阻肺）史。鉴别：肺炎（高热、气促、肺部固定湿啰音，需胸片）、咳嗽变异性哮喘（夜间咳为主、超过8周）、百日咳。
急性支气管炎治疗原则：多为病毒性，对症治疗为主，不推荐常规抗菌药物。干咳剧烈：右美沙芬 15-30mg 每日3次；痰黏稠：氨溴索 30mg 每日3次或乙酰半胱氨酸；伴喘息：可雾化支气管舒张剂。发热头痛：对乙酰氨基酚。嘱多饮水、戒烟、避免刺激气体。红旗症状：咳脓痰伴高热超过3天、气促加重、咯血，需胸片检查排除肺炎。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '急性支气管炎诊疗指南摘要' AND knowledge_base_id = 1), 1, '急性支气管炎诊断要点', '急性支气管炎诊断要点：咳嗽为主要症状，初期干咳，后可有白色黏痰或黄脓痰，常继发于上呼吸道感染，可伴低热、胸骨后不适，双肺呼吸音粗，病程一般1-3周。需问诊确认：咳嗽持续时间、咳痰颜色与量、有无发热、有无喘息气促、吸烟史、基础肺病（哮喘/慢阻肺）史。鉴别：肺炎（高热、气促、肺部固定湿啰音，需胸片）、咳嗽变异性哮喘（夜间咳为主、超过8周）、百日咳。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '急性支气管炎诊疗指南摘要' AND knowledge_base_id = 1), 2, '急性支气管炎治疗原则', '急性支气管炎治疗原则：多为病毒性，对症治疗为主，不推荐常规抗菌药物。干咳剧烈：右美沙芬 15-30mg 每日3次口服；痰黏稠：氨溴索 30mg 每日3次或乙酰半胱氨酸 0.2g 每日3次；伴喘息：可雾化支气管舒张剂。发热头痛：对乙酰氨基酚 0.5g。嘱多饮水、戒烟、避免刺激气体。红旗症状：咳脓痰伴高热超过3天、气促加重、咯血，需胸片检查排除肺炎。', NOW(), NOW());

-- 湿疹（皮炎）
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text, created_at, updated_at)
VALUES ('湿疹诊疗指南摘要', '指南', 1, 'PARSED', 2,
'湿疹（皮炎）诊断要点：多形性皮损（红斑、丘疹、水疱、渗出、结痂、苔藓化），剧烈瘙痒，对称分布，易反复发作。急性期红肿渗出明显，慢性期皮肤增厚苔藓化。需问诊确认：皮损部位与形态、起病时间与诱因（食物、接触物、季节）、瘙痒程度、有无渗出、既往过敏史/特应性体质、自用药物情况。鉴别：真菌感染（环形边缘、鳞屑，需真菌镜检）、荨麻疹（风团、24小时内消退）、接触性皮炎（明确接触部位）。
湿疹治疗原则：避免搔抓与热水烫洗，保湿为基础（润肤剂每日多次）。急性期无渗出：炉甘石洗剂或弱中效糖皮质激素乳膏（丁酸氢化可的松、糠酸莫米松每日1-2次）；渗出明显：3%硼酸溶液湿敷。瘙痒明显：口服抗组胺药氯雷他定 10mg 每日1次或西替利嗪 10mg 每日1次。慢性肥厚皮损可短期用强效激素。红旗症状：皮损泛发全身、继发感染化脓、发热，需面诊。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '湿疹诊疗指南摘要' AND knowledge_base_id = 1), 1, '湿疹诊断要点', '湿疹（皮炎）诊断要点：多形性皮损（红斑、丘疹、水疱、渗出、结痂、苔藓化），剧烈瘙痒，对称分布，易反复发作。急性期红肿渗出明显，慢性期皮肤增厚苔藓化。需问诊确认：皮损部位与形态、起病时间与诱因（食物、接触物、季节）、瘙痒程度、有无渗出、既往过敏史/特应性体质、自用药物情况，最好能提供皮损照片。鉴别：真菌感染（环形边缘、鳞屑，需真菌镜检）、荨麻疹（风团、24小时内消退）、接触性皮炎（明确接触部位）。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '湿疹诊疗指南摘要' AND knowledge_base_id = 1), 2, '湿疹治疗原则', '湿疹治疗原则：避免搔抓与热水烫洗，保湿为基础（润肤剂每日多次）。急性期无渗出：炉甘石洗剂外用或弱中效糖皮质激素乳膏（丁酸氢化可的松乳膏、糠酸莫米松乳膏每日1-2次）；渗出明显：3%硼酸溶液湿敷。瘙痒明显：口服抗组胺药氯雷他定 10mg 每日1次或西替利嗪 10mg 每日1次。慢性肥厚皮损可短期用强效激素，面部及皮肤薄嫩处避免强效激素。红旗症状：皮损泛发全身、继发感染化脓、发热，需面诊。', NOW(), NOW());

-- 失眠
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text, created_at, updated_at)
VALUES ('失眠症诊疗指南摘要', '指南', 1, 'PARSED', 2,
'失眠症诊断要点：入睡困难（>30分钟）、睡眠维持困难（夜间觉醒≥2次）、早醒，伴日间功能受损（疲乏、注意力下降、情绪烦躁），每周≥3次，持续≥3个月为慢性失眠。需问诊确认：失眠类型与持续时间、入睡潜伏期、夜间觉醒次数、日间影响、有无焦虑抑郁情绪、咖啡因/酒精摄入、作息习惯、有无打鼾憋醒（排查睡眠呼吸暂停）、既往用药史。鉴别：焦虑抑郁相关失眠、睡眠呼吸暂停、不宁腿综合征。
失眠治疗原则：首选非药物治疗——睡眠卫生教育与认知行为治疗（CBT-I）：固定作息、卧床时间限制、避免午后咖啡因、睡前远离屏幕。药物仅短期使用：非苯二氮䓬类右佐匹克隆 1-3mg 或唑吡坦 5-10mg 睡前服，疗程一般不超过4周；伴焦虑情绪可考虑曲唑酮小剂量。避免长期依赖苯二氮䓬类。红旗症状：伴明显情绪低落/自杀意念、严重打鼾憋醒，需转诊精神心理科或睡眠专科。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '失眠症诊疗指南摘要' AND knowledge_base_id = 1), 1, '失眠症诊断要点', '失眠症诊断要点：入睡困难（>30分钟）、睡眠维持困难（夜间觉醒≥2次）、早醒，伴日间功能受损（疲乏、注意力下降、情绪烦躁），每周≥3次，持续≥3个月为慢性失眠。需问诊确认：失眠类型与持续时间、入睡潜伏期、夜间觉醒次数、日间影响、有无焦虑抑郁情绪、咖啡因/酒精摄入、作息习惯、有无打鼾憋醒（排查睡眠呼吸暂停）、既往用药史。鉴别：焦虑抑郁相关失眠、睡眠呼吸暂停、不宁腿综合征。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '失眠症诊疗指南摘要' AND knowledge_base_id = 1), 2, '失眠症治疗原则', '失眠症治疗原则：首选非药物治疗——睡眠卫生教育与认知行为治疗（CBT-I）：固定作息、卧床时间限制、避免午后咖啡因、睡前远离屏幕。药物仅短期使用：非苯二氮䓬类右佐匹克隆 1-3mg 或唑吡坦 5-10mg 睡前服，疗程一般不超过4周；伴焦虑情绪可考虑曲唑酮小剂量睡前服。避免长期依赖苯二氮䓬类。红旗症状：伴明显情绪低落/自杀意念、严重打鼾憋醒，需转诊精神心理科或睡眠专科面诊。', NOW(), NOW());

-- 腰痛
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text, created_at, updated_at)
VALUES ('腰痛诊疗指南摘要', '指南', 1, 'PARSED', 2,
'腰痛诊断要点：常见于腰肌劳损与腰椎间盘突出。腰肌劳损：久坐久站后腰部酸胀痛，休息可缓解，无下肢放射痛。腰椎间盘突出：腰痛伴单侧下肢放射痛、麻木，咳嗽时加重。需问诊确认：疼痛部位与性质、有无下肢放射痛/麻木、起病诱因（搬重物、外伤）、持续时间、夜间痛醒与否、大小便功能、既往腰椎病史。鉴别：肾结石（绞痛伴血尿）、强直性脊柱炎（晨僵、青年）、骨质疏松性压缩骨折（老年、轻微创伤）。
腰痛治疗原则：急性期避免久坐与负重，但不必绝对卧床；热敷、适度拉伸。药物：非甾体抗炎药布洛芬 0.3g 每日2次或双氯芬酸钠缓释片 75mg 每日1次；肌肉痉挛明显：乙哌立松 50mg 每日3次；神经病理性疼痛：甲钴胺 0.5mg 每日3次营养神经。外用：双氯芬酸二乙胺乳膏。红旗症状：下肢进行性无力、大小便功能障碍、鞍区麻木（马尾综合征）、夜间痛醒伴消瘦，需尽快面诊行腰椎 MRI。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '腰痛诊疗指南摘要' AND knowledge_base_id = 1), 1, '腰痛诊断要点', '腰痛诊断要点：常见于腰肌劳损与腰椎间盘突出。腰肌劳损：久坐久站后腰部酸胀痛，休息可缓解，无下肢放射痛。腰椎间盘突出：腰痛伴单侧下肢放射痛、麻木，咳嗽时加重。需问诊确认：疼痛部位与性质、有无下肢放射痛/麻木、起病诱因（搬重物、外伤）、持续时间、夜间痛醒与否、大小便功能、既往腰椎病史。鉴别：肾结石（绞痛伴血尿）、强直性脊柱炎（晨僵、青年）、骨质疏松性压缩骨折（老年、轻微创伤）。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '腰痛诊疗指南摘要' AND knowledge_base_id = 1), 2, '腰痛治疗原则', '腰痛治疗原则：急性期避免久坐与负重，但不必绝对卧床；热敷、适度腰背肌拉伸锻炼。药物：非甾体抗炎药布洛芬 0.3g 每日2次或双氯芬酸钠缓释片 75mg 每日1次口服；肌肉痉挛明显：乙哌立松 50mg 每日3次；神经病理性疼痛：甲钴胺 0.5mg 每日3次营养神经。外用：双氯芬酸二乙胺乳膏涂抹。红旗症状：下肢进行性无力、大小便功能障碍、鞍区麻木（马尾综合征）、夜间痛醒伴消瘦，需尽快面诊行腰椎 MRI。', NOW(), NOW());

-- 过敏性鼻炎
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text, created_at, updated_at)
VALUES ('过敏性鼻炎诊疗指南摘要', '指南', 1, 'PARSED', 2,
'过敏性鼻炎诊断要点：阵发性喷嚏、清水样涕、鼻痒、鼻塞，可伴眼痒、流泪，晨起或接触过敏原（花粉、尘螨、冷空气）后发作，无发热与全身症状。分为季节性（花粉）与常年性（尘螨）。需问诊确认：症状发作规律与季节相关性、诱因、有无眼部症状、既往过敏史与哮喘史、家族史、自用药物效果。鉴别：普通感冒（伴发热咽痛、7天自限）、血管运动性鼻炎、鼻窦炎（黄脓涕、面部胀痛）。
过敏性鼻炎治疗原则：避免接触过敏原。一线用药：鼻用糖皮质激素糠酸莫米松鼻喷雾剂每日1次（每侧鼻孔1喷）或布地奈德鼻喷雾剂；口服第二代抗组胺药氯雷他定 10mg 每日1次或西替利嗪 10mg 每日1次。眼痒明显：奥洛他定滴眼液。中重度或持续性可联合用药，生理盐水鼻腔冲洗辅助治疗。红旗症状：单侧鼻塞伴血涕、面部剧烈胀痛伴高热，需面诊排除其他疾病。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '过敏性鼻炎诊疗指南摘要' AND knowledge_base_id = 1), 1, '过敏性鼻炎诊断要点', '过敏性鼻炎诊断要点：阵发性喷嚏、清水样涕、鼻痒、鼻塞，可伴眼痒、流泪，晨起或接触过敏原（花粉、尘螨、冷空气）后发作，无发热与全身症状。分为季节性（花粉）与常年性（尘螨）。需问诊确认：症状发作规律与季节相关性、诱因、有无眼部症状、既往过敏史与哮喘史、家族史、自用药物效果。鉴别：普通感冒（伴发热咽痛、7天自限）、血管运动性鼻炎、鼻窦炎（黄脓涕、面部胀痛）。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '过敏性鼻炎诊疗指南摘要' AND knowledge_base_id = 1), 2, '过敏性鼻炎治疗原则', '过敏性鼻炎治疗原则：避免接触过敏原。一线用药：鼻用糖皮质激素糠酸莫米松鼻喷雾剂每日1次（每侧鼻孔1喷）或布地奈德鼻喷雾剂；口服第二代抗组胺药氯雷他定 10mg 每日1次或西替利嗪 10mg 每日1次。眼痒明显：奥洛他定滴眼液。中重度或持续性可联合用药，生理盐水鼻腔冲洗辅助治疗。红旗症状：单侧鼻塞伴血涕、面部剧烈胀痛伴高热，需面诊排除其他疾病。', NOW(), NOW());

-- 慢性胃炎
INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text, created_at, updated_at)
VALUES ('慢性胃炎诊疗指南摘要', '指南', 1, 'PARSED', 2,
'慢性胃炎诊断要点：上腹隐痛、饱胀、嗳气、反酸、恶心，进食后加重，症状反复迁延。确诊依赖胃镜与幽门螺杆菌（Hp）检测。需问诊确认：上腹症状持续时间与规律（空腹痛/餐后痛）、反酸烧心、食欲与体重变化、黑便史、饮酒与 NSAIDs 用药史、既往胃病与 Hp 检查史。鉴别：消化性溃疡（规律性疼痛）、功能性消化不良、胆道疾病（右上腹、油腻食物诱发）、胃癌（报警症状：消瘦、贫血、吞咽困难、持续呕吐）。
慢性胃炎治疗原则：去除诱因——规律饮食、戒酒、避免辛辣刺激与 NSAIDs。抑酸：质子泵抑制剂奥美拉唑 20mg 每日1次（餐前）或雷贝拉唑 10mg 每日1次，疗程4-8周；胃黏膜保护：铝碳酸镁咀嚼片 1g 每日3次或瑞巴派特。Hp 阳性：四联根除方案（PPI+铋剂+两种抗生素，14天）。腹胀为主：促动力药莫沙必利 5mg 每日3次。红旗症状：消瘦、黑便、贫血、吞咽困难、40岁以上新发症状，需尽快胃镜检查。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '慢性胃炎诊疗指南摘要' AND knowledge_base_id = 1), 1, '慢性胃炎诊断要点', '慢性胃炎诊断要点：上腹隐痛、饱胀、嗳气、反酸、恶心，进食后加重，症状反复迁延。确诊依赖胃镜与幽门螺杆菌（Hp）检测。需问诊确认：上腹症状持续时间与规律（空腹痛/餐后痛）、反酸烧心、食欲与体重变化、黑便史、饮酒与 NSAIDs 用药史、既往胃病与 Hp 检查史。鉴别：消化性溃疡（规律性疼痛）、功能性消化不良、胆道疾病（右上腹、油腻食物诱发）、胃癌（报警症状：消瘦、贫血、吞咽困难、持续呕吐）。', NOW(), NOW());
INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content, created_at, updated_at)
VALUES ((SELECT id FROM sm_knowledge_document WHERE document_name = '慢性胃炎诊疗指南摘要' AND knowledge_base_id = 1), 2, '慢性胃炎治疗原则', '慢性胃炎治疗原则：去除诱因——规律饮食、戒酒、避免辛辣刺激与 NSAIDs。抑酸：质子泵抑制剂奥美拉唑 20mg 每日1次（餐前）或雷贝拉唑 10mg 每日1次，疗程4-8周；胃黏膜保护：铝碳酸镁咀嚼片 1g 每日3次或瑞巴派特 0.1g 每日3次。Hp 阳性：四联根除方案（PPI+铋剂+两种抗生素，14天）。腹胀为主：促动力药莫沙必利 5mg 每日3次。红旗症状：消瘦、黑便、贫血、吞咽困难、40岁以上新发症状，需尽快胃镜检查。', NOW(), NOW());
