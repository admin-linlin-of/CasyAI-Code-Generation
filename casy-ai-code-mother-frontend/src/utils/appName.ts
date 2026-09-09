/** 应用名称默认最大长度，与详情编辑 maxlength 对齐时仍保持标题可读 */
export const APP_NAME_MAX_LEN = 24

const PREFIX =
  /^(请|麻烦|帮我)?(用\s*[A-Za-z0-9.+#]+\s*)?(来)?(做|生成|创建|搭建|写|开发)(一个完整的|一个|一份|一套)?/i

/**
 * 从用户提示词提炼短标题，避免「用 Vue3 做一个个人」这种拦腰截断。
 */
export function deriveAppName(prompt: string, maxLen = APP_NAME_MAX_LEN): string {
  const raw = prompt?.trim()
  if (!raw) return '未命名应用'

  const firstLine = raw.split(/\r?\n/, 1)[0] ?? raw
  const sentence = firstLine.replace(/[。！？；;].*$/, '').trim()
  const stripped = sentence.replace(PREFIX, '').trim()
  let title = stripped.length >= 2 ? stripped : sentence

  const comma = title.search(/[，,：:]/)
  if (comma > 4 && comma < maxLen) {
    title = title.slice(0, comma)
  }
  if (title.length > maxLen) {
    title = title.slice(0, maxLen).replace(/[的与和及\s]+$/, '')
  }
  return title.trim() || '未命名应用'
}

/**
 * 顶栏 / 卡片展示名。
 * 库里若仍是提示词前 12 字截断，则按完整 prompt 重新提炼。
 */
export function displayAppName(
  storedName: string | undefined,
  initPrompt: string | undefined,
  fallback = '未命名应用',
): string {
  const stored = storedName?.trim()
  const prompt = initPrompt?.trim()
  if (prompt && stored && prompt.startsWith(stored) && prompt.length > stored.length && stored.length <= 16) {
    return deriveAppName(prompt)
  }
  return stored || fallback
}
