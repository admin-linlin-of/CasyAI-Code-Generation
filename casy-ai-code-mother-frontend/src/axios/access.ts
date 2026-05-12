import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'

let firstFetchLoginUser = true

const WHITE_LIST = ['/user/login', '/user/register']

router.beforeEach(async (to) => {
  if (WHITE_LIST.includes(to.path)) {
    return true
  }

  const loginUserStore = useLoginUserStore()
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    firstFetchLoginUser = false
  }

  const access = to.meta.access as string | undefined

  const permissions = loginUserStore.loginUser.permissions
  if (permissions) {
    if ((access && permissions.indexOf(access) !== -1) || permissions[0] === '*' || !access) {
      return true
    }
    message.error('无权访问，请重新登录！')
    return `/user/login?redirect=${encodeURIComponent(to.fullPath)}`
  }
  message.error('请先登录！')
  return `/user/login?redirect=${encodeURIComponent(to.fullPath)}`
})
