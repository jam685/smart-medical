<script setup>
/**
 * 医生端「我的排班」页面
 * 数据来源：
 *   GET /doctors/me/schedules                 本周/未来 N 天排班（含每个时段已约数）
 *   PUT /doctors/me/schedules/{id}/status     停诊 / 恢复出诊
 *   PUT /doctors/me/schedules/{id}/capacity   加号 / 减号
 * 说明：停诊只允许在"该时段还没人预约"时操作，已有人预约需先联系患者改约（后端强校验）。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar, Clock, Refresh } from '@element-plus/icons-vue'
import {
  fetchMySchedules, changeScheduleStatus, changeScheduleCapacity,
  submitSuspendRequest, fetchMySuspendRequests,
} from '@/api/schedule'

/** 排班数据加载中 */
const loading = ref(false)
/** 7 天排班（每天含 slots 明细） */
const days = ref([])
/** 当前选中的日期 */
const activeDate = ref('')
/** 查询起始日期（yyyy-MM-dd） */
const startDate = ref(formatDate(new Date()))

/** 停诊申请弹窗 */
const applyVisible = ref(false)
const applySlot = ref(null)
const applyReason = ref('')
const applying = ref(false)
/** 我的停诊申请 */
const myRequests = ref([])

/** 加号弹窗 */
const capacityVisible = ref(false)
const capacitySaving = ref(false)
const capacityValue = ref(5)
const editingSlot = ref(null)

/** Date → yyyy-MM-dd */
function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 当前查看的日期对象 */
const activeDay = computed(() =>
  days.value.find(d => d.date === activeDate.value) || days.value[0] || null
)

/** 本周汇总：总号数、已约、停诊时段数 */
const summary = computed(() => {
  let total = 0
  let booked = 0
  let suspended = 0
  for (const d of days.value) {
    for (const s of d.slots || []) {
      total += s.total || 0
      booked += s.booked || 0
      if (s.status !== 'OPEN') suspended++
    }
  }
  return { total, booked, suspended }
})

/** 加载排班 */
async function load() {
  loading.value = true
  try {
    const data = await fetchMySchedules(startDate.value, 7)
    days.value = data || []
    const today = formatDate(new Date())
    const hit = days.value.find(d => d.date === today)
    activeDate.value = hit ? hit.date : (days.value[0]?.date || '')
  } catch (e) {
    days.value = []
  } finally {
    loading.value = false
  }
}

/** 前后翻一周 */
function shiftWeek(offset) {
  const d = new Date(startDate.value + 'T00:00:00')
  d.setDate(d.getDate() + offset * 7)
  startDate.value = formatDate(d)
  load()
}

/** 回到本周 */
function resetWeek() {
  startDate.value = formatDate(new Date())
  load()
}

/** 停诊 / 恢复 */
async function toggleSuspend(slot) {
  const suspend = slot.status === 'OPEN'
  try {
    await changeScheduleStatus(slot.id, suspend)
    ElMessage.success(suspend ? '已停诊该时段' : '已恢复出诊')
    await load()
  } catch (e) {
    // 错误提示由请求拦截器统一处理（如已有患者预约）
  }
}

/** 打开加号弹窗 */
function openCapacity(slot) {
  editingSlot.value = slot
  capacityValue.value = slot.total
  capacityVisible.value = true
}

/** 保存号数 */
async function saveCapacity() {
  if (!editingSlot.value) return
  capacitySaving.value = true
  try {
    await changeScheduleCapacity(editingSlot.value.id, Number(capacityValue.value))
    ElMessage.success('号数已更新')
    capacityVisible.value = false
    await load()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    capacitySaving.value = false
  }
}

/** 打开停诊申请弹窗 */
function openApply(slot) {
  applySlot.value = slot
  applyReason.value = ''
  applyVisible.value = true
}

/** 提交停诊申请 */
async function submitApply() {
  if (!applySlot.value) return
  if (!applyReason.value.trim()) {
    ElMessage.warning('请填写停诊原因')
    return
  }
  applying.value = true
  try {
    await submitSuspendRequest([applySlot.value.id], applyReason.value.trim())
    ElMessage.success('申请已提交，等待管理员审批')
    applyVisible.value = false
    await loadRequests()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    applying.value = false
  }
}

/** 加载我的停诊申请 */
async function loadRequests() {
  try {
    myRequests.value = (await fetchMySuspendRequests()) || []
  } catch (e) {
    myRequests.value = []
  }
}

/** 时段是否已开始（已开始的时段不再允许停诊/加号） */
function isPast(slot) {
  const start = new Date(`${slot.date}T${slot.startTime}:00`)
  return start.getTime() <= Date.now()
}

onMounted(() => {
  load()
  loadRequests()
})
</script>

<template>
  <div class="schedule-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">我的排班</h2>
        <p class="page-sub">查看出诊号源、停诊/恢复、临时加号</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Calendar" @click="shiftWeek(-1)">上一周</el-button>
        <el-button :icon="Refresh" @click="resetWeek">本周</el-button>
        <el-button :icon="Calendar" @click="shiftWeek(1)">下一周</el-button>
      </div>
    </div>

    <div class="summary-row">
      <div class="summary-card">
        <span class="summary-label">近 7 天号源</span>
        <strong>{{ summary.total }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">已预约</span>
        <strong class="ok">{{ summary.booked }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">停诊时段</span>
        <strong :class="{ warn: summary.suspended > 0 }">{{ summary.suspended }}</strong>
      </div>
    </div>

    <div v-loading="loading" class="week-panel">
      <!-- 日期条 -->
      <div class="date-row">
        <div
          v-for="d in days" :key="d.date"
          :class="['date-item', { active: activeDate === d.date }]"
          @click="activeDate = d.date"
        >
          <span class="date-week">{{ d.weekday }}</span>
          <span class="date-day">{{ d.date.slice(5) }}</span>
          <span class="date-meta">{{ d.bookedCount }}/{{ d.totalRemain + d.bookedCount }}</span>
        </div>
      </div>

      <!-- 选中日期的时段 -->
      <div v-if="activeDay" class="slot-panel">
        <div class="slot-panel-head">
          <span>{{ activeDay.date }}（{{ activeDay.weekday }}）</span>
          <span class="slot-panel-sub">共 {{ activeDay.slotCount }} 个时段 · 已约 {{ activeDay.bookedCount }} 人</span>
        </div>

        <div v-if="activeDay.slotCount === 0" class="empty-tip">当日无排班</div>

        <div v-else class="slot-grid">
          <div v-for="s in activeDay.slots" :key="s.id" :class="['slot-card', { closed: s.status !== 'OPEN' }]">
            <div class="slot-head">
              <span class="slot-time"><el-icon><Clock /></el-icon>{{ s.startTime }} - {{ s.endTime }}</span>
              <el-tag size="small" :type="s.bookable ? 'success' : 'info'" effect="plain">
                {{ s.status !== 'OPEN' ? '停诊' : (s.bookable ? '可约' : (s.remain === 0 ? '约满' : '已过')) }}
              </el-tag>
            </div>
            <div class="slot-body">
              <span>已约 <strong>{{ s.booked }}</strong> / {{ s.total }}</span>
              <span class="remain">余 {{ s.remain }}</span>
            </div>
            <div class="slot-actions">
              <el-button size="small" text type="primary" :disabled="isPast(s)" @click="openCapacity(s)">加号</el-button>
              <el-button
                v-if="s.status === 'OPEN' && s.booked > 0"
                size="small" text type="warning" :disabled="isPast(s)" @click="openApply(s)"
              >
                申请停诊
              </el-button>
              <el-button
                v-else
                size="small" text
                :type="s.status === 'OPEN' ? 'danger' : 'success'"
                :disabled="isPast(s)"
                @click="toggleSuspend(s)"
              >
                {{ s.status === 'OPEN' ? '停诊' : '恢复' }}
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 我的停诊申请 -->
    <div v-if="myRequests.length" class="request-panel">
      <div class="request-title">我的停诊申请</div>
      <div v-for="r in myRequests" :key="r.id" class="request-row">
        <span class="req-slots">{{ r.slotSummary }}</span>
        <span class="req-reason">{{ r.reason || '—' }}</span>
        <el-tag
          size="small" effect="plain"
          :type="r.status === 'APPROVED' ? 'success' : (r.status === 'REJECTED' ? 'danger' : 'warning')"
        >
          {{ r.status === 'APPROVED' ? '已通过' : (r.status === 'REJECTED' ? '已驳回' : '待审批') }}
        </el-tag>
      </div>
    </div>

    <!-- 申请停诊 -->
    <el-dialog v-model="applyVisible" title="申请停诊" width="420px">
      <p v-if="applySlot" class="dialog-tip">
        {{ applySlot.date }} {{ applySlot.startTime }} - {{ applySlot.endTime }}，当前已约 {{ applySlot.booked }} 人
      </p>
      <el-input v-model="applyReason" type="textarea" :rows="3" placeholder="请说明停诊原因（如：临时手术、外出会诊）" />
      <p class="apply-warn">提交后由管理员审批；通过后系统会为已预约患者退号并发送通知。</p>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 加号弹窗 -->
    <el-dialog v-model="capacityVisible" title="调整号数" width="360px">
      <p class="dialog-tip" v-if="editingSlot">
        {{ editingSlot.date }} {{ editingSlot.startTime }} - {{ editingSlot.endTime }}，
        当前已约 {{ editingSlot.booked }} 人
      </p>
      <el-input-number v-model="capacityValue" :min="editingSlot?.booked || 0" :max="50" />
      <template #footer>
        <el-button @click="capacityVisible = false">取消</el-button>
        <el-button type="primary" :loading="capacitySaving" @click="saveCapacity">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.schedule-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }
.page-title { margin: 0; font-size: 20px; color: #111827; }
.page-sub { margin: 6px 0 0; font-size: 13px; color: #6B7280; }
.header-actions { display: flex; gap: 8px; }

.summary-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px; }
.summary-card { background: #fff; border: 1px solid #E5E7EB; border-radius: 10px; padding: 14px 18px; display: flex; justify-content: space-between; align-items: center; }
.summary-label { font-size: 13px; color: #6B7280; }
.summary-card strong { font-size: 22px; color: #111827; }
.summary-card strong.ok { color: #0E9F6E; }
.summary-card strong.warn { color: #D97706; }

.week-panel { background: #fff; border: 1px solid #E5E7EB; border-radius: 12px; padding: 18px; }
.date-row { display: grid; grid-template-columns: repeat(7, 1fr); gap: 10px; margin-bottom: 18px; }
.date-item { border: 1px solid #E5E7EB; border-radius: 10px; padding: 10px 8px; text-align: center; cursor: pointer; transition: all .15s; }
.date-item:hover { border-color: #BFD9F7; }
.date-item.active { background: linear-gradient(135deg, #10B981, #3FC79A); border-color: transparent; }
.date-item.active .date-week, .date-item.active .date-day, .date-item.active .date-meta { color: #fff; }
.date-week { display: block; font-size: 12px; color: #6B7280; }
.date-day { display: block; font-size: 16px; font-weight: 600; color: #111827; margin: 2px 0; }
.date-meta { font-size: 11px; color: #9CA3AF; }

.slot-panel-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 12px; font-weight: 600; color: #111827; }
.slot-panel-sub { font-size: 12px; font-weight: 400; color: #6B7280; }
.slot-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.slot-card { border: 1px solid #E5E7EB; border-radius: 10px; padding: 12px; }
.slot-card.closed { background: #F9FAFB; opacity: .85; }
.slot-head { display: flex; justify-content: space-between; align-items: center; }
.slot-time { display: flex; align-items: center; gap: 4px; font-weight: 600; color: #111827; font-size: 14px; }
.slot-body { display: flex; justify-content: space-between; margin: 10px 0 6px; font-size: 13px; color: #4B5563; }
.slot-body .remain { color: #1E7AE6; }
.slot-actions { display: flex; justify-content: flex-end; gap: 4px; }
.empty-tip { padding: 30px; text-align: center; color: #9CA3AF; }
.dialog-tip { margin: 0 0 12px; font-size: 13px; color: #6B7280; }
.apply-warn { margin: 10px 0 0; font-size: 12px; color: #B45309; }
.request-panel { margin-top: 16px; background: #fff; border: 1px solid #E5E7EB; border-radius: 12px; padding: 16px 18px; }
.request-title { font-weight: 600; color: #111827; margin-bottom: 10px; }
.request-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-top: 1px solid #F3F4F6; font-size: 13px; }
.request-row .req-slots { color: #111827; }
.request-row .req-reason { flex: 1; color: #6B7280; }
</style>
