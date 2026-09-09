import type { CodeFields } from '@/utils/aiContentMarkdown'

/** 与后端 CodeParserConstant 保持一致的正则；围栏语言名允许前后空白，便于流式输出 */
const HTML_FENCE = /```\s*html\s*(?:\r\n|\r|\n)?([\s\S]*?)(?:```|$)/i
const HTML_FENCE_CLOSED = /```\s*html\s*(?:\r\n|\r|\n)?([\s\S]*?)```/i
const CSS_FENCE = /```\s*css\s*(?:\r\n|\r|\n)?([\s\S]*?)(?:```|$)/i
const CSS_FENCE_CLOSED = /```\s*css\s*(?:\r\n|\r|\n)?([\s\S]*?)```/i
const JS_FENCE = /```\s*(?:js|javascript)\s*(?:\r\n|\r|\n)?([\s\S]*?)(?:```|$)/i
const JS_FENCE_CLOSED = /```\s*(?:js|javascript)\s*(?:\r\n|\r|\n)?([\s\S]*?)```/i
const LOOSE_HTML =
  /(?:<!DOCTYPE\s+html[^>]*>[\s\S]*?<\/html>|<html\b[^>]*>[\s\S]*?<\/html>)/is
/** 流式尚未输出 </html> 时，仍把已出现的文档骨架送给编辑器打字机 */
const LOOSE_HTML_STREAM = /(?:<!DOCTYPE\s+html\b[\s\S]*|<html\b[^>]*>[\s\S]*)/is
const LOOSE_CSS = /css\s*格式\s*(?:\r\n|\r|\n)([\s\S]*?)(?=(?:\r\n|\r|\n)\s*```|$)/is
const LOOSE_JS =
  /(?:js|javascript)\s*格式\s*(?:\r\n|\r|\n)([\s\S]*?)(?=(?:\r\n|\r|\n)\s*```|$)/is

function firstNonBlank(...values: (string | undefined)[]): string | undefined {
  for (const value of values) {
    if (value?.trim()) return value.trim()
  }
  return undefined
}

function matchGroup(pattern: RegExp, raw: string): string | undefined {
  const match = raw.match(pattern)
  return match?.[1]?.trim()
}

function matchFull(pattern: RegExp, raw: string): string | undefined {
  const match = raw.match(pattern)
  return match?.[0]?.trim()
}

/** 尝试 JSON.parse；也支持正文前后有说明文字的情况 */
function tryParseJsonObject(raw: string): CodeFields | null {
  const trimmed = raw.trim()
  if (trimmed.startsWith('{')) {
    try {
      const obj = JSON.parse(trimmed) as CodeFields
      if (obj.htmlCode || obj.cssCode || obj.jsCode) return obj
    } catch {
      /* 继续尝试截取 JSON 片段 */
    }
  }
  const jsonMatch = raw.match(/\{[\s\S]*"htmlCode"[\s\S]*\}/)
  if (!jsonMatch) return null
  try {
    const obj = JSON.parse(jsonMatch[0]) as CodeFields
    if (obj.htmlCode || obj.cssCode || obj.jsCode) return obj
  } catch {
    return null
  }
  return null
}

/**
 * 从 AI 原文提取 html/css/js，逻辑对齐后端 MultiFileCodeParser。
 * 支持：JSON、Markdown 围栏、无闭合围栏（流式）、裸 HTML。
 */
export function extractCodeFields(raw: string): CodeFields {
  if (!raw.trim()) return {}

  const jsonCodes = tryParseJsonObject(raw)
  if (jsonCodes) return jsonCodes

  const htmlCode = firstNonBlank(
    matchGroup(HTML_FENCE_CLOSED, raw),
    matchGroup(HTML_FENCE, raw),
    matchFull(LOOSE_HTML, raw),
    matchFull(LOOSE_HTML_STREAM, raw),
  )
  const cssCode = firstNonBlank(
    matchGroup(CSS_FENCE_CLOSED, raw),
    matchGroup(CSS_FENCE, raw),
    matchGroup(LOOSE_CSS, raw),
  )
  const jsCode = firstNonBlank(
    matchGroup(JS_FENCE_CLOSED, raw),
    matchGroup(JS_FENCE, raw),
    matchGroup(LOOSE_JS, raw),
  )

  return { htmlCode, cssCode, jsCode }
}

export function hasCodeFields(codes: CodeFields): boolean {
  return !!(codes.htmlCode?.trim() || codes.cssCode?.trim() || codes.jsCode?.trim())
}
