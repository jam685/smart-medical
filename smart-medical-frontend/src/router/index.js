import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

// 公共页面（无需登录）
const publicRoutes = [
  { path: '/', name: 'Home', component: () => import('@/views/public/Home.vue') },
  { path: '/login/patient', name: 'LoginPatient', component: () => import('@/views/public/LoginPatient.vue') },
  { path: '/login/doctor', name: 'LoginDoctor', component: () => import('@/views/public/LoginDoctor.vue') },
  { path: '/login/admin', name: 'LoginAdmin', component: () => import('@/views/public/LoginAdmin.vue') },
  { path: '/forbidden', name: 'Forbidden', component: () => import('@/views/public/Forbidden.vue') },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/public/NotFound.vue') },
]

// 患者端
const patientRoutes = {
  path: '/patient',
  component: () => import('@/layouts/PatientLayout.vue'),
  meta: { requiresAuth: true, role: 'PATIENT' },
  children: [
    { path: '', redirect: '/patient/home' },
    { path: 'home', name: 'PatientIndex', component: () => import('@/views/patient/Index.vue') },
    { path: 'profile', name: 'PatientProfile', component: () => import('@/views/patient/Profile.vue') },
    { path: 'appointments', name: 'PatientAppointmentList', component: () => import('@/views/patient/AppointmentList.vue') },
    { path: 'appointments/create', name: 'PatientAppointmentCreate', component: () => import('@/views/patient/AppointmentCreate.vue') },
    { path: 'appointments/:id', name: 'PatientAppointmentDetail', component: () => import('@/views/patient/AppointmentDetail.vue') },
    { path: 'visits', name: 'PatientVisitList', component: () => import('@/views/patient/VisitList.vue') },
    { path: 'visits/:id', name: 'PatientVisitDetail', component: () => import('@/views/patient/VisitDetail.vue') },
    { path: 'reports', name: 'PatientReportList', component: () => import('@/views/patient/ReportList.vue') },
    { path: 'reports/upload', name: 'PatientReportUpload', component: () => import('@/views/patient/ReportUpload.vue') },
    { path: 'reports/:id', name: 'PatientReportDetail', component: () => import('@/views/patient/ReportDetail.vue') },
    { path: 'prescriptions/:visitId', name: 'PatientPrescriptionDetail', component: () => import('@/views/patient/PrescriptionDetail.vue') },
    { path: 'online-consults', name: 'PatientConsultList', component: () => import('@/views/patient/ConsultList.vue') },
    { path: 'online-consults/create', name: 'PatientConsultCreate', component: () => import('@/views/patient/ConsultCreate.vue') },
    { path: 'online-consults/:id', name: 'PatientConsultDetail', component: () => import('@/views/patient/ConsultDetail.vue') },
    { path: 'ai-chat', name: 'PatientAiChat', component: () => import('@/views/patient/AiChat.vue') },
  ]
}

// 医生端
const doctorRoutes = {
  path: '/doctor',
  component: () => import('@/layouts/DoctorLayout.vue'),
  meta: { requiresAuth: true, role: 'DOCTOR' },
  children: [
    { path: '', redirect: '/doctor/home' },
    { path: 'home', name: 'DoctorIndex', component: () => import('@/views/doctor/Index.vue') },
    { path: 'profile', name: 'DoctorProfile', component: () => import('@/views/doctor/Profile.vue') },
    { path: 'appointments', name: 'DoctorAppointmentList', component: () => import('@/views/doctor/AppointmentList.vue') },
    { path: 'online-consults', name: 'DoctorConsultList', component: () => import('@/views/doctor/ConsultList.vue') },
    { path: 'online-consults/:id', name: 'DoctorConsultHandle', component: () => import('@/views/doctor/ConsultHandle.vue') },
    { path: 'ai-analysis', name: 'DoctorAiAnalysis', component: () => import('@/views/doctor/AiAnalysis.vue') },
    { path: 'schedule', name: 'DoctorSchedule', component: () => import('@/views/doctor/Schedule.vue') },
  ]
}

// 管理员端
const adminRoutes = {
  path: '/admin',
  component: () => import('@/layouts/AdminLayout.vue'),
  meta: { requiresAuth: true, role: 'ADMIN' },
  children: [
    { path: '', redirect: '/admin/home' },
    { path: 'home', name: 'AdminIndex', component: () => import('@/views/admin/Index.vue') },
    { path: 'users', name: 'AdminUserList', component: () => import('@/views/admin/UserList.vue') },
    { path: 'roles', name: 'AdminRoleList', component: () => import('@/views/admin/RoleList.vue') },
    { path: 'audit-logs', name: 'AdminAuditLogList', component: () => import('@/views/admin/AuditLogList.vue') },
    { path: 'audit-logs/:id', name: 'AdminAuditLogDetail', component: () => import('@/views/admin/AuditLogDetail.vue') },
    { path: 'knowledge-base', name: 'AdminKnowledgeBase', component: () => import('@/views/admin/KnowledgeBase.vue') },
    { path: 'prompt-templates', name: 'AdminPromptTemplate', component: () => import('@/views/admin/PromptTemplate.vue') },
    { path: 'model-configs', name: 'AdminModelConfig', component: () => import('@/views/admin/ModelConfig.vue') },
    { path: 'data-quality', name: 'AdminDataQuality', component: () => import('@/views/admin/DataQuality.vue') },
    { path: 'schedules', name: 'AdminScheduleManage', component: () => import('@/views/admin/ScheduleManage.vue') },
    { path: 'sensitive-review', name: 'AdminSensitiveReview', component: () => import('@/views/admin/SensitiveReview.vue') },
    { path: 'online-consults', name: 'AdminConsultMonitor', component: () => import('@/views/admin/ConsultMonitor.vue') },
    { path: 'online-consults/:id', name: 'AdminConsultMonitorDetail', component: () => import('@/views/admin/ConsultMonitorDetail.vue') },
  ]
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...publicRoutes, patientRoutes, doctorRoutes, adminRoutes],
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const token = userStore.token
  const requiredRole = to.meta.role

  // 无需登录
  if (!to.meta.requiresAuth) {
    next()
    return
  }

  // 未登录 -> 跳转对应登录页
  if (!token) {
    const loginMap = { PATIENT: '/login/patient', DOCTOR: '/login/doctor', ADMIN: '/login/admin' }
    next(loginMap[requiredRole] || '/')
    return
  }

  // 已登录但角色不匹配
  if (requiredRole && userStore.user?.role !== requiredRole) {
    next('/forbidden')
    return
  }

  next()
})

export default router
