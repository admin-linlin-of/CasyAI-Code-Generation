import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/userController.ts'

// 未登录时的默认状态，注意没有 id 字段
const DEFAULT_LOGIN_USER: API.LoginUserVO = {
  userName: '未登录',
}

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({ ...DEFAULT_LOGIN_USER })

  /**
   * 从后端拉取当前登录用户
   * 成功：写入 userRole（页面鉴权）和 permissions（接口鉴权）
   * 失败：重置为未登录状态，供路由守卫通过 id 判断
   */
  async function fetchLoginUser() {
    const res = await getLoginUser()
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data
    } else {
      resetLoginUser()
    }
  }

  function setLoginUser(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
  }

  /** 登出时调用，清空 id 使路由守卫判定为未登录 */
  function resetLoginUser() {
    loginUser.value = { ...DEFAULT_LOGIN_USER }
  }

  return { loginUser, setLoginUser, fetchLoginUser, resetLoginUser }
})
