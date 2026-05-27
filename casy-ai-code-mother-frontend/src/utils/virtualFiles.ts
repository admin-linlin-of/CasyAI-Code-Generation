import { extractCodeFields, hasCodeFields } from '@/utils/codeParser'
import type { CodeFields } from '@/utils/aiContentMarkdown'

/** Monaco 编辑器使用的虚拟文件结构 */
export type VirtualFile = {
  path: string
  content: string
  language: string
}

/** multi_file 模式固定的三个文件及其 Monaco 语言 ID */
const FILE_DEFS = [
  { path: 'index.html', field: 'htmlCode' as const, language: 'html' },
  { path: 'style.css', field: 'cssCode' as const, language: 'css' },
  { path: 'script.js', field: 'jsCode' as const, language: 'javascript' },
]

/** 将 CodeFields 转为虚拟文件列表 */
export function codesToVirtualFiles(codes: CodeFields): VirtualFile[] {
  return FILE_DEFS.map(({ path, field, language }) => ({
    path,
    language,
    content: codes[field] ?? '',
  }))
}

/** AI 原文 → 虚拟文件列表 */
export function parseAiContentToVirtualFiles(raw: string): VirtualFile[] {
  return codesToVirtualFiles(extractCodeFields(raw))
}

/** 判断虚拟文件列表中是否已有可展示的代码内容 */
export function hasVirtualFileContent(files: VirtualFile[]): boolean {
  return files.some((file) => file.content.trim().length > 0)
}

/** 从后端静态资源目录拉取已保存的代码文件（刷新页面或解析失败时的兜底） */
export async function fetchSavedVirtualFiles(baseUrl: string): Promise<VirtualFile[]> {
  const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`
  const files = await Promise.all(
    FILE_DEFS.map(async ({ path, language }) => {
      try {
        const res = await fetch(`${normalizedBase}${path}`, { credentials: 'include' })
        if (!res.ok) return { path, language, content: '' }
        return { path, language, content: await res.text() }
      } catch {
        return { path, language, content: '' }
      }
    }),
  )
  return hasVirtualFileContent(files) ? files : []
}

export { hasCodeFields }
