<template>
  <div class="ai-analysis-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2>AI 辅助分析</h2>
      <p>AI 生成内容仅供医生参考，需人工审核后使用（不做诊断结论，不替代临床判断）</p>
    </div>

    <div class="analysis-grid">
      <!-- 左：输入区 -->
      <div class="card input-card">
        <h3>病例信息</h3>
        <el-form label-position="top">
          <el-form-item label="主诉">
            <el-input
              v-model="form.chiefComplaint"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="例如：反复咳嗽伴低热 3 天"
            />
          </el-form-item>
          <el-form-item label="病情描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="5"
              maxlength="1000"
              show-word-limit
              placeholder="补充症状细节、持续时间、既往史等"
            />
          </el-form-item>

          <el-divider />

          <h3 class="sub-title">图片 / 报告分析</h3>
          <el-form-item label="报告">
            <el-select v-model="form.reportId" placeholder="选择患者报告" clearable filterable style="width: 100%">
              <el-option
                v-for="r in reports"
                :key="r.id"
                :label="`#${r.id} ${r.reportType || '未命名报告'}`"
                :value="r.id"
              />
            </el-select>
          </el-form-item>
        </el-form>

        <div class="action-row">
          <el-button
            type="primary"
            :disabled="loading || !form.chiefComplaint.trim()"
            @click="run('clinical')"
          >病情分析</el-button>
          <el-button
            type="primary"
            plain
            :disabled="loading || !form.chiefComplaint.trim()"
            @click="run('examination')"
          >检查建议</el-button>
          <el-button
            type="primary"
            plain
            :disabled="loading || !form.chiefComplaint.trim()"
            @click="run('reply')"
          >回复草稿</el-button>
          <el-button
            type="warning"
            plain
            :disabled="loading || !form.reportId"
            @click="run('image')"
          >图片分析</el-button>
        </div>
      </div>

      <!-- 右：结果区 -->
      <div class="card result-card" v-loading="loading">
        <h3>分析结果</h3>
        <div v-if="!active" class="result-empty">
          <div class="robot-icon">🤖</div>
          <p>输入病例信息后点击上方按钮，AI 将生成辅助分析</p>
          <p class="tip">所有结果均标注 AI 置信度与参考来源，仅供医生参考</p>
        </div>

        <template v-else>
          <!-- 1. 病情分析 -->
          <template v-if="active === 'clinical' && result.conditionAnalysis">
            <div class="risk-tag" :class="`risk-${String(result.riskLevel || 'MEDIUM').toLowerCase()}`">
              风险评估：{{ result.riskLevel || '-' }}
            </div>
            <el-descriptions :column="1" border class="result-desc">
              <el-descriptions-item label="病情分析">{{ result.conditionAnalysis }}</el-descriptions-item>
              <el-descriptions-item label="诊断建议">{{ result.diagnosisSuggestion }}</el-descriptions-item>
              <el-descriptions-item label="鉴别诊断">
                <pre class="pre-text">{{ result.differentialDiagnosis }}</pre>
              </el-descriptions-item>
              <el-descriptions-item label="用药建议">{{ result.medicationSuggestion }}</el-descriptions-item>
              <el-descriptions-item label="参考来源">{{ result.references }}</el-descriptions-item>
            </el-descriptions>
            <div class="meta-row">
              <el-tag size="small" type="info">AI 置信度 {{ percent(result.confidence) }}</el-tag>
              <span class="disclaimer">{{ result.disclaimer }}</span>
            </div>
          </template>

          <!-- 2. 检查建议 -->
          <template v-else-if="active === 'examination' && result.examinationList">
            <el-alert :title="result.note" type="info" :closable="false" class="mb12" />
            <div class="ex-item" v-for="(ex, i) in result.examinationList" :key="i">
              <span class="idx">{{ i + 1 }}</span>
              <span class="ex-name">{{ ex }}</span>
            </div>
            <div class="meta-row">
              <el-tag size="small" type="info">建议紧急性 {{ result.urgency || '-' }}</el-tag>
              <el-tag size="small" type="info">AI 置信度 {{ percent(result.confidence) }}</el-tag>
            </div>
          </template>

          <!-- 3. 回复草稿 -->
          <template v-else-if="active === 'reply' && result.draft">
            <div class="draft-box">
              <pre class="pre-text">{{ result.draft }}</pre>
            </div>
            <div class="meta-row">
              <el-tag size="small" type="info">语气 {{ result.tone || '-' }}</el-tag>
              <el-tag size="small" type="info">AI 置信度 {{ percent(result.confidence) }}</el-tag>
            </div>
            <el-alert :title="result.suggestedEdit" type="warning" :closable="false" class="mt12" />
          </template>

          <!-- 4. 图片/报告分析 -->
          <template v-else-if="active === 'image'">
            <div class="report-title">报告 #{{ result.reportId }}（{{ result.reportTitle }}）</div>
            <el-alert
              title="当前为 Demo 模式，正式 AI 图片理解将在接入真实大模型后提供"
              type="warning"
              :closable="false"
              class="mb12"
            />
            <div class="analysis-text">{{ result.analysis }}</div>
            <div class="meta-row">
              <el-tag size="small" type="info">置信度 {{ percent(result.confidence) }}</el-tag>
              <el-tag v-if="result.needHuman" size="small" type="danger">需人工审核</el-tag>
            </div>
            <div class="disclaimer">{{ result.disclaimer }}</div>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 医生端「AI 辅助分析」页
 * 数据全部对接后端 /ai（AiController，V2.3 移除旧 /ai/chat）：
 *   POST /ai/clinical-analysis       病情分析 {conditionAnalysis, diagnosisSuggestion, differentialDiagnosis, medicationSuggestion, riskLevel, confidence, references, disclaimer}
 *   POST /ai/examination-suggestions 检查建议 {examinationList[], urgency, confidence, note}
 *   POST /ai/reply-drafts            回复草稿 {draft, confidence, tone, suggestedEdit}
 *   POST /ai/image-analysis          图片分析 {reportId, reportTitle, analysis, confidence, needHuman, disclaimer}
 * 合规边界：AI 输出仅供医生参考，须医生审核；不对患者直接展示置信度与参考来源。
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  clinicalAnalysis,
  examinationSuggestions,
  replyDrafts,
  imageAnalysis,
} from '@/api/ai'
import { fetchReports } from '@/api/report'

/** 输入表单（与后端入参对齐） */
const form = ref({ chiefComplaint: '', description: '', reportId: null })

/** 当前分析结果与激活模式 */
const active = ref('')
const result = ref({})
const loading = ref(false)

/** 可选报告列表（图片分析用，从后端拉取） */
const reports = ref([])

/** 执行指定模式的 AI 分析 */
async function run(mode) {
  if (!form.value.chiefComplaint.trim() && mode !== 'image') return
  if (mode === 'image' && !form.value.reportId) return
  loading.value = true
  try {
    let data
    if (mode === 'clinical') {
      data = await clinicalAnalysis({
        chiefComplaint: form.value.chiefComplaint,
        description: form.value.description,
        patientInfo: {},
      })
    } else if (mode === 'examination') {
      data = await examinationSuggestions({
        chiefComplaint: form.value.chiefComplaint,
        description: form.value.description,
      })
    } else if (mode === 'reply') {
      data = await replyDrafts({
        chiefComplaint: form.value.chiefComplaint,
        description: form.value.description,
      })
    } else if (mode === 'image') {
      data = await imageAnalysis({ reportId: form.value.reportId })
    }
    active.value = mode
    result.value = data || {}
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
}

/** 置信度转为百分比 */
function percent(v) {
  if (v === null || v === undefined) return '-'
  return `${Math.round(Number(v) * 100)}%`
}

/** 加载报告列表 */
async function loadReports() {
  try {
    const data = await fetchReports({ page: 1, size: 50 })
    reports.value = data.list || data || []
  } catch {
    /* 拦截器已统一提示 */
  }
}

onMounted(loadReports)
</script>

<style scoped>
.ai-analysis-page { padding: 24px; }

.page-head h2 { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 6px; }
.page-head p { font-size: 13px; color: #64748b; margin: 0 0 20px; }

.analysis-grid {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 20px;
  align-items: start;
}

.card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef0f4;
  box-shadow: 0 1px 3px rgba(16, 42, 67, 0.06);
  padding: 20px;
}

.card h3 { font-size: 15px; font-weight: 600; color: #1a1a2e; margin: 0 0 16px; }
.sub-title { margin-bottom: 12px; }

.action-row { display: flex; flex-wrap: wrap; gap: 10px; }

.result-card { min-height: 480px; }
.result-empty { text-align: center; color: #94a3b8; font-size: 14px; padding: 80px 0; }
.robot-icon { font-size: 40px; margin-bottom: 12px; }
.result-empty p { margin: 4px 0; }
.result-empty .tip { font-size: 12px; }

.result-desc { margin-top: 12px; }
.pre-text { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: inherit; }

.meta-row { display: flex; align-items: center; gap: 10px; margin-top: 14px; flex-wrap: wrap; }
.disclaimer { font-size: 12px; color: #94a3b8; }
.mb12 { margin-bottom: 12px; }
.mt12 { margin-top: 12px; }

.risk-tag {
  display: inline-block; padding: 4px 12px; border-radius: 100px; font-size: 12px; font-weight: 600;
  margin-bottom: 8px;
}
.risk-high { background: #fef2f2; color: #dc2626; }
.risk-medium { background: #fffbeb; color: #d97706; }
.risk-low { background: #ecfdf5; color: #059669; }

.ex-item {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px; border: 1px solid #eef0f4; border-radius: 10px; margin-bottom: 10px;
}
.idx {
  width: 22px; height: 22px; border-radius: 50%; background: #eaf2ff; color: #1E7AE6;
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600;
}
.ex-name { font-size: 14px; color: #1e293b; }

.draft-box { background: #f8fafc; border: 1px solid #eef0f4; border-radius: 10px; padding: 16px; }
.report-title { font-size: 15px; font-weight: 600; color: #1e293b; margin-bottom: 12px; }
.analysis-text { font-size: 14px; color: #334155; line-height: 1.8; }
</style>
