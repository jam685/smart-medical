<template>
  <div class="kb-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2>医学知识库</h2>
      <p>管理临床指南与科室介绍知识库：知识库、文档、片段与向量化检索记录（KB-01~06）</p>
    </div>

    <el-tabs v-model="tab" class="kb-tabs">
      <!-- ===== 知识库 Tab ===== -->
      <el-tab-pane label="知识库" name="bases">
        <div class="card">
          <div class="toolbar">
            <el-button type="primary" @click="openBaseDialog()">＋ 新增知识库</el-button>
          </div>
          <el-table :data="bases" v-loading="basesLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag :type="row.type === 'CLINICAL' ? 'primary' : 'success'" size="small">
                  {{ row.type === 'CLINICAL' ? '临床指南' : '科室介绍' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ===== 文档 Tab ===== -->
      <el-tab-pane label="知识文档" name="documents">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="docFilter" placeholder="按知识库过滤" clearable style="width: 220px">
              <el-option v-for="b in bases" :key="b.id" :label="b.name" :value="b.id" />
            </el-select>
            <el-button type="primary" @click="openDocDialog()">＋ 新增文档</el-button>
          </div>
          <el-table :data="filteredDocs" v-loading="docsLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="documentName" label="文档名称" min-width="180" />
            <el-table-column prop="type" label="类型" width="110" />
            <el-table-column label="所属知识库" width="150">
              <template #default="{ row }">{{ baseName(row.knowledgeBaseId) }}</template>
            </el-table-column>
            <el-table-column prop="parseStatus" label="解析状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.parseStatus === 'PARSED' ? 'success' : 'danger'" size="small">
                  {{ row.parseStatus === 'PARSED' ? '已解析' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="片段数" width="90" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="loadChunks(row.id); tab = 'chunks'">查看片段</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ===== 片段 Tab ===== -->
      <el-tab-pane label="知识片段" name="chunks">
        <div class="card">
          <el-table :data="chunks" v-loading="chunksLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="chunkIndex" label="序号" width="80" />
            <el-table-column prop="chunkTitle" label="标题" min-width="180" />
            <el-table-column prop="content" label="内容" min-width="320" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ===== 向量索引 Tab ===== -->
      <el-tab-pane label="向量化记录" name="vector">
        <div class="card">
          <el-table :data="vectorIndexes" v-loading="vectorLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="chunkTitle" label="片段标题" min-width="180" />
            <el-table-column prop="sourceDocumentId" label="来源文档" width="110" />
            <el-table-column prop="vectorModel" label="向量模型" min-width="160" />
            <el-table-column prop="dimension" label="维度" width="90" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'READY' ? 'success' : row.status === 'PROCESSING' ? 'warning' : 'danger'" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="160" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增知识库弹窗 -->
    <el-dialog v-model="baseDialog" title="新增知识库" width="480px">
      <el-form :model="baseForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="baseForm.name" placeholder="如：内科临床指南库" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="baseForm.type">
            <el-radio value="CLINICAL">临床指南（面诊 Agent）</el-radio>
            <el-radio value="DEPARTMENT">科室介绍（预问诊 Agent）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="baseForm.description" type="textarea" :rows="3" placeholder="知识库用途说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="baseActive" active-value="ACTIVE" inactive-value="INACTIVE" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="baseDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingBase" @click="submitBase">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增文档弹窗 -->
    <el-dialog v-model="docDialog" title="新增知识文档" width="520px">
      <el-form :model="docForm" label-width="90px">
        <el-form-item label="文档名称" required>
          <el-input v-model="docForm.documentName" placeholder="如：高血压诊疗规范" />
        </el-form-item>
        <el-form-item label="文档类型" required>
          <el-input v-model="docForm.type" placeholder="如：GUIDELINE / MANUAL / INTRO" />
        </el-form-item>
        <el-form-item label="所属知识库" required>
          <el-select v-model="docForm.knowledgeBaseId" placeholder="选择知识库" style="width: 100%">
            <el-option v-for="b in bases" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="原文">
          <el-input v-model="docForm.originalText" type="textarea" :rows="5" placeholder="文档原文（可留空）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingDoc" @click="submitDoc">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 管理员端「医学知识库」页
 * 数据全部对接后端 /admin/knowledge（KB-01~06）：
 *   GET  /bases、POST /bases                         知识库列表 / 新增
 *   GET  /documents、POST /documents                 文档列表 / 新增
 *   GET  /chunks                                     片段列表（按 documentId 过滤）
 *   GET  /vector-indexes                             向量化记录（按 sourceDocumentId 过滤）
 * 角色权限由后端 AuthInterceptor 的 /admin/ 前缀统一校验。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchKnowledgeBases,
  createKnowledgeBase,
  fetchKnowledgeDocuments,
  createKnowledgeDocument,
  fetchKnowledgeChunks,
  fetchVectorIndexes,
} from '@/api/knowledge'

/** 当前激活 Tab */
const tab = ref('bases')

/** 知识库列表 */
const bases = ref([])
const basesLoading = ref(false)

/** 文档列表与过滤 */
const docs = ref([])
const docsLoading = ref(false)
const docFilter = ref(null)
const filteredDocs = computed(() =>
  docFilter.value ? docs.value.filter((d) => d.knowledgeBaseId === docFilter.value) : docs.value
)

/** 片段列表 */
const chunks = ref([])
const chunksLoading = ref(false)

/** 向量化记录 */
const vectorIndexes = ref([])
const vectorLoading = ref(false)

/** 加载知识库列表 */
async function loadBases() {
  basesLoading.value = true
  try {
    bases.value = await fetchKnowledgeBases()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    basesLoading.value = false
  }
}

/** 加载文档列表 */
async function loadDocs() {
  docsLoading.value = true
  try {
    docs.value = await fetchKnowledgeDocuments()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    docsLoading.value = false
  }
}

/** 加载指定文档的片段 */
async function loadChunks(documentId) {
  chunksLoading.value = true
  try {
    chunks.value = await fetchKnowledgeChunks({ documentId })
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    chunksLoading.value = false
  }
}

/** 加载向量化记录 */
async function loadVector() {
  vectorLoading.value = true
  try {
    vectorIndexes.value = await fetchVectorIndexes()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    vectorLoading.value = false
  }
}

/** 根据知识库 ID 反查名称 */
function baseName(id) {
  return bases.value.find((b) => b.id === id)?.name || '-'
}

/** ===== 新增知识库 ===== */
const baseDialog = ref(false)
const savingBase = ref(false)
const baseActive = ref('ACTIVE')
const baseForm = ref({ name: '', type: 'CLINICAL', description: '' })

function openBaseDialog() {
  baseForm.value = { name: '', type: 'CLINICAL', description: '' }
  baseActive.value = 'ACTIVE'
  baseDialog.value = true
}

async function submitBase() {
  if (!baseForm.value.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  savingBase.value = true
  try {
    await createKnowledgeBase({
      name: baseForm.value.name.trim(),
      type: baseForm.value.type,
      description: baseForm.value.description,
      status: baseActive.value,
    })
    ElMessage.success('知识库创建成功')
    baseDialog.value = false
    await loadBases()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    savingBase.value = false
  }
}

/** ===== 新增文档 ===== */
const docDialog = ref(false)
const savingDoc = ref(false)
const docForm = ref({ documentName: '', type: '', knowledgeBaseId: null, originalText: '' })

function openDocDialog() {
  docForm.value = { documentName: '', type: '', knowledgeBaseId: null, originalText: '' }
  docDialog.value = true
}

async function submitDoc() {
  if (!docForm.value.documentName.trim() || !docForm.value.type.trim() || !docForm.value.knowledgeBaseId) {
    ElMessage.warning('请完整填写文档名称、类型与所属知识库')
    return
  }
  savingDoc.value = true
  try {
    await createKnowledgeDocument({
      documentName: docForm.value.documentName.trim(),
      type: docForm.value.type.trim(),
      knowledgeBaseId: docForm.value.knowledgeBaseId,
      originalText: docForm.value.originalText,
    })
    ElMessage.success('文档创建成功')
    docDialog.value = false
    await loadDocs()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    savingDoc.value = false
  }
}

onMounted(() => {
  loadBases()
  loadDocs()
  loadVector()
})
</script>

<style scoped>
.kb-page { padding: 24px; }

.page-head h2 { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 6px; }
.page-head p { font-size: 13px; color: #64748b; margin: 0 0 20px; }

.kb-tabs :deep(.el-tabs__item) { font-size: 14px; }
.card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef0f4;
  box-shadow: 0 1px 3px rgba(16, 42, 67, 0.06);
  padding: 20px;
}
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
