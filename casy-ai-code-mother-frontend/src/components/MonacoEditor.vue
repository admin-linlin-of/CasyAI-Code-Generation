<template>
  <!-- Monaco 挂载点：editor.create() 会把编辑器 DOM 插入这个 div -->
  <div ref="containerRef" class="monaco-editor-host" />
</template>

<script lang="ts" setup>
/**
 * Monaco 编辑器 Vue 封装
 *
 * 职责：
 *   - 调用 loadMonaco() 完成 Monaco 初始化（见 monacoSetup.ts 注释）
 *   - 在 containerRef 上创建编辑器实例
 *   - 响应 props 变化，同步代码内容 / 语言 / 主题 / 只读状态
 *
 * 数据流：
 *   CodeWorkspace 传入 modelValue（文件内容）和 language（html/css/javascript）
 *     → 本组件 watch props → editor.setValue() / setModelLanguage()
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { loadMonaco } from '@/utils/monacoSetup'
import { useThemeStore } from '@/stores/theme'

const props = withDefaults(
  defineProps<{
    modelValue: string
    /** 打字机展示值；未传时与 modelValue 一致 */
    displayValue?: string
    language?: string
    readOnly?: boolean
  }>(),
  {
    language: 'html',
    readOnly: true,
  },
)

const shownValue = computed(() => props.displayValue ?? props.modelValue)

const containerRef = ref<HTMLElement>()
const { isDark } = useThemeStore()

/** loadMonaco() 返回的 monaco 命名空间，含 editor / languages 等 API */
let monacoApi: Awaited<ReturnType<typeof loadMonaco>> | null = null
/** 编辑器实例，卸载时必须 dispose 释放内存 */
let editor: import('monaco-editor').editor.IStandaloneCodeEditor | null = null

const getMonacoTheme = () => (isDark.value ? 'vs-dark' : 'vs')

/** 将外部传入的代码同步到编辑器（SSE 流式更新时会频繁调用） */
const syncContent = (value: string) => {
  if (!editor) return
  if (editor.getValue() === value) return
  editor.setValue(value)
}

/** 切换文件时更新语法高亮语言（如 index.html → style.css） */
const syncLanguage = (language: string) => {
  if (!editor || !monacoApi) return
  const model = editor.getModel()
  if (!model) return
  monacoApi.editor.setModelLanguage(model, language)
}

onMounted(async () => {
  if (!containerRef.value) return

  // loadMonaco 是异步的：首次调用会配置 Worker 并 init，后续调用复用同一 Promise
  monacoApi = await loadMonaco()

  editor = monacoApi.editor.create(containerRef.value, {
    value: shownValue.value,
    language: props.language,
    theme: getMonacoTheme(),
    readOnly: props.readOnly,
    automaticLayout: true, // 容器尺寸变化时自动重算布局
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    fontSize: 13,
    lineNumbers: 'on',
    wordWrap: 'on',
    tabSize: 2,
  })

  // init 完成前 props 可能已有内容（如静态文件已加载），这里补同步一次
  syncContent(shownValue.value)
  await nextTick()
  editor?.layout()
})

watch(shownValue, syncContent)
watch(() => props.language, syncLanguage)
watch(
  () => props.readOnly,
  (readOnly) => editor?.updateOptions({ readOnly }),
)

watch(isDark, () => {
  monacoApi?.editor.setTheme(getMonacoTheme())
})

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
  monacoApi = null
})
</script>

<style scoped>
.monaco-editor-host {
  width: 100%;
  height: 100%;
  min-height: 240px;
}
</style>
