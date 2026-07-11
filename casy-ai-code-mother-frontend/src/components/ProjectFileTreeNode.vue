<!--
  ProjectFileTreeNode — 目录树单个节点（支持递归）

  两种节点：
    isDir=true  → 文件夹行：点击展开/折叠，不触发选文件
    isDir=false → 文件行：点击 emit('select', path) 切换 Monaco 显示

  递归：文件夹的 children 再次渲染 TreeNode（即本组件自身），depth+1 控制缩进
-->
<template>
  <li class="tree-node">
    <!-- ── 文件夹节点：仅控制展开，不选中文件 ── -->
    <div
      v-if="node.isDir"
      class="tree-node__row tree-node__row--dir"
      :style="{ paddingLeft: `${8 + depth * 14}px` }"
      @click="expanded = !expanded"
    >
      <CaretRightOutlined :class="['tree-node__caret', { 'tree-node__caret--open': expanded }]" />
      <FolderOutlined class="tree-node__icon" />
      <span class="tree-node__name">{{ node.name }}</span>
    </div>

    <!-- ── 文件节点：点击选中，高亮 active / generating ── -->
    <div
      v-else
      :class="[
        'tree-node__row',
        {
          'tree-node__row--active': node.path === activePath,
          'tree-node__row--generating': generatingPaths?.has(node.path),
        },
      ]"
      :style="{ paddingLeft: `${8 + depth * 14}px` }"
      @click="emit('select', node.path)"
    >
      <FileOutlined class="tree-node__icon" />
      <span class="tree-node__name">{{ node.name }}</span>
    </div>

    <!-- 文件夹展开时，递归渲染子节点；@select 事件原样向上冒泡 -->
    <ul v-if="node.isDir && expanded && node.children?.length" class="tree-node__children">
      <TreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :active-path="activePath"
        :generating-paths="generatingPaths"
        :depth="depth + 1"
        @select="emit('select', $event)"
      />
    </ul>
  </li>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { CaretRightOutlined, FileOutlined, FolderOutlined } from '@ant-design/icons-vue'
import TreeNode from '@/components/ProjectFileTreeNode.vue'
import type { FileTreeNode } from '@/utils/projectFiles'

defineOptions({ name: 'ProjectFileTreeNode' })

defineProps<{
  /** 当前树节点数据（name / path / isDir / children） */
  node: FileTreeNode
  /** 当前选中的文件 path，与 node.path 相等时加 active 样式 */
  activePath: string
  /** SSE 写入中的 path 集合，命中时文件名变绿 */
  generatingPaths?: Set<string>
  /** 嵌套深度，用于 paddingLeft 缩进 */
  depth: number
}>()

const emit = defineEmits<{
  select: [path: string]
}>()

/** 文件夹默认展开；点击文件夹行切换 */
const expanded = ref(true)
</script>

<style scoped>
.tree-node {
  list-style: none;
}

.tree-node__children {
  list-style: none;
  margin: 0;
  padding: 0;
}

.tree-node__row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px 5px 0;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-main);
}

.tree-node__row:hover {
  background: rgba(22, 119, 255, 0.06);
}

.tree-node__row--active {
  background: rgba(22, 119, 255, 0.12);
  color: #1677ff;
}

.tree-node__row--generating .tree-node__name {
  color: #52c41a;
}

.tree-node__row--dir {
  color: var(--text-secondary);
}

.tree-node__caret {
  font-size: 10px;
  transition: transform 0.15s;
}

.tree-node__caret--open {
  transform: rotate(90deg);
}

.tree-node__icon {
  font-size: 12px;
  opacity: 0.75;
}

.tree-node__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
