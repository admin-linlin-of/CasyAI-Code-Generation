<template>
  <a-layout-header class="global-header">
    <div class="global-header__left">
      <a class="global-header__brand" href="/" @click.prevent="router.push('/')">
        <img class="global-header__logo" :src="logoSrc" alt="logo" />
        <span class="global-header__title">{{ title }}</span>
      </a>

      <a-menu
        class="global-header__menu"
        mode="horizontal"
        :items="menuItems"
        :selected-keys="selectedKeys"
        @click="onMenuClick"
      />
    </div>

    <div class="global-header__right">
      <a-button type="primary">登录</a-button>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import type { MenuProps } from 'ant-design-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps<{
  menuItems?: MenuProps['items']
  title?: string
  logoSrc?: string
}>()

const title = computed(() => props.title ?? 'Casy AI Code Mother')
const logoSrc = computed(() => props.logoSrc ?? '/logo.png')

const router = useRouter()
const route = useRoute()

// 每次改变路由或刷新页面时都会自动更新 current的值，从而实现高亮
const selectedKeys = computed(() => {
  const currentPath = route.path
  const matched = (props.menuItems ?? []).find((i) => {
    if (!i || typeof i !== 'object') return false
    const anyItem = i as any
    return typeof anyItem.path === 'string' && anyItem.path === currentPath
  }) as any
  return matched?.key ? [String(matched.key)] : []
})

const onMenuClick: MenuProps['onClick'] = (info) => {
  const key = String(info.key)
  const item = (props.menuItems ?? []).find(
    (i) => i && typeof i === 'object' && String((i as any).key) === key,
  ) as any
  if (item?.path) router.push(item.path)
}
</script>

<style scoped>
.global-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-inline: 16px;
  background: #fff;
  border-bottom: 1px solid rgba(5, 5, 5, 0.06);
}

.global-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.global-header__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.global-header__logo {
  width: 40px;
  height: 40px;
  object-fit: contain;
}

.global-header__title {
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.global-header__menu {
  flex: 1;
  min-width: 0;
  border-bottom: none;
  background: transparent;
}

.global-header__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

@media (max-width: 768px) {
  .global-header__title {
    display: none;
  }
}
</style>

