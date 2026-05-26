import { computed, ref } from 'vue'
import { theme } from 'ant-design-vue'
import { withThemeTransition, type ThemeTransitionPoint } from '@/utils/themeTransition'

type ThemeMode = 'light' | 'dark'

const THEME_KEY = 'casy-theme-mode'
const themeMode = ref<ThemeMode>('light')

const applyTheme = (mode: ThemeMode) => {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', mode)
}

export const initTheme = () => {
  if (typeof window === 'undefined') return
  const cache = window.localStorage.getItem(THEME_KEY) as ThemeMode | null
  themeMode.value = cache === 'dark' ? 'dark' : 'light'
  applyTheme(themeMode.value)
}

export const useThemeStore = () => {
  const isDark = computed(() => themeMode.value === 'dark')
  const antTheme = computed(() => ({
    algorithm: isDark.value ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      borderRadius: 10,
      colorPrimary: '#1677ff',
    },
  }))

  const setTheme = (mode: ThemeMode) => {
    themeMode.value = mode
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(THEME_KEY, mode)
    }
    applyTheme(mode)
  }

  /**
   * 切换浅色 / 深色主题。
   * @param event 鼠标点击事件（用于取扩散圆心）；也可直接传 { x, y }
   *
   * 调用链：GlobalHeader 点击按钮 → toggleTheme(event) → withThemeTransition → setTheme
   */
  const toggleTheme = (event?: MouseEvent | ThemeTransitionPoint) => {
    // 从 MouseEvent 提取点击坐标，供圆形扩散动画使用
    const point =
      event && 'clientX' in event
        ? { x: event.clientX, y: event.clientY }
        : event

    withThemeTransition(() => {
      setTheme(themeMode.value === 'dark' ? 'light' : 'dark')
    }, point)
  }

  return {
    themeMode,
    isDark,
    antTheme,
    setTheme,
    toggleTheme,
  }
}
