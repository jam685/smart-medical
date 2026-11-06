<script setup>
/**
 * 患者自助改约弹窗（同医生换时段）
 * 数据来源：GET /schedules/slots（余号）、POST /appointments/{id}/reschedule
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import { fetchScheduleSlots } from '@/api/schedule'
import { rescheduleAppointment } from '@/api/appointment'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  appointment: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'success'])

const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

/** 未来 7 天 */
const dateOptions = (() => {
  const list = []
  const today = new Date()
  for (let i = 0; i < 7; i++) {
    const d = new Date(today)
    d.setDate(today.getDate() + i)
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    list.push({
      date: `${y}-${m}-${day}`,
      month: d.getMonth() + 1,
      day: d.getDate(),
      week: i === 0 ? '今天' : (i === 1 ? '明天' : WEEK_LABELS[d.getDay()]),
    })
  }
  return list
})()

const activeDate = ref(dateOptions[0].date)
const slots = ref([])
const loading = ref(false)
const selected = ref(null)
const submitting = ref(false)

/** 按时段分组 */
const groups = computed(() => {
  const list = [
    { key: 'AM', label: '上午', items: [] },
    { key: 'PM', label: '下午', items: [] },
  ]
  for (const s of slots.value) {
    const g = list.find(x => x.key === s.period)
    if (g) g.items.push(s)
  }
  return list.filter(g => g.items.length)
})

/** 当前挂号时段（不可选） */
const currentSlotId = computed(() => props.appointment?.scheduleId)

/** 是否是当前时段 */
function isCurrent(slot) {
  return currentSlotId.value != null && slot.id === currentSlotId.value
}

/** 加载某天号源 */
async function load() {
  if (!props.appointment?.doctorId || !activeDate.value) return
  loading.value = true
  selected.value = null
  try {
    slots.value = (await fetchScheduleSlots(props.appointment.doctorId, activeDate.value)) || []
  } catch (e) {
    slots.value = []
  } finally {
    loading.value = false
  }
}

/** 打开时定位到原就诊日期（若在 7 天内） */
watch(() => props.modelValue, (open) => {
  if (!open) return
  const original = (props.appointment?.appointmentTime || '').slice(0, 10)
  activeDate.value = dateOptions.some(d => d.date === original) ? original : dateOptions[0].date
  selected.value = null
  load()
})

watch(activeDate, load)

/** 选择目标时段 */
function pick(slot) {
  if (!slot.bookable || isCurrent(slot)) return
  selected.value = slot
}

/** 提交改约 */
async function confirm() {
  if (!selected.value || !props.appointment) return
  submitting.value = true
  try {
    await rescheduleAppointment(props.appointment.id, selected.value.id)
    ElMessage.success('改约成功！请按新时间到院')
    visible.value = false
    emit('success')
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/** 展示时间 */
function hhmm(t) {
  return (t || '').slice(0, 5)
}
</script>

<template>
  <el-dialog v-model="visible" title="改约就诊时间" width="640px">
    <div v-if="appointment" class="rs-body">
      <div class="rs-current">
        <span class="rs-label">当前预约</span>
        <span>{{ appointment.doctorName || appointment.doctor }} · {{ (appointment.appointmentTime || '').slice(0, 16) }}</span>
        <span class="rs-tip">改约仅支持同一医生，换医生请先取消再挂号</span>
      </div>

      <div class="rs-dates">
        <div
          v-for="(d, i) in dateOptions" :key="d.date"
          :class="['rs-date', { active: activeDate === d.date }]"
          @click="activeDate = d.date"
        >
          <span class="rs-week">{{ d.week }}</span>
          <span class="rs-day">{{ d.month }}/{{ d.day }}</span>
        </div>
      </div>

      <div v-loading="loading" class="rs-slots">
        <div v-if="!loading && !groups.length" class="rs-empty">该医生当日暂无排班</div>
        <div v-for="g in groups" :key="g.key" class="rs-group">
          <div class="rs-group-title"><el-icon><Clock /></el-icon>{{ g.label }}</div>
          <div class="rs-slot-row">
            <button
              v-for="s in g.items" :key="s.id"
              :class="['rs-slot', {
                active: selected?.id === s.id,
                disabled: !s.bookable || isCurrent(s),
              }]"
              :disabled="!s.bookable || isCurrent(s)"
              @click="pick(s)"
            >
              <span class="rs-time">{{ hhmm(s.startTime) }} - {{ hhmm(s.endTime) }}</span>
              <span class="rs-remain">{{ isCurrent(s) ? '当前时段' : (s.bookable ? `剩 ${s.remain}` : '不可约') }}</span>
            </button>
          </div>
        </div>
      </div>

      <p v-if="selected" class="rs-confirm-tip">
        确认改约到 {{ activeDate }} {{ hhmm(selected.startTime) }} - {{ hhmm(selected.endTime) }}？
      </p>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selected" :loading="submitting" @click="confirm">确认改约</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.rs-current { display: flex; align-items: center; gap: 10px; font-size: 13px; color: #374151; background: #F8FAFC; border-radius: 8px; padding: 10px 12px; margin-bottom: 14px; flex-wrap: wrap; }
.rs-label { color: #6B7280; }
.rs-tip { margin-left: auto; font-size: 12px; color: #9CA3AF; }
.rs-dates { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8px; margin-bottom: 14px; }
.rs-date { border: 1px solid #E5E7EB; border-radius: 8px; text-align: center; padding: 8px 4px; cursor: pointer; }
.rs-date:hover { border-color: #BFD9F7; }
.rs-date.active { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); border-color: transparent; }
.rs-date.active .rs-week, .rs-date.active .rs-day { color: #fff; }
.rs-week { display: block; font-size: 11px; color: #6B7280; }
.rs-day { display: block; font-size: 14px; font-weight: 600; color: #111827; }
.rs-slots { min-height: 140px; }
.rs-empty { text-align: center; color: #9CA3AF; padding: 30px 0; font-size: 13px; }
.rs-group { margin-bottom: 12px; }
.rs-group-title { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #374151; margin-bottom: 8px; }
.rs-slot-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.rs-slot { border: 1px solid #E5E7EB; background: #fff; border-radius: 8px; padding: 8px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
.rs-slot:hover:not(:disabled) { border-color: #1E7AE6; }
.rs-slot.active { background: #1E7AE6; border-color: #1E7AE6; }
.rs-slot.active .rs-time, .rs-slot.active .rs-remain { color: #fff; }
.rs-slot.disabled { background: #F6F8FB; cursor: not-allowed; }
.rs-slot.disabled .rs-time, .rs-slot.disabled .rs-remain { color: #A7B1BF; }
.rs-time { font-size: 13px; color: #111827; }
.rs-remain { font-size: 12px; color: #0E9F6E; }
.rs-confirm-tip { margin: 6px 0 0; font-size: 13px; color: #1E7AE6; }
</style>
