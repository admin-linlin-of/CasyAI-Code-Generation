import { ref, watch, type Ref } from 'vue'
import type { ProjectFileStatus } from '@/utils/projectFiles'

/**
 * Monaco 展示内容同步。
 * 流式打字机已改为直接展示（由 SSE 批量刷新提供渐进效果），避免 16ms 循环拖垮编辑器。
 */
export function useMonacoTypewriter(options: {
  content: Ref<string>
  generating: () => boolean
  /** 保留：树节点高亮等仍可用；打字机不再依赖它 */
  status: Ref<ProjectFileStatus | undefined>
}) {
  const displayContent = ref('')

  watch(
    () => options.content.value,
    (next) => {
      displayContent.value = next
    },
    { immediate: true },
  )

  watch(
    () => options.generating(),
    () => {
      displayContent.value = options.content.value
    },
  )

  return { displayContent }
}
