import { computed, ref } from 'vue'
import { theme } from 'ant-design-vue'

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

  const toggleTheme = () => {
    setTheme(themeMode.value === 'dark' ? 'light' : 'dark')
  }

  return {
    themeMode,
    isDark,
    antTheme,
    setTheme,
    toggleTheme,
  }
}
