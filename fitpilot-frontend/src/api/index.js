import axios from 'axios'

// 生成/读取会话 ID，存 localStorage（每次刷新页面不变）
// 用于后端按 session 维度做限流/去重
const SESSION_KEY = 'fitpilot:sessionId'
function getOrCreateSessionId() {
  let sid = localStorage.getItem(SESSION_KEY)
  if (!sid) {
    sid = (crypto.randomUUID && crypto.randomUUID())
        || (Date.now().toString(36) + Math.random().toString(36).slice(2, 10))
    localStorage.setItem(SESSION_KEY, sid)
  }
  return sid
}

const api = axios.create({ baseURL: '/api' })

// 拦截器：自动带 session header
api.interceptors.request.use((config) => {
  config.headers = config.headers || {}
  config.headers['X-Fitpilot-Session'] = getOrCreateSessionId()
  return config
})

export default api