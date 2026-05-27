/**
 * Monaco Editor 初始化模块
 *
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 两个 npm 包分别是什么？为什么官网只写装 monaco-editor？
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 1) monaco-editor（必须）
 *    - Monaco 本体，VS Code 同款编辑器内核
 *    - 提供 monaco.editor.create()、语法高亮、主题、自动补全等 API
 *    - 官网 `npm install monaco-editor` 指的就是这个
 *
 * 2) @monaco-editor/loader（可选，本项目用来辅助初始化）
 *    - CodeSandbox 维护的「加载器」，不是 Monaco 官方包
 *    - 设计初衷：从 CDN 异步下载 Monaco，避免首屏打包体积过大
 *    - 提供 loader.config() + loader.init()，并保证全局只 init 一次
 *
 * 本项目实际用法：
 *    - monaco-editor 已通过 Vite 打包进项目（不走 CDN）
 *    - loader 仅作为「单例 init 包装」使用，传入本地 monaco 实例：
 *        loader.config({ monaco })  → 告诉 loader 用本地包，不要去 CDN 拉
 *        loader.init()              → 返回 Promise<monaco>，多次调用共享同一 Promise
 *    - 如果去掉 loader，直接 monaco.editor.create() 也可以，效果等价
 *
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * Worker 是什么？为什么要单独配置？
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * Monaco 把语法分析、智能提示等重活放到 Web Worker 线程，避免卡住 UI。
 * 在 Vite 里需要用 `?worker` 后缀导入 worker 文件，并通过 MonacoEnvironment
 * 告诉 Monaco 如何创建对应的 Worker 实例。
 */

import loader from '@monaco-editor/loader'
import * as monaco from 'monaco-editor'

// 以下 5 个 worker 分别负责不同语言的语法服务，Vite 会将它们打包为独立 chunk
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

/** 缓存 init Promise，确保整个应用生命周期内 Monaco 只初始化一次 */
let initPromise: ReturnType<typeof loader.init> | null = null

/**
 * 注册 MonacoEnvironment.getWorker
 * Monaco 在需要语法服务时会调用此函数，根据 language label 返回对应 Worker
 */
function setupMonacoEnvironment() {
  if (typeof globalThis === 'undefined') return

  const g = globalThis as typeof globalThis & {
    MonacoEnvironment?: { getWorker: (workerId: string, label: string) => Worker }
  }

  // 避免重复注册（热更新场景）
  if (g.MonacoEnvironment) return

  g.MonacoEnvironment = {
    getWorker(_workerId: string, label: string) {
      if (label === 'json') return new jsonWorker()
      if (label === 'css' || label === 'scss' || label === 'less') return new cssWorker()
      if (label === 'html' || label === 'handlebars' || label === 'razor') return new htmlWorker()
      if (label === 'typescript' || label === 'javascript') return new tsWorker()
      return new editorWorker()
    },
  }
}

/**
 * 加载 Monaco API（异步）
 *
 * 调用方（MonacoEditor.vue）await loadMonaco() 后即可使用 monaco.editor.create()
 *
 * 流程：
 *   1. setupMonacoEnvironment()  → 配置 Worker
 *   2. loader.config({ monaco })   → 绑定本地 monaco 包（非 CDN）
 *   3. loader.init()             → 返回 monaco 实例（单例 Promise）
 */
export async function loadMonaco() {
  setupMonacoEnvironment()

  if (!initPromise) {
    // 关键：传入本地 import 的 monaco，loader 就不会去 jsdelivr 等 CDN 下载
    loader.config({ monaco })
    initPromise = loader.init()
  }

  return initPromise
}
