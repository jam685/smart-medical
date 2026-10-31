<template>
  <div class="model-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2>模型配置</h2>
      <p>维护 AI 模型接入参数（数据映射 sm_model_config 表）；启用聊天模型后立即热更新生效，无需重启服务</p>
    </div>

    <!-- 当前生效模型（来自 sm_model_config 启用项，无有效配置时回退 application.yml） -->
    <div class="card active-card" v-loading="activeLoading">
      <div class="active-head">
        <div class="active-left">
          <span class="active-dot"></span>
          <span class="active-title">当前生效聊天模型</span>
          <el-tag size="small" :type="active.source === '数据库' ? 'success' : 'info'">
            {{ active.source || '—' }}
          </el-tag>
        </div>
        <span class="active-tip">保存配置后，全部 AI 会话下一轮调用即使用新模型</span>
      </div>
      <div class="active-grid">
        <div class="active-item">
          <span class="k">配置名称</span><span class="v">{{ active.configName || '—' }}</span>
        </div>
        <div class="active-item">
          <span class="k">服务商</span><span class="v">{{ active.provider || '—' }}</span>
        </div>
        <div class="active-item">
          <span class="k">模型名称</span><span class="v mono">{{ active.modelName || '—' }}</span>
        </div>
        <div class="active-item">
          <span class="k">接口地址</span><span class="v mono">{{ active.apiUrl || '—' }}</span>
        </div>
        <div class="active-item">
          <span class="k">最大 Token</span><span class="v">{{ active.maxTokens || '默认（不限制）' }}</span>
        </div>
        <div class="active-item">
          <span class="k">API Key</span><span class="v">{{ active.apiKeyConfigured ? '已配置' : '未配置' }}</span>
        </div>
      </div>
    </div>

    <!-- 配置列表 -->
    <div class="card">
      <div class="toolbar">
        <span class="toolbar-tip">同一类型仅一条启用；切换启用状态即热更新</span>
        <div>
          <el-button @click="reload">刷新</el-button>
          <el-button type="primary" @click="openDialog()">＋ 新增配置</el-button>
        </div>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="configName" label="配置名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === '聊天模型' ? 'primary' : 'warning'" size="small">
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="provider" label="服务商" width="110" show-overflow-tooltip />
        <el-table-column prop="modelName" label="模型名称" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.modelName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="apiUrl" label="接口地址" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.apiUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="apiKey" label="API Key" width="130">
          <template #default="{ row }">
            <span class="mono muted">{{ maskKey(row.apiKey) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="maxTokens" label="最大 Token" width="105">
          <template #default="{ row }">{{ row.maxTokens || '—' }}</template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="95">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :active-value="1"
              :inactive-value="0"
              @change="toggleEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该配置？删除后立即生效" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog" :title="editingId ? '编辑模型配置' : '新增模型配置'" width="620px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.configName" placeholder="如：DeepSeek 聊天模型" />
        </el-form-item>
        <el-form-item label="配置类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="聊天模型（对话/意图/润色）" value="聊天模型" />
            <el-option label="向量模型（知识库检索向量化）" value="向量模型" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务商">
          <el-input v-model="form.provider" placeholder="如：DeepSeek / 千问 / OpenAI 兼容" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.modelName" placeholder="如：deepseek-flash" />
        </el-form-item>
        <el-form-item label="接口地址" required>
          <el-input v-model="form.apiUrl" placeholder="如：https://api.deepseek.com" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId ? '留空则不修改（当前已配置则继续沿用）' : '留空则沿用 application.yml 中的 Key'"
          />
        </el-form-item>
        <el-form-item label="最大 Token">
          <el-input-number v-model="form.maxTokens" :min="0" :step="256" controls-position="right" style="width: 180px" />
          <span class="form-hint">0 或留空表示不限制</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="用途说明，如：用于预问诊与数字医生对话" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="enabledVal" :active-value="1" :inactive-value="0" />
          <span class="form-hint">启用后立即生效（同类型其他配置会自动停用）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存并热更新</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 管理员端「模型配置」页
 * 数据与后端 sm_model_config 表一一映射（配置名称/类型/服务商/模型名/接口地址/API Key/最大 Token/启用/备注）。
 * 对接后端 /admin/model-configs：
 *   GET    /admin/model-configs         配置列表
 *   GET    /admin/model-configs/active  当前生效聊天模型（数据库无有效配置时回退 application.yml）
 *   POST   /admin/model-configs         新增
 *   PUT    /admin/model-configs/{id}    修改
 *   DELETE /admin/model-configs/{id}    删除
 * 热更新：新增/修改/启停/删除后，后端使模型配置缓存失效，下一次 AI 调用即使用新配置，无需重启。
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchModelConfigs,
  fetchActiveModelConfig,
  createModelConfig,
  updateModelConfig,
  deleteModelConfig,
} from '@/api/modelConfig'

/** 配置列表 */
const list = ref([])
const loading = ref(false)

/** 当前生效模型 */
const active = ref({})
const activeLoading = ref(false)

/** 弹窗状态 */
const dialog = ref(false)
const editingId = ref(null)
const saving = ref(false)
const enabledVal = ref(1)
const form = ref(emptyForm())

/**
 * 空表单（新增时使用）
 * @returns {object} 表单初始值
 */
function emptyForm() {
  return {
    configName: '',
    type: '聊天模型',
    provider: '',
    modelName: '',
    apiUrl: '',
    apiKey: '',
    maxTokens: 2048,
    remark: '',
  }
}

/** 加载配置列表 */
async function load() {
  loading.value = true
  try {
    list.value = await fetchModelConfigs()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
}

/** 加载当前生效模型 */
async function loadActive() {
  activeLoading.value = true
  try {
    active.value = await fetchActiveModelConfig()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    activeLoading.value = false
  }
}

/** 刷新列表与生效状态 */
function reload() {
  load()
  loadActive()
}

/** 打开弹窗（编辑时回填；API Key 不回填明文，留空表示不修改） */
function openDialog(row) {
  editingId.value = row?.id ?? null
  form.value = row
    ? {
        configName: row.configName,
        type: row.type,
        provider: row.provider || '',
        modelName: row.modelName,
        apiUrl: row.apiUrl,
        apiKey: '',
        maxTokens: row.maxTokens ?? 0,
        remark: row.remark || '',
      }
    : emptyForm()
  enabledVal.value = row?.enabled ?? 1
  dialog.value = true
}

/** 提交（新增或修改，保存即热更新） */
async function submit() {
  if (!form.value.configName.trim() || !form.value.type || !form.value.modelName.trim() || !form.value.apiUrl.trim()) {
    ElMessage.warning('请完整填写配置名称、类型、模型名称与接口地址')
    return
  }
  saving.value = true
  try {
    const payload = {
      configName: form.value.configName.trim(),
      type: form.value.type,
      provider: form.value.provider?.trim() || '',
      modelName: form.value.modelName.trim(),
      apiUrl: form.value.apiUrl.trim(),
      maxTokens: form.value.maxTokens || null,
      remark: form.value.remark || '',
      enabled: enabledVal.value,
    }
    // API Key 留空不提交：新增时沿用 application.yml，编辑时保留原密钥不被清空
    if (form.value.apiKey && form.value.apiKey.trim()) {
      payload.apiKey = form.value.apiKey.trim()
    }
    if (editingId.value) {
      await updateModelConfig(editingId.value, payload)
      ElMessage.success('配置已更新并热更新生效')
    } else {
      await createModelConfig(payload)
      ElMessage.success('配置已创建并热更新生效')
    }
    dialog.value = false
    reload()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    saving.value = false
  }
}

/** 切换启用状态（立即调用后端热更新；失败回滚开关） */
async function toggleEnabled(row) {
  const next = row.enabled
  try {
    await updateModelConfig(row.id, {
      configName: row.configName,
      type: row.type,
      provider: row.provider,
      modelName: row.modelName,
      apiUrl: row.apiUrl,
      maxTokens: row.maxTokens,
      remark: row.remark,
      enabled: next,
    })
    ElMessage.success(next === 1 ? '已启用，下一轮 AI 调用即生效' : '已停用，配置已热更新')
    reload()
  } catch {
    row.enabled = next === 1 ? 0 : 1
  }
}

/** 删除配置（删除后立即热更新） */
async function remove(id) {
  try {
    await deleteModelConfig(id)
    ElMessage.success('配置已删除并热更新生效')
    reload()
  } catch {
    /* 拦截器已统一提示 */
  }
}

/**
 * API Key 脱敏展示（未配置时提示沿用配置文件）
 * @param {string} key 密钥原文
 * @returns {string} 脱敏文本
 */
function maskKey(key) {
  if (!key) return '沿用配置文件'
  if (key.length <= 6) return '••••••'
  return `${key.slice(0, 3)}••••${key.slice(-4)}`
}

/** 时间格式化（兼容 ISO 与 yyyy-MM-dd HH:mm:ss） */
function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(reload)
</script>

<style scoped>
.model-page { padding: 24px; }

.page-head h2 { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 6px; }
.page-head p { font-size: 13px; color: #64748b; margin: 0 0 20px; }

.card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef0f4;
  box-shadow: 0 1px 3px rgba(16, 42, 67, 0.06);
  padding: 20px;
  margin-bottom: 18px;
}

/* 当前生效模型卡片 */
.active-card { padding: 18px 20px; }
.active-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.active-left { display: flex; align-items: center; gap: 8px; }
.active-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15);
}
.active-title { font-size: 15px; font-weight: 600; color: #1a1a2e; }
.active-tip { font-size: 12px; color: #94a3b8; }
.active-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 24px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed #eef0f4;
}
.active-item { display: flex; align-items: baseline; gap: 8px; min-width: 0; }
.active-item .k { flex: 0 0 68px; font-size: 12px; color: #94a3b8; }
.active-item .v {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar-tip { font-size: 12px; color: #94a3b8; }

.mono { font-family: Menlo, Consolas, monospace; font-size: 12px; }
.muted { color: #94a3b8; }

.form-hint { margin-left: 10px; font-size: 12px; color: #94a3b8; }
</style>
