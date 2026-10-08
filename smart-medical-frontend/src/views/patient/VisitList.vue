<script setup>
/**
 * 患者端「就诊进度」列表页组件
 * 职责：从后端加载当前患者的全部就诊进度记录，按状态分组筛选（候诊/就诊中/已完成），
 *       卡片左侧展示当前进度节点 + 状态徽章，右侧展示医生/科室/挂号时间/单号。
 * 数据来源：GET /visits（sm_visit 联 sm_doctor/sm_patient/sm_appointment 组装的 VisitVO）
 * 热更新：本页不缓存任何业务数据，挂载与状态切换均以后端为准；刷新页面亦从数据库重拉。
 * 状态口径：WAITING 候诊、CALLING 叫号、IN_PROGRESS 就诊中、FINISHED 已完成
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, User, Document, Right } from '@element-plus/icons-vue'
import { fetchVisits } from '@/api/visit'

/** Vue Router 实例，用于跳转详情页 */
const router = useRouter()

// ============== 状态 Tab ==============
/**
 * 状态筛选 Tab 配置（与后端 status 字段对齐，all 表示全部）
 * @property {string} key   Tab 键：all 全部 / WAITING 候诊 / IN_PROGRESS 就诊中 / FINISHED 已完成
 * @property {string} label Tab 显示文案
 * @property {string} cls   卡片状态样式类后缀
 */
const tabs = [
  { key: 'all', label: '全部', cls: 'all' },
  { key: 'WAITING', label: '候诊/叫号', cls: 'waiting' },
  { key: 'IN_PROGRESS', label: '就诊中', cls: 'progress' },
  { key: 'FINISHED', label: '已完成', cls: 'finished' },
]

/** 当前选中的 Tab 键 */
const activeTab = ref('all')

// ============== 接口数据 ==============
/** 后端返回的 VisitVO 数组，初始为空，页面加载时请求 */
const visits = ref([])
/** 列表加载状态：true 时显示加载动画 */
const loading = ref(false)

/** 医院名称（平台级常量，非就诊业务数据） */
const HOSPITAL_NAME = '小林智疗互联网医院'

/**
 * 各状态展示配置：label 文案 + 标签样式类 + 进度条进度百分比
 * 百分比用于卡片左侧进度条，体现就诊流程位置：候诊 25% / 叫号 50% / 就诊中 75% / 完成 100%
 */
const statusMeta = {
  WAITING:     { label: '候诊中', cls: 'st-waiting',    percent: 25 },
  CALLING:     { label: '叫号中', cls: 'st-calling',    percent: 50 },
  IN_PROGRESS: { label: '就诊中', cls: 'st-progress',  percent: 75 },
  FINISHED:    { label: '已完成', cls: 'st-finished',  percent: 100 },
}

/**
 * 当前 Tab 下应展示的就诊记录（响应式）
 * 设计：候诊/叫号 Tab 同时展示 WAITING 与 CALLING 两种状态，体现"等待就诊"语义
 * @returns {Array} VisitVO 子集
 */
const filtered = computed(() => {
  if (activeTab.value === 'all') return visits.value
  if (activeTab.value === 'WAITING') {
    return visits.value.filter(v => v.status === 'WAITING' || v.status === 'CALLING')
  }
  return visits.value.filter(v => v.status === activeTab.value)
})

/**
 * 统计指定 Tab 的就诊记录数量（用于角标）
 * @param {string} key Tab 键，all 表示全部
 * @returns {number} 数量
 */
function countOf(key) {
  if (key === 'all') return visits.value.length
  if (key === 'WAITING') {
    return visits.value.filter(v => v.status === 'WAITING' || v.status === 'CALLING').length
  }
  return visits.value.filter(v => v.status === key).length
}

/**
 * 获取就诊记录的状态展示元数据（兜底处理未知状态）
 * @param {string} status 后端状态码
 * @returns {{label:string,cls:string,percent:number}} 展示配置
 */
function metaOf(status) {
  return statusMeta[status] || { label: status || '未知', cls: 'st-waiting', percent: 0 }
}

/**
 * 格式化就诊时间为「月日 + 时分」短文本（用于卡片紧凑展示）
 * @param {string} t 形如 '2026-09-20 09:00:00'
 * @returns {string} 形如 '9月20日 09:00'
 */
function shortTime(t) {
  if (!t) return '--'
  const [d, hm] = t.split(' ')
  const [, m, day] = d.split('-')
  return `${+m}月${+day}日 ${hm.slice(0, 5)}`
}

/**
 * 把后端 progressNodes 节点数组拼成"节点名1 › 节点名2 › ... "短文本（卡片进度摘要用）
 * @param {Array<{name:string,time:string}>} nodes 进度节点
 * @returns {string} 节点链摘要
 */
function nodesDigest(nodes) {
  if (!Array.isArray(nodes) || nodes.length === 0) return '尚未叫号'
  return nodes.map(n => n.name).join(' › ')
}

/**
 * 从后端加载当前患者的就诊进度列表（热更新的唯一数据入口）
 * 设计：一次取足量数据（size=100），各 Tab 的筛选在前端基于同一份数据计算。
 */
async function loadVisits() {
  loading.value = true
  try {
    const page = await fetchVisits({ page: 1, size: 100 })
    visits.value = page.list || []
  } catch (e) {
    // 请求失败提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/**
 * 跳转就诊详情页
 * @param {object} v VisitVO
 */
function goDetail(v) { router.push(`/patient/visits/${v.id}`) }

// 页面挂载即从后端加载数据（刷新页面也始终以数据库为准）
onMounted(loadVisits)
</script>

<template>
  <div class="visit-list-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2 class="page-title brand-font">就诊进度</h2>
        <p class="page-sub">实时跟踪您的就诊流程：候诊 → 叫号 → 就诊 → 完成</p>
      </div>
      <button class="refresh-btn" @click="loadVisits">
        <svg viewBox="0 0 16 16" width="14" height="14"><path d="M8 2a6 6 0 1 0 5.2 9" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linecap="round"/><path d="M13 2v4h-4" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
        刷新
      </button>
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

    <!-- 卡片列表 -->
    <div class="card-list">
      <div
        v-for="v in filtered" :key="v.id"
        class="visit-card"
        :class="metaOf(v.status).cls"
        @click="goDetail(v)"
      >
        <!-- 左：状态徽章 + 进度条 -->
        <div class="vc-left">
          <div class="vc-status-ring">
            <span class="vc-status-label">{{ metaOf(v.status).label }}</span>
            <span class="vc-progress-text">{{ v.currentNode }}</span>
          </div>
          <div class="vc-progress-bar">
            <div class="vc-progress-fill" :style="{ width: metaOf(v.status).percent + '%' }"></div>
          </div>
        </div>

        <!-- 中：核心信息 -->
        <div class="vc-main">
          <div class="vc-main-top">
            <span class="vc-dept">{{ v.department }}</span>
            <span class="vc-dot">·</span>
            <span class="vc-doctor-title">{{ v.doctorTitle || '医生' }}</span>
          </div>
          <div class="vc-doctor">
            <span class="vc-avatar-mini">
              <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="9" r="4" fill="currentColor"/><path d="M4 21 Q4 14 12 14 Q20 14 20 21" fill="currentColor"/></svg>
            </span>
            <strong>{{ v.doctorName }}</strong>
            <span class="vc-dot">·</span>
            <el-icon><Clock /></el-icon>
            <span>{{ shortTime(v.appointmentTime) }}</span>
          </div>
          <div class="vc-meta">
            <span>{{ HOSPITAL_NAME }}</span>
            <span class="vc-dot">·</span>
            <span v-if="v.orderNo">单号 {{ v.orderNo }}</span>
            <span class="vc-dot">·</span>
            <span class="vc-node-digest">{{ nodesDigest(v.progressNodes) }}</span>
          </div>
        </div>

        <!-- 右：操作区（点击不冒泡触发整卡跳转） -->
        <div class="vc-actions" @click.stop>
          <el-button text class="vc-detail-link" @click="goDetail(v)">
            查看进度<el-icon class="el-icon--right"><Right /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && filtered.length === 0" class="empty-state">
      <svg viewBox="0 0 160 120" fill="none">
        <rect x="25" y="20" width="110" height="80" rx="10" fill="#F2F6FC"/>
        <rect x="42" y="40" width="50" height="7" rx="3.5" fill="#C9D8EC"/>
        <rect x="42" y="56" width="76" height="6" rx="3" fill="#E1E9F4"/>
        <rect x="42" y="70" width="60" height="6" rx="3" fill="#E1E9F4"/>
        <circle cx="115" cy="78" r="14" fill="#1E7AE6" opacity="0.12"/>
        <path d="M109 78l4 4 8-9" stroke="#1E7AE6" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <p class="empty-title">暂无就诊记录</p>
      <p class="empty-sub">当前分类下还没有就诊进度，挂号后即可在此查看</p>
      <el-button type="primary" @click="$router.push('/patient/appointments/create')">
        去挂号
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.visit-list-page { padding: 4px 2px 20px; }

/* 页头 */
.page-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 18px; }
.page-title { font-size: 22px; font-weight: 700; color: #111827; margin: 0 0 4px; }
.page-sub { font-size: 13px; color: #6B7280; margin: 0; }
.refresh-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 999px; border: 1px solid #E5E7EB;
  background: #fff; color: #4B5563; font-size: 13px; cursor: pointer; transition: all .2s;
}
.refresh-btn:hover { border-color: #1E7AE6; color: #1E7AE6; }

/* Tab 行 */
.tab-row { display: flex; gap: 8px; margin-bottom: 18px; flex-wrap: wrap; }
.tab-pill {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 14px; border-radius: 999px; border: 1px solid #E5E7EB;
  background: #fff; color: #6B7280; font-size: 13px; cursor: pointer; transition: all .2s;
}
.tab-pill:hover { border-color: #93B4E8; color: #1E7AE6; }
.tab-pill.active { background: #1E7AE6; border-color: #1E7AE6; color: #fff; }
.tab-count {
  min-width: 18px; padding: 0 5px; height: 16px; line-height: 16px;
  border-radius: 8px; background: rgba(0,0,0,0.06); font-size: 11px; text-align: center;
}
.tab-pill.active .tab-count { background: rgba(255,255,255,0.25); }

/* 卡片列表 */
.card-list { display: flex; flex-direction: column; gap: 12px; }
.visit-card {
  display: grid; grid-template-columns: 140px 1fr auto; align-items: center;
  background: #fff; border: 1px solid #EEF0F3; border-radius: 14px;
  padding: 16px 18px; cursor: pointer; transition: all .2s; gap: 16px;
}
.visit-card:hover { box-shadow: 0 8px 22px rgba(30,122,230,0.08); transform: translateY(-1px); border-color: #D6E4F5; }

/* 左侧状态区 */
.vc-left { display: flex; flex-direction: column; gap: 10px; padding-right: 14px; border-right: 1px dashed #E5E7EB; }
.vc-status-ring { display: flex; flex-direction: column; gap: 2px; }
.vc-status-label { font-size: 13px; font-weight: 600; }
.vc-progress-text { font-size: 11px; color: #9CA3AF; }
.vc-progress-bar { height: 6px; background: #F1F3F7; border-radius: 3px; overflow: hidden; }
.vc-progress-fill { height: 100%; border-radius: 3px; transition: width .4s ease; }

/* 状态色彩 */
.st-waiting .vc-status-label { color: #B45309; }
.st-waiting .vc-progress-fill { background: linear-gradient(90deg, #F59E0B, #FBBF24); }
.st-calling .vc-status-label { color: #1D4ED8; }
.st-calling .vc-progress-fill { background: linear-gradient(90deg, #2563EB, #60A5FA); }
.st-progress .vc-status-label { color: #6D28D8; }
.st-progress .vc-progress-fill { background: linear-gradient(90deg, #7C3AED, #A78BFA); }
.st-finished .vc-status-label { color: #047857; }
.st-finished .vc-progress-fill { background: linear-gradient(90deg, #10B981, #34D399); }
.st-all .vc-status-label { color: #1F2937; }

/* 中部信息 */
.vc-main { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.vc-main-top { display: flex; align-items: center; gap: 6px; font-size: 14px; }
.vc-dept { font-weight: 600; color: #111827; }
.vc-doctor-title { color: #6B7280; font-size: 12px; }
.vc-dot { color: #D1D5DB; }
.vc-doctor { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; }
.vc-avatar-mini {
  width: 22px; height: 22px; border-radius: 50%; background: #E3F2FF; color: #1E7AE6;
  display: inline-flex; align-items: center; justify-content: center;
}
.vc-avatar-mini svg { width: 14px; height: 14px; }
.vc-meta { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #9CA3AF; flex-wrap: wrap; }
.vc-node-digest { color: #1E7AE6; }

/* 右侧操作 */
.vc-actions { display: flex; align-items: center; }
.vc-detail-link { color: #1E7AE6; font-weight: 500; }

/* 空状态 */
.empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 56px 20px; text-align: center;
}
.empty-state svg { width: 160px; height: 120px; margin-bottom: 12px; }
.empty-title { font-size: 16px; font-weight: 600; color: #374151; margin: 0 0 6px; }
.empty-sub { font-size: 13px; color: #9CA3AF; margin: 0 0 18px; }

/* 响应式：小屏单列布局 */
@media (max-width: 720px) {
  .visit-card { grid-template-columns: 1fr; gap: 12px; }
  .vc-left { border-right: none; border-bottom: 1px dashed #E5E7EB; padding-right: 0; padding-bottom: 10px; }
  .vc-actions { justify-content: flex-end; }
}
</style>
