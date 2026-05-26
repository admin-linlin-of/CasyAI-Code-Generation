export const APP_TYPE = {
  WEBSITE: 'website',
  MANAGEMENT: 'management',
  APPLICATION: 'application',
} as const

export const APP_TYPE_OPTIONS = [
  { value: APP_TYPE.WEBSITE, label: '网站' },
  { value: APP_TYPE.MANAGEMENT, label: '管理系统' },
  { value: APP_TYPE.APPLICATION, label: '应用网站' },
]

export const APP_TYPE_LABEL_MAP: Record<string, string> = {
  [APP_TYPE.WEBSITE]: '网站',
  [APP_TYPE.MANAGEMENT]: '管理系统',
  [APP_TYPE.APPLICATION]: '应用网站',
}

export const formatAppTypes = (types?: string[]) => {
  if (!types?.length) return '-'
  return types.map((t) => APP_TYPE_LABEL_MAP[t] || t).join('、')
}
