import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { ProjectFileStatus } from '@/utils/projectFiles'

/**
 * Monaco 编辑器打字机 composable。
 *
 * 使用场景：Vue 项目生成过程中，右侧 CodeViewerPanel 对「正在写入」的文件做渐进展示。
 * 与聊天区 AiMarkdownMessage 的打字机相互独立——聊天区只显示工具摘要，不含源码。
 *
 * 数据流：
 *   SSE t=file → useProjectFileStore 更新 file.content
 *     → CodeViewerPanel 传入 content / generating / status
 *     → 本 composable 产出 displayContent
 *     → MonacoEditor :display-value="displayContent"
 *
 * 启用条件：generating === true 且 status === 'generating'
 * 关闭条件：生成结束、历史加载、用户切换文件 → displayContent 立即对齐 content
 */
export function useMonacoTypewriter(options: {
  /** 文件完整内容（随 SSE 或 HTTP 刷新而增长） */
  content: Ref<string>
  /** 是否处于整轮 SSE 生成中（AppChat.generating） */
  generating: () => boolean
  /** 当前文件状态：generating 时才打字，done/idle 时直接全量展示 */
  status: Ref<ProjectFileStatus | undefined>
}) {
  /** 实际传给 Monaco 的展示文本，长度 ≤ content，打字机逐步追赶 */
  const displayContent = ref('')
  let timer: ReturnType<typeof setTimeout> | null = null

  /** 清除定时器，防止组件卸载或模式切换后仍继续 tick */
  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  /** 判断是否应启用打字机：整轮生成中 + 该文件正在写入 */
  const shouldTypewrite = () => options.generating() && options.status.value === 'generating'

  /**
   * 单步推进 displayContent 向 content 靠拢。
   *
   * 流程：
   * 1. 清掉上一轮定时器，避免重复调度
   * 2. 若不应打字 → 直接 displayContent = content，结束
   * 3. 若已追上 target → 对齐后结束
   * 4. 否则按 remain/12 计算步长（至少 2 字符），slice 追加
   * 5. 20ms 后再次 tick，形成打字机效果
   */
  const tick = () => {
    clearTimer()
    const target = options.content.value
    if (!shouldTypewrite()) {
      displayContent.value = target
      return
    }
    if (displayContent.value.length >= target.length) {
      displayContent.value = target
      return
    }
    const remain = target.length - displayContent.value.length
    const step = Math.max(2, Math.ceil(remain / 12))
    displayContent.value = target.slice(0, displayContent.value.length + step)
    timer = setTimeout(tick, 20)
  }

  // content 变化（SSE 追加块 / 切换文件）→ 立即尝试追赶或全量展示
  watch(() => options.content.value, tick, { immediate: true })

  // generating 或 status 变化 → 关闭打字机时立刻全量；开启时从当前位置继续 tick
  watch(
    () => [options.generating(), options.status.value] as const,
    () => {
      if (!shouldTypewrite()) {
        clearTimer()
        displayContent.value = options.content.value
      } else {
        tick()
      }
    },
  )

  onBeforeUnmount(clearTimer)

  return { displayContent }
}
