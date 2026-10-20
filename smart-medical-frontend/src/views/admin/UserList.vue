<template>
  <div class="user-list-page">
    <!-- 页面头部 -->
    <div class="page-head">
      <div class="head-left">
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">管理患者与医生账号：查询 / 新增 / 编辑 / 启用禁用</p>
      </div>
    </div>

    <el-card shadow="never" class="main-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <el-tabs v-model="activeTab" class="role-tabs" @tab-change="onTabChange">
          <el-tab-pane label="患者" name="PATIENT" />
          <el-tab-pane label="医生" name="DOCTOR" />
        </el-tabs>
        <div class="toolbar-right">
          <el-input
            v-model="keywordInput"
            placeholder="搜索：用户名 / 姓名 / 手机号…"
            clearable
            class="search-input"
            @keyup.enter="onSearch"
            @clear="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="onSearch"><el-icon><Search /></el-icon>&nbsp;搜索</el-button>
          <el-button type="success" @click="openCreate"><el-icon><Plus /></el-icon>&nbsp;新增{{ isPatient ? '患者' : '医生' }}</el-button>
        </div>
      </div>

      <!-- 数据表 -->
      <el-table v-loading="loading" :data="list" stripe class="data-table">
        <template v-if="isPatient">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" min-width="110" />
          <el-table-column prop="realName" label="姓名" min-width="90" />
          <el-table-column prop="gender" label="性别" width="70" />
          <el-table-column prop="phone" label="手机号" min-width="120" />
          <el-table-column prop="idCard" label="身份证号" min-width="180" />
        </template>
        <template v-else>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="employeeNo" label="工号" min-width="90" />
          <el-table-column prop="realName" label="姓名" min-width="90" />
          <el-table-column prop="title" label="职称" min-width="90" />
          <el-table-column prop="department" label="科室" min-width="100" />
          <el-table-column prop="phone" label="手机号" min-width="120" />
          <el-table-column label="在线状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.onlineStatus === 'ONLINE' ? 'success' : 'info'" size="small">{{ onlineText(row.onlineStatus) }}</el-tag>
            </template>
          </el-table-column>
        </template>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="(dialogMode === 'create' ? '新增' : '编辑') + (isPatient ? '患者' : '医生')"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <template v-if="isPatient">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="登录用户名，2-20 位" />
          </el-form-item>
          <el-form-item label="身份证号" prop="idCard">
            <el-input v-model="form.idCard" placeholder="18 位身份证号" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="工号" prop="employeeNo">
            <el-input v-model="form.employeeNo" placeholder="如 D001" />
          </el-form-item>
          <el-form-item label="职称" prop="title">
            <el-input v-model="form.title" placeholder="如 主任医师" />
          </el-form-item>
          <el-form-item label="科室" prop="department">
            <el-input v-model="form.department" placeholder="如 呼吸科" />
          </el-form-item>
          <el-form-item label="简介" prop="bio">
            <el-input v-model="form.bio" type="textarea" :rows="2" placeholder="擅长领域 / 简介（可选）" />
          </el-form-item>
          <el-form-item label="在线状态" prop="onlineStatus">
            <el-radio-group v-model="form.onlineStatus">
              <el-radio value="OFFLINE">离线</el-radio>
              <el-radio value="ONLINE">在线</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">正常</el-radio>
            <el-radio value="DISABLED">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="dialogMode === 'create' ? '密码' : '重置密码'" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="dialogMode === 'create' ? '6-64 位，缺省 123456' : '留空则不修改密码'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  fetchAdminUsers, updateUserStatus, createAdminUser, updateAdminUser,
} from '@/api/admin'

const activeTab = ref('PATIENT')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })
const keywordInput = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('create') // create | edit
const saving = ref(false)
const editingId = ref(null)
const formRef = ref()

const isPatient = computed(() => activeTab.value === 'PATIENT')

const defaultForm = () => ({
  username: '', realName: '', gender: '男', phone: '', idCard: '',
  employeeNo: '', title: '', department: '', bio: '', onlineStatus: 'OFFLINE',
  status: 'ACTIVE', password: '',
})
const form = reactive(defaultForm())

/** 加载用户列表（后端分页 + 关键词模糊搜索） */
async function loadUsers() {
  loading.value = true
  try {
    const res = await fetchAdminUsers(activeTab.value, {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  query.keyword = keywordInput.value.trim()
  loadUsers()
}

function onTabChange() {
  query.page = 1
  query.keyword = ''
  keywordInput.value = ''
  loadUsers()
}

function onPageChange(page) {
  query.page = page
  loadUsers()
}

const statusText = (s) => (s === 'ACTIVE' ? '正常' : '已禁用')
const statusType = (s) => (s === 'ACTIVE' ? 'success' : 'danger')
const onlineText = (s) => (s === 'ONLINE' ? '在线' : '离线')

/** 启用 / 禁用（二次确认） */
async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const label = next === 'ACTIVE' ? '启用' : '禁用'
  const name = isPatient.value ? row.username : row.employeeNo
  try {
    await ElMessageBox.confirm(`确定要${label}用户「${name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateUserStatus(activeTab.value, row.id, next)
    ElMessage.success(`${label}成功`)
    loadUsers()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, defaultForm())
  Object.assign(form, {
    username: row.username || '',
    realName: row.realName || '',
    gender: row.gender || '男',
    phone: row.phone || '',
    idCard: row.idCard || '',
    employeeNo: row.employeeNo || '',
    title: row.title || '',
    department: row.department || '',
    bio: row.bio || '',
    onlineStatus: row.onlineStatus || 'OFFLINE',
    status: row.status || 'ACTIVE',
    password: '',
  })
  dialogVisible.value = true
}

/** 动态校验规则：按角色与新增/编辑模式区分 */
const rules = computed(() => {
  const base = {
    realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
    phone: [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      { pattern: /^\d{11}$/, message: '手机号需为 11 位数字', trigger: 'blur' },
    ],
  }
  if (dialogMode.value === 'create') {
    base.password = [
      { required: true, message: '请输入密码（6-64 位）', trigger: 'blur' },
      { min: 6, max: 64, message: '密码长度需为 6-64 位', trigger: 'blur' },
    ]
  }
  if (isPatient.value) {
    base.username = [{ required: true, message: '请输入登录用户名', trigger: 'blur' }]
    // 身份证号：新增必填；编辑时若填写也必须为 18 位（17 位数字 + 1 位数字或 X），杜绝不足 18 位保存
    base.idCard = [
      { pattern: /^\d{17}[\dXx]$/, message: '身份证号需为 18 位（17 位数字 + 1 位数字或 X）', trigger: 'blur' },
    ]
    if (dialogMode.value === 'create') {
      base.idCard.unshift({ required: true, message: '请输入身份证号', trigger: 'blur' })
    }
  } else {
    base.employeeNo = [{ required: true, message: '请输入工号', trigger: 'blur' }]
  }
  return base
})

/** 提交新增 / 编辑 */
async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = isPatient.value
      ? {
          username: form.username,
          realName: form.realName,
          gender: form.gender,
          phone: form.phone,
          idCard: form.idCard,
          status: form.status,
          password: form.password,
        }
      : {
          employeeNo: form.employeeNo,
          realName: form.realName,
          gender: form.gender,
          title: form.title,
          phone: form.phone,
          department: form.department,
          bio: form.bio,
          onlineStatus: form.onlineStatus,
          status: form.status,
          password: form.password,
        }
    if (dialogMode.value === 'create') {
      await createAdminUser(activeTab.value, payload)
      ElMessage.success('新增成功')
    } else {
      await updateAdminUser(activeTab.value, editingId.value, payload)
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    loadUsers()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    saving.value = false
  }
}

onMounted(loadUsers)
</script>

<style scoped>
.user-list-page { padding: 20px 24px; }
.page-head { margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; margin: 0; color: var(--text-color-primary, #1F2937); }
.page-desc { font-size: 13px; color: #6B7280; margin: 6px 0 0; }
.main-card { border-radius: 12px; }
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 4px; }
.role-tabs { flex: 1; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.search-input { width: 240px; }
.data-table { margin-top: 8px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
