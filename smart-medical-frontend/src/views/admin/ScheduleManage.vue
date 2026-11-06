<script setup>
/**
 * 管理端「排班管理」页面
 * 数据来源：
 *   GET  /admin/schedules/doctors   医生列表（按科室）
 *   GET  /admin/schedules/board     某医生未来 N 天排班（含每时段已约数）
 *   GET  /admin/schedules/impact    停诊影响面预览（患者清单 + 改约建议）
 *   PUT  /admin/schedules/status    批量停诊/恢复（停诊会为已约患者退号）
 *   PUT  /admin/schedules/{id}/capacity  调整号数
 *   POST /admin/schedules/generate  按医生/科室批量生成号源
 *
 * 停诊是"先看影响面，再确认退号"的两步操作：预览接口只读，确认后才真正退号并回补号源。
 */
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Plus, Refresh, WarningFilled, CopyDocument, Tickets } from '@element-plus/icons-vue'
import {
  fetchAdminDoctors, fetchAdminBoard, fetchSuspendImpact, fetchAdminAlternatives,
  changeAdminScheduleStatus, changeAdminCapacity, generateSchedules, reschedulePatients,
  fetchSuspendRequests, fetchPendingRequestCount, reviewSuspendRequest,
} from '@/api/adminSchedule'

/** Date → yyyy-MM-dd */
function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 医生与筛选 */
const doctors = ref([])
const departments = ref([])
const department = ref('')
const doctorId = ref(null)

/** 排班数据 */
const startDate = ref(formatDate(new Date()))
const days = ref([])
const activeDate = ref('')
const loading = ref(false)

/** 停诊影响面弹窗 */
const suspendVisible = ref(false)
const suspendLoading = ref(false)
const suspendSubmitting = ref(false)
const impact = ref(null)
const pendingSlotIds = ref([])
const pendingLabel = ref('')
/** 改约目标：候选时段（同医生 + 跨科室） */
const targetOptions = ref([])
/** 已选目标时段 ID（可多选，按顺序装人） */
const selectedTargetIds = ref([])
const targetDept = ref('')
const targetLoading = ref(false)
const rescheduling = ref(false)

/** 停诊申请 */
const requestsVisible = ref(false)
const requests = ref([])
const requestCount = ref(0)
const reviewLoading = ref(false)

/** 已选目标时段可容纳人数 */
const selectedCapacity = computed(() =>
  targetOptions.value
    .filter(s => selectedTargetIds.value.includes(s.id))
    .reduce((sum, s) => sum + (s.remain || 0), 0)
)

/** 是否可执行改约：已选时段容量 >= 待迁人数 */
const canReschedule = computed(() =>
  !!impact.value && impact.value.affectedCount > 0
  && selectedTargetIds.value.length > 0
  && selectedCapacity.value >= impact.value.affectedCount
)

/** 加号弹窗 */
const capacityVisible = ref(false)
const capacitySaving = ref(false)
const capacityValue = ref(5)
const editingSlot = ref(null)

/** 批量生成弹窗 */
const generateVisible = ref(false)
const generating = ref(false)
const genForm = ref({ department: '', doctorId: null, startDate: formatDate(new Date()), days: 7, capacity: 5 })

const currentDoctors = computed(() =>
  department.value ? doctors.value.filter(d => d.department === department.value) : doctors.value
)

const activeDay = computed(() => days.value.find(d => d.date === activeDate.value) || days.value[0] || null)

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

/** 可整日停诊的时段（未开始且未停诊） */
const dayStoppableSlots = computed(() => {
  if (!activeDay.value) return []
  return (activeDay.value.slots || []).filter(s => s.status === 'OPEN' && !isPast(s))
})

/** 加载医生列表 */
async function loadDoctors() {
  try {
    doctors.value = (await fetchAdminDoctors()) || []
    departments.value = [...new Set(doctors.value.map(d => d.department).filter(Boolean))]
    if (!department.value && departments.value.length) {
      department.value = departments.value[0]
    }
    if (!doctorId.value && currentDoctors.value.length) {
      doctorId.value = currentDoctors.value[0].id
    }
  } catch (e) {
    doctors.value = []
  }
}

/** 加载排班 */
async function loadBoard() {
  if (!doctorId.value) {
    days.value = []
    return
  }
  loading.value = true
  try {
    days.value = (await fetchAdminBoard(doctorId.value, startDate.value, 7)) || []
    const today = formatDate(new Date())
    const hit = days.value.find(d => d.date === today)
    activeDate.value = hit ? hit.date : (activeDate.value || days.value[0]?.date || '')
  } catch (e) {
    days.value = []
  } finally {
    loading.value = false
  }
}

/** 科室切换：重选该科室第一位医生 */
function onDepartmentChange() {
  doctorId.value = currentDoctors.value[0]?.id || null
}

/** 前后翻周 */
function shiftWeek(offset) {
  const d = new Date(startDate.value + 'T00:00:00')
  d.setDate(d.getDate() + offset * 7)
  startDate.value = formatDate(d)
  loadBoard()
}

function resetWeek() {
  startDate.value = formatDate(new Date())
  loadBoard()
}

/** 时段是否已开始 */
function isPast(slot) {
  return new Date(`${slot.date}T${slot.startTime}:00`).getTime() <= Date.now()
}

/** 打开停诊影响面预览 */
async function openSuspend(slotIds, label) {
  if (!slotIds.length) {
    ElMessage.info('没有可停诊的时段')
    return
  }
  pendingSlotIds.value = slotIds
  pendingLabel.value = label
  suspendVisible.value = true
  suspendLoading.value = true
  impact.value = null
  targetOptions.value = []
  selectedTargetIds.value = []
  targetDept.value = ''
  try {
    impact.value = await fetchSuspendImpact(slotIds)
    // 同医生候选先入池，再默认挑出"够装下所有人"的时段组合
    targetOptions.value = (impact.value?.alternatives || []).map(a => ({ ...a }))
    autoSelectTargets()
  } catch (e) {
    suspendVisible.value = false
  } finally {
    suspendLoading.value = false
  }
}

/** 确认停诊（force=true 表示已确认退号） */
async function confirmSuspend() {
  if (!impact.value || impact.value.hasStarted) return
  suspendSubmitting.value = true
  try {
    const res = await changeAdminScheduleStatus(pendingSlotIds.value, true, true)
    const cancelled = res?.cancelled || 0
    ElMessage.success(cancelled > 0
      ? `已停诊 ${res.changed} 个时段，并为 ${cancelled} 位患者退号`
      : `已停诊 ${res?.changed || pendingSlotIds.value.length} 个时段`)
    suspendVisible.value = false
    await loadBoard()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    suspendSubmitting.value = false
  }
}

/** 贪心选一组能装下所有患者的时段（余号大的优先） */
function autoSelectTargets() {
  const need = impact.value?.affectedCount || 0
  if (need <= 0) return
  const sorted = [...targetOptions.value].sort((a, b) => (b.remain || 0) - (a.remain || 0))
  const picked = []
  let capacity = 0
  for (const s of sorted) {
    if (capacity >= need) break
    picked.push(s.id)
    capacity += s.remain || 0
  }
  selectedTargetIds.value = capacity >= need ? picked : []
}

/** 勾选/取消目标时段 */
function toggleTarget(id) {
  const idx = selectedTargetIds.value.indexOf(id)
  if (idx >= 0) {
    selectedTargetIds.value.splice(idx, 1)
  } else {
    selectedTargetIds.value.push(id)
  }
}

/** 按科室补充候选时段（支持跨科室改约） */
async function loadMoreTargets() {
  if (!impact.value) return
  targetLoading.value = true
  try {
    const list = await fetchAdminAlternatives(targetDept.value, '', 7, pendingSlotIds.value)
    const map = new Map(targetOptions.value.map(s => [s.id, s]))
    for (const s of list || []) map.set(s.id, s)
    targetOptions.value = [...map.values()]
    if (!selectedTargetIds.value.length) autoSelectTargets()
  } catch (e) {
    // 忽略
  } finally {
    targetLoading.value = false
  }
}

/** 一键改约并停诊 */
async function confirmReschedule() {
  if (!canReschedule.value) return
  rescheduling.value = true
  try {
    const res = await reschedulePatients(
      pendingSlotIds.value,
      selectedTargetIds.value,
      impact.value.affected.map(a => a.appointmentId),
    )
    ElMessage.success(`已将 ${res.rescheduled} 位患者改约，并停诊 ${res.suspended} 个时段`)
    suspendVisible.value = false
    await loadBoard()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    rescheduling.value = false
  }
}

/** —— 停诊申请 —— */
async function loadRequestCount() {
  try {
    requestCount.value = (await fetchPendingRequestCount())?.count || 0
  } catch (e) {
    requestCount.value = 0
  }
}

async function openRequests() {
  requestsVisible.value = true
  try {
    requests.value = (await fetchSuspendRequests('PENDING')) || []
  } catch (e) {
    requests.value = []
  }
}

async function approveRequest(row) {
  reviewLoading.value = true
  try {
    await reviewSuspendRequest(row.id, true, '')
    ElMessage.success('已通过：相关患者已退号并收到通知')
    requests.value = requests.value.filter(r => r.id !== row.id)
    await loadRequestCount()
    await loadBoard()
  } catch (e) {
    // 忽略
  } finally {
    reviewLoading.value = false
  }
}

async function rejectRequest(row) {
  let note = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因（会通知医生）', '驳回停诊申请', {
      inputPlaceholder: '如：该时段已安排手术，请改约到下午',
    })
    note = value || ''
  } catch (e) {
    return
  }
  reviewLoading.value = true
  try {
    await reviewSuspendRequest(row.id, false, note)
    ElMessage.success('已驳回')
    requests.value = requests.value.filter(r => r.id !== row.id)
    await loadRequestCount()
  } catch (e) {
    // 忽略
  } finally {
    reviewLoading.value = false
  }
}

/** 恢复出诊 */
async function restoreSlots(slotIds) {
  try {
    await changeAdminScheduleStatus(slotIds, false, false)
    ElMessage.success('已恢复出诊')
    await loadBoard()
  } catch (e) {
    // 忽略，拦截器已提示
  }
}

/** 复制电话通知清单 */
async function copyNoticeList() {
  const list = impact.value?.affected || []
  if (!list.length) return
  const text = list.map(a =>
    `${a.patientName} ${a.patientPhone || '（无手机号）'} 挂号单 ${a.orderNo}，原就诊 ${a.appointmentTime}`
  ).join('\n')
  try {
    await navigator.clipboard.writeText(`【停诊通知清单】${pendingLabel.value}\n${text}`)
    ElMessage.success('已复制，可直接粘贴给客服/短信平台')
  } catch (e) {
    ElMessage.warning('浏览器不允许访问剪贴板，请手动复制表格内容')
  }
}

/** 打开加号弹窗 */
function openCapacity(slot) {
  editingSlot.value = slot
  capacityValue.value = slot.total
  capacityVisible.value = true
}

async function saveCapacity() {
  if (!editingSlot.value) return
  capacitySaving.value = true
  try {
    await changeAdminCapacity(editingSlot.value.id, Number(capacityValue.value))
    ElMessage.success('号数已更新')
    capacityVisible.value = false
    await loadBoard()
  } catch (e) {
    // 忽略
  } finally {
    capacitySaving.value = false
  }
}

/** 打开批量生成 */
function openGenerate() {
  genForm.value = {
    department: department.value || '',
    doctorId: doctorId.value,
    startDate: formatDate(new Date()),
    days: 7,
    capacity: 5,
  }
  generateVisible.value = true
}

async function submitGenerate() {
  generating.value = true
  try {
    const payload = {
      startDate: genForm.value.startDate,
      days: genForm.value.days,
      capacity: genForm.value.capacity,
      doctorId: genForm.value.doctorId || null,
      department: genForm.value.doctorId ? null : genForm.value.department,
    }
    const res = await generateSchedules(payload)
    ElMessage.success(`已生成 ${res?.created || 0} 个号源`)
    generateVisible.value = false
    await loadBoard()
  } catch (e) {
    // 忽略
  } finally {
    generating.value = false
  }
}

watch(doctorId, () => {
  activeDate.value = ''
  loadBoard()
})

onMounted(async () => {
  await loadDoctors()
  await loadBoard()
  loadRequestCount()
})
</script>

<template>
  <div class="schedule-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">排班管理</h2>
        <p class="page-sub">按科室/医生查看号源，批量生成、停诊退号、临时加号</p>
      </div>
      <div class="header-actions">
        <el-badge :value="requestCount" :hidden="!requestCount" :max="99">
          <el-button :icon="Tickets" @click="openRequests">停诊申请</el-button>
        </el-badge>
        <el-button type="primary" :icon="Plus" @click="openGenerate">批量生成号源</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select v-model="department" placeholder="选择科室" style="width: 160px" @change="onDepartmentChange">
        <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
      </el-select>
      <el-select v-model="doctorId" placeholder="选择医生" style="width: 180px">
        <el-option
          v-for="d in currentDoctors" :key="d.id"
          :label="`${d.realName}（${d.title || '医师'}）`" :value="d.id"
        />
      </el-select>
      <div class="week-actions">
        <el-button :icon="Calendar" @click="shiftWeek(-1)">上一周</el-button>
        <el-button :icon="Refresh" @click="resetWeek">本周</el-button>
        <el-button :icon="Calendar" @click="shiftWeek(1)">下一周</el-button>
      </div>
    </div>

    <!-- 汇总 -->
    <div class="summary-row">
      <div class="summary-card"><span>近 7 天号源</span><strong>{{ summary.total }}</strong></div>
      <div class="summary-card"><span>已预约</span><strong class="ok">{{ summary.booked }}</strong></div>
      <div class="summary-card"><span>停诊时段</span><strong :class="{ warn: summary.suspended > 0 }">{{ summary.suspended }}</strong></div>
    </div>

    <div v-loading="loading" class="board">
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

      <div v-if="activeDay" class="slot-panel">
        <div class="slot-panel-head">
          <div>
            <span class="day-title">{{ activeDay.date }}（{{ activeDay.weekday }}）</span>
            <span class="slot-panel-sub">共 {{ activeDay.slotCount }} 个时段 · 已约 {{ activeDay.bookedCount }} 人</span>
          </div>
          <el-button
            size="small" type="danger" plain :icon="WarningFilled"
            :disabled="dayStoppableSlots.length === 0"
            @click="openSuspend(dayStoppableSlots.map(s => s.id), `${activeDay.date} 全天`)"
          >
            整日停诊
          </el-button>
        </div>

        <div v-if="activeDay.slotCount === 0" class="empty-tip">当日无排班，可点击右上角「批量生成号源」</div>

        <div v-else class="slot-grid">
          <div v-for="s in activeDay.slots" :key="s.id" :class="['slot-card', { closed: s.status !== 'OPEN' }]">
            <div class="slot-head">
              <span class="slot-time">{{ s.startTime }} - {{ s.endTime }}</span>
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
                v-if="s.status === 'OPEN'" size="small" text type="danger" :disabled="isPast(s)"
                @click="openSuspend([s.id], `${s.date} ${s.startTime}-${s.endTime}`)"
              >
                停诊
              </el-button>
              <el-button v-else size="small" text type="success" :disabled="isPast(s)" @click="restoreSlots([s.id])">
                恢复
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 停诊影响面 -->
    <el-dialog v-model="suspendVisible" title="停诊影响面预览" width="720px" :close-on-click-modal="false">
      <div v-loading="suspendLoading" class="impact-body">
        <template v-if="impact">
          <div class="impact-head">
            <div class="impact-target">{{ pendingLabel }}</div>
            <el-tag type="warning" effect="plain">{{ impact.slotCount }} 个时段</el-tag>
          </div>

          <el-alert
            v-if="impact.hasStarted" type="error" :closable="false" show-icon
            title="所选时段中有已开始的时段，不能停诊"
          />
          <el-alert
            v-else-if="impact.affectedCount === 0" type="success" :closable="false" show-icon
            title="这些时段还没有患者预约，可以直接停诊"
          />
          <el-alert
            v-else type="warning" :closable="false" show-icon
            :title="targetSlotId
              ? `将为 ${impact.affectedCount} 位患者改约到所选时段，并停诊原时段`
              : `停诊将为 ${impact.affectedCount} 位已预约患者退号，号源自动回补`"
          />

          <template v-if="impact.affectedCount > 0">
            <div class="impact-section-title">
              受影响患者（{{ impact.affectedCount }}）
              <el-button size="small" text :icon="CopyDocument" @click="copyNoticeList">复制通知清单</el-button>
            </div>
            <el-table :data="impact.affected" size="small" border max-height="240">
              <el-table-column prop="patientName" label="患者" width="90" />
              <el-table-column prop="patientPhone" label="手机号" width="120" />
              <el-table-column prop="orderNo" label="挂号单号" width="130" />
              <el-table-column prop="appointmentTime" label="就诊时间" width="140" />
              <el-table-column prop="slotTime" label="时段" width="110" />
              <el-table-column prop="status" label="状态" width="90">
                <template #default="{ row }">{{ row.status === 'CONFIRMED' ? '待就诊' : '待确认' }}</template>
              </el-table-column>
            </el-table>
          </template>

          <template v-if="impact.affectedCount > 0">
            <div class="impact-section-title">
              <span>选择改约目标（可多选，支持跨科室）</span>
              <span class="pick-summary">
                已选 {{ selectedTargetIds.length }} 个 · 可容纳 {{ selectedCapacity }} 人 / 需 {{ impact.affectedCount }} 人
              </span>
            </div>
            <div class="target-filter">
              <el-select v-model="targetDept" placeholder="全部科室" clearable style="width: 150px" @change="loadMoreTargets">
                <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
              </el-select>
              <el-button size="small" :loading="targetLoading" @click="loadMoreTargets">查找时段</el-button>
              <span class="target-tip">点击时段多选，容量够即可提交</span>
            </div>
            <div v-if="targetOptions.length" v-loading="targetLoading" class="alt-list">
              <button
                v-for="a in targetOptions" :key="a.id"
                :class="['alt-chip', { active: selectedTargetIds.includes(a.id) }]"
                @click="toggleTarget(a.id)"
              >
                <span v-if="a.doctorName" class="alt-doc">{{ a.doctorName }}</span>
                {{ a.date.slice(5) }} {{ a.startTime }}
                <em>余 {{ a.remain }}</em>
              </button>
            </div>
            <div v-else class="alt-empty">暂无可改约时段，只能选择退号</div>
            <p v-if="canReschedule" class="alt-hint">
              将把 {{ impact.affectedCount }} 位患者依次安排到已选时段，随后停诊原时段
            </p>
            <p v-else-if="selectedTargetIds.length" class="alt-warn">
              已选时段容量不足（{{ selectedCapacity }} / {{ impact.affectedCount }}），请再选几个时段
            </p>
          </template>
        </template>
      </div>

      <template #footer>
        <el-button @click="suspendVisible = false">取消</el-button>
        <template v-if="impact?.affectedCount > 0">
          <el-button
            type="danger" plain :loading="suspendSubmitting"
            :disabled="suspendLoading || impact.hasStarted"
            @click="confirmSuspend"
          >
            仅退号并停诊（{{ impact.affectedCount }} 人）
          </el-button>
          <el-button
            type="primary" :loading="rescheduling"
            :disabled="suspendLoading || impact.hasStarted || !canReschedule"
            @click="confirmReschedule"
          >
            改约并停诊（{{ impact.affectedCount }} 人）
          </el-button>
        </template>
        <el-button
          v-else type="danger" :loading="suspendSubmitting"
          :disabled="suspendLoading || !impact || impact.hasStarted"
          @click="confirmSuspend"
        >
          确认停诊
        </el-button>
      </template>
    </el-dialog>

    <!-- 停诊申请审批 -->
    <el-drawer v-model="requestsVisible" title="停诊申请审批" size="620px">
      <div v-loading="reviewLoading">
        <el-empty v-if="!requests.length" description="暂无待审批的停诊申请" />
        <div v-for="r in requests" :key="r.id" class="req-card">
          <div class="req-head">
            <strong>{{ r.doctorName }} · {{ r.department }}</strong>
            <el-tag size="small" type="warning" effect="plain">待审批</el-tag>
          </div>
          <p class="req-line">时段：{{ r.slotSummary }}</p>
          <p class="req-line">原因：{{ r.reason || '—' }}</p>
          <p class="req-line muted">提交于 {{ r.createdAt }}</p>
          <div class="req-actions">
            <el-button size="small" @click="rejectRequest(r)">驳回</el-button>
            <el-button size="small" type="danger" @click="approveRequest(r)">通过并退号</el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 加号 -->
    <el-dialog v-model="capacityVisible" title="调整号数" width="380px">
      <p v-if="editingSlot" class="dialog-tip">
        {{ editingSlot.date }} {{ editingSlot.startTime }} - {{ editingSlot.endTime }}，
        当前已约 {{ editingSlot.booked }} 人
      </p>
      <el-input-number v-model="capacityValue" :min="editingSlot?.booked || 0" :max="50" />
      <template #footer>
        <el-button @click="capacityVisible = false">取消</el-button>
        <el-button type="primary" :loading="capacitySaving" @click="saveCapacity">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量生成 -->
    <el-dialog v-model="generateVisible" title="批量生成号源" width="440px">
      <el-form label-width="96px">
        <el-form-item label="科室">
          <el-select v-model="genForm.department" placeholder="选择科室" style="width: 100%">
            <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生">
          <el-select v-model="genForm.doctorId" placeholder="整个科室" clearable style="width: 100%">
            <el-option
              v-for="d in doctors.filter(x => !genForm.department || x.department === genForm.department)"
              :key="d.id" :label="d.realName" :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="起始日期">
          <el-date-picker v-model="genForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="天数">
          <el-input-number v-model="genForm.days" :min="1" :max="30" />
        </el-form-item>
        <el-form-item label="每时段号数">
          <el-input-number v-model="genForm.capacity" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="submitGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.schedule-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; color: #111827; }
.page-sub { margin: 6px 0 0; font-size: 13px; color: #6B7280; }
.filter-bar { display: flex; align-items: center; gap: 12px; background: #fff; border: 1px solid #E5E7EB; border-radius: 10px; padding: 12px 16px; margin-bottom: 14px; }
.week-actions { margin-left: auto; display: flex; gap: 8px; }

.summary-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 16px; }
.summary-card { background: #fff; border: 1px solid #E5E7EB; border-radius: 10px; padding: 14px 18px; display: flex; justify-content: space-between; align-items: center; }
.summary-card span { font-size: 13px; color: #6B7280; }
.summary-card strong { font-size: 22px; color: #111827; }
.summary-card strong.ok { color: #0E9F6E; }
.summary-card strong.warn { color: #D97706; }

.board { background: #fff; border: 1px solid #E5E7EB; border-radius: 12px; padding: 18px; }
.date-row { display: grid; grid-template-columns: repeat(7, 1fr); gap: 10px; margin-bottom: 18px; }
.date-item { border: 1px solid #E5E7EB; border-radius: 10px; padding: 10px 8px; text-align: center; cursor: pointer; }
.date-item:hover { border-color: #BFD9F7; }
.date-item.active { background: linear-gradient(135deg, #6366F1, #8284F4); border-color: transparent; }
.date-item.active .date-week, .date-item.active .date-day, .date-item.active .date-meta { color: #fff; }
.date-week { display: block; font-size: 12px; color: #6B7280; }
.date-day { display: block; font-size: 16px; font-weight: 600; color: #111827; margin: 2px 0; }
.date-meta { font-size: 11px; color: #9CA3AF; }

.slot-panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.day-title { font-weight: 600; color: #111827; }
.slot-panel-sub { margin-left: 10px; font-size: 12px; color: #6B7280; }
.slot-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.slot-card { border: 1px solid #E5E7EB; border-radius: 10px; padding: 12px; }
.slot-card.closed { background: #F9FAFB; opacity: .9; }
.slot-head { display: flex; justify-content: space-between; align-items: center; }
.slot-time { font-weight: 600; color: #111827; font-size: 14px; }
.slot-body { display: flex; justify-content: space-between; margin: 10px 0 6px; font-size: 13px; color: #4B5563; }
.slot-body .remain { color: #1E7AE6; }
.slot-actions { display: flex; justify-content: flex-end; gap: 4px; }
.empty-tip { padding: 30px; text-align: center; color: #9CA3AF; }

.impact-body { min-height: 120px; }
.impact-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.impact-target { font-weight: 600; color: #111827; }
.impact-section-title { display: flex; justify-content: space-between; align-items: center; margin: 16px 0 8px; font-size: 13px; font-weight: 600; color: #374151; }
.alt-list { display: flex; flex-wrap: wrap; gap: 8px; }
.alt-chip { padding: 6px 12px; background: #EFF6FF; color: #1E7AE6; border: 1px solid transparent; border-radius: 999px; font-size: 12px; cursor: pointer; }
.alt-chip em { font-style: normal; margin-left: 4px; color: #64748B; }
.alt-chip:hover:not(.disabled) { border-color: #1E7AE6; }
.alt-chip.active { background: #1E7AE6; color: #fff; }
.alt-chip.active em { color: #DBEAFE; }
.alt-chip.disabled { background: #F3F4F6; color: #9CA3AF; cursor: not-allowed; }
.alt-hint { margin: 10px 0 0; font-size: 12px; color: #0E9F6E; }
.alt-warn { margin: 10px 0 0; font-size: 12px; color: #D97706; }
.alt-empty { font-size: 12px; color: #9CA3AF; }
.alt-doc { margin-right: 4px; color: #7C3AED; }
.alt-chip.active .alt-doc { color: #E0E7FF; }
.pick-summary { font-size: 12px; font-weight: 400; color: #6B7280; }
.target-filter { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.target-tip { font-size: 12px; color: #9CA3AF; }
.req-card { border: 1px solid #E5E7EB; border-radius: 10px; padding: 14px; margin-bottom: 12px; }
.req-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.req-line { margin: 4px 0; font-size: 13px; color: #374151; }
.req-line.muted { color: #9CA3AF; font-size: 12px; }
.req-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.dialog-tip { margin: 0 0 12px; font-size: 13px; color: #6B7280; }
</style>
