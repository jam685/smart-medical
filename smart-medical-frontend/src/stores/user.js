import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_KEY = 'smart_medical_token'
const USER_KEY = 'smart_medical_user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || null)

  function setAuth(t, u) {
    token.value = t
    user.value = u
    localStorage.setItem(TOKEN_KEY, t)
    localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  async function login(payload) {
    // TODO: 接入真实登录 API
    // const res = await request.post('/api/auth/login', payload)
    // setAuth(res.data.token, res.data.user)
    // return res.data
    const mockToken = 'mock-token-' + Date.now()
    const mockUser = {
      id: 1,
      role: payload.role,
      realName: payload.realName || '测试用户',
      username: payload.username,
    }
    setAuth(mockToken, mockUser)
    return { token: mockToken, user: mockUser }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  // 兼容登录页的调用方式：setSession(user, token)
  function setSession(user, token = 'demo-token') {
    setAuth(token, user)
  }

  return { token, user, isLoggedIn, role, login, logout, setAuth, setSession }
})
