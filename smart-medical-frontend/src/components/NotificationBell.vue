<script setup>
/**
 * 站内信铃铛（患者端 / 医生端共用）
 * 数据来源：GET /notifications、GET /notifications/unread-count
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import {
  fetchNotifications, fetchUnreadCount, markNotificationRead, markAllNotificationsRead,
} from '@/api/notification'

const unread = ref(0)
const list = ref([])
const loading = ref(false)
let timer = null

/** 拉未读数（轮询，30s 一次） */
async function loadUnread() {
  try {
    const res = await fetchUnreadCount()
    unread.value = res?.count || 0
  } catch (e) {
    unread.value = 0
  }
}

/** 拉列表 */
async function loadList() {
  loading.value = true
  try {
    list.value = (await fetchNotifications(20)) || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 点击单条：标记已读 */
async function open(n) {
  if (!n.read) {
    try {
      await markNotificationRead(n.id)
      n.read = true
      unread.value = Math.max(0, unread.value - 1)
    } catch (e) { /* 忽略 */ }
  }
}

/** 全部已读 */
async function readAll() {
  try {
    await markAllNotificationsRead()
    list.value.forEach(n => { n.read = true })
    unread.value = 0
  } catch (e) { /* 忽略 */ }
}

onMounted(() => {
  loadUnread()
  timer = setInterval(loadUnread, 30000)
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<template>
  <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadList">
    <template #reference>
      <button class="bell-btn" title="消息">
        <el-badge :value="unread" :hidden="unread === 0" :max="99">
          <el-icon :size="18"><Bell /></el-icon>
        </el-badge>
      </button>
    </template>
    <div class="notif-head">
      <span>消息通知</span>
      <el-button text size="small" @click="readAll">全部已读</el-button>
    </div>
    <div v-loading="loading" class="notif-list">
      <div v-if="!list.length" class="notif-empty">暂无消息</div>
      <div
        v-for="n in list" :key="n.id"
        :class="['notif-item', { unread: !n.read }]"
        @click="open(n)"
      >
        <div class="notif-title">
          <span class="dot" v-if="!n.read"></span>{{ n.title }}
        </div>
        <div class="notif-content">{{ n.content }}</div>
        <div class="notif-time">{{ n.createdAt }}</div>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.bell-btn { border: none; background: transparent; cursor: pointer; color: #4B5563; display: flex; align-items: center; padding: 4px; }
.bell-btn:hover { color: #1E7AE6; }
.notif-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: #111827; margin-bottom: 8px; }
.notif-list { max-height: 420px; overflow: auto; }
.notif-empty { text-align: center; color: #9CA3AF; padding: 24px 0; font-size: 13px; }
.notif-item { padding: 10px 8px; border-radius: 8px; cursor: pointer; }
.notif-item:hover { background: #F8FAFC; }
.notif-item.unread { background: #F0F7FF; }
.notif-title { font-size: 13px; font-weight: 600; color: #111827; display: flex; align-items: center; gap: 6px; }
.notif-title .dot { width: 6px; height: 6px; border-radius: 50%; background: #1E7AE6; }
.notif-content { font-size: 12px; color: #4B5563; margin: 4px 0; line-height: 1.5; }
.notif-time { font-size: 11px; color: #9CA3AF; }
</style>
