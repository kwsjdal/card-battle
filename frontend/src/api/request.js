import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 8000 })

http.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = 'Bearer ' + token
  return cfg
})

http.interceptors.response.use(
  (resp) => {
    if (resp.data.code !== 0) {
      ElMessage.error(resp.data.msg || '请求失败')
      return Promise.reject(resp.data)
    }
    return resp.data.data
  },
  (err) => {
    /** 网络层错误(err.response 为 undefined): 后端不可达 / 超时 / DNS 失败, 必须给用户提示 */
    if (!err.response) {
      ElMessage.error('网络异常，请稍后重试')
      return Promise.reject(err)
    }
    const status = err.response.status
    const body = err.response.data || {}
    if (status === 403 && body.msg === '账号已被封禁') {
      ElMessage.error('账号已被封禁，请联系管理员')
      localStorage.clear()
      location.href = '/login'
      return Promise.reject(err)   // 阻止穿透到 401 分支
    }
    if (status === 401) {
      // 401 可能是过期, 也可能是"账号已在其他设备登录"(单设备登录), 把后端 msg 展示出来
      ElMessage.error(body.msg || '登录已过期，请重新登录')
      localStorage.clear()
      location.href = '/login'
      return Promise.reject(err)
    }
    /** 其它 HTTP 错误(4xx/5xx): 尽量展示后端 msg, 否则兜底 */
    ElMessage.error(body.msg || `请求失败(${status})`)
    return Promise.reject(err)
  }
)

/**
 * 生成一次性幂等键。
 * 消耗型操作(抽卡/充值)每次点击生成一个新的 key 随请求带上;
 * 若因超时/网络重试而重复提交同一个 key, 服务端直接回放首次结果, 不会二次扣费。
 */
export function idemKey() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID()
  return 'k' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}

export default http
