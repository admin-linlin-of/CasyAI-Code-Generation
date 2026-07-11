<!--
  ProjectFileTree — Vue 项目左侧「目录树」容器

  职责：把扁平路径数组转成树形结构，并渲染顶层节点。

  数据流：
    AppChat → useProjectFileStore.filePaths（如 ["package.json","src/App.vue"]）
      → CodeWorkspace :project-paths
      → 本组件 :paths
      → buildFileTree() 转成树
      → ProjectFileTreeNode 递归渲染

  用户点击文件 → emit('select', path) → CodeWorkspace → v-model:active-path → store
-->
<template>
  <ul class="project-file-tree">
    <!-- 只渲染根层级；子目录由 ProjectFileTreeNode 内部递归 -->
    <ProjectFileTreeNode
      v-for="node in tree"
      :key="node.path"
      :node="node"
      :active-path="activePath"
      :generating-paths="generatingPaths"
      :depth="0"
      @select="emit('select', $event)"
    />
  </ul>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import ProjectFileTreeNode from '@/components/ProjectFileTreeNode.vue'
import { buildFileTree } from '@/utils/projectFiles'

const props = defineProps<{
  /** 扁平相对路径列表，如 ["src/App.vue", "src/main.js"] */
  paths: string[]
  /** 当前 Monaco 正在查看的文件路径，用于高亮 */
  activePath: string
  /** 正在 SSE 写入中的文件 path 集合，用于绿色「写入中」样式 */
  generatingPaths?: Set<string>
}>()

const emit = defineEmits<{
  /** 用户点击某个文件节点时，向上抛出完整相对路径 */
  select: [path: string]
}>()

/**
 * 将 paths 转为树形 FileTreeNode[]。
 * 例：["src/a.vue","src/b.vue"] → [{ name:"src", isDir:true, children:[...] }]
 */
const tree = computed(() => buildFileTree(props.paths))
</script>

<style scoped>
.project-file-tree {
  list-style: none;
  margin: 0;
  padding: 6px 0;
  overflow: auto;
}
</style>
