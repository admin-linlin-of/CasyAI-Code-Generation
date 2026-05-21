/**
 * 路由守卫：控制「能否进入页面」（UX 层，可被绕过，真正安全在后端注解）
 *
 * 鉴权流程：
 * 1. 白名单直接放行
 * 2. 首次进入时拉取登录用户（含 userRole + permissions）
 * 3. 用 loginUser.id 判断是否登录（不再用 permissions 是否存在来判断）
 * 4. 用 canAccessRoute 按 meta.roles 判断页面角色权限
 */
import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'
import { canAccessRoute } from '@/utils/access'

// 只在应用首次路由跳转时请求一次 /user/get/login，避免每次切换路由都发请求
let firstFetchLoginUser = true

const WHITE_LIST = ['/', '/user/login', '/user/register']

router.beforeEach(async (to) => {
  if (WHITE_LIST.includes(to.path)) {
    return true
  }

  const loginUserStore = useLoginUserStore()
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    firstFetchLoginUser = false
  }

  const loginUser = loginUserStore.loginUser
  // 未登录：跳转登录页，并带上原目标地址便于登录后回跳
  if (!loginUser.id) {
    message.error('请先登录！')
    return `/user/login?redirect=${encodeURIComponent(to.fullPath)}`
  }

  // 已登录但角色不足：跳转首页（不再误导向登录页）
  if (!canAccessRoute(to.meta, loginUser)) {
    message.error('无权访问！')
    return '/'
  }

  return true
})
