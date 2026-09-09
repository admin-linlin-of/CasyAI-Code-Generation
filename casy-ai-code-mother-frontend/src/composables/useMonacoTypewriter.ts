import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { ProjectFileStatus } from '@/utils/projectFiles'

/**
 * Monaco 编辑器打字机 composable。
 *
 * 任意生成模式（传统 / Agent 工作流，HTML / 多文件 / Vue）下，右侧代码区对当前文件做渐进展示。
 * 与聊天区打字机相互独立。
 *
 * 启用条件：generating === true（整轮 SSE 进行中即打字，不依赖单文件 status；
 * writeFile 事件常带 done=true，若等 status 会瞬间跳过打字机）
 * 关闭条件：生成结束 → displayContent 立即等于 content
 */
export function useMonacoTypewriter(options: {
  content: Ref<string>
  generating: () => boolean
  /** 保留：树节点高亮等仍可用；打字机不再依赖它 */
  status: Ref<ProjectFileStatus | undefined>
}) {
  const displayContent = ref('')
  let timer: ReturnType<typeof setTimeout> | null = null

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  const shouldTypewrite = () => options.generating()

  const tick = () => {
    clearTimer()
    const target = options.content.value
    if (!shouldTypewrite()) {
      displayContent.value = target
      return
    }
    // content 被整体替换变短时（覆盖写入），从新内容重新打
    if (displayContent.value.length > target.length) {
      displayContent.value = ''
    }
    if (displayContent.value.length >= target.length) {
      displayContent.value = target
      return
    }
    const remain = target.length - displayContent.value.length
    const step = Math.max(3, Math.min(48, Math.ceil(remain / 10)))
    displayContent.value = target.slice(0, displayContent.value.length + step)
    timer = setTimeout(tick, 16)
  }

  watch(
    () => options.content.value,
    (next) => {
      // 内容整体换源（前缀对不上）时重置，避免把旧进度拼到新文件
      if (
        displayContent.value &&
        next &&
        !next.startsWith(displayContent.value.slice(0, Math.min(32, displayContent.value.length)))
      ) {
        displayContent.value = ''
      }
      tick()
    },
    { immediate: true },
  )

  watch(
    () => options.generating(),
    (isGenerating) => {
      if (!isGenerating) {
        clearTimer()
        displayContent.value = options.content.value
        return
      }
      displayContent.value = ''
      tick()
    },
  )

  onBeforeUnmount(clearTimer)

  return { displayContent }
}
