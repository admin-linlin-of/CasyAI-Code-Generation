<template>
  <div class="code-workspace">
    <!-- 左侧：虚拟文件树，点击切换当前编辑文件 -->
    <aside class="code-workspace__tree">
      <div class="code-workspace__tree-title">文件</div>
      <ul class="file-list">
        <li
          v-for="file in files"
          :key="file.path"
          :class="['file-list__item', { 'file-list__item--active': file.path === activePath }]"
          @click="activePath = file.path"
        >
          <FileOutlined class="file-list__icon" />
          <span class="file-list__name">{{ file.path }}</span>
        </li>
      </ul>
    </aside>

    <!-- 右侧：Tab 栏 + Monaco 编辑器 -->
    <div class="code-workspace__editor">
      <div class="code-workspace__tabs">
        <span
          v-for="file in files"
          :key="file.path"
          :class="['editor-tab', { 'editor-tab--active': file.path === activePath }]"
          @click="activePath = file.path"
        >
          {{ file.path }}
        </span>
      </div>
      <div class="code-workspace__monaco">
        <MonacoEditor
          v-if="activeFile"
          :language="activeFile.language"
          :model-value="activeFile.content"
          :read-only="readOnly"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { FileOutlined } from '@ant-design/icons-vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
import type { VirtualFile } from '@/utils/virtualFiles'

const props = withDefaults(
  defineProps<{
    /** 虚拟文件列表，由 AppChat 从 AI 输出解析得到 */
    files: VirtualFile[]
    /** 是否只读，生成过程中为 true */
    readOnly?: boolean
  }>(),
  {
    readOnly: true,
  },
)

// 当前选中的文件路径，默认 index.html
const activePath = ref('index.html')

// 根据 activePath 找到对应的虚拟文件对象
const activeFile = computed(() =>
  props.files.find((file) => file.path === activePath.value),
)

// 流式生成时若当前文件尚无内容，自动切到有内容的文件
watch(
  () => props.files,
  (files) => {
    const current = files.find((file) => file.path === activePath.value)
    if (current?.content.trim()) return
    const firstWithContent = files.find((file) => file.content.trim())
    if (firstWithContent) activePath.value = firstWithContent.path
  },
  { deep: true, immediate: true },
)
</script>

<style scoped>
.code-workspace {
  display: flex;
  height: 100%;
  min-height: 0;
}

.code-workspace__tree {
  width: 160px;
  flex-shrink: 0;
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.code-workspace__tree-title {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-color);
}

.file-list {
  list-style: none;
  margin: 0;
  padding: 6px 0;
  overflow: auto;
}

.file-list__item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-main);
}

.file-list__item:hover {
  background: rgba(22, 119, 255, 0.06);
}

.file-list__item--active {
  background: rgba(22, 119, 255, 0.12);
  color: #1677ff;
}

.file-list__icon {
  font-size: 12px;
  opacity: 0.7;
}

.file-list__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-workspace__editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.code-workspace__tabs {
  display: flex;
  gap: 2px;
  padding: 0 8px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-page);
  overflow-x: auto;
}

.editor-tab {
  padding: 8px 12px;
  font-size: 12px;
  cursor: pointer;
  color: var(--text-secondary);
  border-bottom: 2px solid transparent;
  white-space: nowrap;
}

.editor-tab--active {
  color: var(--text-main);
  border-bottom-color: #1677ff;
}

.code-workspace__monaco {
  flex: 1;
  min-height: 240px;
  display: flex;
  flex-direction: column;
}
</style>
