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
import MonacoEditor from '@/components/MonacoEditor.vue'
import type { ProjectFile } from '@/utils/projectFiles'

defineProps<{
  file?: ProjectFile
  generating: boolean
  readOnly?: boolean
}>()
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
