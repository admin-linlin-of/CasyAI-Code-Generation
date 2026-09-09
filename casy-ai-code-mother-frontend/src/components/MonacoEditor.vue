<template>
  <div
    ref="containerRef"
    class="monaco-editor-host"
    :class="{ 'monaco-editor-host--streaming': streaming }"
  />
</template>

<script lang="ts" setup>
/**
 * Monaco 编辑器 Vue 封装
 *
 * 职责：
 *   - 调用 loadMonaco() 完成 Monaco 初始化（见 monacoSetup.ts 注释）
 *   - 在 containerRef 上创建编辑器实例
 *   - 响应 props 变化，同步代码内容 / 语言 / 主题 / 只读状态
 *   - streaming 时自动滚到末尾，配合打字机效果
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { loadMonaco } from '@/utils/monacoSetup'
import { useThemeStore } from '@/stores/theme'

const props = withDefaults(
  defineProps<{
    modelValue: string
    displayValue?: string
    language?: string
    readOnly?: boolean
    streaming?: boolean
  }>(),
  {
    language: 'html',
    readOnly: true,
    streaming: false,
  },
)

const shownValue = computed(() => props.displayValue ?? props.modelValue)

const containerRef = ref<HTMLElement>()
const { isDark } = useThemeStore()

let monacoApi: Awaited<ReturnType<typeof loadMonaco>> | null = null
let editor: import('monaco-editor').editor.IStandaloneCodeEditor | null = null

const getMonacoTheme = () => (isDark.value ? 'vs-dark' : 'vs')

const syncContent = (value: string) => {
  if (!editor) return
  if (editor.getValue() === value) return
  editor.setValue(value)
  if (props.streaming) {
    const model = editor.getModel()
    const line = model?.getLineCount() ?? 1
    editor.revealLine(line)
  }
}

const syncLanguage = (language: string) => {
  if (!editor || !monacoApi) return
  const model = editor.getModel()
  if (!model) return
  monacoApi.editor.setModelLanguage(model, language)
}

onMounted(async () => {
  if (!containerRef.value) return

  monacoApi = await loadMonaco()

  editor = monacoApi.editor.create(containerRef.value, {
    value: shownValue.value,
    language: props.language,
    theme: getMonacoTheme(),
    readOnly: props.readOnly,
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    fontSize: 13,
    lineNumbers: 'on',
    wordWrap: 'on',
    tabSize: 2,
  })

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

.monaco-editor-host--streaming {
  box-shadow: inset 0 -2px 0 rgba(22, 119, 255, 0.35);
}
</style>
