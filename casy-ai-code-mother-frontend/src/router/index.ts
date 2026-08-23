import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import Login from '@/views/user/Login.vue'
import UserManager from '../views/user/UserManager.vue'
import Register from '@/views/user/Register.vue'
import AppChat from '@/views/app/AppChat.vue'
import AppManage from '@/views/app/AppManage.vue'
import AppEdit from '@/views/app/AppEdit.vue'
import ChatHistoryManage from '@/views/app/ChatHistoryManage.vue'
import VersionManage from '@/views/app/VersionManage.vue'
import AiModelManage from '@/views/ai/AiModelManage.vue'
import ROLE_ENUM from '@/constant/constant.ts'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: Home,
      // 无 meta.roles：登录即可访问
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
        roles: [ROLE_ENUM.ADMIN],
      },
    },
    {
      path: '/app/chat/:id',
      name: '应用生成对话',
      component: AppChat,
      props: true,
      meta: {
        hideFooter: true,
        noPadding: true,
      },
    },
    {
      path: '/app/manage',
      name: '应用管理',
      component: AppManage,
      meta: {
        roles: [ROLE_ENUM.ADMIN],
      },
    },
    {
      path: '/chatHistory/manage',
      name: '对话管理',
      component: ChatHistoryManage,
      meta: {
        roles: [ROLE_ENUM.ADMIN],
      },
    },
    {
      path: '/appVersion/manage',
      name: '版本管理',
      component: VersionManage,
      meta: {
        roles: [ROLE_ENUM.ADMIN],
      },
    },
    {
      path: '/aiModel/manage',
      name: '模型管理',
      component: AiModelManage,
      meta: {
        roles: [ROLE_ENUM.ADMIN],
      },
    },
    {
      path: '/app/edit/:id',
      name: '应用编辑',
      component: AppEdit,
      props: true,
    },
  ],
})

export default router
