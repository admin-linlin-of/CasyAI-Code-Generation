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
import type { MenuProps } from 'ant-design-vue'
import { computed } from 'vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'

type MenuItemConfig = {
  key: string
  label: string
  path?: string
}

const menuItems = computed<MenuItemConfig[]>(() => [
  { key: 'home', label: '首页', path: '/' },
  { key: 'userManage', label: '用户管理', path: '/user/userManage' },
])

const headerMenuItems = computed<MenuProps['items']>(() =>
  menuItems.value.map((item) => ({
    key: item.key,
    label: item.label,
    ...(item.path ? { path: item.path } : {}),
  })),
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
