<template>
  <div class="profile-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2>个人信息</h2>
      <p>管理你的头像、姓名、职称与联系方式</p>
    </div>

    <div class="profile-grid">
      <!-- 左：头像卡片 -->
      <div class="card avatar-card" v-loading="loading">
        <el-avatar :size="96" :src="form.avatar || undefined" class="big-avatar">
          {{ avatarFallback }}
        </el-avatar>
        <div class="avatar-name">{{ form.realName || '医生用户' }}</div>
        <div class="avatar-sub">@{{ form.employeeNo }}</div>
        <div class="avatar-chip" v-if="form.department">
          {{ form.department }}<template v-if="form.title"> · {{ form.title }}</template>
        </div>
        <button class="btn-change" :disabled="uploading" @click="triggerPick">
          {{ uploading ? '上传中…' : '更换头像' }}
        </button>
        <!-- 原生文件选择：经 request 实例发送，自动携带 JWT -->
        <input
          ref="fileInputRef"
          type="file"
          accept="image/png,image/jpeg,image/webp,image/gif"
          hidden
          @change="onPickFile"
        />
        <p class="avatar-tip">支持 jpg / png / webp / gif，大小不超过 5MB</p>
      </div>

      <!-- 右：资料表单 -->
      <div class="card form-card">
        <h3>基本资料</h3>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="92px"
          label-position="right"
          v-loading="loading"
        >
          <el-form-item label="工号">
            <el-input v-model="form.employeeNo" disabled />
          </el-form-item>

          <el-form-item label="科室">
            <el-input v-model="form.department" disabled />
            <div class="field-hint">科室由管理员分配，如需调整请联系管理员</div>
          </el-form-item>

          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="form.realName" maxlength="50" show-word-limit placeholder="请输入真实姓名" />
          </el-form-item>

          <el-form-item label="性别">
            <el-radio-group v-model="form.gender">
              <el-radio value="男">男</el-radio>
              <el-radio value="女">女</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="职称">
            <el-input v-model="form.title" maxlength="50" placeholder="如：主任医师 / 副主任医师" />
          </el-form-item>

          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="请输入 11 位手机号" />
          </el-form-item>

          <el-form-item label="个人简介">
            <el-input
              v-model="form.bio"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="擅长领域、从医经历、学术成就等，将展示给患者"
            />
          </el-form-item>

          <el-form-item label="账号状态">
            <el-tag :type="form.status === 'ACTIVE' ? 'success' : 'danger'" disable-transitions>
              {{ form.status === 'ACTIVE' ? '正常' : '已禁用' }}
            </el-tag>
          </el-form-item>

          <el-form-item>
            <button class="btn-save" :disabled="saving" @click="onSave">
              {{ saving ? '保存中…' : '保存修改' }}
            </button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 医生端「个人信息」页
 * 数据与操作全部对接后端真实接口：
 *   GET    /doctors/me           拉取资料
 *   PUT    /doctors/me           修改姓名/手机号/性别/职称/简介
 *   POST   /doctors/me/avatar    上传头像（multipart，字段 file）
 */
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getMyProfile, updateMyProfile, uploadAvatar } from '@/api/doctor'

const userStore = useUserStore()

/** 表单引用（校验用）与隐藏文件选择框引用 */
const formRef = ref(null)
const fileInputRef = ref(null)

/** 页面/保存/上传 三种加载态 */
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)

/** 资料表单（与后端 /doctors/me 返回字段对齐） */
const form = ref({
  employeeNo: '',
  realName: '',
  gender: '',
  title: '',
  phone: '',
  department: '',
  bio: '',
  avatar: '',
  status: 'ACTIVE',
})

/** 无头像时的占位文字：姓名首字（与患者端口径一致） */
const avatarFallback = computed(() => {
  const name = form.value.realName || userStore.user?.realName || '医'
  return name.charAt(0)
})

/** 表单校验规则 */
const rules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' },
  ],
}

/** 拉取最新资料 */
async function loadProfile() {
  loading.value = true
  try {
    const data = await getMyProfile()
    form.value = {
      employeeNo: data.employeeNo || '',
      realName: data.realName || '',
      gender: data.gender || '',
      title: data.title || '',
      phone: data.phone || '',
      department: data.department || '',
      bio: data.bio || '',
      avatar: data.avatar || '',
      status: data.status || 'ACTIVE',
    }
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
}

/** 保存资料：校验通过后提交，并同步本地登录态（顶栏姓名/头像立即刷新） */
async function onSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const data = await updateMyProfile({
      realName: form.value.realName,
      phone: form.value.phone,
      gender: form.value.gender,
      title: form.value.title,
      bio: form.value.bio,
    })
    form.value.avatar = data.avatar || form.value.avatar
    form.value.status = data.status || form.value.status
    syncStore({
      realName: data.realName,
      gender: data.gender,
      title: data.title,
      phone: data.phone,
      bio: data.bio,
      avatar: data.avatar,
    })
    ElMessage.success('资料保存成功')
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    saving.value = false
  }
}

/** 点击「更换头像」→ 打开系统文件选择器 */
function triggerPick() {
  fileInputRef.value?.click()
}

/** 选择文件后：前端先校验类型与大小，再上传 */
async function onPickFile(event) {
  const file = event.target.files?.[0]
  // 允许重新选择同一个文件（否则 change 不再触发）
  event.target.value = ''
  if (!file) return

  const allowed = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
  if (!allowed.includes(file.type)) {
    ElMessage.warning('仅支持 jpg / png / webp / gif 格式的图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像图片大小不能超过 5MB')
    return
  }

  uploading.value = true
  try {
    const data = await uploadAvatar(file)
    form.value.avatar = data.avatar
    syncStore({ avatar: data.avatar, realName: form.value.realName })
    ElMessage.success('头像更新成功')
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    uploading.value = false
  }
}

/**
 * 将后端最新字段合并进登录态并持久化到 localStorage
 * （直接改 store 属性不会写回 localStorage，刷新会丢，故走 setAuth）
 */
function syncStore(patch) {
  userStore.setAuth(userStore.token, { ...userStore.user, ...patch })
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { padding: 24px; }

.page-head h2 { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 6px; }
.page-head p { font-size: 13px; color: #64748b; margin: 0 0 20px; }

.profile-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
  align-items: start;
}

.card {
  background: #fff;
  border-radius: 18px;
  padding: 26px 24px;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.05);
}

/* 头像卡片 */
.avatar-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.big-avatar {
  background: linear-gradient(135deg, #1E7AE6, #4A9EF5);
  color: #fff;
  font-size: 38px;
  font-weight: 600;
  box-shadow: 0 8px 24px rgba(30, 122, 230, 0.22);
}
.avatar-name { font-size: 18px; font-weight: 700; color: #1a1a2e; margin-top: 16px; }
.avatar-sub { font-size: 13px; color: #94A3B8; margin-top: 4px; }
.avatar-chip {
  margin-top: 10px;
  padding: 4px 14px;
  border-radius: 999px;
  background: #E3F2FF;
  color: #1E7AE6;
  font-size: 12px;
  font-weight: 600;
}

.btn-change {
  margin-top: 18px;
  padding: 9px 26px;
  border-radius: 10px;
  border: 1px solid #1E7AE6;
  background: #fff;
  color: #1E7AE6;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-change:hover:not(:disabled) { background: #E3F2FF; }
.btn-change:disabled { opacity: 0.6; cursor: not-allowed; }

.avatar-tip { font-size: 12px; color: #94A3B8; margin: 12px 0 0; line-height: 1.6; }

/* 资料表单 */
.form-card h3 { font-size: 16px; font-weight: 700; color: #1a1a2e; margin: 0 0 20px; }
.field-hint { font-size: 12px; color: #94A3B8; line-height: 1.4; margin-top: 2px; }

.btn-save {
  padding: 10px 34px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #1E7AE6, #4A9EF5);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 6px 18px rgba(30, 122, 230, 0.25);
}
.btn-save:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 10px 24px rgba(30, 122, 230, 0.32); }
.btn-save:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 1024px) {
  .profile-grid { grid-template-columns: 1fr; }
}
</style>
