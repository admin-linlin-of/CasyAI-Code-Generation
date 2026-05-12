import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import Login from '@/views/user/Login.vue'
import UserManager from '../views/user/UserManager.vue'
import Register from '@/views/user/Register.vue'
import ACCESS_ENUM from '@/constant/constant.ts'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: Home,
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: Login,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: Register,
    },
    {
      path: '/user/userManage',
      name: '用户管理',
      component: UserManager,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
  ],
})

export default router
