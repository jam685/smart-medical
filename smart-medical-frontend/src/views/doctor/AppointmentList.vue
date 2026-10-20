<script setup>
/**
 * 医生端「挂号/接诊管理」列表页组件
 * 职责：从后端就诊进度接口加载当前医生的接诊列表，支持按状态分组筛选，
 *       提供叫号→接诊→开处方→完成就诊的全流程操作。
 * 数据来源：GET  /visits（按医生 ID 隔离）
 *           PUT  /visits/{id}/status（推进就诊状态）
 *           POST /prescriptions（开具处方）
 *           GET  /prescriptions/by-visit/{id}（查已有处方）
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Remove, Edit, Document, Search } from '@element-plus/icons-vue'
import { fetchVisits, updateVisitStatus } from '@/api/visit'
import { createPrescription, fetchPrescriptionByVisit } from '@/api/prescription'

// ============== 状态 Tab ==============
/**
 * 状态筛选 Tab 配置
 * @property {string} key   Tab 键
 * @property {string} label Tab 显示文案
 */
const tabs = [
  { key: 'all', label: '全部' },
  { key: 'WAITING', label: '候诊中' },
  { key: 'CALLING', label: '已叫号' },
  { key: 'IN_PROGRESS', label: '就诊中' },
  { key: 'FINISHED', label: '已完成' },
]

/** 当前选中的 Tab 键 */
const activeTab = ref('all')

// ============== 分页 ==============
/** 每页条数（每满 6 条换页） */
const PAGE_SIZE = 6
/** 当前页码 */
const currentPage = ref(1)

// ============== 搜索 ==============
/** 患者姓名关键词（模糊匹配） */
const searchName = ref('')
/** 就诊日期（YYYY-MM-DD，匹配当天就诊） */
const searchDate = ref('')

/** 清除搜索条件 */
function clearSearch() {
  searchName.value = ''
  searchDate.value = ''
}

// ============== 接口数据 ==============
/** 就诊记录数组（后端 VisitVO），初始为空 */
const visits = ref([])
/** 列表加载状态 */
const loading = ref(false)

/**
 * 状态展示配置：label 文案 + 标签样式类
 */
const statusMap = {
  WAITING:     { label: '候诊中', cls: 'st-waiting' },
  CALLING:     { label: '已叫号', cls: 'st-calling' },
  IN_PROGRESS: { label: '就诊中', cls: 'st-progress' },
  FINISHED:    { label: '已完成', cls: 'st-finished' },
}

/**
 * 从后端加载当前医生的就诊列表
 */
async function loadVisits() {
  loading.value = true
  try {
    const page = await fetchVisits({ page: 1, size: 100 })
    visits.value = page.list || []
  } catch {
    // 请求失败提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/**
 * 当前 Tab + 搜索条件下应展示的就诊记录。
 * 排序规则：
 *   1. 按就诊进度状态为最优先级（候诊中→已叫号→就诊中→已完成 递增）；
 *   2. 同状态下按就诊时间最近优先（倒序）。
 */
const filtered = computed(() => {
  let list = visits.value
  // ① 状态 Tab 过滤
  if (activeTab.value !== 'all') {
    list = list.filter(v => v.status === activeTab.value)
  }
  // ② 姓名模糊搜索（支持部分匹配，如"王"匹配"王五"）
  const kw = searchName.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(v => (v.patientName || '').toLowerCase().includes(kw))
  }
  // ③ 就诊日期精确匹配（取就诊日期部分，等于所选日期）
  if (searchDate.value) {
    list = list.filter(v => (v.appointmentTime || '').slice(0, 10) === searchDate.value)
  }
  // ④ 状态优先 + 时间最近排序
  const statusOrder = { WAITING: 1, CALLING: 2, IN_PROGRESS: 3, FINISHED: 4 }
  return [...list].sort((a, b) => {
    const oa = statusOrder[a.status] ?? 5
    const ob = statusOrder[b.status] ?? 5
    if (oa !== ob) return oa - ob
    return String(b.appointmentTime || '').localeCompare(String(a.appointmentTime || ''))
  })
})

/**
 * 当前页应展示的就诊记录：对 filtered 结果按当前页码切片（每页 6 条）。
 */
const pageList = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})

/** 翻页（切换 Tab / 搜索后回到第一页） */
function switchTab(key) {
  activeTab.value = key
  currentPage.value = 1
}

/** 搜索条件变化时回到第一页 */
function resetPage() {
  currentPage.value = 1
}

/**
 * 统计指定 Tab 的数量
 */
function countOf(key) {
  return key === 'all' ? visits.value.length : visits.value.filter(v => v.status === key).length
}

// ============== 状态推进 ==============
/**
 * 推进就诊状态（叫号/接诊/完成）
 * @param {object} v    就诊记录
 * @param {string} status 目标状态
 * @param {string} label  操作确认文案
 */
async function advanceStatus(v, status, label) {
  try {
    await ElMessageBox.confirm(`确认对「${v.patientName}」执行${label}操作？`, '操作确认', {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'info', roundButton: true,
    })
  } catch { return }
  await updateVisitStatus(v.id, status)
  ElMessage.success(`${label}成功`)
  await loadVisits()
}

/**
 * 叫号：WAITING → CALLING
 */
function callPatient(v) { advanceStatus(v, 'CALLING', '叫号') }

/**
 * 接诊：CALLING → IN_PROGRESS
 */
function receivePatient(v) { advanceStatus(v, 'IN_PROGRESS', '接诊') }

/**
 * 完成就诊：IN_PROGRESS → FINISHED
 */
function finishVisit(v) { advanceStatus(v, 'FINISHED', '完成就诊') }

// ============== 处方弹窗 ==============
/** 处方弹窗可见性 */
const rxDialogVisible = ref(false)
/** 当前开处方的就诊记录 */
const rxVisit = ref(null)
/** 诊断 */
const rxDiagnosis = ref('')
/** 医嘱 */
const rxAdvice = ref('')
/** 药品明细列表 */
const rxItems = ref([])
/** 已有处方（查询结果） */
const existingRx = ref(null)
/** 处方提交中 */
const rxSubmitting = ref(false)

/**
 * 打开处方弹窗（先查已有处方）
 * @param {object} v 就诊记录
 */
async function openRxDialog(v) {
  rxVisit.value = v
  rxDiagnosis.value = ''
  rxAdvice.value = ''
  rxItems.value = [createEmptyItem()]
  existingRx.value = null
  rxDialogVisible.value = true
  // 查询是否已有处方
  try {
    const rx = await fetchPrescriptionByVisit(v.id)
    if (rx) {
      existingRx.value = rx
      rxDiagnosis.value = rx.diagnosis || ''
      rxAdvice.value = rx.advice || ''
      rxItems.value = rx.items?.length ? rx.items.map(i => ({ ...i })) : [createEmptyItem()]
    }
  } catch { /* 无处方忽略 */ }
}

/**
 * 创建空药品明细行
 */
function createEmptyItem() {
  return { drugName: '', specification: '', dosage: '', frequency: '', days: 1, quantity: '', usageMethod: '口服', remark: '' }
}

/**
 * 添加药品行
 */
function addItem() { rxItems.value.push(createEmptyItem()) }

/**
 * 删除药品行
 * @param {number} idx 索引
 */
function removeItem(idx) { rxItems.value.splice(idx, 1) }

/**
 * 提交处方
 */
async function submitPrescription() {
  if (!rxDiagnosis.value.trim()) { ElMessage.warning('请填写诊断'); return }
  const validItems = rxItems.value.filter(i => i.drugName.trim())
  if (validItems.length === 0) { ElMessage.warning('至少添加一种药品'); return }
  rxSubmitting.value = true
  try {
    await createPrescription({
      visitId: rxVisit.value.id,
      diagnosis: rxDiagnosis.value,
      advice: rxAdvice.value,
      items: validItems,
    })
    ElMessage.success('处方开具成功')
    rxDialogVisible.value = false
    await loadVisits()
  } catch { /* 拦截器已处理 */ } finally {
    rxSubmitting.value = false
  }
}

// 页面挂载即从后端加载数据
onMounted(loadVisits)
</script>

<template>
  <div class="appt-list-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2 class="page-title brand-font">接诊管理</h2>
        <p class="page-sub">查看与管理您的就诊患者，叫号→接诊→开处方→完成就诊</p>
      </div>
      <el-button plain @click="loadVisits">刷新列表</el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchName"
        placeholder="按患者姓名模糊搜索"
        clearable
        class="search-name"
        @input="resetPage"
        @clear="resetPage"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-date-picker
        v-model="searchDate"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="按就诊日期查询"
        clearable
        class="search-date"
        @change="resetPage"
      />
      <el-button v-if="searchName || searchDate" text class="clear-btn" @click="clearSearch; resetPage()">
        清除筛选
      </el-button>
    </div>

    <!-- 状态筛选 Tab -->
    <div class="tab-row">
      <button
        v-for="t in tabs" :key="t.key"
        :class="['tab-pill', { active: activeTab === t.key }]"
        @click="switchTab(t.key)"
      >
        {{ t.label }}<span class="tab-count">{{ countOf(t.key) }}</span>
      </button>
    </div>

    <!-- 就诊卡片列表 -->
    <div class="card-list">
      <div v-for="v in pageList" :key="v.id" class="visit-card">
        <!-- 左：状态徽章 -->
        <div class="status-badge" :class="statusMap[v.status]?.cls || 'st-waiting'">
          <span class="sb-icon">
            <svg v-if="v.status === 'WAITING'" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M12 8v4l3 2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            <svg v-else-if="v.status === 'CALLING'" viewBox="0 0 24 24" fill="none"><path d="M5 4v16l7-5 7 5V4z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
            <svg v-else-if="v.status === 'IN_PROGRESS'" viewBox="0 0 24 24" fill="none"><path d="M12 2v6m0 0a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm-6.4 9.6A9.6 9.6 0 0 0 12 21.6m6.4-10A9.6 9.6 0 0 1 12 21.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
            <svg v-else viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/></svg>
          </span>
          <span class="sb-label">{{ statusMap[v.status]?.label || v.status }}</span>
        </div>

        <!-- 中：患者信息 -->
        <div class="visit-main">
          <div class="main-top">
            <span class="patient-name">{{ v.patientName }}</span>
            <span class="dept-tag">{{ v.department }}</span>
            <span class="doctor-title">{{ v.doctorTitle }}</span>
          </div>
          <div class="main-meta">
            <span>挂号单号 {{ v.orderNo || '—' }}</span>
            <span class="dot-sep">·</span>
            <span>就诊时间 {{ v.appointmentTime || '—' }}</span>
          </div>
          <div v-if="v.progressNodes?.length" class="progress-line">
            <span v-for="(n, i) in v.progressNodes" :key="i" class="node-chip">
              {{ typeof n === 'object' ? n.name : n }}
              <em v-if="typeof n === 'object' && n.time" class="node-time">{{ n.time.slice(11, 16) }}</em>
            </span>
          </div>
        </div>

        <!-- 右：操作按钮 -->
        <div class="visit-actions" @click.stop>
          <el-button v-if="v.status === 'WAITING'" type="primary" size="small" @click="callPatient(v)">叫号</el-button>
          <el-button v-if="v.status === 'CALLING'" type="success" size="small" @click="receivePatient(v)">接诊</el-button>
          <el-button v-if="v.status === 'IN_PROGRESS'" type="warning" size="small" @click="openRxDialog(v)">
            <el-icon class="el-icon--left"><Edit /></el-icon>开处方
          </el-button>
          <el-button v-if="v.status === 'IN_PROGRESS'" plain size="small" @click="finishVisit(v)">完成就诊</el-button>
          <el-button v-if="v.status === 'FINISHED'" plain type="primary" size="small" @click="openRxDialog(v)">
            <el-icon class="el-icon--left"><Document /></el-icon>查看处方
          </el-button>
        </div>
      </div>
    </div>

    <!-- 分页（每满 6 条换页） -->
    <div v-if="filtered.length > PAGE_SIZE" class="pager">
      <el-pagination
        background
        layout="prev, pager, next"
        :total="filtered.length"
        :page-size="PAGE_SIZE"
        :current-page="currentPage"
        @current-change="currentPage = $event"
      />
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && filtered.length === 0" class="empty-state">
      <svg viewBox="0 0 160 120" fill="none">
        <rect x="25" y="20" width="110" height="80" rx="10" fill="#F2F6FC"/>
        <rect x="42" y="40" width="50" height="7" rx="3.5" fill="#C9D8EC"/>
        <rect x="42" y="56" width="76" height="6" rx="3" fill="#E1E9F4"/>
        <rect x="42" y="70" width="60" height="6" rx="3" fill="#E1E9F4"/>
      </svg>
      <p class="empty-title">暂无就诊记录</p>
      <p class="empty-sub">当前分类下没有就诊患者</p>
    </div>

    <!-- 处方弹窗 -->
    <el-dialog
      v-model="rxDialogVisible"
      :title="existingRx ? '处方详情' : '开具处方'"
      width="680px"
      class="rx-dialog"
      destroy-on-close
    >
      <div class="rx-patient-info">
        <span>患者：<strong>{{ rxVisit?.patientName }}</strong></span>
        <span class="dot-sep">·</span>
        <span>科室：{{ rxVisit?.department }}</span>
        <span class="dot-sep">·</span>
        <span>单号：{{ rxVisit?.orderNo }}</span>
      </div>

      <el-form label-width="70px" class="rx-form">
        <el-form-item label="诊断">
          <el-input v-model="rxDiagnosis" :disabled="!!existingRx" placeholder="如：2型糖尿病、急性上呼吸道感染" />
        </el-form-item>
        <el-form-item label="医嘱">
          <el-input v-model="rxAdvice" :disabled="!!existingRx" type="textarea" :rows="2" placeholder="如：控制饮食，定期监测血糖，适量运动" />
        </el-form-item>

        <div class="rx-items-head">
          <span>药品明细</span>
          <el-button v-if="!existingRx" type="primary" link size="small" @click="addItem">
            <el-icon class="el-icon--left"><Plus /></el-icon>添加药品
          </el-button>
        </div>

        <div class="rx-items">
          <div v-for="(item, idx) in rxItems" :key="idx" class="rx-item-row">
            <div class="rx-item-top">
              <el-input v-model="item.drugName" :disabled="!!existingRx" placeholder="药品名称" class="drug-name-input" />
              <el-input v-model="item.specification" :disabled="!!existingRx" placeholder="规格 如0.5g×20片" class="spec-input" />
              <el-button v-if="!existingRx" type="danger" link :icon="Remove" @click="removeItem(idx)" />
            </div>
            <div class="rx-item-bottom">
              <el-input v-model="item.dosage" :disabled="!!existingRx" placeholder="单次剂量" class="short-input" />
              <el-input v-model="item.frequency" :disabled="!!existingRx" placeholder="频次 如每日3次" class="short-input" />
              <el-input-number v-model="item.days" :disabled="!!existingRx" :min="1" :max="90" placeholder="天数" class="days-input" controls-position="right" />
              <el-input v-model="item.quantity" :disabled="!!existingRx" placeholder="数量" class="short-input" />
              <el-select v-model="item.usageMethod" :disabled="!!existingRx" placeholder="用法" class="usage-select">
                <el-option label="口服" value="口服" />
                <el-option label="外用" value="外用" />
                <el-option label="静滴" value="静滴" />
                <el-option label="肌注" value="肌注" />
                <el-option label="吸入" value="吸入" />
              </el-select>
            </div>
            <el-input v-model="item.remark" :disabled="!!existingRx" placeholder="备注（如餐后服用）" class="remark-input" />
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="rxDialogVisible = false">{{ existingRx ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!existingRx" type="primary" :loading="rxSubmitting" @click="submitPrescription">
          确认开具处方
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.appt-list-page { padding: 4px 2px 20px; }

/* 页头 */
.page-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 18px; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0 0 6px; }
.page-sub { font-size: 13px; color: #8492A6; margin: 0; }

/* Tab */
.search-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.search-name { width: 260px; }
.search-date { width: 200px; }
.clear-btn { color: #8492A6; }

.tab-row { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.tab-pill {
  border: 1.5px solid #E2EAF4; background: #fff; color: #5A6B82; border-radius: 100px;
  padding: 7px 16px; font-size: 13.5px; cursor: pointer; display: inline-flex; align-items: center; gap: 7px;
  transition: all 0.18s;
}
.tab-pill:hover { border-color: #BFD9F7; color: #1E7AE6; }
.tab-pill.active { background: linear-gradient(135deg, #10B981, #3FC79A); border-color: transparent; color: #fff; box-shadow: 0 5px 13px rgba(16,185,129,0.28); }
.tab-count {
  background: #EDF2F8; color: #8492A6; border-radius: 100px; font-size: 11.5px;
  min-width: 20px; height: 20px; display: inline-flex; align-items: center; justify-content: center; padding: 0 6px;
}
.tab-pill.active .tab-count { background: rgba(255,255,255,0.25); color: #fff; }

/* 卡片 */
.card-list { display: flex; flex-direction: column; gap: 12px; }
.visit-card {
  display: flex; align-items: stretch; gap: 16px; background: #fff;
  border: 1px solid #ECF1F7; border-radius: 14px; padding: 16px 20px;
  transition: all 0.22s;
}
.visit-card:hover { border-color: #BFE6D5; box-shadow: 0 10px 24px rgba(16,185,129,0.08); }

/* 状态徽章 */
.status-badge {
  width: 64px; flex-shrink: 0; border-radius: 12px; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 4px; padding: 10px 0;
}
.st-waiting  { background: #E3F2FF; color: #1E7AE6; }
.st-calling  { background: #FFF3E0; color: #F59E0B; }
.st-progress { background: #F3EEFF; color: #7C3AED; }
.st-finished { background: #E2F7EF; color: #10B981; }
.sb-icon svg { width: 24px; height: 24px; }
.sb-label { font-size: 11.5px; font-weight: 600; }

/* 中间信息 */
.visit-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 7px; }
.main-top { display: flex; align-items: center; gap: 10px; }
.patient-name { font-size: 16px; font-weight: 700; color: #1A2B45; }
.dept-tag { font-size: 12px; color: #10B981; background: #E2F7EF; padding: 2px 10px; border-radius: 100px; }
.doctor-title { font-size: 12px; color: #7A5AF8; background: #F1EDFF; padding: 2px 10px; border-radius: 100px; }
.main-meta { font-size: 12.5px; color: #A7B1BF; display: flex; align-items: center; gap: 7px; }
.dot-sep { color: #C3CCD8; }
.progress-line { display: flex; gap: 6px; flex-wrap: wrap; }
.node-chip { font-size: 11.5px; color: #5A6B82; background: #F0F4F8; padding: 2px 8px; border-radius: 6px; display: inline-flex; align-items: center; gap: 4px; }
.node-time { font-style: normal; color: #A7B1BF; font-size: 10.5px; }

/* 右侧操作 */
.visit-actions { display: flex; flex-direction: column; align-items: flex-end; justify-content: center; gap: 8px; flex-shrink: 0; }

/* 分页 */
.pager { display: flex; justify-content: center; margin-top: 20px; }
.pager :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: linear-gradient(135deg, #10B981, #3FC79A);
  border-color: transparent;
}

/* 空状态 */
.empty-state { text-align: center; padding: 50px 0 30px; }
.empty-state svg { width: 170px; }
.empty-title { font-size: 16px; font-weight: 700; color: #2C3E50; margin: 14px 0 4px; }
.empty-sub { font-size: 13px; color: #A7B1BF; margin: 0; }

/* 处方弹窗 */
.rx-dialog :deep(.el-dialog__body) { padding-top: 10px; }
.rx-patient-info { display: flex; align-items: center; gap: 8px; font-size: 13.5px; color: #4A5A70; margin-bottom: 16px; padding: 10px 14px; background: #F8FAFC; border-radius: 8px; }
.rx-patient-info strong { color: #1A2B45; }
.rx-form { margin-top: 4px; }
.rx-items-head { display: flex; align-items: center; justify-content: space-between; margin: 8px 0 10px; font-size: 14px; font-weight: 600; color: #1A2B45; }
.rx-items { display: flex; flex-direction: column; gap: 12px; }
.rx-item-row { border: 1px solid #ECF1F7; border-radius: 10px; padding: 12px; background: #FAFBFC; }
.rx-item-top { display: flex; gap: 10px; margin-bottom: 10px; }
.rx-item-top .drug-name-input { flex: 2; }
.rx-item-top .spec-input { flex: 1.5; }
.rx-item-bottom { display: flex; gap: 10px; flex-wrap: wrap; }
.rx-item-bottom .short-input { width: 110px; }
.rx-item-bottom .days-input { width: 100px; }
.rx-item-bottom .usage-select { width: 100px; }
.remark-input { margin-top: 10px; }

@media (max-width: 1100px) {
  .visit-card { flex-wrap: wrap; }
  .visit-actions { width: 100%; flex-direction: row; justify-content: flex-end; }
}
</style>
