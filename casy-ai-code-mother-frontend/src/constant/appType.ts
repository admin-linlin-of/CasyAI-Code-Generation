export const APP_TYPE = {
  WEBSITE: 'website',
  TOOL: 'tool',
  BLOG: 'blog',
  ADMIN: 'admin',
} as const

export const APP_TYPE_OPTIONS = [
  { value: APP_TYPE.WEBSITE, label: '网站' },
  { value: APP_TYPE.TOOL, label: '工具' },
  { value: APP_TYPE.BLOG, label: '博客' },
  { value: APP_TYPE.ADMIN, label: '管理后台' },
]

export const APP_TYPE_LABEL_MAP: Record<string, string> = {
  [APP_TYPE.WEBSITE]: '网站',
  [APP_TYPE.TOOL]: '工具',
  [APP_TYPE.BLOG]: '博客',
  [APP_TYPE.ADMIN]: '管理后台',
  // 历史值兼容展示
  management: '管理后台',
  application: '网站',
}

export const formatAppTypes = (types?: string[]) => {
  if (!types?.length) return '-'
  return types.map((t) => APP_TYPE_LABEL_MAP[t] || t).join('、')
}
