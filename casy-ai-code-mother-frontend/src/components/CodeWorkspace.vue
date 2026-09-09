<template>
  <div class="code-workspace" :class="{ 'code-workspace--live': generating }">
    <aside class="code-workspace__tree">
      <div class="code-workspace__tree-title">文件</div>

      <ProjectFileTree
        v-if="mode === 'tree'"
        :paths="projectPaths"
        :active-path="treeActivePath"
        :generating-paths="generatingPaths"
        @select="onTreeSelect"
      />

      <ul v-else class="file-list">
        <li
          v-for="file in files"
          :key="file.path"
          :class="[
            'file-list__item',
            { 'file-list__item--active': file.path === flatActivePath },
            { 'file-list__item--writing': generating && file.content.trim() },
          ]"
          @click="flatActivePath = file.path"
        >
          <FileOutlined class="file-list__icon" />
          <span class="file-list__name">{{ file.path }}</span>
        </li>
      </ul>
    </aside>

    <div class="code-workspace__editor">
      <template v-if="mode === 'tree'">
        <CodeViewerPanel
          :file="activeProjectFile"
          :generating="generating"
          :read-only="readOnly"
        />
      </template>

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
          <span v-if="generating && activeFlatFile?.content" class="code-workspace__badge">写入中</span>
        </div>
        <div class="code-workspace__monaco">
          <MonacoEditor
            v-if="activeFlatFile"
            :language="activeFlatFile.language"
            :model-value="activeFlatFile.content"
            :display-value="flatShownContent"
            :read-only="readOnly"
            :streaming="generating"
          />
          <div v-else class="code-workspace__empty">等待模型输出代码…</div>
        </div>
      </template>

      <div v-if="generating" class="code-wave" aria-hidden="true">
        <span class="code-wave__line code-wave__line--1" />
        <span class="code-wave__line code-wave__line--2" />
        <span class="code-wave__line code-wave__line--3" />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { FileOutlined } from '@ant-design/icons-vue'
import CodeViewerPanel from '@/components/CodeViewerPanel.vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
import ProjectFileTree from '@/components/ProjectFileTree.vue'
import type { ProjectFile } from '@/utils/projectFiles'
import type { VirtualFile } from '@/utils/virtualFiles'

const props = withDefaults(
  defineProps<{
    mode?: 'flat' | 'tree'
    files?: VirtualFile[]
    projectFiles?: ProjectFile[]
    projectPaths?: string[]
    activePath?: string
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

const flatActivePath = ref('index.html')

const treeActivePath = computed({
  get: () => props.activePath ?? '',
  set: (path: string) => emit('update:activePath', path),
})

const generatingPaths = computed(
  () =>
    new Set(
      props.projectFiles
        .filter((f) => f.status === 'generating' || (props.generating && f.content.trim()))
        .map((f) => f.path),
    ),
)

const activeProjectFile = computed(() =>
  props.projectFiles.find((file) => file.path === treeActivePath.value),
)

const activeFlatFile = computed(() => props.files.find((file) => file.path === flatActivePath.value))

const flatReveal = ref<Record<string, number>>({})
let flatTimer: ReturnType<typeof setTimeout> | null = null

const flatShownContent = computed(() => {
  const file = activeFlatFile.value
  if (!file) return ''
  if (!props.generating) return file.content
  const reached = flatReveal.value[file.path] ?? 0
  return file.content.slice(0, Math.min(reached, file.content.length))
})

const clearFlatTimer = () => {
  if (flatTimer) {
    clearTimeout(flatTimer)
    flatTimer = null
  }
}

const flatTick = () => {
  flatTimer = null
  const file = activeFlatFile.value
  if (!file || !props.generating) return
  let current = flatReveal.value[file.path] ?? 0
  const target = file.content.length
  if (current > target) {
    current = 0
    flatReveal.value = { ...flatReveal.value, [file.path]: 0 }
  }
  if (current >= target) return
  const remain = target - current
  const step = Math.max(3, Math.min(48, Math.ceil(remain / 10)))
  flatReveal.value = { ...flatReveal.value, [file.path]: Math.min(target, current + step) }
  flatTimer = setTimeout(flatTick, 16)
}

const ensureFlatTypewriter = () => {
  const file = activeFlatFile.value
  if (props.generating && file) {
    if ((flatReveal.value[file.path] ?? 0) < file.content.length) {
      if (!flatTimer) flatTimer = setTimeout(flatTick, 16)
    }
    return
  }
  clearFlatTimer()
}

watch(
  () => [props.generating, props.files, flatActivePath.value, activeFlatFile.value?.content] as const,
  ensureFlatTypewriter,
  { deep: true, immediate: true },
)

watch(
  () => props.generating,
  (isGenerating) => {
    clearFlatTimer()
    if (isGenerating) {
      flatReveal.value = {}
      ensureFlatTypewriter()
      return
    }
    const full: Record<string, number> = {}
    for (const file of props.files) full[file.path] = file.content.length
    flatReveal.value = full
  },
  { immediate: true },
)

watch(
  () => props.files,
  (files) => {
    if (!files.length) flatReveal.value = {}
  },
)

onBeforeUnmount(clearFlatTimer)

const onTreeSelect = (path: string) => {
  treeActivePath.value = path
}

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
  position: relative;
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

.file-list__item--writing .file-list__name {
  color: #52c41a;
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
  position: relative;
}

.code-workspace__tabs {
  display: flex;
  align-items: center;
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

.code-workspace__badge {
  margin-left: 8px;
  font-size: 11px;
  color: #52c41a;
}

.code-workspace__monaco {
  flex: 1;
  min-height: 240px;
  display: flex;
  flex-direction: column;
}

.code-workspace__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.code-wave {
  pointer-events: none;
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 72px;
  overflow: hidden;
  z-index: 2;
}

.code-wave__line {
  position: absolute;
  left: -20%;
  width: 140%;
  height: 48px;
  border-radius: 50%;
  opacity: 0.55;
}

.code-wave__line--1 {
  bottom: -18px;
  background: radial-gradient(ellipse at center, rgba(22, 119, 255, 0.28), transparent 68%);
  animation: code-wave-drift 2.8s ease-in-out infinite;
}

.code-wave__line--2 {
  bottom: -28px;
  background: radial-gradient(ellipse at center, rgba(82, 196, 26, 0.2), transparent 70%);
  animation: code-wave-drift 3.6s ease-in-out infinite reverse;
}

.code-wave__line--3 {
  bottom: -10px;
  background: radial-gradient(ellipse at center, rgba(105, 177, 255, 0.22), transparent 65%);
  animation: code-wave-drift 2.2s ease-in-out infinite;
  animation-delay: -0.8s;
}

@keyframes code-wave-drift {
  0%,
  100% {
    transform: translateX(-6%) translateY(6px) scaleY(0.85);
  }
  50% {
    transform: translateX(6%) translateY(-4px) scaleY(1.15);
  }
}
</style>
