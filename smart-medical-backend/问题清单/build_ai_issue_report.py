# -*- coding: utf-8 -*-
"""
生成《智慧医疗系统 AI 模块代码问题清单》Word 文档。
数据与排版分离：ISSUES 为内容，其余为排版辅助函数。
"""
import os
import re

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

OUT_DIR = r"D:\smart-medical\smart-medical-backend\docs"
OUT = os.path.join(OUT_DIR, "AI模块代码问题清单.docx")

# ---------- 配色 ----------
C_DARK = RGBColor(0x1F, 0x38, 0x64)
C_MID = RGBColor(0x2E, 0x5C, 0x8A)
C_GRAY = RGBColor(0x59, 0x59, 0x59)
C_LIGHT = RGBColor(0x7F, 0x7F, 0x7F)
C_RED = RGBColor(0xB0, 0x1E, 0x1E)
C_ORANGE = RGBColor(0xB5, 0x5A, 0x0A)
C_GREEN = RGBColor(0x1E, 0x6B, 0x33)
C_WHITE = RGBColor(0xFF, 0xFF, 0xFF)

EA_BODY = "宋体"
EA_HEAD = "微软雅黑"
ASCII_BODY = "Calibri"
ASCII_HEAD = "Calibri"

# ---------- 级别显示 ----------
LEVEL_COLOR = {"P0": C_RED, "P1": C_ORANGE, "P2": C_MID}
STATUS_COLOR = {"已修复": C_GREEN, "未修复": C_RED, "部分修复": C_ORANGE, "需确认": C_ORANGE}


def set_run_font(run, size=None, bold=None, color=None, ea=EA_BODY, ascii_font=ASCII_BODY, italic=None):
    run.font.name = ascii_font
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), ascii_font)
    rfonts.set(qn("w:hAnsi"), ascii_font)
    rfonts.set(qn("w:eastAsia"), ea)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if italic is not None:
        run.font.italic = italic
    if color is not None:
        run.font.color.rgb = color


def style_font(style, size, bold=False, color=None, ea=EA_BODY, ascii_font=ASCII_BODY):
    style.font.name = ascii_font
    style.font.size = Pt(size)
    style.font.bold = bold
    if color is not None:
        style.font.color.rgb = color
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), ascii_font)
    rfonts.set(qn("w:hAnsi"), ascii_font)
    rfonts.set(qn("w:eastAsia"), ea)


def _insert_before_first(parent, child, later_tags):
    """按 OOXML 顺序把 child 插到 later_tags 中首个兄弟之前，避免次序非法被 Word 忽略。"""
    for tag in later_tags:
        found = parent.find(qn(tag))
        if found is not None:
            found.addprevious(child)
            return
    parent.append(child)


def shade(cell, hex_fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_fill)
    _insert_before_first(tc_pr, shd, ("w:noWrap", "w:tcMar", "w:vAlign", "w:hideMark"))


def cell_margins(table, top=40, bottom=40, left=80, right=80):
    tbl_pr = table._tbl.tblPr
    mar = OxmlElement("w:tblCellMar")
    for tag, val in (("top", top), ("left", left), ("bottom", bottom), ("right", right)):
        node = OxmlElement("w:" + tag)
        node.set(qn("w:w"), str(val))
        node.set(qn("w:type"), "dxa")
        mar.append(node)
    _insert_before_first(tbl_pr, mar, ("w:tblLook", "w:tblCaption", "w:tblDescription"))


def no_split(row):
    tr_pr = row._tr.get_or_add_trPr()
    node = OxmlElement("w:cantSplit")
    tr_pr.append(node)


def repeat_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    node = OxmlElement("w:tblHeader")
    tr_pr.append(node)


def para_border_bottom(paragraph, color="1F3864", size=8, space=2):
    p_pr = paragraph._p.get_or_add_pPr()
    borders = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), str(size))
    bottom.set(qn("w:space"), str(space))
    bottom.set(qn("w:color"), color)
    borders.append(bottom)
    _insert_before_first(p_pr, borders, ("w:shd", "w:tabs", "w:spacing", "w:ind", "w:jc", "w:rPr"))


def add_field(paragraph, instr, placeholder="1"):
    def fld(kind):
        run = paragraph.add_run()
        el = OxmlElement("w:fldChar")
        el.set(qn("w:fldCharType"), kind)
        run._r.append(el)
        return run

    fld("begin")
    run = paragraph.add_run()
    instr_el = OxmlElement("w:instrText")
    instr_el.set(qn("xml:space"), "preserve")
    instr_el.text = instr
    run._r.append(instr_el)
    fld("separate")
    run = paragraph.add_run(placeholder)
    fld("end")


RICH_RE = re.compile(r"(`[^`]+`|\*\*[^*]+\*\*)")


def add_rich(paragraph, text, size=10.5, color=None, bold=False):
    """支持 `代码` 与 **加粗** 两种行内标记。"""
    for piece in RICH_RE.split(text):
        if not piece:
            continue
        if piece.startswith("`") and piece.endswith("`") and len(piece) > 2:
            run = paragraph.add_run(piece[1:-1])
            set_run_font(run, size=size - 1, color=RGBColor(0x8B, 0x1A, 0x1A),
                         ea="Consolas", ascii_font="Consolas")
        elif piece.startswith("**") and piece.endswith("**") and len(piece) > 4:
            run = paragraph.add_run(piece[2:-2])
            set_run_font(run, size=size, bold=True, color=color)
        else:
            run = paragraph.add_run(piece)
            set_run_font(run, size=size, bold=bold, color=color)


def p(doc, text="", size=10.5, color=None, bold=False, align=None, space_before=0,
      space_after=4, indent=None, line=1.45, style=None):
    paragraph = doc.add_paragraph(style=style) if style else doc.add_paragraph()
    pf = paragraph.paragraph_format
    pf.space_before = Pt(space_before)
    pf.space_after = Pt(space_after)
    pf.line_spacing = line
    if indent is not None:
        pf.first_line_indent = Cm(indent)
    if align is not None:
        paragraph.alignment = align
    if text:
        add_rich(paragraph, text, size=size, color=color, bold=bold)
    return paragraph


def bullets(doc, items, size=10.5, space_after=2):
    for item in items:
        paragraph = doc.add_paragraph(style="List Bullet")
        pf = paragraph.paragraph_format
        pf.space_before = Pt(0)
        pf.space_after = Pt(space_after)
        pf.line_spacing = 1.4
        pf.left_indent = Cm(0.75)
        add_rich(paragraph, item, size=size)


def heading(doc, text, level):
    h = doc.add_heading(level=level)
    h.paragraph_format.keep_with_next = True
    run = h.add_run(text)
    if level == 1:
        set_run_font(run, size=16, bold=True, color=C_DARK, ea=EA_HEAD, ascii_font=ASCII_HEAD)
        h.paragraph_format.space_before = Pt(16)
        h.paragraph_format.space_after = Pt(10)
        para_border_bottom(h, color="1F3864", size=6)
    elif level == 2:
        set_run_font(run, size=12.5, bold=True, color=C_MID, ea=EA_HEAD, ascii_font=ASCII_HEAD)
        h.paragraph_format.space_before = Pt(14)
        h.paragraph_format.space_after = Pt(6)
    else:
        set_run_font(run, size=11, bold=True, color=C_GRAY, ea=EA_HEAD, ascii_font=ASCII_HEAD)
        h.paragraph_format.space_before = Pt(8)
        h.paragraph_format.space_after = Pt(4)
    return h


def table(doc, headers, rows, widths, font=9, header_fill="D9E2F3", zebra="F2F5FA"):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    _insert_before_first(t._tbl.tblPr, layout,
                         ("w:tblCellMar", "w:tblLook", "w:tblCaption", "w:tblDescription"))
    cell_margins(t)
    hdr = t.rows[0]
    no_split(hdr)
    repeat_header(hdr)
    for i, h in enumerate(headers):
        cell = hdr.cells[i]
        cell.width = Cm(widths[i])
        shade(cell, header_fill)
        cell.text = ""
        paragraph = cell.paragraphs[0]
        paragraph.paragraph_format.space_after = Pt(0)
        paragraph.paragraph_format.space_before = Pt(0)
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = paragraph.add_run(h)
        set_run_font(run, size=font, bold=True, color=C_DARK, ea=EA_HEAD, ascii_font=ASCII_HEAD)
    for r_idx, row in enumerate(rows):
        cells = t.add_row()
        no_split(cells)
        for i, value in enumerate(row):
            cell = cells.cells[i]
            cell.width = Cm(widths[i])
            cell.text = ""
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_after = Pt(0)
            paragraph.paragraph_format.space_before = Pt(0)
            paragraph.paragraph_format.line_spacing = 1.25
            if i == 0:
                paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
            add_rich(paragraph, str(value), size=font)
            if r_idx % 2 == 1 and zebra:
                shade(cell, zebra)
    return t


# =====================================================================
# 内容区
# =====================================================================

META = [
    ("文档名称", "智慧医疗系统 AI 模块代码问题清单"),
    ("版本 / 日期", "v1.1 / 2026-11-03"),
    ("审查对象", "smart-medical-backend · src/main/java/com/hospital/smartmedical/module/ai"),
    ("关联代码", "common/security/AuthInterceptor、module/preconsult、module/admin"),
    ("审查方式", "逐文件静态阅读（34 个 Java 文件，约 7,200 行）；未编译、未运行单元测试"),
    ("代码快照", "2026-11-03 14:5x（审查期间开发侧已修复 P0-1、P0-2，本文按最新代码标注状态）"),
    ("结论", "22 项问题：P0 3 项、P1 8 项、P2 11 项；其中 2 项已修复，20 项待处理"),
]

LEVEL_DEF = [
    ("P0", "安全与权限：可被利用造成越权、数据泄露或写入错误数据", "3", "2", "1"),
    ("P1", "功能正确性与一致性：功能可用但会答错、记错、开关不生效", "8", "0", "8"),
    ("P2", "性能与工程质量：低并发下不显现，规模上来后集中爆发", "11", "0", "11"),
    ("合计", "—", "22", "2", "20"),
]

ROADMAP = [
    ("阶段一（本周）", "P0-3", "接入或删除失效的路径白名单", "0.2", "与 P0-2 注释直接冲突，越早澄清越好"),
    ("阶段一（本周）", "P1-4", "统一 sm_pre_consult 写入方 + 幂等", "0.5", "注释与实现矛盾，按注释改会丢医生端数据"),
    ("阶段一（本周）", "P2-19", "修复 getLatestDraft 空指针", "0.1", "一行改动，防 500"),
    ("阶段二（本迭代）", "P1-9 / P1-10", "挂号复用意图实体、去掉单字「挂」", "1.0", "用户能直接感知的答非所问"),
    ("阶段二（本迭代）", "P1-11", "时间解析补 HH:mm、中文数字、相对时间", "1.0", "挂号主流程可用性"),
    ("阶段二（本迭代）", "P1-8", "向量维度以模型实际输出为准", "0.5", "避免 Milvus 静默不可用"),
    ("阶段二（本迭代）", "P1-6 / P1-7", "空模板不播种、语义缓存开关归位", "0.5", "减少「配置无效」类排查成本"),
    ("阶段二（本迭代）", "P1-5", "阶段机补 FINISHED 流转或删分支", "0.5", "先定产品语义再动手"),
    ("阶段三（排期）", "P2-12 / P2-13", "流式改线程池、会话写加锁", "2.0", "并发量上来前完成"),
    ("阶段三（排期）", "P2-14 / P2-15 / P2-16 / P2-17", "模板缓存、历史裁剪、RAG 去重与阈值", "3.0", "性能专项"),
    ("阶段三（排期）", "P2-18 / P2-20 / P2-21 / P2-22", "草稿归档、Mapper 注入、补测试、清理 Demo", "3.0", "随功能迭代逐步偿还"),
]

ISSUES = [
    {
        "no": "P0-1",
        "level": "P0",
        "title": "会话 IDOR：拿着别人的会话编号就能读、能续聊",
        "type": "权限 / 数据安全",
        "loc": "AiAgentService.java · process（L110-129 归属校验、L152-174 归属写入）",
        "status": "已修复",
        "scope": "全部患者与医生会话（cs_ / ds_ / ai_）",
        "phenomenon": "每轮对话都会用 sessionId 从 Redis 读出整段会话再继续聊。修复前，读出后既不核对「这段会话是不是你的」，"
                      "还把会话里的 patientId 覆盖为当前登录人，导致后续的归属校验自动放行。",
        "cause": "这就像超市存包柜只检查「你有没有编号」，不检查「编号是不是你的」。编号可能来自前端日志、"
                 "分享链接、抓包，甚至碰撞，拿到即等于拿到对方完整的病情自述、姓名、年龄，并能以对方身份继续对话。"
                 "更隐蔽的是「覆盖 ID」这一步：它让事后审计也看不出是谁读过。",
        "effects": [
            "读取他人完整对话，含姓名、性别、年龄、症状描述；",
            "以他人会话上下文继续提问，模型会把对方病史当作当前患者信息回答；",
            "修复前的 history 归属校验形同虚设，可反复回读历史。",
        ],
        "fixes": [
            "加载会话后先校验归属：patientId / doctorId 必须与登录身份一致，不一致直接抛 FORBIDDEN（已实现）；",
            "只有新建会话才写入归属人，已存在会话禁止覆盖（已实现 stateIsNew 分支）；",
            "残余风险一：老会话 patientId 为 null 时校验会放行，建议把「归属为空」视为不可访问或做数据补齐；",
            "残余风险二：患者会话被医生角色加载时，因 state.doctorId 为 null 同样放行，建议增加角色一致性校验；",
            "history 目前越权时返回空列表而非 403，建议统一为拒绝，便于审计发现探测行为。",
        ],
        "verify": "用 A 账号创建会话，拿 sessionId 以 B 账号调用 /ai/patient-chat 与 /history，应分别返回 403 与空列表；"
                  "再查日志确认出现「会话归属不匹配」告警。",
    },
    {
        "no": "P0-2",
        "level": "P0",
        "title": "旧接口不查角色，报告接口按编号即可读他人报告",
        "type": "权限 / 水平越权",
        "loc": "AiController.java · requireDoctor（L69-75）；AiServiceImpl.java · imageAnalysis（L101-112）",
        "status": "已修复",
        "scope": "/ai/clinical-analysis、/ai/examination-suggestions、/ai/reply-drafts、/ai/image-analysis",
        "phenomenon": "修复前，这四个医生端接口没有任何角色判断，其中 imageAnalysis 只按 reportId 查库返回，不校验报告归属。"
                      "任何登录患者把 reportId 从 1 试到 1000，就能读到他人报告的类型与标题。",
        "cause": "接口是早期 V2.3 的遗留实现，当时返回的是硬编码 Demo 内容，所以「读谁的都一样」没有暴露问题。"
                 "但接口形状已经定型：一旦把真实模型接进来，这就是一条现成的数据泄露通道。",
        "effects": [
            "患者可枚举 reportId 读取他人报告元信息（当前为 Demo 内容，危害受限）；",
            "接口语义是「医生专用」，却对任何登录用户开放，权限模型失真；",
            "与同目录 DoctorAssistController 的写法不一致，容易在评审中被忽略。",
        ],
        "fixes": [
            "Controller 统一 requireDoctor 校验（已实现）；",
            "imageAnalysis 增加归属校验：患者只能看自己的报告，医生放行（已实现）；",
            "遗留问题：新增注释声称「AuthInterceptor 已按 DOCTOR_PATHS 做了一层拦截」，但该名单并未生效（见 P0-3），"
            "这类注释会误导后续维护者，建议改写为「拦截器未覆盖，此处为唯一校验点」；",
            "建议确认 AiService 接口签名变更后无其他调用方（编译验证）。",
        ],
        "verify": "以患者 token 调用四个接口，应全部返回 403；以医生 token 调用 imageAnalysis 传入他人 reportId，应正常返回。",
    },
    {
        "no": "P0-3",
        "level": "P0",
        "title": "门禁名单写在墙上，闸机却没通电",
        "type": "安全基础设施 / 误导",
        "loc": "AuthInterceptor.java · DOCTOR_PATHS / PATIENT_PATHS（L34-35）vs preHandle（L70-72）",
        "status": "未修复",
        "scope": "全站 /ai/*、/doctors/*、/appointments/*、/reports/* 等路径",
        "phenomenon": "拦截器里定义了两张路径清单：哪些只给医生、哪些只给患者。但 preHandle 实际只用了管理员那张，"
                      "医生/患者两张从未被调用。",
        "cause": "这类问题真正的危害不是「漏了检查」（各 Controller 大多自己查了），而是误导：看到这两行的人会以为"
                 "「这块已经有人守了」，于是新代码不再写校验。P0-2 修复时新增的注释恰好又引用了这张不生效的名单，"
                 "说明误导已经发生。",
        "effects": [
            "安全边界看着有、实际无，后续接口容易漏做校验；",
            "新注释与真实行为矛盾，评审与交接时难以判断；",
            "若将来有人按名单调整路径，会出现「以为被拦截、其实没有」的回归。",
        ],
        "fixes": [
            "二选一：把两张名单真正接入 preHandle（按前缀匹配角色），或直接删除并在文档中说明「校验在 Controller 层」；",
            "推荐接入，因为拦截器层能覆盖未来新增接口，而 Controller 层依赖每位开发者自觉；",
            "同步修正 AiController 中引用该名单的注释。",
        ],
        "verify": "用患者 token 访问 /ai/clinical-analysis，应 403；医生 token 访问 /appointments/* 按业务语义确认是否应放行。",
    },
    {
        "no": "P1-4",
        "level": "P1",
        "title": "注释说「我不写」，代码其实在写：sm_pre_consult 写入方不明",
        "type": "数据一致性 / 注释失真",
        "loc": "AiAgentService.java · mirrorToPreConsult（L432-466）；注释 L419-420 与代码 L435-439",
        "status": "未修复",
        "scope": "患者端预问诊数据，医生端「预问诊摘要」",
        "phenomenon": "方法的 Javadoc 写着「ai_ 前缀的会话由旧链路 PreConsultServiceImpl 写过 MySQL，这里跳过避免重复」，"
                      "但代码只跳过了 ds_，ai_ 照样写；同一个方法内的行内注释又写着「cs_ 小林客服 / ai_ 智能预问诊页面」，"
                      "与 Javadoc 自相矛盾。",
        "cause": "这段双写的存在原因是：会话主存储是 Redis，而医生端追问摘要读的是 MySQL，两边不通，所以每轮抄一份。"
                 "「谁该抄」需要唯一答案，代码和注释却各给了一个。补充一个关键事实：旧链路的默认前缀其实是 pre_"
                 "（PreConsultServiceImpl.L137-139），并不生成 ai_，所以「ai_ 已由旧链路写过」这个前提本身就不成立。",
        "effects": [
            "当前代码对 ai_ 会话是写对的（只写一遍）；一旦有人「按注释修」，ai_ 会一行都不写，医生端直接看不到预问诊内容；",
            "若同一条消息同时被新旧两条链路处理，且客户端把 ai_ 编号传给旧接口，同一会话内会出现重复行；",
            "若客户端不传编号，旧接口会自建 pre_ 会话，形成平行会话，医生端按「最近一次会话」兜底时可能挑到旧关键词话术；",
            "两条 insert 无事务，第二条失败会留下「患者说了、AI 没回」的半截记录，且只记一条 warn。",
        ],
        "fixes": [
            "确定唯一写入方：建议新链路负责 ai_/cs_，旧 /preconsult 发送接口改为转调 agent 或下线，并删除矛盾注释；",
            "或最少改动：把跳过条件从 ds_ 改为 pre_（谁的孩子谁不写），但客户端传编号时仍会重复；",
            "无论选哪种，都叠加幂等：插入前判重（会话 + 发送方 + 内容 + 时间窗）或建立内容哈希唯一索引；",
            "为两条 insert 补事务边界。",
        ],
        "verify": "查 sm_pre_consult 按会话与内容分组，确认无重复行；在前端工程搜索 patient-chat 与 preconsult，"
                  "确认预问诊页面只调一条链路。",
    },
    {
        "no": "P1-5",
        "level": "P1",
        "title": "红绿灯装了三个灯，只有一个会亮：FINISHED 阶段不可达",
        "type": "状态机 / 死代码",
        "loc": "MedicalGraphBuilder.java · route（L147-148）；ConsultStage.java",
        "status": "未修复",
        "scope": "全部会话的图路由",
        "phenomenon": "问诊分三个阶段：PRE_CONSULT → DOCTOR_RECEIVE → FINISHED。全工程只有创建会话时给阶段赋值，"
                      "没有任何代码把阶段推进到 FINISHED，因此路由里 case FINISHED 永远不会执行。",
        "cause": "设计图很完整，施工只做了一半。眼下不影响使用（用不到），代价在以后：产品提出「问诊结束后只能查报告、"
                 "不能再采集症状」时，开发者会以为改改路由就行，结果发现没人把状态切过去，得先补一整套迁移逻辑。",
        "effects": [
            "阶段路由只有两档有效，注释与设计文档描述的第三档是假的；",
            "「问诊结束」的业务语义无处落地，后续需求会重复踩坑；",
            "枚举与分支成为理解成本，评审时容易被当成已实现。",
        ],
        "fixes": [
            "先定产品语义：什么动作代表结束（医生点「结束面诊」/ 处方确认 / 超时）；",
            "在对应业务入口写阶段推进，并补一条「结束后再发消息」的回归用例；",
            "若短期不做，删除 FINISHED 枚举与分支，避免继续误导。",
        ],
        "verify": "构造一条 FINISHED 会话状态，确认路由进入 business 节点且不再采集症状。",
    },
    {
        "no": "P1-6",
        "level": "P1",
        "title": "播种了一条空白模板，把备用模板永久挡住",
        "type": "配置 / 提示词加载",
        "loc": "PromptTemplateLoader.java · init（L64-87，L80 空串播种）与 render（L104-113）",
        "status": "未修复",
        "scope": "doctor-dialog / doctor-intent / doctor-polish 三个模板",
        "phenomenon": "提示词按「数据库 → yaml 备份 → 报错」三级加载。启动时会把内置模板播种进数据库，方便管理员编辑；"
                      "但 yaml 里没有的三个医生端模板被以空字符串写进了库，而渲染逻辑「库里有行就算命中」，空串也算命中。",
        "cause": "等于在净水器（yaml 兜底）前面塞了一个空滤芯：水龙头看着有，水却流不过来。目前没炸，是因为客服节点"
                 "对空模板有兜底话术；但这意味着把模板补进 yaml 也不会生效，还得先手工删库里的空行。",
        "effects": [
            "yaml 兜底对这三个模板永久失效；",
            "管理员端列表里出现三条空白模板，容易误改误删；",
            "排查「提示词为什么没生效」时会多花数小时。",
        ],
        "fixes": [
            "播种时跳过 yaml 中没有内容的模板，不要写空行；",
            "render 把空白内容视为「未配置」，继续查 yaml；",
            "启动时清理历史遗留的空模板行（需确认无人工编辑内容）。",
        ],
        "verify": "清空库中三条模板后重启，确认管理员端不再出现空白项；向 yaml 增补内容后确认生效。",
    },
    {
        "no": "P1-7",
        "level": "P1",
        "title": "三个开关看着能调，其实没接线：影子模式、TTL、命名空间",
        "type": "配置 / 缓存",
        "loc": "SemanticCacheService.java（shadow L114、ttlDays L85/L246、ns 写入 L219、检索 L174-178）",
        "status": "未修复",
        "scope": "语义缓存（患者端小林客服）",
        "phenomenon": "配置里有 shadow（影子/真命中）、ttl-days（7 天过期）、ns（版本命名空间）三项。实际：shadow 只用于拼日志；"
                      "ttl-days 只出现在统计返回值里，没有任何过期清理；ns 写进了向量元数据，但检索时不过滤。",
        "cause": "典型的「旋钮没接线」。最坑的是 ns：它本来的作用是「换向量模型就整体作废旧缓存」，现在新旧向量混在一个池子里检索，"
                 "换模型后命中率会莫名下降或乱命中，而看配置会以为「我设了版本号」。",
        "effects": [
            "缓存条目只增不减，内存/Milvus 持续膨胀；",
            "换模型或换模板后旧缓存仍然参与检索，命中质量不可控；",
            "运维按配置调参，得不到预期效果。",
        ],
        "fixes": [
            "检索时按当前 ns 过滤；或换模型时清空对应命名空间；",
            "实现 TTL：写入时记 createdAt，检索后按阈值清理（或依赖 Milvus 的 TTL 能力）；",
            "shadow=false 要么实现真命中返回，要么把该配置标为预留并写清「暂不生效」。",
        ],
        "verify": "把 shadow 置 false 确认行为变化；检查 stats 中 hitRate 与检索日志是否一致。",
    },
    {
        "no": "P1-8",
        "level": "P1",
        "title": "抽屉按 1024 格做，模型只吐 512 个数：向量维度不一致",
        "type": "配置 / 向量检索",
        "loc": "DoctorRagService.java · createStore（L326-345）、createEmbeddingModel（L355-369）；EmbeddingConfig.dimension 默认 1024",
        "status": "未修复",
        "scope": "科室介绍库 RAG、语义缓存的 Milvus 集合",
        "phenomenon": "Milvus 集合维度取配置里的 dimension（默认 1024）；而向量模型有两套：配了 API Key 用千问（1024 维），"
                      "没配 Key 自动降级本地 BGE（512 维）。集合仍按 1024 建。",
        "cause": "「启用 Milvus + 未配 Key」这个组合一旦出现，往 1024 格的集合塞 512 个数，Milvus 直接报错，而错误被 catch 成"
                 "「RAG 推荐暂不可用」。这是全清单里最难查的一条——不是因为它严重，而是失败得很安静。",
        "effects": [
            "RAG 静默降级，推荐退化为关键词流程，日志看不出真因；",
            "同一份代码在不同环境（有/无 Key）表现不同，问题难以复现；",
            "语义缓存使用同一维度配置，同样会失败。",
        ],
        "fixes": [
            "别信配置：启动时先 embed 一条探针文本，用真实向量长度去建集合；",
            "降级到 BGE 时明确记录「维度 512，Milvus 不可用，已降级内存库」，而不是静默置空；",
            "文档中注明「启用 Milvus 必须配置向量模型 Key，否则自动回退内存库」。",
        ],
        "verify": "分别在有/无 API Key 两种配置下启动，确认集合维度与向量长度一致，且 RAG 可用。",
    },
    {
        "no": "P1-9",
        "level": "P1",
        "title": "脑子已经想出答案，手却不用：挂号忽略 LLM 抽取的实体",
        "type": "逻辑一致性",
        "loc": "BusinessToolExecutor.java · doAppoint（L249-299）、matchDept（L1151-1162）、matchDoctorName（L1167-1178）；"
               "对照 doProgress（L589-601）；关键词表 MedicalGraphBuilder.L311-338 与 BusinessToolExecutor.L152-187",
        "status": "未修复",
        "scope": "患者挂号流程、意图判断",
        "phenomenon": "路由阶段会调一次大模型，把意图、科室、医生名、患者名、时间范围都抽出来存进状态。查进度时优先用这些实体，"
                      "挂号时却完全不用：自己用正则「挂(.{2,4})的号」抠名字、用硬编码 13 个科室匹配。",
        "cause": "同一份「大脑输出」，一只手用、一只手不用，行为自然不一致。再加上关键词表在两个文件里各写一份，判断顺序已经不同，"
                 "后续必然漂移。",
        "effects": [
            "「帮我挂张伟」（没说「的号」）识别不出人名，答「请问挂哪个科」，而模型其实知道是谁；",
            "医院新增科室（如风湿免疫科）不在硬编码表里，患者怎么说都识别不了；",
            "两份关键词表不一致时，路由判「是业务」进业务节点，业务节点又判「不是业务」，回兜底话术，用户困惑。",
        ],
        "fixes": [
            "让「大脑抽取的实体」成为唯一来源，正则与硬编码科室降级为兜底；",
            "两份关键词表合并为单一 IntentRules，路由与业务共用；",
            "科室校验改为查库（sm_doctor.department 去重），而不是固定数组。",
        ],
        "verify": "构造「帮我挂张伟」「挂风湿免疫科」两类输入，确认进入挂号且能定位医生/科室。",
    },
    {
        "no": "P1-10",
        "level": "P1",
        "title": "单字「挂」太宽：说「挂念」也想给你挂号",
        "type": "意图识别 / 误判",
        "loc": "MedicalGraphBuilder.java L334、L336；BusinessToolExecutor.java L168",
        "status": "未修复",
        "scope": "全部患者会话的业务意图判断",
        "phenomenon": "关键词兜底列表里有单字「挂」。中文里带「挂」的词很多：挂念、挂钩、挂水、挂断、挂电话。",
        "cause": "关键词兜底为了「宁多勿漏」把单字也放进去，代价是把正常闲聊误判成业务诉求。",
        "effects": [
            "用户随口说「我有点挂念家里老人」也会进挂号流程，回「请问挂哪个科室」；",
            "答非所问是 AI 客服最伤体验的毛病，且日志里看不出是关键词误伤；",
            "误判会连带调用业务工具、增加数据库压力与模型润色成本。",
        ],
        "fixes": [
            "去掉单字，只保留双字以上明确词（挂号、预约、挂个号、的号、号吗）；",
            "「挂水」「挂瓶」等医疗口语要么单独处理，要么交回对话节点；",
            "为关键词判断补一组正负样本用例（见 P2-21）。",
        ],
        "verify": "输入「我有点挂念家里老人」「昨天挂了个电话」，均应进入对话节点而非业务节点。",
    },
    {
        "no": "P1-11",
        "level": "P1",
        "title": "时间只认「上午9点」：说 9:30、下周三就听不懂",
        "type": "解析健壮性",
        "loc": "BusinessToolExecutor.java · extractTime（L509-534）、extractDoctorFromHistory（L559-577）",
        "status": "未修复",
        "scope": "患者挂号（时间确认）、跨轮挂号续接",
        "phenomenon": "时间解析只认「上午/下午/晚上/中午 + 数字 + 点/时 + 半」。患者说「明天 9:30」「下周三」「三天后」"
                      "「十点半」，一律解析为「没听懂」，回「请告诉我希望就诊的时间」。",
        "cause": "补充一个结构性问题：挂号要分两轮（先选医生、再说时间），原来靠「从上一轮 AI 回复里用正则捞医生名」，"
                 "而上一轮回复经过大模型润色，改个格式正则就失效。现在已有 pendingDoctor 结构化字段兜底（正确方向），"
                 "但说明「让文本充当数据」本身就脆。",
        "effects": [
            "患者连续两轮被要求补充时间，多数会放弃；",
            "跨轮续接依赖文本格式，润色策略一变即失效；",
            "中文数字、区间、相对日期等常见表达全部缺失。",
        ],
        "fixes": [
            "补 HH:mm、中文数字、下周X、X天后、X月X日等常见表达，并返回解析置信度；",
            "跨轮信息一律走状态字段（pendingDoctor/pendingTime），禁止从润色文案反捞；",
            "解析失败时给出可选项而非空泛追问（如「明天上午 9:30 可以吗」）。",
        ],
        "verify": "用「明天 9:30」「下周三上午十点」「三天后下午」各测一遍，确认生成正确时间。",
    },
    {
        "no": "P2-12",
        "level": "P2",
        "title": "每个请求开一辆新车，用完还不熄火",
        "type": "并发 / 资源管理",
        "loc": "AiAgentController.java L82-105；DoctorAssistController.java L103-120",
        "status": "未修复",
        "scope": "全部流式（SSE）接口",
        "phenomenon": "每个流式请求都 new Thread 起一个新线程执行图流转与 SSE 推送，没有线程池、没有队列、没有上限；"
                      "前者创建的还是非 daemon 线程。",
        "cause": "就像餐厅每来一位客人就临时招一个服务员，客人走了服务员还在店里晃——短期看不出问题，忙起来就是灾难。",
        "effects": [
            "高并发时线程数无上限，可能 OOM 或响应雪崩；",
            "没有队列就没有背压，撑不住时不是排队而是直接失败；",
            "非 daemon 线程会拖慢 JVM 关停；线程名虽带会话 ID，但缺少统一追踪。",
        ],
        "fixes": [
            "改用 Spring TaskExecutor（有界队列 + 拒绝策略 + 超时）；",
            "线程上下文透传 traceId，便于串联日志；",
            "SSE 超时与前端断开时的回调清理纳入统一生命周期。",
        ],
        "verify": "压测 200 并发流式请求，观察线程数与拒绝策略是否按预期触发。",
    },
    {
        "no": "P2-13",
        "level": "P2",
        "title": "两个人同时改同一张表，后保存的把前面的擦了",
        "type": "并发 / 数据一致性",
        "loc": "AiAgentService.process（读改写 L110-191）；StreamTokenRegistry.java（以 sessionId 为键）",
        "status": "未修复",
        "scope": "同一会话的并发请求",
        "phenomenon": "会话状态在 Redis 里是「整份 JSON 读出来 → 改 → 整份写回去」；流式回调注册表只用 sessionId 做键。",
        "cause": "本质是没上锁的读改写。两台服务器各有各的锁等于没锁，需要分布式锁。",
        "effects": [
            "用户连点两次发送：两个请求各读旧状态、各写一遍，后到的覆盖先到的，其中一轮对话丢失；",
            "同会话两个流式请求：第二个回调顶掉第一个，第一个的字推不出去；先结束的还会在 finally 里删掉第二个的回调。",
        ],
        "fixes": [
            "会话写加锁（单机分段锁 / 多机 Redis 分布式锁），锁粒度到 sessionId；",
            "回调注册键改为 (sessionId, requestId)，互不覆盖；",
            "或对同会话请求做串行化（前端禁重复提交 + 后端幂等 token）。",
        ],
        "verify": "并发两次调用同一 sessionId，确认两轮消息都落库、没有丢失。",
    },
    {
        "no": "P2-14",
        "level": "P2",
        "title": "一句话要问三遍模型、查四遍表",
        "type": "性能 / 成本",
        "loc": "PromptTemplateLoader.render（每次查库）；MedicalGraphBuilder.isBusinessIntent；BusinessAgentNode.polishReply",
        "status": "未修复",
        "scope": "全部真实模式对话",
        "phenomenon": "一次对话里提示词渲染会查库 2~4 次（路由、节点、润色各一次，render 每次实时查库）；真实模式下可能发生"
                      "「路由 LLM + 生成 LLM + 润色 LLM」三次模型调用。",
        "cause": "为支持管理员热更新，模板刻意不缓存；润色是为了让业务数据更像人话，却带来第三跳调用。",
        "effects": [
            "单次回复延迟 3~10 秒，流式掩盖了部分但首字延迟仍在；",
            "token 成本约为必要量的三倍；",
            "高峰期对数据库造成无意义压力。",
        ],
        "fixes": [
            "模板查询加 30 秒本地缓存 + 主动 invalidate（与 AiModelConfigProvider 同款模式，已有先例）；",
            "润色合并进生成提示词，或在 mock/低风险场景跳过；",
            "为单次请求加耗时分段日志，量化三跳各自占比。",
        ],
        "verify": "压测下单链路，对比优化前后首字延迟、总延迟与 token 消耗。",
    },
    {
        "no": "P2-15",
        "level": "P2",
        "title": "聊天记录只进不出，列表页还得整包搬",
        "type": "存储 / 性能",
        "loc": "MedicalGraphState.chatHistory；RedisAiStateStore.listByPatient；AiAgentService.listSessions",
        "status": "未修复",
        "scope": "患者端会话历史、会话列表",
        "phenomenon": "chatHistory 每轮追加、从不裁剪，整份序列化进 Redis；会话列表要把该患者每个会话逐个取回、整包反序列化后再排序。",
        "cause": "提示词实际只用最近 6 条，其余 99% 历史「存着不用、还拖慢每次读写」。",
        "effects": [
            "会话越长，单条 Redis value 越大，读写越来越慢；",
            "列表页取 N 个完整会话才显示 N 行摘要，N+1 次大 JSON 往返；",
            "内存占用随使用时间线性增长。",
        ],
        "fixes": [
            "历史设上限（如最近 40 条）或超长时做摘要；",
            "会话列表单独存轻量摘要（sessionId + 最后内容 + 时间 + 阶段）；",
            "列表接口改 MGET 或读摘要索引。",
        ],
        "verify": "模拟 200 轮对话后检查单会话 Redis 大小与列表接口耗时。",
    },
    {
        "no": "P2-16",
        "level": "P2",
        "title": "同一段文字给三个医生各算一遍向量；改一个字，整库重算",
        "type": "性能 / RAG 构建",
        "loc": "DoctorRagService.rebuild（L140-173，L166 循环内 embed）、rebuildDocument、createStore；KnowledgeServiceImpl 重建调用",
        "status": "未修复",
        "scope": "科室介绍库索引构建与热更新",
        "phenomenon": "在「片段 × 命中医生」的双层循环里对同一段文本重复调 embed；按库→文档→片段逐层查询（N+1）。"
                      "内存向量库不支持按文档过滤删除，所以管理端每改一个片段都会触发临床指南库全量重建。",
        "cause": "以医生为粒度建索引（一个片段可关联多位医生）本身合理，但把「同一个片段的向量」也按医生重复计算了；"
                 "内存库的能力缺失又把局部更新退化成全量。",
        "effects": [
            "一段介绍提到 3 位医生就要算 3 次相同向量，启动变慢、算力白烧；",
            "管理员每保存一次就触发后台全量重算，改得越勤越慢；",
            "数据量增长后重建时间会线性放大。",
        ],
        "fixes": [
            "先按片段算一次向量，复用到所有匹配医生；",
            "批量查询替代逐层查；",
            "内存库升级为支持按文档删除，或直接以 Milvus 为主。",
        ],
        "verify": "记录重建耗时与 embed 调用次数，优化后应下降为原来的 1/N（N=每位片段关联的医生数）。",
    },
    {
        "no": "P2-17",
        "level": "P2",
        "title": "检索永远返回前 3 条，哪怕相似度只有 0.05",
        "type": "准确性 / 医疗安全",
        "loc": "ClinicalRagService.search（L119-135）、buildRagContext（L144-156）；DoctorAssistAnalyzeService.prepare（L199、L201）",
        "status": "未修复",
        "scope": "医生端问诊分析",
        "phenomenon": "临床指南检索只按分数排序取 topK，没有最低分门槛；prepare 里为了两个用途把同一句查询算了两次向量。",
        "cause": "「有依据总比没依据好」是错觉。低相关上下文比没有上下文更危险：模型看到「有指南」，可能顺着编。",
        "effects": [
            "指南库只有「感冒」「糖尿病」时，来一个「崴脚」也会把 0.05 相似度的片段塞进提示词；",
            "医生看到的「依据来源」可能完全不相关，损害信任；",
            "同一查询 embed 两次，纯属浪费。",
        ],
        "fixes": [
            "加 minScore（如 0.6），低于阈值直接丢弃并如实告知「未检索到相关指南」；",
            "检索只算一次，结构化来源与上下文字符串复用同一结果；",
            "来源列表展示分数，让医生可判断可信度。",
        ],
        "verify": "用一个与指南库无关的症状测试，确认 ragSources 为空且提示词中无低相关片段。",
    },
    {
        "no": "P2-18",
        "level": "P2",
        "title": "草稿表只写不读，患者说一句就多一行",
        "type": "存储 / 数据治理",
        "loc": "PatientDialogAgentNode L137-139；DoctorAssistAgentNode L77-84；SaveDraftAuditNode；DoctorAssistAnalyzeService.getLatestDraft",
        "status": "未修复",
        "scope": "sm_prescription_draft",
        "phenomenon": "患者每说一句落一条 SYMPTOM_SUMMARY 草稿，医生每说一句落一条 REPLY_DRAFT；但全工程只有 ANALYSIS 类型有读取方。",
        "cause": "草稿落库是「可追溯」的合规要求，实现时对所有草稿类型一视同仁，却没有配套的查阅与归档。",
        "effects": [
            "表持续膨胀，却没有任何页面会读这些行；",
            "将来做审计统计时会被噪声数据带偏；",
            "备份与迁移成本随之上升。",
        ],
        "fixes": [
            "要么找到使用场景（医生端「查看患者预问诊草稿」）；",
            "要么停止落库或按月归档；",
            "至少为表加「保留期 + 清理任务」，并统计各类型占比再决定。",
        ],
        "verify": "统计各 type 的行数与近 30 天增速，确认清理策略覆盖无读取方的类型。",
    },
    {
        "no": "P2-19",
        "level": "P2",
        "title": "一行代码能让接口直接 500",
        "type": "健壮性",
        "loc": "DoctorAssistAnalyzeService.getLatestDraft（L591）",
        "status": "未修复",
        "scope": "医生端草稿缓存接口",
        "phenomenon": "getLatestDraft 写的是 record.getDoctorId().equals(doctorId)，而同文件 prepare（L181）明确判断过 doctorId 可能为 null。",
        "cause": "作者知道这里有坑，但另一处忘了。属于典型的「局部已防御、全局未统一」。",
        "effects": [
            "问诊记录未分配医生（数据迁移遗留/异常数据）时抛空指针，接口 500；",
            "调用方无法区分「无草稿」与「服务异常」。",
        ],
        "fixes": [
            "改为 Objects.equals(record.getDoctorId(), doctorId)；",
            "顺带补 record 为 null 的分支（当前已判，但建议统一写法）；",
            "同类写法在全模块做一次检索。",
        ],
        "verify": "构造 doctorId 为 null 的问诊记录，调用 /draft/{consultId} 应返回 null 而非 500。",
    },
    {
        "no": "P2-20",
        "level": "P2",
        "title": "自己造对象工厂，还每条药查一次库",
        "type": "工程规范 / N+1",
        "loc": "AiAgentController L42、DoctorAssistController L39、DoctorAssistAnalyzeService L88（new ObjectMapper）；"
               "markInHospitalDrugs（L517-536）",
        "status": "未修复",
        "scope": "全部接口的 JSON 处理、分析结果的药品匹配",
        "phenomenon": "三处手工 new ObjectMapper，绕过 Spring 统一配置；markInHospitalDrugs 对每个候选药单独 selectCount 一次。",
        "cause": "早期快速实现留下的习惯，后来没人统一。",
        "effects": [
            "日期格式、未知字段策略、命名策略可能与其他模块不一致；",
            "一次分析 5 个药就是 5 次查询，可合并为一次；",
            "序列化行为难以通过配置统一调整。",
        ],
        "fixes": [
            "注入 Spring 的 ObjectMapper；",
            "药品匹配改为一次 in 查询后内存比对；",
            "补一条「序列化格式与其他模块一致」的集成测试。",
        ],
        "verify": "对比该接口与其他接口的日期字段格式；统计一次分析的 SQL 条数。",
    },
    {
        "no": "P2-21",
        "level": "P2",
        "title": "一个测试都没有",
        "type": "质量保障",
        "loc": "src/test 下仅一个与本模块无关的调度器测试",
        "status": "未修复",
        "scope": "AI 模块全部纯逻辑",
        "phenomenon": "意图关键词判断、时间解析、增量 JSON 解析、来源去重这些最适合单测、最容易写错的纯函数，零覆盖。",
        "cause": "功能迭代快，测试被排在后面；而这些逻辑又恰好是「改一处、崩三处」的高发区。",
        "effects": [
            "P1-9、P1-10、P1-11、P2-17 中任何一条都能被单测拦住；",
            "挂号涉及真实号源，回归风险高；",
            "重构时没有安全网，只能靠手工点。",
        ],
        "fixes": [
            "先把纯函数抽出来（去掉数据库依赖）；",
            "为关键词、时间、解析器各写参数化用例表；",
            "为图路由补一条「输入 → 期望节点」的用例。",
        ],
        "verify": "mvn test 通过，且覆盖上述四类逻辑的主路径与边界。",
    },
    {
        "no": "P2-22",
        "level": "P2",
        "title": "两个门：一个通往真房间，一个通往样板间",
        "type": "技术债 / 可维护性",
        "loc": "AiService / AiServiceImpl（4 个 Demo 接口）vs DoctorAssistAnalyzeService（真实能力）",
        "status": "未修复",
        "scope": "/ai/clinical-analysis 等 4 个接口",
        "phenomenon": "AiServiceImpl 返回硬编码假数据，注释还写着「待接入真实 FastAPI」；而真实能力已经在 DoctorAssistAnalyzeService 里实现。",
        "cause": "V2.3 阶段先做接口占位，后来能力在另一条线上实现，旧入口没清理。",
        "effects": [
            "新同学改半天发现它根本不调模型；",
            "前端误接后上线被投诉「AI 结果是固定文案」；",
            "两套实现同时存在，文档与代码对不上。",
        ],
        "fixes": [
            "确认无调用后删除，或标注 @Deprecated 并在文档划掉；",
            "若保留，改为委托真实服务，避免出现第二套事实。",
        ],
        "verify": "全局搜索四个接口路径的调用方，确认前端已迁移。",
    },
]

APPENDIX_A = [
    ("cs_", "新链路 · 患者端数字医生小林", "Redis + MySQL（双写）", "是（当前代码）", "是", "正常路径"),
    ("ds_", "新链路 · 医生端数字医生小林", "Redis", "否（明确跳过）", "否", "内部助手，不进预问诊表"),
    ("ai_", "新链路 · 预问诊（source 为空）", "Redis + MySQL（双写）", "是（当前代码）", "是", "注释声称跳过，实际未跳过"),
    ("pre_", "旧链路 PreConsultServiceImpl", "MySQL（自己写）", "是（自己写）", "是（客户端不传 sessionId 时）", "默认前缀，与 ai_ 不同"),
]

APPENDIX_B = [
    ("B-1", "检查是否重复落库", "SELECT session_id, sender, LEFT(content,40) c, COUNT(*) n FROM sm_pre_consult "
                              "GROUP BY session_id, sender, c HAVING n > 1 ORDER BY n DESC;"),
    ("B-2", "检查前缀分布", "SELECT LEFT(session_id,4) p, COUNT(*) FROM sm_pre_consult GROUP BY p;"),
    ("B-3", "检查平行会话", "SELECT patient_id, session_id, MIN(created_at), COUNT(*) FROM sm_pre_consult "
                          "GROUP BY patient_id, session_id HAVING COUNT(*) > 2 ORDER BY patient_id;"),
    ("B-4", "检查草稿表增长", "SELECT type, COUNT(*), MAX(created_at) FROM sm_prescription_draft GROUP BY type;"),
    ("B-5", "确认前端调用链", "在前端工程检索 patient-chat、preconsult，确认预问诊页面只调一条链路。"),
    ("B-6", "确认校验是否仍在", "检索 stateStore.get(sid) 附近是否有归属校验；检索 requireDoctor 是否覆盖 4 个接口。"),
]

TERMS = [
    ("IDOR", "越权访问对象引用：不校验「这个对象是不是你的」，只认编号，拿到编号即可读写他人数据。"),
    ("双写", "同一份数据同时写入两个存储（此处为 Redis 与 MySQL），用于跨链路读取，但需要唯一写入方与幂等。"),
    ("幂等", "同一请求执行多次与执行一次效果相同，通常靠唯一键或判重实现。"),
    ("影子模式", "新机制先只统计、不真正生效，用于观察命中率与风险，再决定是否放开。"),
    ("向量维度", "把文本转成数字数组后的长度；模型不同长度不同，向量库集合必须与之匹配。"),
    ("RAG", "检索增强生成：先从知识库检索相关片段，再连同问题一起交给模型，减少凭空编造。"),
    ("SSE", "Server-Sent Events：服务端单向持续推送文本，用于流式输出与打字机效果。"),
    ("N+1 查询", "循环里逐条查库，1 次主查询带出 N 次子查询；数据量大时成为性能瓶颈。"),
    ("阶段机", "用枚举描述业务流程所处阶段（预问诊/接诊/结束），路由据此选择处理节点。"),
    ("草稿与正式处方", "AI 只写草稿表并留审计，正式处方必须由医生确认后经业务逻辑写入，二者物理隔离。"),
]

FILES = [
    ("agent/graph/MedicalGraphBuilder.java", "图装配、条件路由、业务意图识别"),
    ("agent/node/PatientDialogAgentNode.java", "预问诊对话：症状摘要、科室推荐、RAG 上下文"),
    ("agent/node/AssistantDialogNode.java", "数字医生小林客服对话（含医生端）"),
    ("agent/node/BusinessAgentNode.java", "业务意图 → 工具执行 → 回复润色"),
    ("agent/node/DoctorAssistAgentNode.java", "医生回复草稿 / 处方草稿生成"),
    ("agent/node/SaveDraftAuditNode.java", "草稿落库与审计日志的统一收口"),
    ("agent/tool/BusinessToolExecutor.java", "挂号、进度、报告、医生接诊的白名单工具"),
    ("agent/llm/LlmClientService.java", "模型封装：mock/真实、同步/流式、重试"),
    ("agent/llm/PromptTemplateLoader.java", "提示词三级加载与内置模板播种"),
    ("agent/rag/DoctorRagService.java", "科室介绍库 RAG，医生推荐"),
    ("agent/rag/ClinicalRagService.java", "临床指南库 RAG，医生分析依据"),
    ("agent/cache/SemanticCacheService.java", "语义缓存（影子模式）"),
    ("agent/service/AiAgentService.java", "会话装载、图执行、双写、返回组装"),
    ("agent/service/DoctorAssistAnalyzeService.java", "问诊分析：上下文装配、RAG、JSON 解析、留痕"),
    ("agent/service/AnalysisStreamParser.java", "流式 JSON 增量解析（字段级推送）"),
    ("agent/service/AiStateStore.java", "会话存储接口（内存 / Redis 两实现）"),
    ("agent/stream/StreamTokenRegistry.java", "流式回调注册表（绕开 State 序列化）"),
    ("controller/AiAgentController.java", "对话入口（同步 + SSE）"),
    ("controller/DoctorAssistController.java", "问诊分析入口（同步 + SSE + 草稿缓存）"),
    ("controller/AiController.java", "4 个医生端 Demo 接口（已加角色校验）"),
]


# =====================================================================
# 排版
# =====================================================================

def build():
    doc = Document()

    # 页面与默认样式
    section = doc.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.4)
    section.bottom_margin = Cm(2.2)
    section.left_margin = Cm(2.6)
    section.right_margin = Cm(2.6)
    section.header_distance = Cm(1.2)
    section.footer_distance = Cm(1.1)
    section.different_first_page_header_footer = True

    normal = doc.styles["Normal"]
    style_font(normal, 10.5, color=RGBColor(0x26, 0x26, 0x26))
    normal.paragraph_format.line_spacing = 1.45
    normal.paragraph_format.space_after = Pt(4)

    # 页眉
    header = section.header
    hp = header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    hr = hp.add_run("智慧医疗系统 · AI 模块代码问题清单  |  v1.1")
    set_run_font(hr, size=8, color=C_LIGHT, ea=EA_HEAD, ascii_font=ASCII_HEAD)

    # 页脚页码
    footer = section.footer
    fp = footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r1 = fp.add_run("第 ")
    set_run_font(r1, size=8, color=C_GRAY)
    add_field(fp, " PAGE ", "1")
    r2 = fp.add_run(" 页 / 共 ")
    set_run_font(r2, size=8, color=C_GRAY)
    add_field(fp, " NUMPAGES ", "1")
    r3 = fp.add_run(" 页")
    set_run_font(r3, size=8, color=C_GRAY)
    for run in fp.runs:
        set_run_font(run, size=8, color=C_GRAY)

    # ---------------- 封面 ----------------
    p(doc, "", space_after=60)
    kicker = doc.add_paragraph()
    kicker.alignment = WD_ALIGN_PARAGRAPH.LEFT
    kr = kicker.add_run("CODE REVIEW  ·  STATIC ANALYSIS")
    set_run_font(kr, size=9, color=C_MID, bold=True, ea=EA_HEAD, ascii_font=ASCII_HEAD)
    kicker.paragraph_format.space_after = Pt(10)

    title = doc.add_paragraph()
    tr = title.add_run("AI 模块代码问题清单")
    set_run_font(tr, size=30, bold=True, color=C_DARK, ea=EA_HEAD, ascii_font=ASCII_HEAD)
    title.paragraph_format.space_after = Pt(6)

    sub = doc.add_paragraph()
    sr = sub.add_run("智慧医疗系统 · smart-medical-backend")
    set_run_font(sr, size=13, color=C_MID, ea=EA_HEAD, ascii_font=ASCII_HEAD)
    sub.paragraph_format.space_after = Pt(18)

    rule = doc.add_paragraph()
    para_border_bottom(rule, color="1F3864", size=12)
    rule.paragraph_format.space_after = Pt(22)

    lead = doc.add_paragraph()
    lead.paragraph_format.space_after = Pt(26)
    lr = lead.add_run("22 项问题 · 2 项已修复 · 1 项需本周处理 · 覆盖权限、数据一致性、性能与工程质量")
    set_run_font(lr, size=10.5, color=C_GRAY, ea=EA_HEAD, ascii_font=ASCII_HEAD)

    meta_table = doc.add_table(rows=0, cols=2)
    meta_table.autofit = False
    cell_margins(meta_table, top=60, bottom=60, left=0, right=80)
    for key, value in META:
        row = meta_table.add_row()
        no_split(row)
        c0, c1 = row.cells
        c0.width = Cm(3.6)
        c1.width = Cm(11.6)
        c0.text = ""
        c1.text = ""
        pp = c0.paragraphs[0]
        pp.paragraph_format.space_after = Pt(0)
        rk = pp.add_run(key)
        set_run_font(rk, size=9, color=C_LIGHT, ea=EA_HEAD, ascii_font=ASCII_HEAD)
        pv = c1.paragraphs[0]
        pv.paragraph_format.space_after = Pt(0)
        pv.paragraph_format.line_spacing = 1.35
        add_rich(pv, value, size=9.5)

    doc.add_page_break()

    # ---------------- 一、结论摘要 ----------------
    heading(doc, "一、结论摘要", 1)
    p(doc, "本模块是「LangGraph4j 状态图 + 5 个节点 + 条件路由」的医疗多智能体实现，另有一条 V2.3 遗留的 Demo REST 路线。"
           "整体分层清晰：对话节点只负责问诊交互，业务节点只做白名单查询，草稿与审计由统一收口节点落库，AI 与正式处方物理隔离。"
           "这些设计是可靠的，问题集中在三处：**权限边界依赖各 Controller 自觉**、**同一份事实存在多个来源**、"
           "**并发与规模相关的工程化缺位**。")
    p(doc, "审查期间开发侧已修复合话 IDOR 与旧接口角色校验两项 P0；本文按最新代码标注状态，并给出残余风险与后续建议。")

    heading(doc, "1.1 问题分布", 2)
    table(doc, ["级别", "判定标准", "数量", "已修复", "未修复"], LEVEL_DEF, [1.8, 8.6, 1.6, 1.6, 1.6])

    heading(doc, "1.2 风险判断", 2)
    p(doc, "P0-1 与 P0-2 是本次审查中唯一「可被直接利用」的两条，目前均已修复；但修复引入了新的注释错误（见 P0-3），"
           "且残留「归属为空即放行」的边界条件，建议复查。P1 的七条都属于「功能看着能用、特定输入会答错或记错」，"
           "其中 P1-4 的风险在于「注释会骗下一个改代码的人」，按注释修改反而会丢数据。P2 在当前数据量下不疼，"
           "但 P2-12、P2-13 是并发一旦上来就会集中爆发的类型，建议排期前移。")

    heading(doc, "1.3 建议先做的三件事", 2)
    bullets(doc, [
        "**修 P0-3（0.2 人日）**：把路径白名单接入拦截器或删除，同时修正 P0-2 中引用它的注释——这是当下最容易误导后来者的点。",
        "**定 P1-4 的唯一写入方（0.5 人日）**：先在前端确认预问诊页面到底调哪条链路，再决定注释与代码谁改；无论怎么选都叠加幂等。",
        "**给纯逻辑补单测（1.0 人日）**：意图关键词、时间解析、增量 JSON 解析、来源去重四类各一张用例表，"
        "后续修 P1 的每一条都有安全网。",
    ])

    # ---------------- 二、修复路线图 ----------------
    heading(doc, "二、修复路线图", 1)
    p(doc, "工作量为人日估算，含自测；依赖项未满足时先做前置确认。阶段划分按「风险 × 感知度」而非单纯按编号。")
    table(doc, ["阶段", "编号", "事项", "工作量", "说明"], ROADMAP, [2.4, 2.9, 5.0, 1.4, 4.1])

    # ---------------- 三、问题清单 ----------------
    doc.add_page_break()
    heading(doc, "三、问题清单", 1)
    p(doc, "每条包含：元信息（级别/类型/位置/状态/影响面）、现象、原因（通俗解释）、影响、修复建议、验证方法。"
           "行号以 2026-11-03 的代码快照为准，若后续改动请以方法名定位。")

    for issue in ISSUES:
        heading(doc, f"{issue['no']}  {issue['title']}", 2)

        info = doc.add_table(rows=2, cols=4)
        info.style = "Table Grid"
        info.autofit = False
        cell_margins(info, top=50, bottom=50, left=90, right=90)
        labels = [("级别", issue["level"]), ("类型", issue["type"]), ("状态", issue["status"]), ("影响面", issue["scope"])]
        first = info.rows[0]
        second = info.rows[1]
        no_split(first)
        no_split(second)
        widths = [1.5, 2.2, 1.5, 10.0]
        for idx, (label, value) in enumerate(labels):
            cell = first.cells[idx]
            cell.width = Cm(widths[idx])
            shade(cell, "EDF1F8")
            cell.text = ""
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_after = Pt(0)
            paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = paragraph.add_run(label)
            set_run_font(run, size=8.5, color=C_GRAY, bold=True, ea=EA_HEAD, ascii_font=ASCII_HEAD)
            cell2 = second.cells[idx]
            cell2.width = Cm(widths[idx])
            cell2.text = ""
            paragraph2 = cell2.paragraphs[0]
            paragraph2.paragraph_format.space_after = Pt(0)
            paragraph2.paragraph_format.line_spacing = 1.25
            if idx == 0:
                paragraph2.alignment = WD_ALIGN_PARAGRAPH.CENTER
                run2 = paragraph2.add_run(value)
                color = LEVEL_COLOR.get(value, C_GRAY)
                set_run_font(run2, size=10, bold=True, color=color, ea=EA_HEAD, ascii_font=ASCII_HEAD)
            elif idx == 2:
                paragraph2.alignment = WD_ALIGN_PARAGRAPH.CENTER
                run2 = paragraph2.add_run(value)
                color = STATUS_COLOR.get(value, C_GRAY)
                set_run_font(run2, size=9, bold=True, color=color, ea=EA_HEAD, ascii_font=ASCII_HEAD)
            else:
                add_rich(paragraph2, value, size=9)

        p(doc, "", space_after=2)
        p(doc, "位置：" + issue["loc"], size=9, color=C_GRAY, space_after=6)

        p(doc, "现象", size=9.5, bold=True, color=C_DARK, space_before=4, space_after=2)
        p(doc, issue["phenomenon"], indent=0.0)

        p(doc, "原因", size=9.5, bold=True, color=C_DARK, space_before=4, space_after=2)
        p(doc, issue["cause"], indent=0.0)

        p(doc, "影响", size=9.5, bold=True, color=C_DARK, space_before=4, space_after=2)
        bullets(doc, issue["effects"])

        p(doc, "修复建议", size=9.5, bold=True, color=C_DARK, space_before=4, space_after=2)
        bullets(doc, issue["fixes"])

        p(doc, "验证方法", size=9.5, bold=True, color=C_DARK, space_before=4, space_after=2)
        p(doc, issue["verify"], size=9.5, color=RGBColor(0x33, 0x33, 0x33))

    # ---------------- 附录 ----------------
    doc.add_page_break()
    heading(doc, "附录 A  会话前缀与数据写入方", 1)
    p(doc, "排查「数据记了两遍」时先看这张表：前缀本身就是写入方的线索，但前缀是调用方可传入的，不能作为可靠身份。")
    table(doc, ["前缀", "生成方", "存储", "是否写 MySQL", "进医生摘要", "备注"], APPENDIX_A,
          [1.2, 4.0, 2.8, 2.6, 1.8, 3.4], font=8.5)

    heading(doc, "附录 B  自查清单", 1)
    p(doc, "下列 SQL 与检索可直接用于确认问题是否仍存在，建议在修复前后各执行一次。")
    table(doc, ["编号", "目的", "语句 / 操作"], APPENDIX_B, [1.3, 3.2, 11.3], font=8.5)

    heading(doc, "附录 C  术语表", 1)
    table(doc, ["术语", "含义"], TERMS, [3.0, 12.8], font=8.5)

    heading(doc, "附录 D  审查对象清单", 1)
    p(doc, "以下为本题域的核心文件（共 34 个 Java 文件，此处列出与问题直接相关的 20 个）。")
    table(doc, ["文件（module/ai 下）", "职责"], FILES, [7.6, 8.2], font=8.5)

    heading(doc, "说明与限制", 1)
    bullets(doc, [
        "本文为**静态代码审查**结论：逐文件阅读源码与配置，未编译、未运行单元测试、未做接口联调。",
        "审查期间代码发生变更（P0-1、P0-2 的修复），行号与状态以 2026-11-03 14:5x 的快照为准；后续改动请以方法名定位。",
        "P0-2 修复后 AiService 接口签名发生变化，若编译报错请同步其他调用方。",
        "文中「影响」为基于代码路径的推断，未做线上流量验证；涉及数据量的判断（如草稿表增长）需结合真实库确认。",
        "工作量估算为经验值，不含需求确认、联调与回归测试时间。",
    ])

    doc.core_properties.title = "智慧医疗系统 AI 模块代码问题清单"
    doc.core_properties.subject = "静态代码审查报告"
    doc.core_properties.author = "代码审查"
    doc.core_properties.keywords = "AI 模块, 代码审查, 问题清单, LangGraph4j, RAG, 医疗"
    doc.core_properties.comments = "v1.1 / 2026-11-03"

    doc.save(OUT)
    return OUT


def verify(path):
    d = Document(path)
    texts = [q.text for q in d.paragraphs]
    body = "\n".join(texts)
    head_texts = set()
    for q in d.paragraphs:
        if q.style is not None and q.style.name in ("Heading 1", "Heading 2"):
            head_texts.add(q.text)
    print("PARAS:", len(d.paragraphs), "TABLES:", len(d.tables), "HEADINGS:", len(head_texts))
    checks = ["AI 模块代码问题清单", "一、结论摘要", "二、修复路线图", "三、问题清单",
              "P0-1", "P0-3", "P1-11", "P2-19", "P2-22",
              "附录 A", "附录 B", "附录 C", "附录 D", "说明与限制"]
    missing = [c for c in checks if c not in body]
    print("MISSING:", missing if missing else "none")
    print("NONE_LITERAL:", body.count("None"))
    h2 = [q.text for q in d.paragraphs if q.style is not None and q.style.name == "Heading 2"]
    print("H2_COUNT:", len(h2))
    # 表格宽度检查：正文可用宽度 15.8cm，超出会压边距
    over = []
    for idx, t in enumerate(d.tables):
        try:
            total = sum(c.width.cm for c in t.rows[0].cells if c.width is not None)
        except Exception:
            continue
        if total > 15.85:
            over.append((idx, round(total, 2)))
    print("TABLES_TOO_WIDE:", over if over else "none")
    return not missing and not over


if __name__ == "__main__":
    path = build()
    print("SAVED:", path)
    print("SIZE:", os.path.getsize(path))
    ok = verify(path)
    print("VERIFY:", "PASS" if ok else "FAIL")
