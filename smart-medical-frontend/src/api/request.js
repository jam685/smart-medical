import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

// 后端错误提示时，把 traceId 附在消息末尾，方便用户发给客服排查
// 后端 ResponseBodyTraceAdvice 会在每个响应 Result 里填 traceId（成功/失败都有）
function showErrorWithTrace(message, traceId) {
  const hint = traceId ? `  [编号: ${traceId}]` : ''
  ElMessage.error({
    message: `${message || '请求失败'}${hint}`,
    duration: 6000,
    showClose: true,
  })
}

// Axios 实例：baseURL 对齐后端 context-path，Vite 代理把 /api 转发到 http://localhost:8080
const request = axios.create({
  baseURL: '/api/smart-medical/v1',
  timeout: 15000,
})

// 请求拦截器：自动携带 token
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    const data = response.data
    // 后端统一返回格式 { code, message, data, traceId }
    if (data && typeof data.code !== 'undefined') {
      if (data.code === 0 || data.code === 200) {
        return data.data
      }
      // 业务错误（code != 0）：后端已附 traceId
      showErrorWithTrace(data.message, data.traceId)
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  (error) => {
    const status = error.response?.status
    const traceId = error.response?.data?.traceId
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.push('/')
      ElMessage.error('登录已过期，请重新登录')
    } else if (status === 403) {
      showErrorWithTrace('无权限访问', traceId)
    } else if (status === 404) {
      showErrorWithTrace('资源不存在', traceId)
    } else if (status >= 500) {
      showErrorWithTrace('服务器错误，请稍后重试', traceId)
    } else {
      showErrorWithTrace(error.message || '请求失败', traceId)
    }
    return Promise.reject(error)
  }
)

export default request
