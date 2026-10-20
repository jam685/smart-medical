<template>
  <div class="login-page doctor-login">
    <div class="login-form-area">
      <div class="form-container">
        <div class="form-header">
          <div class="mini-logo">
            <AppLogo :size="34"/>
            <span class="brand-font">小林智疗</span>
          </div>
          <h1>医生工作台</h1>
          <p>Doctor Login · 工号或姓名登录</p>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" class="login-form" @submit.prevent="handleLogin">
          <el-form-item prop="account">
            <el-input v-model="form.account" placeholder="工号（D001）或姓名" size="large">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-button type="success" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            登录工作台
          </el-button>
        </el-form>

        <div class="demo-account">
          <div class="demo-title">🎯 演示账号（点击自动填充）</div>
          <div class="demo-cards">
            <div class="demo-card" @click="fillDemo('D015', '123456')">
              <strong>韩医生 · 急诊科</strong>
              <small>D015 / 123456 · 在线</small>
            </div>
            <div class="demo-card" @click="fillDemo('D001', '123456')">
              <strong>李医生 · 内分泌</strong>
              <small>D001 / 123456 · 在线</small>
            </div>
            <div class="demo-card" @click="fillDemo('张医生', '123456')">
              <strong>张医生 · 呼吸科</strong>
              <small>姓名登录 / 123456 · 在线</small>
            </div>
          </div>
        </div>

        <div class="back-home" @click="$router.push('/')">← 返回角色选择</div>
      </div>
    </div>

    <div class="login-visual">
      <svg class="bg-pattern" viewBox="0 0 500 500" preserveAspectRatio="xMidYMid slice">
        <defs>
          <linearGradient id="grad1" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#10B981" stop-opacity="0.05"/>
            <stop offset="100%" stop-color="#34D399" stop-opacity="0.15"/>
          </linearGradient>
        </defs>
        <rect width="500" height="500" fill="url(#grad1)"/>
        <!-- 医学网格 -->
        <g stroke="#10B981" stroke-width="0.5" opacity="0.3">
          <line x1="0" y1="100" x2="500" y2="100"/>
          <line x1="0" y1="200" x2="500" y2="200"/>
          <line x1="0" y1="300" x2="500" y2="300"/>
          <line x1="0" y1="400" x2="500" y2="400"/>
          <line x1="100" y1="0" x2="100" y2="500"/>
          <line x1="200" y1="0" x2="200" y2="500"/>
          <line x1="300" y1="0" x2="300" y2="500"/>
          <line x1="400" y1="0" x2="400" y2="500"/>
        </g>
      </svg>

      <div class="visual-content">
        <div class="illu-card">
          <div class="illu-float">
            <img :src="doctorGif" class="illu-img person-anim" alt="医疗团队手绘动画"/>
          </div>
          <div class="illu-badge">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="M12 4v16M4 12h16" stroke="white" stroke-width="3" stroke-linecap="round"/>
            </svg>
          </div>
        </div>

        <div class="info-cards">
          <div class="info-card">
            <svg viewBox="0 0 40 40"><rect x="4" y="4" width="32" height="32" rx="8" fill="#10B981" opacity="0.1"/><path d="M13 20h14M20 13v14" stroke="#10B981" stroke-width="2.5" stroke-linecap="round"/></svg>
            <div><strong>AI 辅助诊断</strong><small>智能分析病历</small></div>
          </div>
          <div class="info-card">
            <svg viewBox="0 0 40 40"><rect x="4" y="4" width="32" height="32" rx="8" fill="#7A5AF8" opacity="0.1"/><circle cx="20" cy="20" r="10" stroke="#7A5AF8" stroke-width="2"/><path d="M20 15v5l3 2" stroke="#7A5AF8" stroke-width="2" stroke-linecap="round"/></svg>
            <div><strong>在线接诊</strong><small>实时问诊处理</small></div>
          </div>
          <div class="info-card">
            <svg viewBox="0 0 40 40"><rect x="4" y="4" width="32" height="32" rx="8" fill="#E53935" opacity="0.1"/><path d="M14 20l4 4 8-8" stroke="#E53935" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <div><strong>电子签名</strong><small>病历权威发布</small></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { doctorLogin } from '@/api/auth'
import doctorGif from '@/assets/login-doctor.gif'
import AppLogo from '@/components/AppLogo.vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = ref({ account: '', password: '' })
const rules = {
  account: [{ required: true, message: '请输入工号或姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const fillDemo = (a, p) => { form.value.account = a; form.value.password = p }

/**
 * 医生登录：调用后端 /doctors/login 真实校验，
 * 成功后保存 JWT 与用户信息（后端 user 不含角色字段，前端按登录端补 role=DOCTOR）
 */
const handleLogin = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await doctorLogin(form.value.account, form.value.password)
    userStore.setSession(
      { ...data.user, role: 'DOCTOR' },
      data.token
    )
    ElMessage.success('登录成功')
    router.push('/doctor')
  } catch {
    // 401/业务错误已由请求拦截器统一提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; }
.login-form-area { flex: 1; display: flex; align-items: center; justify-content: center; padding: 48px; background: white; }
.form-container { width: 100%; max-width: 400px; }

.mini-logo { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #64748b; margin-bottom: 20px; }
.form-header h1 { font-size: 28px; font-weight: 700; margin: 0 0 6px; color: #1a1a2e; }
.form-header p { font-size: 14px; color: #94a3b8; margin: 0 0 32px; }

.login-btn { width: 100%; height: 48px; border-radius: 12px; font-size: 16px; font-weight: 600; background: linear-gradient(135deg, #10B981, #34D399); border: none; }
.login-btn:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(16, 185, 129, 0.4); }
.login-form :deep(.el-input__wrapper) { border-radius: 10px; }

.demo-account { margin-top: 24px; padding: 16px; border-radius: 12px; background: #f0fdf4; border: 1px dashed #86efac; }
.demo-title { font-size: 12px; color: #166534; margin-bottom: 12px; }
.demo-cards { display: flex; gap: 8px; }
.demo-card { flex: 1; padding: 10px; border-radius: 8px; background: white; border: 1px solid #dcfce7; cursor: pointer; transition: all 0.2s; display: flex; flex-direction: column; gap: 2px; }
.demo-card:hover { border-color: #10B981; background: #f0fdf4; }
.demo-card strong { font-size: 12px; color: #1a1a2e; }
.demo-card small { font-size: 11px; color: #64748b; }

.back-home { margin-top: 24px; text-align: center; font-size: 13px; color: #94a3b8; cursor: pointer; }
.back-home:hover { color: #10B981; }

/* 右侧视觉 */
.login-visual {
  flex: 1; position: relative; overflow: hidden;
  background: linear-gradient(135deg, #ECFDF5 0%, #D1FAE5 100%);
  display: flex; align-items: center; justify-content: center;
}
.bg-pattern { position: absolute; inset: 0; width: 100%; height: 100%; }
.visual-content { position: relative; z-index: 2; padding: 40px; max-width: 450px; display: flex; flex-direction: column; gap: 32px; }

/* 插画卡片 + 人物 1.5s 入场动画 */
.illu-card {
  position: relative; width: 100%; aspect-ratio: 1 / 1;
  background: #ffffff; border-radius: 28px; padding: 14px;
  box-shadow: 0 24px 60px rgba(16, 185, 129, 0.22);
}
.illu-float { width: 100%; height: 100%; animation: floatY 4.5s ease-in-out 1.5s infinite; }
.illu-img {
  width: 100%; height: 100%; object-fit: contain; border-radius: 18px; display: block;
  animation: personIn 1.5s cubic-bezier(0.22, 1.15, 0.36, 1) both;
}
.illu-badge {
  position: absolute; top: -18px; right: -14px; width: 58px; height: 58px;
  border-radius: 18px; background: linear-gradient(135deg, #10B981, #34D399);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 10px 24px rgba(16, 185, 129, 0.45);
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

.info-cards { display: flex; flex-direction: column; gap: 12px; }
.info-card {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 18px; background: white; border-radius: 14px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.05);
}
.info-card svg { width: 40px; height: 40px; flex-shrink: 0; }
.info-card strong { font-size: 14px; color: #1a1a2e; display: block; }
.info-card small { font-size: 12px; color: #64748b; }

@media (max-width: 900px) { .login-visual { display: none; } }
</style>
