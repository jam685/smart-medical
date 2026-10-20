<script setup>
/**
 * 患者端「发起问诊」页组件
 * 职责：引导患者 ①选择自己的挂号主治医生 ②填写主诉与病情描述 ③提交创建问诊。
 * 业务规则：患者只能对自己挂号成功（挂号状态非 CANCELLED）的主治医生发起在线问诊，
 * 医生列表由后端根据挂号记录派生（GET /online-consults/my-doctors）。
 * 数据来源（真实接口）：
 *   GET  /online-consults/my-doctors  我的挂号主治医生列表（科室由医生所属科室去重派生）
 *   POST /online-consults             创建问诊（写 sm_online_consult_record + 系统消息）
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check } from '@element-plus/icons-vue'
import { fetchMyDoctors, createConsult } from '@/api/onlineconsult'

/** 路由实例：用于返回列表、提交成功后跳转聊天页 */
const router = useRouter()

/** 医生加载状态 */
const doctorLoading = ref(false)
/** 提交按钮 loading（防重复提交） */
const submitting = ref(false)

/**
 * 后端返回的「我的挂号主治医生」原始数组
 * 单项字段：{id, employeeNo, realName, gender, title, department, avatar, bio, onlineStatus}
 */
const allDoctors = ref([])

/** 当前选中的科室（用于筛选医生），'全部' 表示不筛选 */
const selectedDept = ref('全部')
/** 患者已选医生对象（未选为 null） */
const selectedDoctor = ref(null)

/** 问诊表单：主诉 + 病情描述 */
const form = ref({ chiefComplaint: '', description: '' })

/**
 * 科室列表（响应式）：对主治医生按 department 去重派生，最前加「全部」
 */
const departments = computed(() => {
  const set = new Set()
  allDoctors.value.forEach(d => { if (d.department) set.add(d.department) })
  return ['全部', ...set]
})

/**
 * 当前科室下应展示的医生（响应式）
 */
const doctorsOfDept = computed(() =>
  selectedDept.value === '全部'
    ? allDoctors.value
    : allDoctors.value.filter(d => d.department === selectedDept.value)
)

/**
 * 医生头像背景色（按性别区分）
 * @param {string} gender 性别
 * @returns {string} CSS 背景色
 */
function avatarBg(gender) {
  if (gender === '女') return 'linear-gradient(135deg, #EC4899, #F472B6)'
  if (gender === '男') return 'linear-gradient(135deg, #1E7AE6, #4A9EF5)'
  return 'linear-gradient(135deg, #6B7280, #9CA3AF)'
}

/**
 * 取医生姓名首字（头像占位文字）
 * @param {string} name 医生姓名
 * @returns {string} 首字
 */
function initialOf(name) {
  return (name || '医').slice(0, 1)
}

/** 选择医生 */
function chooseDoctor(doc) { selectedDoctor.value = doc }

/** 返回问诊列表 */
function goBack() { router.push('/patient/online-consults') }

/**
 * 提交问诊：调用 POST /online-consults，成功后跳转聊天页。
 * 患者 ID 由后端从 JWT 解析，无需前端传。
 */
async function submit() {
  if (!selectedDoctor.value) {
    ElMessage.warning('请先选择一位您挂号的主治医生')
    return
  }
  if (!form.value.chiefComplaint.trim()) {
    ElMessage.warning('请填写主诉')
    return
  }
  submitting.value = true
  try {
    const payload = {
      doctorId: selectedDoctor.value.id,
      department: selectedDoctor.value.department,
      chiefComplaint: form.value.chiefComplaint.trim(),
      description: form.value.description.trim(),
    }
    const consult = await createConsult(payload)
    ElMessage.success('问诊已发起，等待医生接诊')
    router.push(`/patient/online-consults/${consult.id}`)
  } catch (e) {
    // 失败提示由请求拦截器统一处理（如医生不在线、非主治医生）
  } finally {
    submitting.value = false
  }
}

/** 页面加载时请求我的挂号主治医生（科室列表由其去重派生） */
onMounted(async () => {
  doctorLoading.value = true
  try {
    allDoctors.value = await fetchMyDoctors()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    doctorLoading.value = false
  }
})
</script>

<template>
  <div class="create-page">
    <!-- 顶部返回栏 -->
    <div class="top-bar">
      <el-button text class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>&nbsp;返回问诊列表
      </el-button>
      <h2 class="page-title brand-font">发起问诊</h2>
      <span class="top-placeholder"></span>
    </div>

    <!-- 选择医生 -->
    <p class="panel-hint">1. 选择您挂号的主治医生</p>
    <div v-if="departments.length > 1" class="dept-chips">
      <button
        v-for="d in departments" :key="d"
        :class="['dept-chip', { active: selectedDept === d }]"
        @click="selectedDept = d"
      >{{ d }}</button>
    </div>

    <div class="doctor-list" v-loading="doctorLoading">
      <div
        v-for="doc in doctorsOfDept" :key="doc.id"
        :class="['doctor-card', { selected: selectedDoctor?.id === doc.id }]"
        @click="chooseDoctor(doc)"
      >
        <span class="doc-avatar" :style="{ background: avatarBg(doc.gender) }">
          <img v-if="doc.avatar" :src="doc.avatar" :alt="doc.realName" class="doc-avatar-img" />
          <span v-else class="doc-avatar-text">{{ initialOf(doc.realName) }}</span>
          <i class="online-dot"></i>
        </span>
        <div class="doc-meta">
          <div class="doc-name-line">
            <strong>{{ doc.realName }}</strong>
            <span class="doc-title">{{ doc.title }}</span>
            <span class="doc-gender" v-if="doc.gender">{{ doc.gender }}</span>
          </div>
          <div class="doc-id-line">
            <span class="doc-id-tag">工号 {{ doc.employeeNo }}</span>
            <span class="doc-dept-tag">{{ doc.department }}</span>
            <span class="doc-online"><i class="online-dot-sm"></i>挂号主治医生</span>
          </div>
          <p class="doc-bio" v-if="doc.bio">{{ doc.bio }}</p>
          <p class="doc-bio empty" v-else>暂无简介</p>
        </div>
        <span v-if="selectedDoctor?.id === doc.id" class="doc-check"><el-icon><Check /></el-icon></span>
      </div>
      <div v-if="!doctorLoading && doctorsOfDept.length === 0" class="no-doctor">
        您还没有挂号成功的主治医生，无法发起在线问诊。<br/>
        请先到「挂号」页预约医生，挂号成功后再回来发起问诊。
      </div>
    </div>

    <!-- 填写问诊信息 -->
    <p class="panel-hint sub">2. 描述您的病情</p>
    <div class="form-card">
      <el-form :model="form" label-position="top">
        <el-form-item label="主诉（哪里不舒服？）" required>
          <el-input
            v-model="form.chiefComplaint"
            maxlength="100"
            show-word-limit
            placeholder="例如：头晕乏力三天"
          />
        </el-form-item>
        <el-form-item label="病情描述（可选）">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="请补充症状、持续时间、既往病史、用药情况等，便于医生更快了解您的情况"
          />
        </el-form-item>
      </el-form>
      <div class="form-footer">
        <el-button size="large" @click="goBack">取消</el-button>
        <el-button type="primary" size="large" :loading="submitting" @click="submit">提交问诊</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.create-page { padding: 4px 2px 40px; }

/* 顶部栏 */
.top-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.back-btn { font-size: 14px; color: #5A6B82; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0; }
.top-placeholder { width: 110px; }

.panel-hint { font-size: 15px; font-weight: 600; color: #2C3E50; margin: 0 0 14px; }
.panel-hint.sub { margin-top: 26px; }

/* 科室筛选 chips */
.dept-chips { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
.dept-chip {
  border: 1.5px solid #E2EAF4; background: #fff; color: #5A6B82; border-radius: 100px;
  padding: 6px 15px; font-size: 13px; cursor: pointer; transition: all 0.18s;
}
.dept-chip:hover { border-color: #BFD9F7; color: #1E7AE6; }
.dept-chip.active { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); border-color: transparent; color: #fff; }

/* 医生卡片 */
.doctor-list { display: flex; flex-direction: column; gap: 12px; }
.no-doctor { text-align: center; color: #A7B1BF; font-size: 13px; padding: 26px 0; background: #FAFCFE; border: 1px dashed #E2EAF4; border-radius: 12px; }
.doctor-card {
  position: relative; display: flex; align-items: center; gap: 16px; background: #fff;
  border: 1.5px solid #ECF1F7; border-radius: 14px; padding: 16px 20px; cursor: pointer; transition: all 0.22s;
}
.doctor-card:hover { border-color: #BFD9F7; box-shadow: 0 8px 20px rgba(30,122,230,0.09); transform: translateY(-2px); }
.doctor-card.selected { border-color: #1E7AE6; background: #F7FBFF; box-shadow: 0 8px 22px rgba(30,122,230,0.15); }
.doc-avatar {
  position: relative; width: 60px; height: 60px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; overflow: hidden; border: 2px solid #fff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
}
.doc-avatar-img { width: 100%; height: 100%; object-fit: cover; }
.doc-avatar-text { color: #fff; font-size: 24px; font-weight: 700; }
.online-dot { position: absolute; right: -1px; bottom: -1px; width: 14px; height: 14px; border-radius: 50%; background: #22C55E; border: 2px solid #fff; }
.doc-meta { flex: 1; min-width: 0; }
.doc-name-line { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.doc-name-line strong { font-size: 17px; color: #1A2B45; font-weight: 700; }
.doc-title { font-size: 12px; color: #1E7AE6; background: #E3F2FF; padding: 2px 9px; border-radius: 100px; font-weight: 600; }
.doc-gender { font-size: 12px; color: #6B7280; background: #F3F4F6; padding: 2px 8px; border-radius: 100px; }
.doc-id-line { display: flex; align-items: center; gap: 8px; margin: 6px 0; flex-wrap: wrap; }
.doc-id-tag { font-size: 12px; color: #4B5563; background: #F9FAFB; padding: 2px 8px; border-radius: 6px; font-family: 'SF Mono','Consolas',monospace; }
.doc-dept-tag { font-size: 12px; color: #1E7AE6; }
.doc-online { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #10B981; }
.online-dot-sm { width: 8px; height: 8px; border-radius: 50%; background: #22C55E; }
.doc-bio { font-size: 12.5px; color: #6B7280; margin: 0; line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.doc-bio.empty { color: #C5CBD6; font-style: italic; }
.doc-check {
  position: absolute; top: 14px; right: 14px; width: 24px; height: 24px; border-radius: 50%;
  background: #1E7AE6; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 14px;
}

/* 表单卡片 */
.form-card { background: #fff; border: 1px solid #ECF1F7; border-radius: 16px; padding: 22px 26px; }
.form-footer { display: flex; justify-content: flex-end; gap: 12px; }

@media (max-width: 1100px) {
  .doctor-card { flex-wrap: wrap; }
}
</style>
