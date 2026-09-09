export const SYS_PARAM_KEY = {
  GITHUB_URL: 'site.github.url',
  GITEE_URL: 'site.gitee.url',
  LOGIN_DEMO_ACCOUNT: 'login.demo.account',
  LOGIN_DEMO_PASSWORD: 'login.demo.password',
} as const

/** 仅允许 http(s) 链接作为外跳地址 */
export function isHttpUrl(value?: string): value is string {
  if (!value?.trim()) return false
  const lower = value.trim().toLowerCase()
  return lower.startsWith('http://') || lower.startsWith('https://')
}
