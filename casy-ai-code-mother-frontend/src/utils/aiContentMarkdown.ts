/**
 * 将后端 SSE 返回的 AI 原文转为可展示的 Markdown。
 * 后端可能返回 JSON（htmlCode/cssCode/jsCode）或已是 Markdown 代码块，本模块统一处理。
 */

import { extractCodeFields, hasCodeFields } from '@/utils/codeParser'

/** 后端 JSON 格式中的代码字段，对应 index.html / style.css / script.js */
export type CodeFields = {
  htmlCode?: string
  cssCode?: string
  jsCode?: string
}

/** 从 AI 原文解析代码字段，供 Monaco 虚拟文件系统使用 */
export function parseAiCodes(raw: string): CodeFields | null {
  const codes = extractCodeFields(raw)
  return hasCodeFields(codes) ? codes : null
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
 * 是 JSON / 围栏 / 裸 HTML 则拆成 HTML/CSS/JS 三个代码块；否则原样返回。
 */
export function aiContentToMarkdown(raw: string): string {
  const codes = extractCodeFields(raw)
  if (hasCodeFields(codes)) return codesToMarkdown(codes)
  return raw
}
