import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

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
    // 后端统一返回格式 { code, message, data }
    if (data && typeof data.code !== 'undefined') {
      if (data.code === 0 || data.code === 200) {
        return data.data
      }
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.push('/')
      ElMessage.error('登录已过期，请重新登录')
    } else if (status === 403) {
      ElMessage.error('无权限访问')
    } else if (status === 404) {
      ElMessage.error('资源不存在')
    } else if (status >= 500) {
      ElMessage.error('服务器错误，请稍后重试')
    } else {
      ElMessage.error(error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

export default request
