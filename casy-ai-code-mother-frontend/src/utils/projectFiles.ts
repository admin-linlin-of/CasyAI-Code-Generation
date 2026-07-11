export type ProjectFileStatus = 'idle' | 'generating' | 'done'

export type ProjectFile = {
  path: string
  content: string
  displayContent: string
  language: string
  status: ProjectFileStatus
  updatedAt: number
}

export type FileTreeNode = {
  name: string
  path: string
  isDir: boolean
  children?: FileTreeNode[]
}

const LANG_MAP: Record<string, string> = {
  html: 'html',
  htm: 'html',
  css: 'css',
  js: 'javascript',
  mjs: 'javascript',
  cjs: 'javascript',
  ts: 'typescript',
  tsx: 'typescript',
  jsx: 'javascript',
  vue: 'html',
  json: 'json',
  md: 'markdown',
  yaml: 'yaml',
  yml: 'yaml',
  xml: 'xml',
  sql: 'sql',
  java: 'java',
  py: 'python',
  sh: 'shell',
}

export function pathToLanguage(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase() ?? ''
  return LANG_MAP[ext] ?? 'plaintext'
}

export function buildFileTree(paths: string[]): FileTreeNode[] {
  const root: FileTreeNode[] = []
  for (const filePath of paths) {
    const parts = filePath.split('/').filter(Boolean)
    let current = root
    let currentPath = ''
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]!
      currentPath = currentPath ? `${currentPath}/${part}` : part
      const isDir = i < parts.length - 1
      let node = current.find((item) => item.name === part)
      if (!node) {
        node = { name: part, path: currentPath, isDir, children: isDir ? [] : undefined }
        current.push(node)
      }
      if (isDir && node.children) {
        current = node.children
      }
    }
  }
  sortTree(root)
  return root
}

function sortTree(nodes: FileTreeNode[]) {
  nodes.sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1
    return a.name.localeCompare(b.name)
  })
  for (const node of nodes) {
    if (node.children?.length) sortTree(node.children)
  }
}

export async function fetchProjectFileContent(baseUrl: string, path: string): Promise<string> {
  const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`
  try {
    const res = await fetch(`${normalizedBase}${path}`, { credentials: 'include' })
    if (!res.ok) return ''
    return res.text()
  } catch {
    return ''
  }
}
