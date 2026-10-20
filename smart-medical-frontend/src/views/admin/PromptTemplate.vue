<template>
  <div class="prompt-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2>提示词模板</h2>
      <p>维护 AI 提示词模板：编码、场景、变量与内容（PT-01~04）</p>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()">＋ 新增模板</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="templateCode" label="模板编码" width="140" />
        <el-table-column prop="templateName" label="模板名称" min-width="160" />
        <el-table-column prop="scene" label="场景" width="110">
          <template #default="{ row }">
            <el-tag :type="row.scene === 'DOCTOR' ? 'primary' : row.scene === 'PATIENT' ? 'success' : 'warning'" size="small">
              {{ row.scene }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="variables" label="变量" min-width="140" show-overflow-tooltip />
        <el-table-column prop="promptContent" label="模板内容" min-width="260" show-overflow-tooltip />
        <el-table-column prop="enabled" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该模板？" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog" :title="editingId ? '编辑模板' : '新增模板'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="模板编码" required>
          <el-input v-model="form.templateCode" placeholder="如：AI_DIAGNOSIS" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="form.templateName" placeholder="如：病情分析模板" />
        </el-form-item>
        <el-form-item label="使用场景" required>
          <el-select v-model="form.scene" placeholder="选择场景" style="width: 100%">
            <el-option label="患者端 PATIENT" value="PATIENT" />
            <el-option label="医生端 DOCTOR" value="DOCTOR" />
            <el-option label="管理端 ADMIN" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="变量">
          <el-input v-model="form.variables" placeholder="如：{doctorId}{patientId}" />
        </el-form-item>
        <el-form-item label="模板内容" required>
          <el-input v-model="form.promptContent" type="textarea" :rows="6" placeholder="prompt 内容" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="enabledVal" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 管理员端「提示词模板」页
 * 数据全部对接后端 /admin/prompt-templates（PT-01~04）：
 *   GET    /admin/prompt-templates      模板列表
 *   POST   /admin/prompt-templates      新增模板
 *   PUT    /admin/prompt-templates/{id} 修改模板
 *   DELETE /admin/prompt-templates/{id} 删除模板
 * 角色权限由后端 AuthInterceptor 的 /admin/ 前缀统一校验。
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchPromptTemplates,
  createPromptTemplate,
  updatePromptTemplate,
  deletePromptTemplate,
} from '@/api/prompt'

/** 模板列表 */
const list = ref([])
const loading = ref(false)

/** 弹窗状态 */
const dialog = ref(false)
const editingId = ref(null)
const saving = ref(false)
const enabledVal = ref(1)
const form = ref({ templateCode: '', templateName: '', scene: 'DOCTOR', variables: '', promptContent: '' })

/** 加载模板列表 */
async function load() {
  loading.value = true
  try {
    list.value = await fetchPromptTemplates()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
}

/** 打开弹窗（编辑时回填数据） */
function openDialog(row) {
  editingId.value = row?.id ?? null
  form.value = row
    ? {
        templateCode: row.templateCode,
        templateName: row.templateName,
        scene: row.scene,
        variables: row.variables || '',
        promptContent: row.promptContent,
      }
    : { templateCode: '', templateName: '', scene: 'DOCTOR', variables: '', promptContent: '' }
  enabledVal.value = row?.enabled ?? 1
  dialog.value = true
}

/** 提交（新增或编辑） */
async function submit() {
  if (!form.value.templateCode.trim() || !form.value.templateName.trim() || !form.value.promptContent.trim()) {
    ElMessage.warning('请完整填写模板编码、名称与内容')
    return
  }
  saving.value = true
  try {
    const payload = { ...form.value, enabled: enabledVal.value }
    if (editingId.value) {
      await updatePromptTemplate(editingId.value, payload)
      ElMessage.success('模板更新成功')
    } else {
      await createPromptTemplate(payload)
      ElMessage.success('模板创建成功')
    }
    dialog.value = false
    await load()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    saving.value = false
  }
}

/** 删除模板 */
async function remove(id) {
  try {
    await deletePromptTemplate(id)
    ElMessage.success('模板已删除')
    await load()
  } catch {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.prompt-page { padding: 24px; }

.page-head h2 { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 6px; }
.page-head p { font-size: 13px; color: #64748b; margin: 0 0 20px; }

.card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef0f4;
  box-shadow: 0 1px 3px rgba(16, 42, 67, 0.06);
  padding: 20px;
}
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 16px; }
</style>
