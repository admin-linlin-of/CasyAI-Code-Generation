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
      <a-button class="theme-btn" @click="themeStore.toggleTheme">
        {{ themeStore.isDark ? '浅色' : '深色' }}
      </a-button>
      <div v-if="loginUserStore.loginUser.id">
        <a-dropdown>
          <a-space>
            <a-avatar :src="loginUserStore.loginUser.userAvatar" />
            {{ loginUserStore.loginUser.userName ?? '无名' }}
          </a-space>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="doLogout">
                <LogoutOutlined />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
      <div v-else>
        <a-button type="primary" href="/user/login">登录</a-button>
      </div>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { type MenuProps, message } from 'ant-design-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogoutOutlined } from '@ant-design/icons-vue'
import defaultLogoUrl from '@/assets/logo.png?url'

const props = defineProps<{
  menuItems?: MenuProps['items']
  title?: string
  logoSrc?: string
}>()

import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import { useThemeStore } from '@/stores/theme'

const loginUserStore = useLoginUserStore()
const themeStore = useThemeStore()
loginUserStore.fetchLoginUser()

const title = computed(() => props.title ?? 'Casy AI Code Mother')
const logoSrc = computed(() => props.logoSrc ?? defaultLogoUrl)

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

// 用户注销
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    // 清空 id，使路由守卫判定为未登录
    loginUserStore.resetLoginUser()
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.global-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-inline: 16px;
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  backdrop-filter: blur(8px);
  color: var(--text-main);
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

.theme-btn {
  min-width: 64px;
}

@media (max-width: 768px) {
  .global-header__title {
    display: none;
  }
}
</style>
