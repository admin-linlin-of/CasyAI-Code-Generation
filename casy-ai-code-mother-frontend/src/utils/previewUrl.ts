import { API_BASE_URL } from '@/config'

export const CodeGenTypeEnum = {
  HTML: 'html',
  MULTI_FILE: 'multi_file',
  VUE_PROJECT: 'vue_project',
} as const

const STATIC_BASE_URL = `${API_BASE_URL}/static`

export const getStaticBaseUrl = (deployKey: string) => `${STATIC_BASE_URL}/${deployKey}/`

export const getStaticPreviewUrl = (codeGenType: string, deployKey: string) => {
  const baseUrl = getStaticBaseUrl(deployKey)
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}

/** 单次检测静态资源是否可访问 */
export const checkStaticResourceReady = async (url: string) => {
  try {
    const res = await fetch(url, { credentials: 'include', cache: 'no-store' })
    return res.ok
  } catch {
    return false
  }
}

/** Vue 项目异步 npm build 完成后 dist/index.html 才可访问，轮询直到就绪
 *
 * 每 3 秒请求一次预览 URL（Vue 项目是 dist/index.html）：
 * 404 / 网络错误 → 认为还没打完，继续等
 * HTTP 200 → 认为文件已存在，可以预览
 * 每次间隔 3 秒 → 120 × 3 = 360 秒（6 分钟） 超时
 */
export const waitForStaticResourceReady = async (
  url: string,
  options?: { maxAttempts?: number; intervalMs?: number },
) => {
  const maxAttempts = options?.maxAttempts ?? 120
  const intervalMs = options?.intervalMs ?? 3000
  for (let i = 0; i < maxAttempts; i++) {
    if (await checkStaticResourceReady(url)) return true
    await new Promise((r) => setTimeout(r, intervalMs))
  }
  return false
}
