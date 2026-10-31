<template>
  <div class="kb-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2>医学知识库</h2>
      <p>管理临床指南与科室介绍知识库：知识库 → 文档 → 片段 → 向量化存储（KB-01~06，增删改后自动热更新向量索引）</p>
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
            <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openBaseDialog(row)">编辑</el-button>
                <el-popconfirm title="删除该知识库将级联删除其下所有文档、片段与向量索引记录，确定删除？" @confirm="removeBase(row)">
                  <template #reference>
                    <el-button link type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
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
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewChunks(row)">片段</el-button>
                <el-button link type="primary" size="small" @click="openDocDialog(row)">编辑</el-button>
                <el-popconfirm title="删除该文档将级联删除其下片段与向量索引记录，确定删除？" @confirm="removeDoc(row)">
                  <template #reference>
                    <el-button link type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ===== 片段 Tab ===== -->
      <el-tab-pane label="知识片段" name="chunks">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="chunkDocFilter" placeholder="按文档筛选片段（清空=全部）" clearable style="width: 240px">
              <el-option v-for="d in docs" :key="d.id" :label="d.documentName" :value="d.id" />
            </el-select>
            <el-button type="primary" @click="openChunkDialog()">＋ 新增片段</el-button>
          </div>
          <el-table :data="filteredChunks" v-loading="chunksLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="所属文档" min-width="170">
              <template #default="{ row }">{{ docName(row.documentId) }}</template>
            </el-table-column>
            <el-table-column prop="chunkIndex" label="序号" width="80" />
            <el-table-column prop="chunkTitle" label="标题" min-width="180" />
            <el-table-column prop="content" label="内容" min-width="320" show-overflow-tooltip />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openChunkDialog(row)">编辑</el-button>
                <el-popconfirm title="删除该片段将同步删除其向量索引记录并重建索引，确定删除？" @confirm="removeChunk(row)">
                  <template #reference>
                    <el-button link type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
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

    <!-- 新增/编辑知识库弹窗 -->
    <el-dialog v-model="baseDialog" :title="baseDialogTitle" width="480px">
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

    <!-- 新增/编辑文档弹窗 -->
    <el-dialog v-model="docDialog" :title="docDialogTitle" width="520px">
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
          <el-input v-model="docForm.originalText" type="textarea" :rows="5" placeholder="文档原文（可留空，检索以「知识片段」内容为准）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingDoc" @click="submitDoc">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑片段弹窗 -->
    <el-dialog v-model="chunkDialog" :title="chunkDialogTitle" width="560px">
      <el-form :model="chunkForm" label-width="90px">
        <el-form-item label="所属文档" required>
          <el-select v-model="chunkForm.documentId" placeholder="选择文档" style="width: 100%" :disabled="!!editingChunkId">
            <el-option v-for="d in docs" :key="d.id" :label="d.documentName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="片段序号">
          <el-input-number v-model="chunkForm.chunkIndex" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="片段标题" required>
          <el-input v-model="chunkForm.chunkTitle" placeholder="如：呼吸科 张医生 擅长领域介绍" />
        </el-form-item>
        <el-form-item label="片段内容" required>
          <el-input v-model="chunkForm.content" type="textarea" :rows="6" placeholder="科室介绍内容（内容中出现医生真实姓名即可被 RAG 关联推荐）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chunkDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingChunk" @click="submitChunk">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 管理员端「医学知识库」页
 * 数据全部对接后端 /admin/knowledge（KB-01~06，支持三层增删改）：
 *   GET/POST/PUT/DELETE /bases       知识库 列表/新增/更新/删除
 *   GET/POST/PUT/DELETE /documents   文档 列表/新增/更新/删除
 *   GET/POST/PUT/DELETE /chunks      片段 列表/新增/更新/删除
 *   GET                /vector-indexes  向量化记录列表
 * 任何增删改后端均自动重建科室介绍库向量索引（热更新，无需重启）。
 * 角色权限由后端 AuthInterceptor 的 /admin/ 前缀统一校验。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  fetchKnowledgeDocuments,
  createKnowledgeDocument,
  updateKnowledgeDocument,
  deleteKnowledgeDocument,
  fetchKnowledgeChunks,
  createKnowledgeChunk,
  updateKnowledgeChunk,
  deleteKnowledgeChunk,
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

/** 片段列表（全量，前端按文档筛选展示） */
const chunks = ref([])
const chunksLoading = ref(false)
/** 片段所属文档筛选（清空=显示全部文档的片段） */
const chunkDocFilter = ref(null)
/** 按文档筛选后的片段列表 */
const filteredChunks = computed(() =>
  chunkDocFilter.value ? chunks.value.filter((c) => c.documentId === chunkDocFilter.value) : chunks.value
)

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

/** 从文档 Tab 跳转片段 Tab：选中该文档并展示其片段 */
async function viewChunks(row) {
  chunkDocFilter.value = row.id
  await loadAllChunks()
  tab.value = 'chunks'
}

/** 加载全部知识片段（前端按文档筛选展示） */
async function loadAllChunks() {
  chunksLoading.value = true
  try {
    chunks.value = await fetchKnowledgeChunks()
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

/** 根据文档 ID 反查文档名称 */
function docName(id) {
  return docs.value.find((d) => d.id === id)?.documentName || '-'
}

/** ===== 知识库 新增/编辑/删除 ===== */
const baseDialog = ref(false)
const baseDialogTitle = ref('新增知识库')
const savingBase = ref(false)
const baseActive = ref('ACTIVE')
const editingBaseId = ref(null)
const baseForm = ref({ name: '', type: 'CLINICAL', description: '' })

/** 打开知识库弹窗：row 为空为新增，非空为编辑回填 */
function openBaseDialog(row = null) {
  editingBaseId.value = row ? row.id : null
  baseDialogTitle.value = row ? '编辑知识库' : '新增知识库'
  baseForm.value = row
    ? { name: row.name, type: row.type, description: row.description || '' }
    : { name: '', type: 'CLINICAL', description: '' }
  baseActive.value = row ? row.status : 'ACTIVE'
  baseDialog.value = true
}

/** 保存知识库（新增走 POST、编辑走 PUT，后端均自动重建向量索引） */
async function submitBase() {
  if (!baseForm.value.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  savingBase.value = true
  try {
    const payload = {
      name: baseForm.value.name.trim(),
      type: baseForm.value.type,
      description: baseForm.value.description,
      status: baseActive.value,
    }
    if (editingBaseId.value) {
      await updateKnowledgeBase(editingBaseId.value, payload)
      ElMessage.success('知识库更新成功，向量化索引已热更新')
    } else {
      await createKnowledgeBase(payload)
      ElMessage.success('知识库创建成功，向量化索引已重建')
    }
    baseDialog.value = false
    await loadBases()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    savingBase.value = false
  }
}

/** 删除知识库（级联删除文档/片段/向量记录） */
async function removeBase(row) {
  try {
    await deleteKnowledgeBase(row.id)
    ElMessage.success('知识库已删除，向量化索引已热更新')
    await loadBases()
  } catch {
    /* 拦截器已统一提示 */
  }
}

/** ===== 文档 新增/编辑/删除 ===== */
const docDialog = ref(false)
const docDialogTitle = ref('新增知识文档')
const savingDoc = ref(false)
const editingDocId = ref(null)
const docForm = ref({ documentName: '', type: '', knowledgeBaseId: null, originalText: '' })

/** 打开文档弹窗：row 为空为新增，非空为编辑回填 */
function openDocDialog(row = null) {
  editingDocId.value = row ? row.id : null
  docDialogTitle.value = row ? '编辑知识文档' : '新增知识文档'
  docForm.value = row
    ? { documentName: row.documentName, type: row.type, knowledgeBaseId: row.knowledgeBaseId, originalText: row.originalText || '' }
    : { documentName: '', type: '', knowledgeBaseId: null, originalText: '' }
  docDialog.value = true
}

/** 保存文档（新增走 POST、编辑走 PUT，后端均自动重建向量索引） */
async function submitDoc() {
  if (!docForm.value.documentName.trim() || !docForm.value.type.trim() || !docForm.value.knowledgeBaseId) {
    ElMessage.warning('请完整填写文档名称、类型与所属知识库')
    return
  }
  savingDoc.value = true
  try {
    const payload = {
      documentName: docForm.value.documentName.trim(),
      type: docForm.value.type.trim(),
      knowledgeBaseId: docForm.value.knowledgeBaseId,
      originalText: docForm.value.originalText,
    }
    if (editingDocId.value) {
      await updateKnowledgeDocument(editingDocId.value, payload)
      ElMessage.success('文档更新成功，向量化索引已热更新')
    } else {
      await createKnowledgeDocument(payload)
      ElMessage.success('文档创建成功，向量化索引已重建')
    }
    docDialog.value = false
    await loadDocs()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    savingDoc.value = false
  }
}

/** 删除文档（级联删除其下片段与向量记录） */
async function removeDoc(row) {
  try {
    await deleteKnowledgeDocument(row.id)
    ElMessage.success('文档已删除，向量化索引已热更新')
    await loadDocs()
  } catch {
    /* 拦截器已统一提示 */
  }
}

/** ===== 片段 新增/编辑/删除 ===== */
const chunkDialog = ref(false)
const chunkDialogTitle = ref('新增知识片段')
const savingChunk = ref(false)
const editingChunkId = ref(null)
const chunkForm = ref({ documentId: null, chunkIndex: 1, chunkTitle: '', content: '' })

/** 打开片段弹窗：row 为空为新增（默认归属筛选选中的文档），非空为编辑回填 */
function openChunkDialog(row = null) {
  editingChunkId.value = row ? row.id : null
  chunkDialogTitle.value = row ? '编辑知识片段' : '新增知识片段'
  chunkForm.value = row
    ? { documentId: row.documentId, chunkIndex: row.chunkIndex, chunkTitle: row.chunkTitle, content: row.content }
    : { documentId: chunkDocFilter.value || null, chunkIndex: nextChunkIndex(), chunkTitle: '', content: '' }
  chunkDialog.value = true
}

/** 计算下一个片段序号（当前筛选范围内最大序号 + 1，无筛选则取全量） */
function nextChunkIndex() {
  const list = filteredChunks.value.length ? filteredChunks.value : chunks.value
  if (!list.length) {
    return 1
  }
  return Math.max(...list.map((c) => c.chunkIndex || 0)) + 1
}

/** 保存片段（新增走 POST、编辑走 PUT，后端均自动重建向量索引） */
async function submitChunk() {
  if (!chunkForm.value.documentId) {
    ElMessage.warning('请选择所属文档')
    return
  }
  if (!chunkForm.value.chunkTitle.trim() || !chunkForm.value.content.trim()) {
    ElMessage.warning('请完整填写片段标题与内容')
    return
  }
  savingChunk.value = true
  try {
    const payload = {
      documentId: chunkForm.value.documentId,
      chunkIndex: chunkForm.value.chunkIndex,
      chunkTitle: chunkForm.value.chunkTitle.trim(),
      content: chunkForm.value.content.trim(),
    }
    if (editingChunkId.value) {
      await updateKnowledgeChunk(editingChunkId.value, payload)
      ElMessage.success('片段更新成功，向量化索引已热更新')
    } else {
      await createKnowledgeChunk(payload)
      ElMessage.success('片段创建成功，向量化索引已重建')
    }
    chunkDialog.value = false
    await loadAllChunks()
    await loadDocs()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    savingChunk.value = false
  }
}

/** 删除片段（同步删除其向量索引记录并重建） */
async function removeChunk(row) {
  try {
    await deleteKnowledgeChunk(row.id)
    ElMessage.success('片段已删除，向量化索引已热更新')
    await loadAllChunks()
    await loadDocs()
  } catch {
    /* 拦截器已统一提示 */
  }
}

onMounted(() => {
  loadBases()
  loadDocs()
  loadAllChunks()
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
