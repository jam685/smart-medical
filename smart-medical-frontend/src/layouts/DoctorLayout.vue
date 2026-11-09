<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AiRobotDialog from '@/components/AiRobotDialog.vue'
import AppLogo from '@/components/AppLogo.vue'
import NotificationBell from '@/components/NotificationBell.vue'
import {
  Monitor,
  User,
  ChatDotSquare,
  DataAnalysis,
  Calendar,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const menus = [
  { path: '/doctor/home', title: '工作台', icon: Monitor },
  { path: '/doctor/appointments', title: '接诊管理', icon: User },
  { path: '/doctor/online-consults', title: '问诊处理', icon: ChatDotSquare },
  { path: '/doctor/ai-analysis', title: 'AI 辅助分析', icon: DataAnalysis },
  { path: '/doctor/schedule', title: '我的排班', icon: Calendar },
]

function handleLogout() {
  userStore.logout()
  router.push('/')
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="logo">
        <AppLogo :size="34"/>
        <span class="brand-font">小林智疗</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="side-menu"
        router
        background-color="transparent"
        text-color="#4B5563"
        active-text-color="#1E7AE6"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="main">
      <header class="top-bar">
        <div class="breadcrumb">{{ route.meta.title || '医生中心' }}</div>
        <NotificationBell class="bell-wrap" />
        <div class="user-area">
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.user?.avatar" />
              <span class="name">{{ userStore.user?.realName }}</span>
              <el-icon><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/doctor/profile')">个人信息</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>

    <AiRobotDialog />
  </div>
</template>

<script>
import { ArrowDown } from '@element-plus/icons-vue'
export default { components: { ArrowDown } }
</script>

<style scoped>
.layout { display: flex; height: 100vh; }
.sidebar {
  width: 240px; flex-shrink: 0;
  background: #fff; border-right: 1px solid #E5E7EB;
  display: flex; flex-direction: column;
}
.logo {
  height: 60px; display: flex; align-items: center; gap: 10px;
  padding: 0 16px; font-size: 16px; font-weight: 600;
  background: linear-gradient(135deg, #10B981, #3FC79A); color: #fff;
}
.logo :deep(.app-logo) { border-radius: 50%; box-shadow: 0 2px 8px rgba(0,0,0,0.18); }
.side-menu { border-right: none; flex: 1; padding: 12px 8px; }
.side-menu .el-menu-item { border-radius: 6px; margin-bottom: 4px; }

.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.top-bar {
  height: 60px; background: #fff; border-bottom: 1px solid #E5E7EB;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px;
}
.breadcrumb { font-size: 16px; font-weight: 500; color: #111827; margin-right: auto; }
.bell-wrap { margin-right: 18px; }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.name { font-size: 14px; color: #374151; }
.content { flex: 1; overflow: auto; background: var(--app-window-bg); }
</style>
