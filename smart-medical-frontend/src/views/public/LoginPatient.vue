<template>
  <div class="login-page patient-login">
    <!-- 左侧插画区 -->
    <div class="login-visual">
      <div class="visual-bg">
        <svg class="wave" viewBox="0 0 500 500" preserveAspectRatio="xMidYMid slice">
          <path d="M0,300 Q125,250 250,300 T500,300 L500,500 L0,500 Z" fill="#1E7AE6" opacity="0.08"/>
          <path d="M0,350 Q125,300 250,350 T500,350 L500,500 L0,500 Z" fill="#1E7AE6" opacity="0.12"/>
          <path d="M0,400 Q125,360 250,400 T500,400 L500,500 L0,500 Z" fill="#1E7AE6" opacity="0.18"/>
        </svg>
      </div>
      <div class="visual-content">
        <div class="illu-card">
          <div class="illu-ring"></div>
          <div class="illu-float">
            <img :src="patientImg" class="illu-img person-anim" alt="智能问诊场景插画"/>
          </div>
          <div class="illu-badge">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="M12 4v16M4 12h16" stroke="white" stroke-width="3" stroke-linecap="round"/>
            </svg>
          </div>
        </div>
        <div class="visual-text">
          <h2 class="brand-font">小林智疗 · 患者端</h2>
          <p>AI 辅助问诊，让就医更简单</p>
          <ul class="feature-list">
            <li><svg viewBox="0 0 16 16"><path d="M3 8l3 3 7-7" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg> 在线预约挂号</li>
            <li><svg viewBox="0 0 16 16"><path d="M3 8l3 3 7-7" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg> AI 智能解读报告</li>
            <li><svg viewBox="0 0 16 16"><path d="M3 8l3 3 7-7" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg> 电子病历随时查</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 右侧表单 -->
    <div class="login-form-area">
      <div class="form-container">
        <div class="form-header">
          <div class="mini-logo">
            <AppLogo :size="34"/>
            <span class="brand-font">小林智疗</span>
          </div>
          <h1>欢迎回来</h1>
          <p>患者登录 · Patient Login</p>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" class="login-form" @submit.prevent="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large">
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password>
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <div class="form-options">
              <el-checkbox v-model="remember">记住我</el-checkbox>
              <a class="forgot-link" @click.prevent>忘记密码？</a>
            </div>
          </el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form>

        <div class="demo-account">
          <div class="demo-title">
            <svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="7" stroke="currentColor" stroke-width="2"/><path d="M8 4v4l2.5 1.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            演示账号（点击自动填充）
          </div>
          <div class="demo-cards">
            <div class="demo-card" @click="fillDemo('zhangsan', '123456')">
              <strong>张三</strong>
              <small>zhangsan / 123456</small>
            </div>
            <div class="demo-card" @click="fillDemo('lisi', '123456')">
              <strong>李四</strong>
              <small>lisi / 123456</small>
            </div>
          </div>
        </div>

        <div class="back-home" @click="$router.push('/')">
          <svg viewBox="0 0 16 16"><path d="M10 12l-4-4 4-4" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
          返回角色选择
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
import { patientLogin } from '@/api/auth'
import patientImg from '@/assets/login-patient.png'
import AppLogo from '@/components/AppLogo.vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const remember = ref(false)

const form = ref({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const fillDemo = (u, p) => { form.value.username = u; form.value.password = p }

/**
 * 患者登录：调用后端 /patients/login 真实校验，
 * 成功后保存 JWT 与用户信息（后端 user 不含角色字段，前端按登录端补 role=PATIENT）
 */
const handleLogin = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await patientLogin(form.value.username, form.value.password)
    userStore.setSession(
      { ...data.user, role: 'PATIENT', username: form.value.username },
      data.token
    )
    ElMessage.success('登录成功')
    router.push('/patient')
  } catch (e) {
    // 401/业务错误已由请求拦截器统一提示，这里无需重复弹窗
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh; display: flex;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* 左侧视觉 */
.login-visual {
  flex: 1; position: relative; overflow: hidden;
  background: linear-gradient(135deg, #E8F3FF 0%, #D6E8FF 100%);
  display: flex; align-items: center; justify-content: center;
}
.visual-bg { position: absolute; inset: 0; }
.visual-bg .wave { position: absolute; bottom: 0; left: 0; width: 100%; }
.visual-content { position: relative; z-index: 2; padding: 40px; max-width: 460px; width: 100%; }

/* 插画卡片 + 人物 1.5s 入场动画 */
.illu-card {
  position: relative; width: 100%; aspect-ratio: 1 / 1;
  background: #ffffff; border-radius: 28px; padding: 14px;
  box-shadow: 0 24px 60px rgba(30, 122, 230, 0.22);
}
.illu-float { width: 100%; height: 100%; animation: floatY 4.5s ease-in-out 1.5s infinite; }
.illu-img {
  width: 100%; height: 100%; object-fit: contain; border-radius: 18px; display: block;
  animation: personIn 1.5s cubic-bezier(0.22, 1.15, 0.36, 1) both;
}
.illu-badge {
  position: absolute; top: -18px; right: -14px; width: 58px; height: 58px;
  border-radius: 18px; background: linear-gradient(135deg, #1E7AE6, #4A9EF5);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 10px 24px rgba(30, 122, 230, 0.45);
  animation: badgePop 0.6s cubic-bezier(0.22, 1.4, 0.4, 1) 1s both;
}
.illu-badge svg { width: 28px; height: 28px; }
.illu-ring {
  position: absolute; left: 50%; bottom: 18px; transform: translateX(-50%);
  width: 62%; height: 26px; background: radial-gradient(ellipse at center, rgba(30,122,230,0.22), transparent 70%);
  animation: ringIn 1.5s ease-out both;
}
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
@keyframes ringIn {
  0%   { opacity: 0; transform: translateX(-50%) scaleX(0.4); }
  100% { opacity: 1; transform: translateX(-50%) scaleX(1); }
}
.visual-text { margin-top: 32px; }
.visual-text h2 { font-size: 28px; font-weight: 700; color: #1a1a2e; margin: 0 0 8px; }
.visual-text p { font-size: 15px; color: #475569; margin: 0 0 20px; }
.feature-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.feature-list li {
  display: flex; align-items: center; gap: 10px;
  font-size: 14px; color: #334155;
}
.feature-list li svg { width: 16px; height: 16px; color: #10B981; flex-shrink: 0; }

/* 右侧表单 */
.login-form-area { flex: 1.1; display: flex; align-items: center; justify-content: center; padding: 48px; background: white; }
.form-container { width: 100%; max-width: 400px; }

.form-header { margin-bottom: 32px; }
.mini-logo { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #64748b; margin-bottom: 20px; }
.form-header h1 { font-size: 28px; font-weight: 700; margin: 0 0 6px; color: #1a1a2e; }
.form-header p { font-size: 14px; color: #94a3b8; margin: 0; }

.login-form :deep(.el-input__wrapper) { border-radius: 10px; }
.login-btn { width: 100%; height: 48px; border-radius: 12px; font-size: 16px; font-weight: 600; background: linear-gradient(135deg, #1E7AE6, #4A9EF5); border: none; }
.login-btn:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(30, 122, 230, 0.4); }

.form-options { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.forgot-link { color: #1E7AE6; font-size: 14px; cursor: pointer; }
.forgot-link:hover { text-decoration: underline; }

.demo-account {
  margin-top: 24px; padding: 16px; border-radius: 12px; background: #f8fafc;
  border: 1px dashed #cbd5e1;
}
.demo-title { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #64748b; margin-bottom: 12px; }
.demo-title svg { width: 14px; height: 14px; }
.demo-cards { display: flex; gap: 8px; }
.demo-card {
  flex: 1; padding: 10px; border-radius: 8px; background: white; cursor: pointer;
  border: 1px solid #e2e8f0; transition: all 0.2s;
  display: flex; flex-direction: column; gap: 2px;
}
.demo-card:hover { border-color: #1E7AE6; background: #f0f7ff; }
.demo-card strong { font-size: 13px; color: #1a1a2e; }
.demo-card small { font-size: 11px; color: #94a3b8; }

.back-home {
  margin-top: 24px; display: flex; align-items: center; justify-content: center; gap: 6px;
  font-size: 13px; color: #94a3b8; cursor: pointer; transition: color 0.2s;
}
.back-home:hover { color: #1E7AE6; }
.back-home svg { width: 14px; height: 14px; }

@media (max-width: 900px) {
  .login-visual { display: none; }
  .login-form-area { flex: 1; }
}
</style>
