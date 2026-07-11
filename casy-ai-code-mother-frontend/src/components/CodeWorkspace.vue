<!--
  CodeWorkspace — 右侧「代码」面板总容器（左树 + 右编辑器）

  两种模式（由 AppChat 根据应用类型传入 mode）：

  ┌─ mode=flat ─────────────────────────────────────────────┐
  │  HTML / MULTI_FILE：固定 3 文件（index.html 等）         │
  │  左侧：扁平 file-list                                    │
  │  右侧：Tab + MonacoEditor（直接绑 content，无打字机）     │
  └──────────────────────────────────────────────────────────┘

  ┌─ mode=tree ─────────────────────────────────────────────┐
  │  VUE_PROJECT：多目录多文件                               │
  │  左侧：ProjectFileTree                                   │
  │  右侧：CodeViewerPanel（Monaco + 打字机）                │
  │  数据来自 useProjectFileStore（projectFiles / paths）    │
  └──────────────────────────────────────────────────────────┘
-->
<template>
  <div class="code-workspace">
    <!-- ── 左侧：文件列表 / 目录树 ── -->
    <aside class="code-workspace__tree">
      <div class="code-workspace__tree-title">文件</div>

      <!-- Vue 项目：树形目录 -->
      <ProjectFileTree
        v-if="mode === 'tree'"
        :paths="projectPaths"
        :active-path="treeActivePath"
        :generating-paths="generatingPaths"
        @select="onTreeSelect"
      />

      <!-- HTML/MULTI_FILE：三文件扁平列表 -->
      <ul v-else class="file-list">
        <li
          v-for="file in files"
          :key="file.path"
          :class="['file-list__item', { 'file-list__item--active': file.path === flatActivePath }]"
          @click="flatActivePath = file.path"
        >
          <FileOutlined class="file-list__icon" />
          <span class="file-list__name">{{ file.path }}</span>
        </li>
      </ul>
    </aside>

    <!-- ── 右侧：编辑器区域 ── -->
    <div class="code-workspace__editor">
      <!-- Vue 项目：带打字机的 CodeViewerPanel -->
      <template v-if="mode === 'tree'">
        <CodeViewerPanel
          :file="activeProjectFile"
          :generating="generating"
          :read-only="readOnly"
        />
      </template>

      <!-- 传统三文件：Tab + Monaco 直出 -->
      <template v-else>
        <div class="code-workspace__tabs">
          <span
            v-for="file in files"
            :key="file.path"
            :class="['editor-tab', { 'editor-tab--active': file.path === flatActivePath }]"
            @click="flatActivePath = file.path"
          >
            {{ file.path }}
          </span>
        </div>
        <div class="code-workspace__monaco">
          <MonacoEditor
            v-if="activeFlatFile"
            :language="activeFlatFile.language"
            :model-value="activeFlatFile.content"
            :read-only="readOnly"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { FileOutlined } from '@ant-design/icons-vue'
import CodeViewerPanel from '@/components/CodeViewerPanel.vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
import ProjectFileTree from '@/components/ProjectFileTree.vue'
import type { ProjectFile } from '@/utils/projectFiles'
import type { VirtualFile } from '@/utils/virtualFiles'

const props = withDefaults(
  defineProps<{
    /** flat=三文件模式；tree=Vue 多文件目录树模式 */
    mode?: 'flat' | 'tree'
    /** flat 模式：虚拟文件列表（来自 AI 解析或静态目录） */
    files?: VirtualFile[]
    /** tree 模式：store 中的 ProjectFile 对象列表 */
    projectFiles?: ProjectFile[]
    /** tree 模式：扁平 path 列表，供 buildFileTree */
    projectPaths?: string[]
    /** tree 模式：当前选中文件，与 AppChat v-model:active-path 双向绑定 */
    activePath?: string
    /** 是否处于 SSE 生成中（传给 CodeViewerPanel 控制打字机） */
    generating?: boolean
    readOnly?: boolean
  }>(),
  {
    mode: 'flat',
    files: () => [],
    projectFiles: () => [],
    projectPaths: () => [],
    generating: false,
    readOnly: true,
  },
)

const emit = defineEmits<{
  'update:activePath': [path: string]
}>()

/** flat 模式内部维护的当前文件 path */
const flatActivePath = ref('index.html')

/**
 * tree 模式的 activePath 代理：
 * get 读 props.activePath；set 通过 emit 通知 AppChat / store 更新
 */
const treeActivePath = computed({
  get: () => props.activePath ?? '',
  set: (path: string) => emit('update:activePath', path),
})

/** 从 projectFiles 筛出 status=generating 的 path，供树节点绿色高亮 */
const generatingPaths = computed(
  () => new Set(props.projectFiles.filter((f) => f.status === 'generating').map((f) => f.path)),
)

/** 当前选中的 ProjectFile 对象，传给 CodeViewerPanel */
const activeProjectFile = computed(() =>
  props.projectFiles.find((file) => file.path === treeActivePath.value),
)

/** flat 模式当前选中的 VirtualFile */
const activeFlatFile = computed(() => props.files.find((file) => file.path === flatActivePath.value))

/** 用户点击树节点 → 更新 activePath（会同步到 store） */
const onTreeSelect = (path: string) => {
  treeActivePath.value = path
}

/**
 * flat 模式：流式生成时若当前文件仍空，自动切到第一个有内容的文件。
 */
watch(
  () => props.files,
  (files) => {
    if (props.mode !== 'flat') return
    const current = files.find((file) => file.path === flatActivePath.value)
    if (current?.content.trim()) return
    const firstWithContent = files.find((file) => file.content.trim())
    if (firstWithContent) flatActivePath.value = firstWithContent.path
  },
  { deep: true, immediate: true },
)

/**
 * tree 模式：路径列表变化时，若当前选中 path 不存在，默认打开第一项。
 * 场景：首次 refreshFromServer 或生成过程中新增文件。
 */
watch(
  () => props.projectPaths,
  (paths) => {
    if (props.mode !== 'tree' || !paths.length) return
    if (!treeActivePath.value || !paths.includes(treeActivePath.value)) {
      treeActivePath.value = paths[0]!
    }
  },
  { immediate: true },
)
</script>

<style scoped>
.code-workspace {
  display: flex;
  height: 100%;
  min-height: 0;
}

.code-workspace__tree {
  width: 220px;
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
