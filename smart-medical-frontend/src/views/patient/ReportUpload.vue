<script setup>
/**
 * 患者端「上传报告」页组件
 * 职责：选择本地报告图片上传至系统，成功后回到报告列表。
 * 数据来源（真实接口）：
 *   POST /reports/upload  上传报告图片（multipart，字段 file；后端写入 sm_report）
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { uploadReport } from '@/api/report'

const router = useRouter()

/** 上传中的文件及其预览地址 */
const file = ref(null)
const previewUrl = ref('')
/** 上传中状态 */
const uploading = ref(false)

/** 选择文件的通用回调（兼容 element 组件） */
function onFileChange(e) {
  const f = e.target.files?.[0]
  if (!f) return
  file.value = f
  previewUrl.value = URL.createObjectURL(f)
}

/** 清空已选文件 */
function clearFile() {
  file.value = null
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

/** 提交上传：POST /reports/upload，成功后跳回报告列表 */
async function submit() {
  if (!file.value) {
    ElMessage.warning('请先选择需要上传的报告图片')
    return
  }
  uploading.value = true
  try {
    const res = await uploadReport(file.value)
    ElMessage.success('报告上传成功')
    router.push('/patient/reports')
  } catch (e) {
    // 失败提示由请求拦截器统一处理
  } finally {
    uploading.value = false
  }
}

/** 返回报告列表 */
function goBack() {
  router.push('/patient/reports')
}
</script>

<template>
  <div class="report-upload-page">
    <div class="top-bar">
      <el-button text class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>&nbsp;返回我的报告
      </el-button>
      <h2 class="page-title brand-font">上传报告</h2>
      <span class="top-placeholder"></span>
    </div>

    <div class="upload-card">
      <p class="panel-hint">请上传检查/检验报告图片（jpg / png）</p>

      <!-- 图片选择区 -->
      <div class="drop-zone" v-if="!previewUrl">
        <svg viewBox="0 0 24 24" fill="none" class="dz-icon">
          <rect x="3" y="3" width="18" height="18" rx="3" stroke="#1E7AE6" stroke-width="1.6"/>
          <circle cx="9" cy="9" r="2" stroke="#1E7AE6" stroke-width="1.6"/>
          <path d="M3 17l5-5 4 4 3-3 6 6" stroke="#1E7AE6" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <p class="dz-text">点击选择或拖拽报告图片到此处</p>
        <p class="dz-sub">支持 jpg / png，建议大小不超过 5MB</p>
        <el-button type="primary" @click="$refs.fileInput.click()">选择文件</el-button>
      </div>

      <!-- 预览区 -->
      <div class="preview-zone" v-else>
        <img :src="previewUrl" alt="报告预览" class="preview-img" />
        <p class="preview-name">{{ file.name }}</p>
        <div class="preview-actions">
          <el-button @click="clearFile">重新选择</el-button>
          <el-button type="primary" :loading="uploading" @click="submit">确认上传</el-button>
        </div>
      </div>

      <input ref="fileInput" type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png" style="display: none" @change="onFileChange" />
    </div>
  </div>
</template>

<style scoped>
.report-upload-page { padding: 4px 2px 40px; }

/* 顶部 */
.top-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.back-btn { font-size: 14px; color: #5A6B82; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0; }
.top-placeholder { width: 110px; }

.panel-hint { font-size: 15px; font-weight: 600; color: #2C3E50; margin: 0 0 16px; }

/* 上传卡片 */
.upload-card {
  max-width: 640px; margin: 0 auto; background: #fff;
  border: 1px solid #ECF1F7; border-radius: 16px; padding: 28px 26px;
}
.drop-zone {
  border: 2px dashed #BFD9F7; border-radius: 14px; background: #F7FBFF;
  padding: 50px 20px; text-align: center; transition: all 0.2s;
}
.drop-zone:hover { border-color: #1E7AE6; background: #F1F8FF; }
.dz-icon { width: 52px; height: 52px; margin: 0 auto 14px; }
.dz-text { font-size: 15px; color: #1A2B45; font-weight: 600; margin: 0 0 6px; }
.dz-sub { font-size: 12.5px; color: #8492A6; margin: 0 0 18px; }

/* 预览 */
.preview-zone { text-align: center; }
.preview-img { max-width: 100%; max-height: 420px; border-radius: 12px; box-shadow: 0 6px 20px rgba(0,0,0,0.08); }
.preview-name { font-size: 14px; color: #5A6B82; margin: 14px 0 18px; }
.preview-actions { display: flex; justify-content: center; gap: 12px; }
</style>