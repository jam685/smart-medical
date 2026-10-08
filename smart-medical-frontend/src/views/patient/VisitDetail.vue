<script setup>
/**
 * 患者端「就诊详情」页组件
 * 职责：从路由参数取就诊进度 ID，调 GET /visits/{id} 拉取真实数据；
 *       顶部展示就诊状态横幅 + 当前节点 / 下一节点；
 *       中部展示就诊信息卡（订单、科室、医生、就诊时间、患者、地点）；
 *       下部展示进度时间线（候诊 → 叫号 → 就诊 → 完成 四段，已发生节点高亮）。
 * 数据来源：GET /visits/{id} → VisitVO（含 progressNodes 解析后的数组）
 * 热更新：页面挂载即从数据库重拉；刷新页面数据始终以数据库为准。
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, InfoFilled, Clock, User } from '@element-plus/icons-vue'
import { fetchVisitDetail } from '@/api/visit'

/** 路由实例（取 :id 参数）和路由器（返回列表） */
const route = useRoute()
const router = useRouter()

/** 页面加载中状态 */
const loading = ref(true)
/** 就诊详情数据（接口返回的 VisitVO） */
const visit = ref(null)

/** 医院名称（平台级常量） */
const HOSPITAL_NAME = '小林智疗互联网医院'

/**
 * 标准就诊流程节点定义（顺序固定，用于时间线骨架）
 * @property {string[]} keys 节点 key 候选：用于匹配 progressNodes 中的 name（兼容旧数据"候诊"与新数据"叫号"）
 * @property {string}   label 节点中文显示名
 * @property {string}   desc  节点对应业务阶段的说明
 */
const FLOW_NODES = [
  { keys: ['挂号'],          label: '挂号', desc: '已完成预约挂号' },
  { keys: ['叫号', '候诊'],  label: '叫号', desc: '请到对应诊区候诊' },
  { keys: ['就诊'],          label: '就诊', desc: '医生正在为您看诊' },
  { keys: ['完成'],          label: '完成', desc: '本次就诊已结束' },
]

/**
 * 状态 → 时间线进度阶段映射
 * WAITING 候诊 = 1（挂号已完成，等待叫号）
 * CALLING 叫号 = 2（叫号已完成，等待就诊）
 * IN_PROGRESS 就诊中 = 3（就诊进行中）
 * FINISHED 已完成 = 4（全部完成）
 * @param {string} status 后端状态码
 * @returns {number} 已完成阶段数（0~4）
 */
function stageOf(status) {
  return { WAITING: 1, CALLING: 2, IN_PROGRESS: 3, FINISHED: 4 }[status] ?? 0
}

/**
 * 当前就诊的状态展示元数据（label/desc/cls）
 */
const statusInfo = computed(() => {
  const s = visit.value?.status
  const map = {
    WAITING:     { label: '候诊中', desc: '请耐心等候叫号', cls: 'waiting' },
    CALLING:     { label: '叫号中', desc: '请前往诊区候诊', cls: 'calling' },
    IN_PROGRESS: { label: '就诊中', desc: '医生正在为您看诊', cls: 'progress' },
    FINISHED:    { label: '已完成', desc: '本次就诊已结束', cls: 'finished' },
  }
  return map[s] || { label: s || '未知', desc: '', cls: 'waiting' }
})

/**
 * 时间线节点视图对象（合并 FLOW_NODES 骨架与 progressNodes 实际时间）
 * 兼容策略：第二节点同时匹配"叫号"（新数据）与"候诊"（旧 data.sql 数据）
 * @returns {Array<{label,desc,time,done,active}>} 时间线节点列表
 */
const timeline = computed(() => {
  if (!visit.value) return []
  // 把 progressNodes 数组转成 {name -> time} 映射，便于按节点名查询时间
  const realMap = {}
  ;(visit.value.progressNodes || []).forEach(n => {
    if (n && n.name) realMap[n.name] = n.time
  })
  const stage = stageOf(visit.value.status)
  return FLOW_NODES.map((n, idx) => {
    const done = idx + 1 <= stage
    const active = idx + 1 === stage && visit.value.status !== 'FINISHED'
    // 从候选 key 列表中查找第一个匹配的节点时间（兼容新旧节点命名）
    const matchedTime = n.keys.map(k => realMap[k]).find(Boolean) || null
    return {
      label: n.label,
      desc: n.desc,
      time: matchedTime,
      done,
      active,
    }
  })
})

/**
 * 将 yyyy-MM-dd HH:mm:ss 格式化为「月日 周几 HH:mm」中文展示
 * @param {string} str 后端时间字符串
 * @returns {string} 形如「9月20日（周日）09:00」
 */
function formatTime(str) {
  if (!str) return '--'
  const [date, time] = str.split(' ')
  const d = new Date(`${date}T${time || '00:00:00'}`)
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${date}（${week}）${time ? time.slice(0, 5) : ''}`
}

/**
 * 加载就诊详情数据
 */
async function loadDetail() {
  loading.value = true
  try {
    const id = route.params.id
    visit.value = await fetchVisitDetail(id)
  } catch {
    visit.value = null
  } finally {
    loading.value = false
  }
}

/** 返回就诊进度列表页 */
function goBack() {
  router.push('/patient/visits')
}

onMounted(loadDetail)
</script>

<template>
  <div class="visit-detail" v-loading="loading">
    <!-- 顶部返回栏 -->
    <div class="detail-header">
      <el-button text class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回就诊进度
      </el-button>
    </div>

    <!-- 详情主体（仅在有数据时渲染） -->
    <template v-if="visit">
      <!-- 状态横幅 -->
      <div :class="['status-banner', `is-${statusInfo.cls}`]">
        <div class="sb-left">
          <span class="sb-icon">
            <!-- 候诊/叫号：时钟图标 -->
            <svg v-if="visit.status === 'WAITING' || visit.status === 'CALLING'" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M12 8v4l3 2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            <!-- 就诊中：脉动医疗图标 -->
            <svg v-else-if="visit.status === 'IN_PROGRESS'" viewBox="0 0 24 24" fill="none"><path d="M3 12h4l2-6 4 12 2-6h6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <!-- 已完成：勾选图标 -->
            <svg v-else viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M8 12l3 3 5-6" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </span>
          <div class="sb-text">
            <strong>{{ statusInfo.label }}</strong>
            <span>{{ statusInfo.desc }}</span>
          </div>
        </div>
        <div class="sb-right">
          <span class="sb-current">{{ visit.currentNode }}</span>
          <span class="sb-current-label">当前节点</span>
        </div>
      </div>

      <!-- 就诊信息卡 -->
      <div class="info-card">
        <h3 class="card-title">就诊信息</h3>
        <ul class="info-list">
          <li>
            <span class="i-label">订单编号</span>
            <span class="i-value mono">{{ visit.orderNo || '—（无关联挂号）' }}</span>
          </li>
          <li>
            <span class="i-label">就诊科室</span>
            <span class="i-value">{{ visit.department }}</span>
          </li>
          <li>
            <span class="i-label">接诊医生</span>
            <span class="i-value">{{ visit.doctorName }} <span class="title-tag" v-if="visit.doctorTitle">{{ visit.doctorTitle }}</span></span>
          </li>
          <li>
            <span class="i-label">患者姓名</span>
            <span class="i-value">{{ visit.patientName }}</span>
          </li>
          <li>
            <span class="i-label">预约就诊</span>
            <span class="i-value">{{ formatTime(visit.appointmentTime) }}</span>
          </li>
          <li>
            <span class="i-label">就诊地点</span>
            <span class="i-value">{{ HOSPITAL_NAME }} · 门诊 {{ visit.department }}候诊区</span>
          </li>
          <li>
            <span class="i-label">创建时间</span>
            <span class="i-value">{{ formatTime(visit.createdAt) }}</span>
          </li>
          <li>
            <span class="i-label">最近更新</span>
            <span class="i-value">{{ formatTime(visit.updatedAt) }}</span>
          </li>
        </ul>
      </div>

      <!-- 进度时间线卡 -->
      <div class="timeline-card">
        <h3 class="card-title">就诊进度</h3>
        <ol class="timeline">
          <li
            v-for="(n, idx) in timeline" :key="n.label"
            class="tl-item"
            :class="{ done: n.done, active: n.active, pending: !n.done }"
          >
            <!-- 左侧节点圆 + 连接线 -->
            <div class="tl-marker">
              <span class="tl-dot"></span>
              <span class="tl-line" v-if="idx < timeline.length - 1"></span>
            </div>
            <!-- 右侧节点内容 -->
            <div class="tl-content">
              <div class="tl-head">
                <strong>{{ n.label }}</strong>
                <span class="tl-time" v-if="n.time">{{ formatTime(n.time) }}</span>
                <span class="tl-time tl-pending-text" v-else>待进行</span>
              </div>
              <p class="tl-desc">{{ n.desc }}</p>
            </div>
          </li>
        </ol>
      </div>

      <!-- 就诊提示卡（按状态显示不同提示） -->
      <div class="tips-card" v-if="visit.status === 'WAITING' || visit.status === 'CALLING'">
        <div class="tips-head">
          <el-icon><InfoFilled /></el-icon>
          <span>候诊提示</span>
        </div>
        <ol class="tips-list">
          <li>请提前 15 分钟到院，在自助机或分诊台完成签到；</li>
          <li>请关注诊区叫号屏，听到姓名后前往对应诊室；</li>
          <li>就诊时请携带本人身份证 / 医保卡。</li>
        </ol>
      </div>
      <div class="tips-card" v-else-if="visit.status === 'IN_PROGRESS'">
        <div class="tips-head">
          <el-icon><InfoFilled /></el-icon>
          <span>就诊中提示</span>
        </div>
        <ol class="tips-list">
          <li>请配合医生问诊，如实陈述病情与既往病史；</li>
          <li>如需开具检查/检验单，可在手机端查看缴费明细；</li>
          <li>就诊结束后，医生会为您出具病历与处方。</li>
        </ol>
      </div>
      <div class="tips-card finished-tip" v-else-if="visit.status === 'FINISHED'">
        <div class="tips-head">
          <el-icon><InfoFilled /></el-icon>
          <span>就诊完成提示</span>
        </div>
        <ol class="tips-list">
          <li>本次就诊已结束，可在「我的报告」查看检查结果；</li>
          <li>如有复诊需求，请重新挂号预约；</li>
          <li>对本次服务有建议，可在个人中心反馈。</li>
        </ol>
      </div>
    </template>

    <!-- 空状态 -->
    <div v-if="!loading && !visit" class="empty-state">
      <svg viewBox="0 0 160 120" fill="none">
        <rect x="25" y="20" width="110" height="80" rx="10" fill="#F2F6FC"/>
        <rect x="42" y="40" width="50" height="7" rx="3.5" fill="#C9D8EC"/>
        <rect x="42" y="56" width="76" height="6" rx="3" fill="#E1E9F4"/>
        <rect x="42" y="70" width="60" height="6" rx="3" fill="#E1E9F4"/>
        <circle cx="115" cy="78" r="14" fill="#1E7AE6" opacity="0.12"/>
        <path d="M109 78l4 4 8-9" stroke="#1E7AE6" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <p>该就诊记录不存在或已被删除</p>
      <el-button type="primary" @click="goBack">返回列表</el-button>
    </div>
  </div>
</template>

<style scoped>
.visit-detail { padding: 24px; max-width: 720px; margin: 0 auto; }

/* 顶部返回栏 */
.detail-header { margin-bottom: 16px; }
.back-btn { color: #5A6B82; font-size: 14px; }
.back-btn:hover { color: #1E7AE6; }

/* 状态横幅 */
.status-banner {
  display: flex; align-items: center; justify-content: space-between;
  border-radius: 16px; padding: 24px 28px; margin-bottom: 20px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
  color: #fff;
}
.status-banner.is-waiting { background: linear-gradient(135deg, #F59E0B, #FBBF24); }
.status-banner.is-calling { background: linear-gradient(135deg, #2563EB, #60A5FA); }
.status-banner.is-progress { background: linear-gradient(135deg, #7C3AED, #A78BFA); }
.status-banner.is-finished { background: linear-gradient(135deg, #10B981, #34D399); }

.sb-left { display: flex; align-items: center; gap: 16px; }
.sb-icon {
  width: 48px; height: 48px; display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.2); border-radius: 50%;
}
.sb-icon svg { width: 28px; height: 28px; }
.sb-text { display: flex; flex-direction: column; gap: 4px; }
.sb-text strong { font-size: 20px; font-weight: 700; }
.sb-text span { font-size: 13px; opacity: 0.85; }

.sb-right { text-align: right; }
.sb-current { font-size: 22px; font-weight: 700; display: block; }
.sb-current-label { font-size: 12px; opacity: 0.8; }

/* 信息卡 */
.info-card, .timeline-card, .tips-card {
  background: #fff; border: 1px solid #ECF1F7; border-radius: 16px;
  padding: 24px 28px; margin-bottom: 16px;
}
.card-title { font-size: 16px; font-weight: 600; color: #1A2332; margin: 0 0 16px; }

.info-list { list-style: none; padding: 0; margin: 0; }
.info-list li {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 11px 0; border-bottom: 1px solid #F5F7FA;
}
.info-list li:last-child { border-bottom: none; }
.i-label { font-size: 14px; color: #8B95A7; flex-shrink: 0; width: 90px; }
.i-value { font-size: 14px; color: #1A2332; text-align: right; font-weight: 500; }
.i-value.mono { font-family: 'SF Mono', 'Consolas', monospace; letter-spacing: 0.5px; }
.title-tag {
  display: inline-block; padding: 1px 8px; border-radius: 4px;
  background: #E3F2FF; color: #1E7AE6; font-size: 12px; margin-left: 4px;
}

/* 时间线 */
.timeline { list-style: none; padding: 0; margin: 0; }
.tl-item { display: flex; gap: 14px; padding-bottom: 22px; position: relative; }
.tl-item:last-child { padding-bottom: 0; }

.tl-marker { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; width: 18px; }
.tl-dot {
  width: 18px; height: 18px; border-radius: 50%;
  background: #E5E7EB; border: 3px solid #fff;
  box-shadow: 0 0 0 2px #E5E7EB;
  transition: all .3s;
  flex-shrink: 0;
}
.tl-line {
  flex: 1; width: 2px; min-height: 22px;
  background: #E5E7EB; margin-top: 2px;
}
.tl-item.done .tl-dot {
  background: #10B981; box-shadow: 0 0 0 2px #10B981;
}
.tl-item.done .tl-line { background: linear-gradient(180deg, #10B981, #E5E7EB); }
.tl-item.active .tl-dot {
  background: #7C3AED; box-shadow: 0 0 0 2px #7C3AED, 0 0 0 6px rgba(124, 58, 237, 0.15);
  animation: tl-pulse 1.6s ease-in-out infinite;
}
.tl-item.active .tl-line { background: linear-gradient(180deg, #7C3AED, #E5E7EB); }
@keyframes tl-pulse {
  0%, 100% { box-shadow: 0 0 0 2px #7C3AED, 0 0 0 6px rgba(124, 58, 237, 0.15); }
  50% { box-shadow: 0 0 0 2px #7C3AED, 0 0 0 10px rgba(124, 58, 237, 0.08); }
}

.tl-content { flex: 1; padding-top: 0; }
.tl-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 4px; }
.tl-head strong { font-size: 14px; color: #1A2332; font-weight: 600; }
.tl-item.pending .tl-head strong { color: #9CA3AF; }
.tl-time { font-size: 12px; color: #6B7280; font-family: 'SF Mono', 'Consolas', monospace; }
.tl-pending-text { color: #C5CBD6; font-style: italic; }
.tl-desc { font-size: 12px; color: #9CA3AF; margin: 0; line-height: 1.5; }
.tl-item.pending .tl-desc { color: #C5CBD6; }

/* 提示卡 */
.tips-card { background: #FFFBF0; border: 1px solid #FFF0C4; }
.tips-card.finished-tip { background: #F0FBF6; border-color: #C4F0DB; }
.tips-head { display: flex; align-items: center; gap: 6px; color: #B8860B; font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.finished-tip .tips-head { color: #047857; }
.tips-list { padding-left: 20px; margin: 0; }
.tips-list li { font-size: 13px; color: #7C6B3A; line-height: 1.8; }
.finished-tip .tips-list li { color: #3F7C66; }

/* 空状态 */
.empty-state { text-align: center; padding: 60px 0; }
.empty-state svg { width: 160px; height: 120px; }
.empty-state p { color: #8B95A7; font-size: 15px; margin: 16px 0 24px; }
</style>
