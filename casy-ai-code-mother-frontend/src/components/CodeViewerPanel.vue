<!--
  CodeViewerPanel — Vue 项目右侧「单文件 Monaco 查看器」

  由 CodeWorkspace（mode=tree）调用，只负责展示一个 ProjectFile。

  数据流：
    props.file（来自 store，随 SSE t=file 更新 content）
      → watch 同步到 content / status ref
      → useMonacoTypewriter 计算 displayContent（生成中打字机，结束全量）
      → MonacoEditor
           model-value = file.content（完整内容，用于语言切换等）
           display-value = displayContent（实际显示，可能短于 content）

  未选中文件时显示占位文案。
-->
<template>
  <div class="code-viewer-panel">
    <!-- 顶栏：当前文件 path + 写入中徽章 -->
    <div v-if="file" class="code-viewer-panel__tabs">
      <span class="editor-tab editor-tab--active">{{ file.path }}</span>
      <span v-if="file.status === 'generating'" class="code-viewer-panel__badge">写入中</span>
    </div>

    <div class="code-viewer-panel__monaco">
      <MonacoEditor
        v-if="file"
        :language="file.language"
        :model-value="file.content"
        :display-value="displayContent"
        :read-only="readOnly"
      />
      <div v-else class="code-viewer-panel__empty">选择左侧文件查看代码</div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
import { useMonacoTypewriter } from '@/composables/useMonacoTypewriter'
import type { ProjectFile, ProjectFileStatus } from '@/utils/projectFiles'

const props = defineProps<{
  /** 当前选中的项目文件；undefined 表示尚未选中 */
  file?: ProjectFile
  /** AppChat.generating：整轮 SSE 是否进行中 */
  generating: boolean
  readOnly?: boolean
}>()

/**
 * 将 props.file 拆成 ref，供 useMonacoTypewriter 监听。
 * 不直接用 props.file.content 是因为 composable 需要 Ref 类型。
 */
const content = ref('')
const status = ref<ProjectFileStatus | undefined>()

watch(
  () => props.file,
  (file) => {
    content.value = file?.content ?? ''
    status.value = file?.status
  },
  { immediate: true, deep: true },
)

/**
 * 打字机 composable：
 *   generating && status==='generating' → displayContent 逐步追赶 content
 *   否则 → displayContent = content（历史加载 / 生成结束）
 */
const { displayContent } = useMonacoTypewriter({
  content,
  generating: () => props.generating,
  status,
})
</script>

<style scoped>
.code-viewer-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.code-viewer-panel__tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-page);
}

.editor-tab {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  border-bottom: 2px solid transparent;
  white-space: nowrap;
}

.editor-tab--active {
  color: var(--text-main);
  border-bottom-color: #1677ff;
}

.code-viewer-panel__badge {
  font-size: 11px;
  color: #52c41a;
}

.code-viewer-panel__monaco {
  flex: 1;
  min-height: 240px;
  display: flex;
  flex-direction: column;
}

.code-viewer-panel__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
