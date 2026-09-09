<template>
  <div class="code-viewer-panel">
    <div v-if="file" class="code-viewer-panel__tabs">
      <span class="editor-tab editor-tab--active">{{ file.path }}</span>
      <span v-if="generating" class="code-viewer-panel__badge">写入中</span>
    </div>

    <div class="code-viewer-panel__monaco">
      <MonacoEditor
        v-if="file"
        :language="file.language"
        :model-value="file.content"
        :display-value="displayContent"
        :read-only="readOnly"
        :streaming="generating"
      />
      <div v-else class="code-viewer-panel__empty">
        {{ generating ? '等待模型写入文件…' : '选择左侧文件查看代码' }}
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
import { useMonacoTypewriter } from '@/composables/useMonacoTypewriter'
import type { ProjectFile, ProjectFileStatus } from '@/utils/projectFiles'

const props = defineProps<{
  file?: ProjectFile
  generating: boolean
  readOnly?: boolean
}>()

const content = ref('')
const status = ref<ProjectFileStatus | undefined>()
const currentPath = ref('')

watch(
  () => props.file,
  (file) => {
    if (file?.path !== currentPath.value) {
      currentPath.value = file?.path ?? ''
      content.value = ''
    }
    content.value = file?.content ?? ''
    status.value = file?.status
  },
  { immediate: true, deep: true },
)

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
