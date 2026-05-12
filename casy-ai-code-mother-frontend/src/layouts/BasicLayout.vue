<template>
  <a-layout class="basic-layout">
    <GlobalHeader :menu-items="headerMenuItems" />
    <a-layout-content class="basic-layout__content">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </a-layout-content>
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import { type MenuProps } from 'ant-design-vue'
import { computed } from 'vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import router from '@/router'

type MenuItemConfig = {
  key: string
  label: string
  path?: string
}

// 菜单配置项
const originItems = [
  { key: 'home', label: '首页', path: '/' },
  { key: 'userManage', label: '用户管理', path: '/user/userManage' },
]
const loginUserStore = useLoginUserStore()

const filterMenus = (menus: MenuItemConfig[]) => {
  const permissions = loginUserStore.loginUser.permissions
  return menus.filter((menu) => {
    const menuPath = menu.path
    if (!menuPath) return true
    const access = router.getRoutes().find((r) => r.path === menuPath)?.meta
      .access as string | undefined
    if (!permissions) {
      return false
    }
    if (!access || permissions[0] === '*') return true
    return permissions.indexOf(access) !== -1
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
}

.basic-layout__content {
  padding: 24px;
  padding-bottom: 72px; /* 预留底部固定 Footer 的高度 */
}
</style>
