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
              <a class="forgot-link" @click.prevent="openReset">忘记密码？</a>
            </div>
          </el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            登录
          </el-button>
          <div class="register-entry">
            还没有账号？
            <a class="register-link" @click.prevent="openRegister">立即注册</a>
          </div>
        </el-form>

        <!-- 忘记密码弹窗：第一步验证身份（用户名+身份证+手机号→获取验证码），第二步设置新密码 -->
        <el-dialog
          v-model="resetVisible"
          title="重置密码"
          width="420px"
          :close-on-click-modal="false"
          class="reset-dialog"
          align-center
        >
          <div class="reset-steps">
            <div class="reset-step" :class="{ active: resetStep === 1 }">
              <span class="step-dot">1</span>
              <span class="step-text">验证身份</span>
            </div>
            <span class="step-line"></span>
            <div class="reset-step" :class="{ active: resetStep === 2 }">
              <span class="step-dot">2</span>
              <span class="step-text">设置新密码</span>
            </div>
          </div>

          <!-- 步骤 1：身份验证 -->
          <el-form
            v-if="resetStep === 1"
            :model="resetForm"
            :rules="resetRules1"
            ref="resetFormRef1"
            label-position="top"
            class="reset-form"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="resetForm.username" placeholder="请输入注册用户名" size="large">
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="身份证号" prop="idCard">
              <el-input v-model="resetForm.idCard" placeholder="请输入注册时填写的身份证号" size="large">
                <template #prefix><el-icon><Postcard /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="resetForm.phone" placeholder="请输入注册时填写的手机号" size="large">
                <template #prefix><el-icon><Iphone /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-button
              type="primary" size="large" class="reset-btn" :loading="sendingCode"
              @click="handleSendCode"
            >
              获取验证码
            </el-button>
            <div v-if="sentCode" class="code-hint">
              <svg viewBox="0 0 16 16"><path d="M8 1l6 2v4c0 4-2.5 6.5-6 8-3.5-1.5-6-4-6-8V3l6-2z" stroke="currentColor" stroke-width="1.4" fill="none" stroke-linejoin="round"/><path d="M5.5 8l2 2 3-3.5" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
              演示环境未接入短信，验证码：<strong>{{ sentCode }}</strong>（5 分钟内有效）
            </div>
          </el-form>

          <!-- 步骤 2：设置新密码 -->
          <el-form
            v-else
            :model="resetForm"
            :rules="resetRules2"
            ref="resetFormRef2"
            label-position="top"
            class="reset-form"
          >
            <el-form-item label="验证码" prop="code">
              <el-input v-model="resetForm.code" placeholder="请输入收到的验证码" size="large" maxlength="6">
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
            </el-form-item>
            <div v-if="sentCode" class="code-hint code-hint-step2">
              演示环境未接入短信，验证码：<strong>{{ sentCode }}</strong>
            </div>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="resetForm.newPassword" type="password" placeholder="请输入新密码（6-64 位）" size="large" show-password>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="resetForm.confirmPassword" type="password" placeholder="请再次输入新密码" size="large" show-password>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-button
              type="primary" size="large" class="reset-btn" :loading="resetting"
              @click="handleResetPassword"
            >
              重置密码
            </el-button>
            <div class="reset-back">
              <el-link type="primary" :underline="false" @click="backToStep1">← 返回修改身份信息</el-link>
            </div>
          </el-form>
        </el-dialog>

        <!-- 注册弹窗 -->
        <el-dialog
          v-model="registerVisible"
          title="注册账号"
          width="440px"
          :close-on-click-modal="false"
          class="reset-dialog"
          align-center
        >
          <el-form
            :model="registerForm"
            :rules="registerRules"
            ref="registerFormRef"
            label-position="top"
            class="reset-form"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="registerForm.username" placeholder="2-20 位，登录时使用" size="large" maxlength="20">
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="registerForm.realName" placeholder="请输入真实姓名" size="large">
                <template #prefix><el-icon><Avatar /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="registerForm.gender">
                <el-radio value="男">男</el-radio>
                <el-radio value="女">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="registerForm.phone" placeholder="请输入 11 位手机号" size="large" maxlength="11">
                <template #prefix><el-icon><Iphone /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="身份证号" prop="idCard">
              <el-input v-model="registerForm.idCard" placeholder="请输入 18 位身份证号" size="large" maxlength="18">
                <template #prefix><el-icon><Postcard /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="6-64 位" size="large" show-password>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" size="large" show-password>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-button
              type="primary" size="large" class="reset-btn" :loading="registering"
              @click="handleRegister"
            >
              注册并登录
            </el-button>
            <div class="reset-back">
              <el-link type="primary" :underline="false" @click="registerVisible = false">已有账号？返回登录</el-link>
            </div>
          </el-form>
        </el-dialog>

        <div class="demo-account">
          <div class="demo-title">
            <svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="7" stroke="currentColor" stroke-width="2"/><path d="M8 4v4l2.5 1.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            演示账号（点击自动填充）
          </div>
          <div class="demo-cards">
            <div class="demo-card" @click="fillDemo('wangwu', '123456')">
              <strong>王五</strong>
              <small>wangwu / 123456</small>
            </div>
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
import { User, Lock, Postcard, Iphone, Key, Avatar } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { patientLogin, patientRegister, sendPasswordResetCode, resetPassword } from '@/api/auth'
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

// ===================== 忘记密码 =====================
/** 弹窗可见性 */
const resetVisible = ref(false)
/** 当前步骤：1 验证身份 → 2 设置新密码 */
const resetStep = ref(1)
/** 获取验证码 loading */
const sendingCode = ref(false)
/** 重置密码 loading */
const resetting = ref(false)
/** 返回的验证码（演示环境透出展示） */
const sentCode = ref('')

const resetFormRef1 = ref(null)
const resetFormRef2 = ref(null)
const resetForm = ref({
  username: '', idCard: '', phone: '',
  code: '', newPassword: '', confirmPassword: ''
})

/** 步骤 1 校验规则 */
const resetRules1 = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  idCard: [{ required: true, message: '请输入身份证号', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }]
}
/** 步骤 2 校验规则 */
const resetRules2 = {
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度需为 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== resetForm.value.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/** 打开忘记密码弹窗 */
function openReset() {
  resetStep.value = 1
  sentCode.value = ''
  resetForm.value = { username: '', idCard: '', phone: '', code: '', newPassword: '', confirmPassword: '' }
  resetVisible.value = true
}

/** 步骤 1：获取验证码（后端校验 用户名+身份证+手机号 三要素） */
const handleSendCode = async () => {
  await resetFormRef1.value.validate()
  sendingCode.value = true
  try {
    const data = await sendPasswordResetCode(
      resetForm.value.username, resetForm.value.idCard, resetForm.value.phone
    )
    sentCode.value = data.code
    resetStep.value = 2 // 身份验证通过，自动进入设置新密码
    ElMessage.success('验证码已发送')
  } catch (e) {
    // 业务错误（身份不匹配等）已由请求拦截器统一提示
  } finally {
    sendingCode.value = false
  }
}

/** 步骤 2：校验验证码并重置密码 */
const handleResetPassword = async () => {
  await resetFormRef2.value.validate()
  resetting.value = true
  try {
    await resetPassword(
      resetForm.value.username, resetForm.value.code, resetForm.value.newPassword
    )
    ElMessage.success('密码重置成功，请使用新密码登录')
    resetVisible.value = false
    form.value.password = ''
  } catch (e) {
    // 验证码错误等已由请求拦截器统一提示
  } finally {
    resetting.value = false
  }
}

/** 返回步骤 1 修改身份信息 */
function backToStep1() {
  resetStep.value = 1
}

// ===================== 注册 =====================
/** 注册弹窗可见性 */
const registerVisible = ref(false)
/** 注册提交 loading */
const registering = ref(false)
const registerFormRef = ref(null)
const registerForm = ref({
  username: '', realName: '', gender: '男', phone: '', idCard: '', password: '', confirmPassword: ''
})

/** 注册表单校验规则 */
const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度需为 2-20 位', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' }
  ],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^\d{17}[\dXx]$/, message: '请输入正确的 18 位身份证号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需为 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.value.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/** 打开注册弹窗 */
function openRegister() {
  registerForm.value = {
    username: '', realName: '', gender: '男', phone: '', idCard: '', password: '', confirmPassword: ''
  }
  registerVisible.value = true
}

/** 注册：成功后自动登录进入患者端 */
const handleRegister = async () => {
  await registerFormRef.value.validate()
  registering.value = true
  try {
    const data = await patientRegister({
      username: registerForm.value.username,
      password: registerForm.value.password,
      realName: registerForm.value.realName,
      gender: registerForm.value.gender,
      phone: registerForm.value.phone,
      idCard: registerForm.value.idCard
    })
    userStore.setSession(
      { ...data.user, role: 'PATIENT', username: registerForm.value.username },
      data.token
    )
    ElMessage.success('注册成功，已自动登录')
    registerVisible.value = false
    router.push('/patient')
  } catch (e) {
    // 唯一性冲突等业务错误已由请求拦截器统一提示
  } finally {
    registering.value = false
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

/* 注册入口 */
.register-entry { margin-top: 16px; text-align: center; font-size: 13.5px; color: #94a3b8; }
.register-link { color: #1E7AE6; font-weight: 600; cursor: pointer; margin-left: 2px; }
.register-link:hover { text-decoration: underline; }

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

/* 忘记密码弹窗 */
.reset-steps { display: flex; align-items: center; gap: 10px; margin: 2px 0 20px; }
.reset-step { display: flex; align-items: center; gap: 7px; color: #94a3b8; font-size: 13px; }
.reset-step .step-dot {
  width: 22px; height: 22px; border-radius: 50%; background: #EDF2F8; color: #8492A6;
  display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700;
}
.reset-step.active { color: #1E7AE6; font-weight: 600; }
.reset-step.active .step-dot { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; box-shadow: 0 4px 10px rgba(30,122,230,0.3); }
.step-line { flex: 1; height: 1.5px; background: #E2E8F0; border-radius: 2px; }
.reset-form :deep(.el-input__wrapper) { border-radius: 10px; }
.reset-form :deep(.el-form-item__label) { font-size: 13px; color: #475569; font-weight: 600; }
.reset-btn { width: 100%; height: 44px; border-radius: 10px; font-size: 15px; font-weight: 600; margin-top: 4px; background: linear-gradient(135deg, #1E7AE6, #4A9EF5); border: none; }
.reset-btn:hover { transform: translateY(-1px); box-shadow: 0 8px 20px rgba(30, 122, 230, 0.35); }
.code-hint {
  margin-top: 14px; padding: 10px 14px; border-radius: 10px; font-size: 13px; color: #475569;
  background: #F0F7FF; border: 1px dashed #9CC5F3; display: flex; align-items: center; gap: 8px;
}
.code-hint svg { width: 16px; height: 16px; color: #1E7AE6; flex-shrink: 0; }
.code-hint strong { color: #1E7AE6; font-size: 16px; letter-spacing: 3px; }
.code-hint-step2 { margin-top: 0; margin-bottom: 16px; }
.reset-back { margin-top: 12px; text-align: center; }
.reset-dialog :deep(.el-dialog__title) { font-size: 17px; font-weight: 700; color: #1a1a2e; }

@media (max-width: 900px) {
  .login-visual { display: none; }
  .login-form-area { flex: 1; }
}
</style>
