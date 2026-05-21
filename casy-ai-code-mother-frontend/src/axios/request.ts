import axios from 'axios'
import { message } from 'ant-design-vue'

const myAxios = axios.create({
  baseURL: 'http://localhost:8124/api',
  timeout: 60000,
  withCredentials: true,
})

/**
 * 与后端 ErrorCode 对齐：
 * - 40100 NOT_LOGIN_ERROR：未登录（Sa-Token NotLoginException）
 * - 40102 LOGIN_ERROR：Sa-Token 其他登录态异常（保留兼容）
 * - 40101 NO_AUTH_ERROR：已登录但无权限（NotPermissionException / NotRoleException）
 * - 40300 FORBIDDEN_ERROR：禁止访问
 */
const LOGIN_CODES = [40100, 40102]

myAxios.interceptors.request.use(
  function (config) {
    return config
  },
  function (error) {
    return Promise.reject(error)
  },
)

myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    // 未登录 / 登录态失效 → 跳转登录页
    if (LOGIN_CODES.includes(data.code)) {
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    } else if (data.code === 40101 || data.code === 40300) {
      // 已登录但无权限 → 仅提示，不跳转登录页
      message.error(data.message || '无权限')
    }
    return response
  },
  function (error) {
    return Promise.reject(error)
  },
)

export default myAxios
