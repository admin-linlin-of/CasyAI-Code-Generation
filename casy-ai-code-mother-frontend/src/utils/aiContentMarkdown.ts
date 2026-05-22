/**
 * 将后端 SSE 返回的 AI 原文转为可展示的 Markdown。
 * 后端可能返回 JSON（htmlCode/cssCode/jsCode）或已是 Markdown 代码块，本模块统一处理。
 */

/** 后端 JSON 格式中的代码字段 */
type CodeFields = {
  htmlCode?: string
  cssCode?: string
  jsCode?: string
}

/** 还原 JSON 字符串里的转义字符（\n、\" 等）为真实字符 */
function unescapeJsonString(s: string): string {
  return s
    .replace(/\\n/g, '\n')
    .replace(/\\r/g, '\r')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\')
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex: string) =>
      String.fromCharCode(parseInt(hex, 16)),
    )
}

/**
 * 从不完整或完整的 JSON 文本中提取单个字段值。
 * 流式输出时 JSON 可能未闭合，正则允许值末尾没有结束引号。
 */
function extractJsonField(raw: string, field: string): string | undefined {
  const re = new RegExp(`"${field}"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)(?:"|$)`, 's')
  const m = raw.match(re)
  if (!m?.[1]) return undefined
  return unescapeJsonString(m[1])
}

/**
 * 尝试识别并解析 JSON 代码结构。
 * 先整段 JSON.parse；失败则用正则逐字段提取（适配流式未收齐的情况）。
 */
function tryParseJsonCodes(raw: string): CodeFields | null {
  const trimmed = raw.trim()
  if (!trimmed.startsWith('{')) return null
  try {
    const obj = JSON.parse(trimmed) as CodeFields
    if (obj.htmlCode || obj.cssCode || obj.jsCode) return obj
  } catch {
    /* 流式未结束，继续走正则提取 */
  }
  const htmlCode = extractJsonField(raw, 'htmlCode')
  const cssCode = extractJsonField(raw, 'cssCode')
  const jsCode = extractJsonField(raw, 'jsCode')
  if (htmlCode || cssCode || jsCode) return { htmlCode, cssCode, jsCode }
  return null
}

/** 将解析出的代码块组装为带语法标记的 Markdown，供 markdown-it 高亮 */
function codesToMarkdown(codes: CodeFields): string {
  const blocks: string[] = []
  if (codes.htmlCode) blocks.push(`**HTML**\n\n\`\`\`html\n${codes.htmlCode}\n\`\`\``)
  if (codes.cssCode) blocks.push(`**CSS**\n\n\`\`\`css\n${codes.cssCode}\n\`\`\``)
  if (codes.jsCode) blocks.push(`**JavaScript**\n\n\`\`\`javascript\n${codes.jsCode}\n\`\`\``)
  return blocks.join('\n\n')
}

/**
 * 入口：AI 原文 → 展示用 Markdown。
 * 是 JSON 则拆成 HTML/CSS/JS 三个代码块；否则原样返回（已是 Markdown 时直接渲染）。
 */
export function aiContentToMarkdown(raw: string): string {
  const codes = tryParseJsonCodes(raw)
  if (codes) return codesToMarkdown(codes)
  return raw
}
