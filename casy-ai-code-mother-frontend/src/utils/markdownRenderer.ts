/**
 * Markdown 渲染：markdown-it 解析 + highlight.js 代码高亮 + DOMPurify 防 XSS。
 */
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import 'highlight.js/styles/github.min.css'

// HTML 在 highlight.js 里用 xml 语言包
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)

const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const md = new MarkdownIt({
  html: false, // 禁止原始 HTML，降低 XSS 风险
  linkify: true,
  breaks: true, // 单换行转 <br>
  highlight(str, lang): string {
    const language = lang && hljs.getLanguage(lang) ? lang : undefined
    if (language) {
      return `<pre class="hljs"><code>${hljs.highlight(str, { language }).value}</code></pre>`
    }
    return `<pre class="hljs"><code>${escapeHtml(str)}</code></pre>`
  },
})

/** 将 Markdown 字符串转为可安全插入 v-html 的 HTML */
export function renderMarkdown(markdown: string): string {
  if (!markdown) return ''
  return DOMPurify.sanitize(md.render(markdown), {
    ADD_TAGS: ['pre', 'code'],
    ADD_ATTR: ['class'],
  })
}
