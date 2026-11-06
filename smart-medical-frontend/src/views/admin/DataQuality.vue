<template>
  <div class="dq-page">
    <!-- 页面头部 -->
    <div class="page-head">
      <div class="head-left">
        <h2 class="page-title">数据质量</h2>
        <p class="page-desc">核心业务表健康巡检：每天凌晨 02:00 自动执行，也可手动立即巡检</p>
      </div>
    </div>

    <el-card shadow="never" class="main-card" v-loading="loading">
      <!-- 最新快照 4 指标卡片 -->
      <div class="metrics" v-if="latest">
        <div class="metric-card metric-primary">
          <div class="metric-label">数据总量</div>
          <div class="metric-value">{{ latest.dataCount?.toLocaleString() ?? 0 }}</div>
          <div class="metric-sub">6 张核心表行数之和</div>
        </div>
        <div class="metric-card metric-success">
          <div class="metric-label">已处理量</div>
          <div class="metric-value">{{ latest.labeledCount?.toLocaleString() ?? 0 }}</div>
          <div class="metric-sub">已解析文档 + 档案完整</div>
        </div>
        <div class="metric-card" :class="rateClass(latest.missingRate)">
          <div class="metric-label">字段缺失率</div>
          <div class="metric-value">{{ pct(latest.missingRate) }}</div>
          <div class="metric-sub">关键字段为 NULL 的占比</div>
          <el-progress :percentage="toPercent(latest.missingRate)" :stroke-width="6" :color="progressColor(latest.missingRate)" />
        </div>
        <div class="metric-card" :class="rateClass(latest.abnormalRate)">
          <div class="metric-label">数据异常率</div>
          <div class="metric-value">{{ pct(latest.abnormalRate) }}</div>
          <div class="metric-sub">格式/枚举/范围异常</div>
          <el-progress :percentage="toPercent(latest.abnormalRate)" :stroke-width="6" :color="progressColor(latest.abnormalRate)" />
        </div>
      </div>
      <el-empty v-else description="暂无巡检数据，点「立即巡检」生成第一条" />

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-button type="primary" @click="onRunNow" :loading="running">
          <el-icon><Refresh /></el-icon>&nbsp;立即巡检
        </el-button>
        <el-button @click="load"><el-icon><RefreshLeft /></el-icon>&nbsp;刷新</el-button>
        <span class="last-time" v-if="latest">上次巡检：{{ formatTime(latest.updatedAt) }}</span>
      </div>

      <!-- 历史快照表格 -->
      <el-table :data="list" stripe class="data-table" v-if="list.length">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="巡检时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="数据总量" width="130" align="right">
          <template #default="{ row }">{{ row.dataCount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="已处理量" width="130" align="right">
          <template #default="{ row }">{{ row.labeledCount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="字段缺失率" width="160">
          <template #default="{ row }">
            <el-tag :type="tagType(row.missingRate)" size="small" effect="light">{{ pct(row.missingRate) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据异常率" width="160">
          <template #default="{ row }">
            <el-tag :type="tagType(row.abnormalRate)" size="small" effect="light">{{ pct(row.abnormalRate) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="质量评级" width="110">
          <template #default="{ row }">
            <el-tag :type="gradeType(row)" size="small" effect="dark">{{ grade(row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { fetchDataQuality, runDataQualityCheck } from '@/api/admin'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshLeft } from '@element-plus/icons-vue'

const list = ref([])
const loading = ref(false)
const running = ref(false)

const latest = computed(() => list.value[0] || null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await fetchDataQuality()
  } catch (e) {
    ElMessage.error('拉取数据质量快照失败')
  } finally {
    loading.value = false
  }
}

async function onRunNow() {
  running.value = true
  try {
    const snap = await runDataQualityCheck()
    list.value.unshift(snap)
    ElMessage.success('巡检完成，新快照已生成')
  } catch (e) {
    ElMessage.error('巡检失败：' + (e?.response?.data?.msg || '未知错误'))
  } finally {
    running.value = false
  }
}

// ============ 辅助：数值格式化 ============

/** 0.0123 → "1.23%" */
function pct(v) {
  if (v == null) return '—'
  return (Number(v) * 100).toFixed(2) + '%'
}

/** 0.0123 → 1.23（el-progress 需要 0~100 的数字） */
function toPercent(v) {
  if (v == null) return 0
  const p = Number(v) * 100
  return Math.min(100, Math.max(0, p))
}

function formatTime(ts) {
  if (!ts) return '—'
  // 后端返回可能是 "2026-11-03T11:51:19" 或带时区
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ts
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ============ 辅助：视觉映射 ============

/** 比率阈值分级 */
function rateClass(v) {
  const n = Number(v || 0)
  if (n === 0) return 'metric-rate-good'
  if (n < 0.02) return 'metric-rate-warn'
  return 'metric-rate-bad'
}

function progressColor(v) {
  const n = Number(v || 0)
  if (n === 0) return '#67c23a'
  if (n < 0.02) return '#e6a23c'
  return '#f56c6c'
}

function tagType(v) {
  const n = Number(v || 0)
  if (n === 0) return 'success'
  if (n < 0.02) return 'warning'
  return 'danger'
}

/** 综合评级：A/B/C/D */
function grade(row) {
  const m = Number(row.missingRate || 0)
  const a = Number(row.abnormalRate || 0)
  const total = m + a
  if (total === 0) return 'A 优秀'
  if (total < 0.02) return 'B 良好'
  if (total < 0.05) return 'C 一般'
  return 'D 需关注'
}

function gradeType(row) {
  const g = grade(row)
  if (g.startsWith('A')) return 'success'
  if (g.startsWith('B')) return 'info'
  if (g.startsWith('C')) return 'warning'
  return 'danger'
}
</script>

<style scoped>
.dq-page {
  padding: 20px;
}
.page-head {
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 4px 0;
}
.page-desc {
  font-size: 13px;
  color: #6b7280;
  margin: 0;
}
.main-card {
  border-radius: 8px;
}

/* 指标卡片网格 */
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.metric-card {
  background: linear-gradient(135deg, #fafbfc 0%, #ffffff 100%);
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 20px;
  transition: box-shadow 0.2s;
}
.metric-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}
.metric-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 8px;
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 4px;
  font-variant-numeric: tabular-nums;
}
.metric-sub {
  font-size: 12px;
  color: #9ca3af;
}
.metric-primary .metric-value { color: #409eff; }
.metric-success .metric-value { color: #67c23a; }
.metric-rate-good .metric-value { color: #67c23a; }
.metric-rate-warn .metric-value { color: #e6a23c; }
.metric-rate-bad .metric-value { color: #f56c6c; }
.metric-card :deep(.el-progress) {
  margin-top: 8px;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.last-time {
  margin-left: auto;
  font-size: 12px;
  color: #9ca3af;
}

.data-table {
  font-size: 13px;
}
</style>
