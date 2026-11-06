-- FAQ 知识库种子数据（常见问题库：流程/材料/费用/退号/医保/报告）
-- 幂等：知识库、文档按名称去重；片段先清后插
SET NAMES utf8mb4;

INSERT INTO sm_medical_knowledge_base (name, type, description, status)
SELECT '常见问题库', 'FAQ', '挂号/就诊/材料/费用/退号/医保/报告等流程类问答（供患者端客服引用）', 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM sm_medical_knowledge_base WHERE name = '常见问题库' AND type = 'FAQ'
);

SET @base := (SELECT id FROM sm_medical_knowledge_base WHERE name = '常见问题库' AND type = 'FAQ' LIMIT 1);

INSERT INTO sm_knowledge_document (document_name, type, knowledge_base_id, parse_status, chunk_count, original_text)
SELECT '就诊流程与须知', 'FAQ', @base, 'PARSED', 7, '本院挂号、就诊、材料、费用、退号、医保与报告领取说明'
WHERE NOT EXISTS (
    SELECT 1 FROM sm_knowledge_document WHERE knowledge_base_id = @base AND document_name = '就诊流程与须知'
);

SET @doc := (SELECT id FROM sm_knowledge_document WHERE knowledge_base_id = @base AND document_name = '就诊流程与须知' LIMIT 1);

DELETE FROM sm_knowledge_chunk WHERE document_id = @doc;

INSERT INTO sm_knowledge_chunk (document_id, chunk_index, chunk_title, content) VALUES
(@doc, 1, '线上挂号流程',
 '进入「挂号」页面，先选择科室，再选择医生与就诊时间段，确认患者信息后提交并支付挂号费，支付成功后在「我的挂号」中查看挂号单。首次就诊需先完成实名建档（姓名、身份证号、手机号）。挂号成功后请按预约时间到院就诊，无需现场排队取号。'),
(@doc, 2, '就诊当天流程',
 '建议按预约时间提前 15 分钟到院，在自助机或分诊台凭身份证/就诊卡签到，随后在候诊区等待叫号。医生接诊后按医嘱进行检查、检验或取药。检查项目一般需先缴费再前往对应科室。'),
(@doc, 3, '就诊需携带材料',
 '请携带本人身份证或医保卡、既往病历本与近期检查报告；正在长期服用的药物建议携带药盒或清单。儿童就诊请携带出生证明或户口本，并由监护人陪同。'),
(@doc, 4, '挂号与就诊费用',
 '挂号费按职称分档收取（普通门诊、专家门诊、特需门诊价格不同），具体以院内公示价格为准。医保患者在挂号与缴费时可直接结算报销，自费部分由个人承担。'),
(@doc, 5, '退号与改约规则',
 '就诊日前一天 18:00 前可在「我的挂号」中自助退号或改约；就诊当天退号需到门诊窗口办理。为保障号源，爽约 3 次将限制线上挂号 30 天。已叫号或已接诊的挂号不支持退号。'),
(@doc, 6, '医保报销说明',
 '本地医保患者在挂号、缴费时出示医保卡或电子医保凭证即可直接结算；异地医保需先在参保地办理异地就医备案，备案成功后方可在本院直接结算。具体报销比例以当地医保政策为准。'),
(@doc, 7, '检查报告领取',
 '检查检验报告一般在检查完成后 24–48 小时出具，可在「我的报告」中在线查看；需要纸质报告时，可凭就诊卡在门诊楼一层自助打印机打印。如报告显示异常，建议尽快复诊由医生解读。');

UPDATE sm_knowledge_document
SET chunk_count = (SELECT COUNT(*) FROM sm_knowledge_chunk WHERE document_id = @doc),
    parse_status = 'PARSED',
    updated_at = NOW()
WHERE id = @doc;

SELECT @base AS faq_base_id, @doc AS faq_doc_id,
       (SELECT COUNT(*) FROM sm_knowledge_chunk WHERE document_id = @doc) AS chunk_count;
