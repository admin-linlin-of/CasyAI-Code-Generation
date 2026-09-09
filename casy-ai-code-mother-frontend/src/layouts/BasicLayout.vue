<template>
  <a-layout class="basic-layout">
    <GlobalHeader :menu-items="headerMenuItems" />
    <a-layout-content :class="['basic-layout__content', { 'basic-layout__content--compact': noPadding }]">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </a-layout-content>
    <GlobalFooter v-if="!hideFooter" />
  </a-layout>
</template>

<script setup lang="ts">
import { type MenuProps } from 'ant-design-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import router from '@/router'
import { canAccessRoute } from '@/utils/access'

type MenuItemConfig = {
  key: string
  label: string
  path?: string
}

// 菜单配置项
const originItems = [
  { key: 'home', label: '首页', path: '/' },
  { key: 'appManage', label: '应用管理', path: '/app/manage' },
  { key: 'chatHistoryManage', label: '对话管理', path: '/chatHistory/manage' },
  { key: 'versionManage', label: '版本管理', path: '/appVersion/manage' },
  { key: 'aiModelManage', label: '模型管理', path: '/aiModel/manage' },
  { key: 'sysParamManage', label: '参数管理', path: '/sysParam/manage' },
  { key: 'userManage', label: '用户管理', path: '/user/userManage' },
]
const loginUserStore = useLoginUserStore()
const route = useRoute()
const hideFooter = computed(() => Boolean(route.meta.hideFooter))
const noPadding = computed(() => Boolean(route.meta.noPadding))

// 菜单过滤逻辑与路由守卫一致，复用 canAccessRoute，避免两处维护不同规则
const filterMenus = (menus: MenuItemConfig[]) => {
  const loginUser = loginUserStore.loginUser
  return menus.filter((menu) => {
    const menuPath = menu.path
    if (!menuPath) return true
    const routeMeta = router.getRoutes().find((r) => r.path === menuPath)?.meta
    if (!routeMeta) return true
    if (!loginUser.id) return !routeMeta.roles?.length
    return canAccessRoute(routeMeta, loginUser)
  })
}

const menuItems = computed<MenuItemConfig[]>(() => filterMenus(originItems))

const headerMenuItems = computed<MenuProps['items']>(() =>
  menuItems.value.map((item) => ({
    key: item.key,
    label: item.label,
    ...(item.path ? { path: item.path } : {}),
  })) as MenuProps['items'],
)
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: var(--bg-page);
}

.basic-layout__content {

}

.basic-layout__content--compact {
  padding: 0;
  padding-bottom: 0;
}
</style>
