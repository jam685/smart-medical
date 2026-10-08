<template>
  <div class="login-page admin-login">
    <div class="login-visual">
      <svg class="bg-grid" viewBox="0 0 500 500" preserveAspectRatio="xMidYMid slice">
        <defs>
          <pattern id="dots" x="0" y="0" width="30" height="30" patternUnits="userSpaceOnUse">
            <circle cx="2" cy="2" r="1.5" fill="#7A5AF8" opacity="0.15"/>
          </pattern>
        </defs>
        <rect width="500" height="500" fill="url(#dots)"/>
        <!-- 大圆环装饰 -->
        <circle cx="400" cy="100" r="80" fill="none" stroke="#7A5AF8" stroke-width="1" stroke-dasharray="6 10" opacity="0.4"/>
        <circle cx="100" cy="400" r="60" fill="none" stroke="#7A5AF8" stroke-width="1" stroke-dasharray="4 8" opacity="0.3"/>
      </svg>

      <div class="visual-content">
        <div class="illu-card">
          <div class="illu-float">
            <img :src="adminImg" class="illu-img person-anim" alt="医疗管理团队插画"/>
          </div>
          <div class="illu-badge">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="M12 3l7 3v5c0 4.2-2.9 6.6-7 8-4.1-1.4-7-3.8-7-8V6z" stroke="white" stroke-width="2.2" stroke-linejoin="round"/>
              <path d="M8.8 12l2.2 2.2 4.2-4.4" stroke="white" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>
        <div class="stat-cards">
          <div class="stat-card"><strong>42</strong><span>API 接口</span></div>
          <div class="stat-card"><strong>21</strong><span>数据模型</span></div>
          <div class="stat-card"><strong>100%</strong><span>审计覆盖</span></div>
        </div>
      </div>
    </div>

    <div class="login-form-area">
      <div class="form-container">
        <div class="form-header">
          <div class="mini-logo">
            <AppLogo :size="34"/>
            <span class="brand-font">小林智疗 · 管理后台</span>
          </div>
          <h1>管理员登录</h1>
          <p>System Administration · 简化认证（仅 Demo）</p>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" class="login-form" @submit.prevent="handleLogin">
          <el-form-item prop="employeeNo">
            <el-input v-model="form.employeeNo" placeholder="工号 / 手机号 / 身份证号" size="large">
              <template #prefix><el-icon><Key /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-alert type="info" :closable="false" show-icon
            title="管理员登录为 Demo 简化模式，无需密码，输入任一匹配身份标识即可进入">
          </el-alert>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin" style="margin-top: 16px;">
            安全进入管理后台
          </el-button>
        </el-form>

        <div class="demo-account">
          <div class="demo-title">🔑 演示账号（点击直接进入）</div>
          <div class="demo-cards single">
            <div class="demo-card" @click="fillDemo('A001')">
              <div class="demo-user">
                <svg viewBox="0 0 40 40"><circle cx="20" cy="15" r="8" fill="#7A5AF8" opacity="0.2"/><path d="M6 36 Q6 24 20 24 Q34 24 34 36" fill="#7A5AF8" opacity="0.3"/></svg>
                <div>
                  <strong>系统管理员</strong>
                  <small>工号 A001 · 最高权限</small>
                </div>
              </div>
              <svg viewBox="0 0 16 16" class="enter-arrow"><path d="M3 8h10M9 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            </div>
          </div>
        </div>

        <div class="security-notice">
          <svg viewBox="0 0 16 16"><path d="M8 2l6 3v4c0 4-3 6-6 7-3-1-6-3-6-7V5z" stroke="currentColor" stroke-width="1.5" fill="none"/><path d="M8 7v3l2 1" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
          <span>所有管理员操作均会被审计记录</span>
        </div>

        <div class="back-home" @click="$router.push('/')">← 返回角色选择</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { adminLogin } from '@/api/auth'
import adminImg from '@/assets/login-admin.png'
import AppLogo from '@/components/AppLogo.vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = ref({ employeeNo: '' })
const rules = {
  employeeNo: [{ required: true, message: '请输入工号/手机号/身份证号', trigger: 'blur' }]
}
const fillDemo = (a) => { form.value.employeeNo = a }

/**
 * 管理员登录：调用真实后端 POST /admin/login 接口拿 JWT
 * 成功后通过 userStore.setAuth 持久化 token 与用户信息，再跳转到管理后台首页
 */
const handleLogin = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const { token, user } = await adminLogin(form.value.employeeNo.trim())
    userStore.setAuth(token, user)
    ElMessage.success('登录成功')
    router.push('/admin')
  } catch (e) {
    // axios 拦截器已统一弹出错误提示，这里兜底防止未捕获异常
    if (e?.message && !String(e.message).includes('Request failed')) {
      ElMessage.error(e.message || '登录失败')
    }
  } finally { loading.value = false }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; }

.login-visual {
  flex: 1; position: relative; overflow: hidden;
  background: linear-gradient(135deg, #F5F3FF 0%, #EDE9FE 100%);
  display: flex; align-items: center; justify-content: center;
}
.bg-grid { position: absolute; inset: 0; width: 100%; height: 100%; }
.visual-content { position: relative; z-index: 2; padding: 40px; max-width: 450px; width: 100%; display: flex; flex-direction: column; gap: 32px; align-items: center; }

/* 插画卡片 + 人物 1.5s 入场动画 */
.illu-card {
  position: relative; width: 100%; aspect-ratio: 1 / 1;
  background: #ffffff; border-radius: 28px; padding: 14px;
  box-shadow: 0 24px 60px rgba(122, 90, 248, 0.25);
}
.illu-float { width: 100%; height: 100%; animation: floatY 4.5s ease-in-out 1.5s infinite; }
.illu-img {
  width: 100%; height: 100%; object-fit: contain; border-radius: 18px; display: block;
  animation: personIn 1.5s cubic-bezier(0.22, 1.15, 0.36, 1) both;
}
.illu-badge {
  position: absolute; top: -18px; right: -14px; width: 58px; height: 58px;
  border-radius: 18px; background: linear-gradient(135deg, #7A5AF8, #9B7DFB);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 10px 24px rgba(122, 90, 248, 0.45);
  animation: badgePop 0.6s cubic-bezier(0.22, 1.4, 0.4, 1) 1s both;
}
.illu-badge svg { width: 28px; height: 28px; }
@keyframes personIn {
  0%   { opacity: 0; transform: translateY(46px) scale(0.9); }
  65%  { opacity: 1; transform: translateY(-8px) scale(1.02); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}
@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-10px); }
}
@keyframes badgePop {
  0%   { opacity: 0; transform: scale(0) rotate(-30deg); }
  100% { opacity: 1; transform: scale(1) rotate(0); }
}

.stat-cards { display: flex; gap: 16px; }
.stat-card {
  padding: 16px 20px; background: white; border-radius: 14px; text-align: center;
  box-shadow: 0 4px 16px rgba(122, 90, 248, 0.1); min-width: 80px;
}
.stat-card strong { font-size: 22px; font-weight: 700; color: #7A5AF8; display: block; }
.stat-card span { font-size: 11px; color: #64748b; }

/* 右侧表单 */
.login-form-area { flex: 1.1; display: flex; align-items: center; justify-content: center; padding: 48px; background: white; }
.form-container { width: 100%; max-width: 420px; }

.mini-logo { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #64748b; margin-bottom: 20px; }
.form-header h1 { font-size: 28px; font-weight: 700; margin: 0 0 6px; color: #1a1a2e; }
.form-header p { font-size: 14px; color: #94a3b8; margin: 0 0 32px; }

.login-btn {
  width: 100%; height: 48px; border-radius: 12px; font-size: 16px; font-weight: 600;
  background: linear-gradient(135deg, #7A5AF8, #9B7DFB); border: none;
}
.login-btn:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(122, 90, 248, 0.4); }
.login-form :deep(.el-input__wrapper) { border-radius: 10px; }

.demo-account { margin-top: 24px; padding: 16px; border-radius: 12px; background: #faf5ff; border: 1px dashed #c4b5fd; }
.demo-title { font-size: 12px; color: #6d28d9; margin-bottom: 12px; }
.demo-cards.single .demo-card {
  padding: 14px 18px; display: flex; align-items: center; justify-content: space-between;
  background: white; border-radius: 10px; border: 1px solid #e9d5ff; cursor: pointer; transition: all 0.2s;
}
.demo-cards.single .demo-card:hover { border-color: #7A5AF8; background: #faf5ff; }
.demo-user { display: flex; align-items: center; gap: 12px; }
.demo-user svg { width: 40px; height: 40px; }
.demo-user strong { font-size: 14px; color: #1a1a2e; display: block; }
.demo-user small { font-size: 12px; color: #64748b; }
.enter-arrow { width: 18px; height: 18px; color: #7A5AF8; }

.security-notice {
  margin-top: 20px; display: flex; align-items: center; justify-content: center; gap: 6px;
  font-size: 12px; color: #94a3b8;
}
.security-notice svg { width: 14px; height: 14px; color: #7A5AF8; }

.back-home { margin-top: 20px; text-align: center; font-size: 13px; color: #94a3b8; cursor: pointer; }
.back-home:hover { color: #7A5AF8; }

@media (max-width: 900px) { .login-visual { display: none; } }
</style>
