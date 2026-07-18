/**
 * 预览页可视化编辑工具。
 *
 * 这个文件把 iframe 内部的 DOM 选择、边框高亮、postMessage 通信集中放在一起，
 * AppChat.vue 只需要关心「是否开启编辑模式」和「当前选中的元素信息」。
 *
 * 注意：当前方案依赖主站与预览站同源。只有同源时，主站才能向 iframe document 注入
 * 选择脚本；注入脚本再通过 window.parent.postMessage 把用户点击的元素信息传回主站。
 */

export type VisualEditorSelectedElement = {
  /** 标签名，如 div / button / img */
  tagName: string
  /** 元素 id，没有则为空字符串 */
  id: string
  /** className 统一压缩成空格分隔字符串，避免 SVGAnimatedString 等非字符串值污染展示 */
  className: string
  /** 元素自身文本摘要，最长 80 字，避免把整段页面内容塞进提示词 */
  text: string
  /** 尽量稳定的 CSS 选择器，优先 id，其次 class 与 nth-of-type */
  selector: string
  /** 从 body 到目标节点的结构路径，便于大模型理解元素所在层级 */
  path: string
}

type VisualEditorMessage = {
  type: typeof VISUAL_EDITOR_MESSAGE_TYPE
  payload: VisualEditorSelectedElement
}

export const VISUAL_EDITOR_MESSAGE_TYPE = 'casy-ai-code-mother:visual-editor-selected'

const INJECTED_FLAG = '__casyVisualEditorInjected__'

/**
 * iframe 内运行的脚本源码。
 *
 * 这里必须写成纯字符串函数，便于注入到预览页面。脚本只使用浏览器原生 API，
 * 不依赖主站 Vue 运行时，避免生成站点框架不同导致失效。
 */
const getInjectedScriptContent = () => `
;(function () {
  if (window.${INJECTED_FLAG}) return
  window.${INJECTED_FLAG} = true

  var enabled = false
  var hoveredElement = null
  var selectedElement = null
  var hoverClass = 'casy-visual-editor-hover'
  var selectedClass = 'casy-visual-editor-selected'
  var styleId = 'casy-visual-editor-style'

  function ensureStyle() {
    if (document.getElementById(styleId)) return
    var style = document.createElement('style')
    style.id = styleId
    style.textContent = [
      '.casy-visual-editor-hover { outline: 2px dashed rgba(22, 119, 255, .8) !important; outline-offset: 2px !important; cursor: crosshair !important; }',
      '.casy-visual-editor-selected { outline: 3px solid rgba(9, 88, 217, .95) !important; outline-offset: 2px !important; }'
    ].join('\\n')
    document.head.appendChild(style)
  }

  function normalizeClassName(element) {
    if (!element || !element.className) return ''
    if (typeof element.className === 'string') return element.className.replace(/\\s+/g, ' ').trim()
    if (typeof element.className.baseVal === 'string') return element.className.baseVal.replace(/\\s+/g, ' ').trim()
    return ''
  }

  function cssEscape(value) {
    if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(value)
    return String(value).replace(/[^a-zA-Z0-9_-]/g, '\\\\$&')
  }

  function getElementIndex(element) {
    var index = 1
    var sibling = element.previousElementSibling
    while (sibling) {
      if (sibling.tagName === element.tagName) index += 1
      sibling = sibling.previousElementSibling
    }
    return index
  }

  function buildSelector(element) {
    if (element.id) return '#' + cssEscape(element.id)
    var parts = []
    var current = element
    while (current && current.nodeType === 1 && current !== document.body) {
      var part = current.tagName.toLowerCase()
      var className = normalizeClassName(current)
      if (className) {
        part += '.' + className.split(' ').slice(0, 3).map(cssEscape).join('.')
      }
      part += ':nth-of-type(' + getElementIndex(current) + ')'
      parts.unshift(part)
      current = current.parentElement
    }
    return parts.length ? parts.join(' > ') : element.tagName.toLowerCase()
  }

  function buildPath(element) {
    var parts = []
    var current = element
    while (current && current.nodeType === 1 && current !== document.body) {
      var label = current.tagName.toLowerCase()
      if (current.id) label += '#' + current.id
      var className = normalizeClassName(current)
      if (className) label += '.' + className.split(' ').slice(0, 2).join('.')
      parts.unshift(label)
      current = current.parentElement
    }
    return parts.join(' > ')
  }

  function getElementInfo(element) {
    return {
      tagName: element.tagName.toLowerCase(),
      id: element.id || '',
      className: normalizeClassName(element),
      text: (element.innerText || element.textContent || '').replace(/\\s+/g, ' ').trim().slice(0, 80),
      selector: buildSelector(element),
      path: buildPath(element)
    }
  }

  function clearHover() {
    if (hoveredElement && hoveredElement !== selectedElement) {
      hoveredElement.classList.remove(hoverClass)
    }
    hoveredElement = null
  }

  function clearSelected() {
    if (selectedElement) selectedElement.classList.remove(selectedClass)
    selectedElement = null
  }

  function onMouseOver(event) {
    if (!enabled) return
    var target = event.target
    if (!(target instanceof Element) || target === document.documentElement || target === document.body) return
    if (hoveredElement && hoveredElement !== target && hoveredElement !== selectedElement) {
      hoveredElement.classList.remove(hoverClass)
    }
    hoveredElement = target
    if (hoveredElement !== selectedElement) hoveredElement.classList.add(hoverClass)
  }

  function onMouseOut(event) {
    if (!enabled) return
    var target = event.target
    if (!(target instanceof Element) || target !== hoveredElement || target === selectedElement) return
    target.classList.remove(hoverClass)
    hoveredElement = null
  }

  function onClick(event) {
    if (!enabled) return
    var target = event.target
    if (!(target instanceof Element) || target === document.documentElement || target === document.body) return
    event.preventDefault()
    event.stopPropagation()
    clearSelected()
    selectedElement = target
    selectedElement.classList.remove(hoverClass)
    selectedElement.classList.add(selectedClass)
    window.parent.postMessage({
      type: '${VISUAL_EDITOR_MESSAGE_TYPE}',
      payload: getElementInfo(selectedElement)
    }, window.location.origin)
  }

  function setEnabled(nextEnabled) {
    enabled = nextEnabled
    ensureStyle()
    clearHover()
    if (!enabled) clearSelected()
  }

  window.addEventListener('message', function (event) {
    if (event.origin !== window.location.origin) return
    var data = event.data || {}
    if (data.type === 'casy-visual-editor:set-enabled') setEnabled(Boolean(data.enabled))
    if (data.type === 'casy-visual-editor:clear-selected') clearSelected()
  })

  document.addEventListener('mouseover', onMouseOver, true)
  document.addEventListener('mouseout', onMouseOut, true)
  document.addEventListener('click', onClick, true)
})()
`

const isSelectedElementMessage = (data: unknown): data is VisualEditorMessage => {
  const record = data as Partial<VisualEditorMessage>
  return record?.type === VISUAL_EDITOR_MESSAGE_TYPE && typeof record.payload?.selector === 'string'
}

/**
 * 把可视化选择信息转换成附加提示词。
 * 用户原始输入保持在前面，便于后端和历史对话仍以用户需求为主体。
 */
export const appendSelectedElementToPrompt = (
  messageText: string,
  selectedElement?: VisualEditorSelectedElement | null,
) => {
  if (!selectedElement) return messageText
  const lines = [
    messageText,
    '',
    '【可视化选中的页面元素】',
    `标签：${selectedElement.tagName}`,
    `选择器：${selectedElement.selector}`,
    selectedElement.id ? `ID：${selectedElement.id}` : '',
    selectedElement.className ? `类名：${selectedElement.className}` : '',
    selectedElement.text ? `文本：${selectedElement.text}` : '',
    selectedElement.path ? `层级：${selectedElement.path}` : '',
    '请优先围绕该元素理解并修改页面。',
  ]
  return lines.filter(Boolean).join('\n')
}

/** 用于 Alert 描述区的人类可读摘要。 */
export const formatSelectedElementLabel = (selectedElement: VisualEditorSelectedElement) => {
  const parts = [
    `<${selectedElement.tagName}>`,
    selectedElement.id ? `#${selectedElement.id}` : '',
    selectedElement.className
      ? `.${selectedElement.className.split(' ').slice(0, 2).join('.')}`
      : '',
    selectedElement.text ? `文本：“${selectedElement.text}”` : '',
  ]
  return parts.filter(Boolean).join(' ')
}

/**
 * 创建主站侧 iframe 控制器。
 *
 * 控制器职责：
 * 1. 在 iframe load 后注入选择脚本；
 * 2. 开关编辑模式时通过 postMessage 通知 iframe；
 * 3. 监听 iframe 回传的选中元素并交给页面状态保存；
 * 4. 页面卸载时移除主站 message 监听，避免重复注册。
 */
export const createVisualEditorController = (options: {
  getIframe: () => HTMLIFrameElement | undefined
  onSelected: (element: VisualEditorSelectedElement) => void
}) => {
  let enabled = false

  const postToIframe = (message: Record<string, unknown>) => {
    const iframeWindow = options.getIframe()?.contentWindow
    if (!iframeWindow) return
    try {
      iframeWindow.postMessage(message, window.location.origin)
    } catch (error) {
      // 预览站如果通过不同端口/域名打开，严格 targetOrigin 会抛 DOMException。
      // iframe 内部仍会校验 event.origin，这里降级只为避免清理高亮时打断主流程。
      console.warn('发送可视化编辑消息失败：', error)
    }
  }

  const injectScript = () => {
    const iframe = options.getIframe()
    const iframeDocument = iframe?.contentDocument
    if (!iframe?.contentWindow || !iframeDocument?.documentElement) return false
    if ((iframe.contentWindow as unknown as Record<string, boolean> | undefined)?.[INJECTED_FLAG])
      return true
    const script = iframeDocument.createElement('script')
    script.textContent = getInjectedScriptContent()
    iframeDocument.documentElement.appendChild(script)
    script.remove()
    return true
  }

  const syncEnabledToIframe = () => {
    if (!injectScript()) return
    postToIframe({ type: 'casy-visual-editor:set-enabled', enabled })
  }

  const setEnabled = (nextEnabled: boolean) => {
    enabled = nextEnabled
    syncEnabledToIframe()
  }

  const clearSelectedInIframe = () => {
    postToIframe({ type: 'casy-visual-editor:clear-selected' })
  }

  const handleIframeLoad = () => {
    syncEnabledToIframe()
  }

  const handleMessage = (event: MessageEvent) => {
    if (event.origin !== window.location.origin || !isSelectedElementMessage(event.data)) return
    options.onSelected(event.data.payload)
  }

  window.addEventListener('message', handleMessage)

  return {
    setEnabled,
    clearSelectedInIframe,
    handleIframeLoad,
    destroy() {
      window.removeEventListener('message', handleMessage)
    },
  }
}
