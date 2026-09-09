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
      <div v-if="githubUrl || giteeUrl" class="global-header__social">
        <a
          v-if="githubUrl"
          class="social-link"
          :href="githubUrl"
          target="_blank"
          rel="noopener noreferrer"
          title="GitHub"
          aria-label="GitHub"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              fill="currentColor"
              d="M12 0C5.37 0 0 5.37 0 12c0 5.3 3.44 9.8 8.21 11.39.6.11.82-.26.82-.58 0-.28-.01-1.02-.02-2-3.34.73-4.04-1.61-4.04-1.61-.55-1.39-1.33-1.76-1.33-1.76-1.09-.74.08-.73.08-.73 1.2.09 1.84 1.24 1.84 1.24 1.07 1.83 2.8 1.3 3.49 1 .11-.78.42-1.3.76-1.6-2.67-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.12-.3-.54-1.52.12-3.18 0 0 1.01-.32 3.3 1.23a11.5 11.5 0 0 1 6 0c2.29-1.55 3.3-1.23 3.3-1.23.66 1.66.24 2.88.12 3.18.77.84 1.24 1.91 1.24 3.22 0 4.61-2.81 5.62-5.48 5.92.43.37.81 1.1.81 2.22 0 1.6-.01 2.89-.01 3.29 0 .32.22.7.82.58A12.01 12.01 0 0 0 24 12c0-6.63-5.37-12-12-12z"
            />
          </svg>
        </a>
        <a
          v-if="giteeUrl"
          class="social-link social-link--gitee"
          :href="giteeUrl"
          target="_blank"
          rel="noopener noreferrer"
          title="Gitee"
          aria-label="Gitee"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              fill="currentColor"
              d="M11.984 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0a12 12 0 0 0-.016 0zm6.09 5.333c.328 0 .593.266.592.593v1.482a.594.594 0 0 1-.593.592H9.777c-.982 0-1.778.796-1.778 1.778v5.63c0 .327.266.592.593.592h5.63c.982 0 1.778-.796 1.778-1.778v-.296a.593.593 0 0 0-.592-.593h-4.148a.592.592 0 0 1-.592-.592v-1.482a.593.593 0 0 1 .593-.592h6.666c.327 0 .593.265.593.592v3.407a2.963 2.963 0 0 1-2.963 2.963h-7.407A2.963 2.963 0 0 1 4.74 13.63V8.296a2.963 2.963 0 0 1 2.963-2.963h10.37z"
            />
          </svg>
        </a>
      </div>
      <nav class="global-header__about" aria-label="关于">
        <button
          class="header-text-btn"
          type="button"
          :class="{ 'header-text-btn--active': route.path === '/about/author' }"
          @click="router.push('/about/author')"
        >
          <UserOutlined />
          关于作者
        </button>
        <button
          class="header-text-btn"
          type="button"
          :class="{ 'header-text-btn--active': route.path === '/about/project' }"
          @click="router.push('/about/project')"
        >
          <ReadOutlined />
          关于项目
        </button>
      </nav>
      <!-- 浅色：月亮 + 黑底；深色：太阳 + 白底 -->
      <a-tooltip :title="themeStore.isDark ? '切换到浅色模式' : '切换到深色模式'">
        <a-button
          class="theme-btn"

          type="text"
          @click="onToggleTheme"
        >
          <img
            :src="icon"
            alt="theme"
            class="theme-btn__icon"
          />
        </a-button>
      </a-tooltip>
      <div v-if="loginUserStore.loginUser.id">
        <a-dropdown>
          <a-space>
            <ExternalAvatar
              :src="loginUserStore.loginUser.userAvatar"
              :fallback="loginUserStore.loginUser.userName"
              :size="32"
            />
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
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogoutOutlined, ReadOutlined, UserOutlined } from '@ant-design/icons-vue'
import defaultLogoUrl from '@/assets/logo.png?url'
import sunIcon from '@/assets/太阳.svg?url'
import moonIcon from '@/assets/月亮.svg?url'
import ExternalAvatar from '@/components/ExternalAvatar.vue'
import { listPublicSysParams } from '@/api/sysParamController'
import { SYS_PARAM_KEY, isHttpUrl } from '@/constant/sysParam'

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

const githubUrl = ref('')
const giteeUrl = ref('')

const loadSocialLinks = async () => {
  try {
    const res = await listPublicSysParams()
    if (res.data.code !== 0 || !res.data.data) return
    const params = res.data.data
    const github = params[SYS_PARAM_KEY.GITHUB_URL]
    const gitee = params[SYS_PARAM_KEY.GITEE_URL]
    githubUrl.value = isHttpUrl(github) ? github.trim() : ''
    giteeUrl.value = isHttpUrl(gitee) ? gitee.trim() : ''
  } catch {
    // 公开参数加载失败不影响页面主流程
  }
}

onMounted(loadSocialLinks)

const icon = computed(() => (themeStore.isDark.value ? moonIcon : sunIcon))

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

// 把点击事件交给 themeStore，用于 View Transition 圆形扩散动画的圆心
const onToggleTheme = (event: MouseEvent) => {
  themeStore.toggleTheme(event)
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

.global-header__social {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.social-link {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: var(--text-main);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease, color 0.2s ease;
}

.social-link:hover {
  background: color-mix(in srgb, var(--text-main) 8%, transparent);
}

.social-link--gitee {
  color: #c71d23;
}

.social-link svg {
  width: 18px;
  height: 18px;
  display: block;
}

.global-header__about {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding-right: 12px;
  margin-right: 4px;
  border-right: 1px solid var(--border-color);
}

.header-text-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--text-sub);
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.header-text-btn :deep(.anticon) {
  font-size: 14px;
}

.header-text-btn:hover {
  color: var(--text-main);
  background: color-mix(in srgb, var(--text-main) 6%, transparent);
}

.header-text-btn--active {
  color: var(--tag-text-active);
  background: var(--tag-bg-active);
  border-color: var(--tag-bg-active);
}

.header-text-btn--active:hover {
  color: var(--tag-text-active);
  background: #4096ff;
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
  width: 36px;
  height: 36px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: none;
}

.theme-btn--light {
  background: #000;
}

.theme-btn--dark {
  background: #fff;
}

.theme-btn__icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
  display: block;
}

@media (max-width: 768px) {
  .global-header__title {
    display: none;
  }

  .global-header__about {
    padding-right: 8px;
    gap: 4px;
  }

  .header-text-btn {
    padding: 0 8px;
    font-size: 12px;
  }
}
</style>
