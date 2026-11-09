<template>
  <div class="consult-detail-page">
    <div class="page-head">
      <el-button link @click="goBack"><el-icon><ArrowLeft /></el-icon>&nbsp;返回问诊列表</el-button>
      <h2 class="page-title">问诊详情 #{{ record.id }}</h2>
    </div>

    <el-card shadow="never" class="info-card" v-loading="loading">
      <div v-if="record" class="info-grid">
        <div class="info-row"><span class="label">患者</span><span class="val">{{ record.patientName }}</span></div>
        <div class="info-row"><span class="label">接诊医生</span><span class="val">{{ record.doctorName }}</span></div>
        <div class="info-row"><span class="label">科室</span><span class="val">{{ record.department }}</span></div>
        <div class="info-row">
          <span class="label">状态</span>
          <el-tag :type="statusTag(record.status)" size="small">{{ statusName(record.status) }}</el-tag>
        </div>
        <div class="info-row"><span class="label">开始时间</span><span class="val">{{ formatTime(record.startTime) }}</span></div>
        <div class="info-row"><span class="label">结束时间</span><span class="val">{{ formatTime(record.endTime) }}</span></div>
        <div class="info-row"><span class="label">问诊时长</span><span class="val">{{ record.durationMinutes ?? 0 }} 分钟</span></div>
        <div class="info-row"><span class="label">消息数</span><span class="val">{{ messages.length }}</span></div>
      </div>
    </el-card>

    <el-card shadow="never" class="msg-card">
      <template #header>
        <div class="msg-header">
          <span>会话消息</span>
          <el-tag size="small" type="info">共 {{ messages.length }} 条</el-tag>
        </div>
      </template>

      <div v-if="messages.length === 0" class="empty">暂无消息</div>
      <div v-else class="msg-list">
        <div v-for="(m, idx) in messages" :key="idx" class="msg-item" :class="'role-' + (m.senderRole || m.role || 'UNKNOWN')">
          <div class="msg-meta">
            <span class="role-tag">{{ roleName(m.senderRole || m.role) }}</span>
            <span class="sender">{{ m.senderName || m.senderId }}</span>
            <span class="time">{{ formatTime(m.sentAt || m.createdAt) }}</span>
          </div>
          <div class="msg-body">
            <template v-if="m.messageType === 'IMAGE' || m.content?.startsWith('data:image')">
              <img :src="m.content" class="msg-img" @click="previewImg(m.content)" />
            </template>
            <template v-else>
              {{ m.content || '(无内容)' }}
            </template>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 图片预览 -->
    <el-image-viewer v-if="previewVisible" :url-list="[previewUrl]" :initial-index="0" @close="previewVisible = false" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()
const consultId = route.params.id

const loading = ref(true)
const record = ref(null)
const messages = ref([])

const previewVisible = ref(false)
const previewUrl = ref('')

function statusName(s) { return { PENDING: '进行中', REPLIED: '已回复', CLOSED: '已关闭' }[s] || s || '—' }
function statusTag(s) { return { PENDING: 'warning', REPLIED: 'success', CLOSED: 'info' }[s] || 'info' }
function roleName(r) { return { PATIENT: '患者', DOCTOR: '医生', ADMIN: '管理员', AI: 'AI' }[r] || r || '—' }
function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function goBack() { router.push('/admin/online-consults') }

function previewImg(url) { previewUrl.value = url; previewVisible.value = true }

async function loadData() {
  loading.value = true
  try {
    // 用管理员列表接口拿详情（带 patientName/doctorName）
    const list = await request.get('/admin/online-consults', { params: { page: 1, size: 1, status: '', department: '' } })
    // 列表接口不支持按 id 查，所以先用 messages 接口补足 —— 详情里把 id 的基本信息展示出来
    const all = list?.list || []
    record.value = all.find(x => String(x.id) === String(consultId)) || { id: consultId }
  } catch (e) {
    record.value = { id: consultId }
  }

  try {
    // 消息接口：管理员没有 role 约束，AuthInterceptor 只要求登录态
    messages.value = await request.get(`/online-consults/${consultId}/messages`)
  } catch {
    messages.value = []
  }

  loading.value = false
}

onMounted(loadData)
</script>

<style scoped>
.consult-detail-page { padding: 24px; }
.page-head { margin-bottom: 18px; }
.page-title { font-size: 20px; font-weight: 700; color: #1a1a2e; margin: 8px 0 0; }

.info-card { border-radius: 12px; border: 1px solid #eef0f4; margin-bottom: 18px; }
.info-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px 24px;
}
.info-row .label { display: block; font-size: 12px; color: #94a3b8; margin-bottom: 4px; }
.info-row .val { font-size: 15px; color: #1a1a2e; font-weight: 500; }

.msg-card { border-radius: 12px; border: 1px solid #eef0f4; }
.msg-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }

.msg-list { max-height: 520px; overflow-y: auto; }
.empty { text-align: center; color: #94a3b8; padding: 30px; }

.msg-item { padding: 12px 0; border-bottom: 1px dashed #eef0f4; }
.msg-item:last-child { border-bottom: none; }

.msg-meta { font-size: 12px; color: #64748b; margin-bottom: 6px; display: flex; gap: 8px; align-items: center; }
.msg-meta .role-tag { padding: 1px 8px; border-radius: 8px; font-size: 11px; font-weight: 600; }
.role-PATIENT .role-tag { background: #EFF6FF; color: #1E7AE6; }
.role-DOCTOR  .role-tag { background: #ECFDF5; color: #10B981; }
.role-ADMIN   .role-tag { background: #FEF3C7; color: #F59E0B; }
.role-AI      .role-tag { background: #F5F3FF; color: #8B5CF6; }

.role-PATIENT .msg-body { border-left: 3px solid #1E7AE6; }
.role-DOCTOR  .msg-body { border-left: 3px solid #10B981; }
.role-ADMIN   .msg-body { border-left: 3px solid #F59E0B; }
.role-AI      .msg-body { border-left: 3px solid #8B5CF6; }
.msg-body { padding: 8px 12px; background: #f8fafc; border-radius: 0 8px 8px 0; font-size: 14px; color: #1a1a2e; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.msg-img { max-width: 280px; border-radius: 6px; cursor: zoom-in; }
</style>
