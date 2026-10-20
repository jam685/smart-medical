<script setup>
/**
 * 患者端「在线问诊」列表页组件
 * 职责：从后端加载当前患者发起的全部问诊，支持按状态分组筛选，点击卡片进入聊天详情，
 *       顶部「发起问诊」入口跳转新建页。
 * 数据来源：GET /online-consults（后端按患者本人过滤，返回 sm_online_consult_record 实体）
 * 状态口径（后端枚举）：
 *   PENDING / AI_ANALYZED / DOCTOR_REVIEWING → 进行中
 *   REPLIED → 已回复；CLOSED → 已关闭
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { fetchConsults } from '@/api/onlineconsult'

/** Vue Router 实例：用于跳转发起问诊页 / 问诊聊天页 */
const router = useRouter()

// ============== 状态 Tab ==============
/**
 * 状态筛选 Tab 配置
 * @property {string} key   Tab 键：all 全部 / ongoing 进行中 / replied 已回复 / closed 已关闭
 * @property {string} label Tab 显示文案
 */
const tabs = [
  { key: 'all', label: '全部' },
  { key: 'ongoing', label: '进行中' },
  { key: 'replied', label: '已回复' },
  { key: 'closed', label: '已关闭' },
]

/** 当前选中的 Tab 键 */
const activeTab = ref('all')

// ============== 接口数据 ==============
/** 问诊视图对象数组（由后端实体经 normalize 转换），初始为空，页面加载时请求 */
const consults = ref([])
/** 列表加载状态：true 时显示加载动画 */
const loading = ref(false)

/**
 * 把后端问诊实体归一化为页面渲染对象
 * @param {object} raw 后端 OnlineConsult 实体
 * @returns {object} 视图对象：状态、时间、补充次数等派生字段
 */
function normalize(raw) {
  return {
    id: raw.id,
    department: raw.department || '未指定科室',
    chiefComplaint: raw.chiefComplaint || '未填写主诉',
    description: raw.description || '',
    supplementCount: raw.supplementCount || 0,
    status: mapStatus(raw.status),
    statusText: statusText(raw.status),
    createdAt: formatTime(raw.createdAt),
  }
}

/**
 * 后端状态码映射为页面状态键
 * @param {string} s PENDING/AI_ANALYZED/DOCTOR_REVIEWING/REPLIED/CLOSED
 * @returns {string} ongoing/replied/closed（未知状态兜底为 ongoing）
 */
function mapStatus(s) {
  if (s === 'REPLIED') return 'replied'
  if (s === 'CLOSED') return 'closed'
  return 'ongoing' // PENDING / AI_ANALYZED / DOCTOR_REVIEWING 均视为进行中
}

/**
 * 后端状态码映射为中文文案
 * @param {string} s 后端状态码
 * @returns {string} 中文文案
 */
function statusText(s) {
  if (s === 'REPLIED') return '已回复'
  if (s === 'CLOSED') return '已关闭'
  if (s === 'AI_ANALYZED') return '分析中'
  if (s === 'DOCTOR_REVIEWING') return '处理中'
  return '待接诊'
}

/**
 * 时间戳格式化：ISO 字符串 → 'YYYY-MM-DD HH:mm'
 * @param {string} t ISO 时间字符串
 * @returns {string} 格式化后的时间
 */
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * 从后端加载当前患者的问诊列表（热更新的唯一数据入口）
 * 一次取足量数据（size=100），各 Tab 的筛选在前端基于同一份数据计算。
 */
async function loadConsults() {
  loading.value = true
  try {
    const page = await fetchConsults({ page: 1, size: 100 })
    consults.value = (page.list || []).map(normalize)
  } catch (e) {
    // 请求失败提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 状态展示配置：文案 + 标签样式类 */
const statusMap = {
  ongoing: { label: '进行中', cls: 'st-ongoing' },
  replied: { label: '已回复', cls: 'st-replied' },
  closed: { label: '已关闭', cls: 'st-closed' },
}

/**
 * 当前 Tab 下应展示的问诊（响应式）
 * @returns {Array} 全部时返回整表，否则按页面状态键过滤
 */
const filtered = computed(() =>
  activeTab.value === 'all'
    ? consults.value
    : consults.value.filter(c => c.status === activeTab.value)
)

/**
 * 统计指定 Tab 的问诊数量（用于角标）
 * @param {string} key Tab 键，all 表示全部
 * @returns {number} 数量
 */
function countOf(key) {
  return key === 'all' ? consults.value.length : consults.value.filter(c => c.status === key).length
}

// ============== 页面操作方法 ==============
/** 跳转发起问诊页 */
function goCreate() { router.push('/patient/online-consults/create') }

/** 跳转问诊聊天页 */
function goDetail(c) { router.push(`/patient/online-consults/${c.id}`) }

// 页面挂载即从后端加载数据
onMounted(loadConsults)
</script>

<template>
  <div class="consult-list-page" v-loading="loading">
    <!-- 页头：标题 + 发起问诊按钮 -->
    <div class="page-head">
      <div>
        <h2 class="page-title brand-font">在线问诊</h2>
        <p class="page-sub">随时发起线上问诊，与医生在线交流</p>
      </div>
      <el-button type="primary" size="large" class="create-btn" @click="goCreate">
        <el-icon class="el-icon--left"><Plus /></el-icon>发起问诊
      </el-button>
    </div>

    <!-- 状态筛选 Tab -->
    <div class="tab-row">
      <button
        v-for="t in tabs" :key="t.key"
        :class="['tab-pill', { active: activeTab === t.key }]"
        @click="activeTab = t.key"
      >
        {{ t.label }}<span class="tab-count">{{ countOf(t.key) }}</span>
      </button>
    </div>

    <!-- 问诊卡片列表 -->
    <div class="card-list">
      <div v-for="c in filtered" :key="c.id" class="consult-card" @click="goDetail(c)">
        <!-- 左：状态徽章 -->
        <div class="status-badge" :class="`is-${c.status}`">
          <span class="sb-icon">
            <svg viewBox="0 0 24 24" fill="none"><path d="M4 6h16v11H4z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M8 10h8M8 13h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
          </span>
          <span class="sb-text">{{ statusMap[c.status].label }}</span>
        </div>

        <!-- 中：问诊信息 -->
        <div class="consult-main">
          <div class="main-top">
            <span class="dept-name">{{ c.department }}</span>
            <span class="status-tag" :class="statusMap[c.status].cls">{{ c.statusText }}</span>
          </div>
          <div class="chief">{{ c.chiefComplaint }}</div>
          <div class="main-meta">
            <span>发起于 {{ c.createdAt }}</span>
            <span class="dot-sep">·</span>
            <span>补充材料 {{ c.supplementCount }} 次</span>
          </div>
        </div>

        <!-- 右：进入详情 -->
        <div class="consult-actions" @click.stop>
          <el-button text class="detail-link" @click="goDetail(c)">进入对话 ›</el-button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && filtered.length === 0" class="empty-state">
      <svg viewBox="0 0 160 120" fill="none">
        <rect x="25" y="20" width="110" height="80" rx="10" fill="#F2F6FC"/>
        <circle cx="70" cy="60" r="9" fill="#DCE8F6"/>
        <circle cx="100" cy="60" r="9" fill="#DCE8F6"/>
        <path d="M55 78c6 6 24 6 30 0" stroke="#C9D8EC" stroke-width="2.4" stroke-linecap="round"/>
        <path d="M118 46l4 4 8-9" stroke="#1E7AE6" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <p class="empty-title">暂无问诊记录</p>
      <p class="empty-sub">当前分类下还没有问诊，找一位在线医生聊聊吧</p>
      <el-button type="primary" @click="goCreate">
        <el-icon class="el-icon--left"><Plus /></el-icon>立即问诊
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.consult-list-page { padding: 4px 2px 20px; }

/* 页头 */
.page-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 18px; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0 0 6px; }
.page-sub { font-size: 13px; color: #8492A6; margin: 0; }
.create-btn { border-radius: 10px; font-weight: 600; box-shadow: 0 6px 16px rgba(30,122,230,0.25); }

/* Tab */
.tab-row { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.tab-pill {
  border: 1.5px solid #E2EAF4; background: #fff; color: #5A6B82; border-radius: 100px;
  padding: 7px 16px; font-size: 13.5px; cursor: pointer; display: inline-flex; align-items: center; gap: 7px;
  transition: all 0.18s;
}
.tab-pill:hover { border-color: #BFD9F7; color: #1E7AE6; }
.tab-pill.active { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); border-color: transparent; color: #fff; box-shadow: 0 5px 13px rgba(30,122,230,0.28); }
.tab-count {
  background: #EDF2F8; color: #8492A6; border-radius: 100px; font-size: 11.5px;
  min-width: 20px; height: 20px; display: inline-flex; align-items: center; justify-content: center; padding: 0 6px;
}
.tab-pill.active .tab-count { background: rgba(255,255,255,0.25); color: #fff; }

/* 卡片 */
.card-list { display: flex; flex-direction: column; gap: 12px; }
.consult-card {
  display: flex; align-items: center; gap: 18px; background: #fff;
  border: 1px solid #ECF1F7; border-radius: 14px; padding: 16px 20px; cursor: pointer;
  transition: all 0.22s;
}
.consult-card:hover { border-color: #BFD9F7; box-shadow: 0 10px 24px rgba(30,122,230,0.1); transform: translateY(-2px); }

/* 状态徽章 */
.status-badge {
  width: 74px; flex-shrink: 0; border-radius: 12px; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 4px; padding: 12px 0;
  background: linear-gradient(160deg, #EAF3FF, #DCEBFF); color: #1E7AE6;
}
.status-badge.is-replied { background: #EEF6F1; color: #10B981; }
.status-badge.is-closed { background: #F4F6F9; color: #A7B1BF; }
.sb-icon svg { width: 24px; height: 24px; }
.sb-text { font-size: 12px; font-weight: 600; }

/* 中间信息 */
.consult-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 7px; }
.main-top { display: flex; align-items: center; gap: 10px; }
.dept-name { font-size: 16.5px; font-weight: 700; color: #1A2B45; }
.status-tag { font-size: 12px; font-weight: 600; padding: 3px 12px; border-radius: 100px; }
.st-ongoing { color: #1E7AE6; background: #E3F2FF; }
.st-replied { color: #0E9F6E; background: #E2F7EF; }
.st-closed { color: #8492A6; background: #EEF1F5; }
.chief { font-size: 14px; color: #4A5A70; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.main-meta { font-size: 12.5px; color: #A7B1BF; display: flex; align-items: center; gap: 7px; }
.dot-sep { color: #C3CCD8; }

/* 右侧操作 */
.consult-actions { display: flex; align-items: center; flex-shrink: 0; }
.detail-link { color: #1E7AE6; font-weight: 600; padding: 0; }

/* 空状态 */
.empty-state { text-align: center; padding: 50px 0 30px; }
.empty-state svg { width: 170px; }
.empty-title { font-size: 16px; font-weight: 700; color: #2C3E50; margin: 14px 0 4px; }
.empty-sub { font-size: 13px; color: #A7B1BF; margin: 0 0 18px; }

@media (max-width: 1100px) {
  .consult-card { flex-wrap: wrap; }
}
</style>
