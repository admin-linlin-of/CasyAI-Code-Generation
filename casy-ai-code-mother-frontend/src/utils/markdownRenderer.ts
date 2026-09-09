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

// 后端会把工具执行摘要输出成轻量标签，例如 <fileWrite>...</fileWrite>。
// markdown-it 开启了 html=false，因此这些标签会先被安全转义；
// 这里再通过白名单把固定工具标签转换成前端样式化的工具徽标。
const TOOL_TAGS: Record<string, { label: string; className: string }> = {
  fileWrite: { label: '📝 写入', className: 'file-write' },
  fileModify: { label: '🔧 修改', className: 'file-modify' },
  fileRead: { label: '📖 读取', className: 'file-read' },
  fileDelete: { label: '🗑️ 删除', className: 'file-delete' },
  dirRead: { label: '📁 目录', className: 'dir-read' },
  toolCall: { label: '🛠️ 工具', className: 'tool-call' },
}
const DEFAULT_TOOL_TAG = { label: '🛠️ 工具', className: 'tool-call' }

// 只匹配 TOOL_TAGS 中声明过的已转义标签，避免用户或模型输出的任意 HTML 被激活，
// 同时允许我们约定好的工具标记渲染成动态徽标。
const TOOL_TAG_PATTERN =
  /&lt;(fileWrite|fileModify|fileRead|fileDelete|dirRead|toolCall)&gt;([\s\S]*?)&lt;\/\1&gt;/g

// 将白名单工具标签转换为 span，具体视觉效果由 AiMarkdownMessage.vue 中的样式控制。
// 标签内部内容已经经过 Markdown 渲染和 DOMPurify 清洗。
function renderToolTags(html: string): string {
  return html.replace(TOOL_TAG_PATTERN, (_, tagName: string, content: string) => {
    const config = TOOL_TAGS[tagName] ?? DEFAULT_TOOL_TAG
    return `<span class="ai-tool-call ai-tool-call--${config.className}"><span class="ai-tool-call__icon" aria-hidden="true"></span><span class="ai-tool-call__label">${config.label}</span><span class="ai-tool-call__content">${content}</span></span>`
  })
}

export function renderMarkdown(markdown: string): string {
  if (!markdown) return ''
  // 工作流进度会输出 ![desc](url)，需要放行 img/src，否则素材和网站预览图渲染不出来
  const html = DOMPurify.sanitize(md.render(markdown), {
    ADD_TAGS: ['pre', 'code', 'img'],
    ADD_ATTR: ['class', 'src', 'alt', 'title'],
  })
  return renderToolTags(html)
}
