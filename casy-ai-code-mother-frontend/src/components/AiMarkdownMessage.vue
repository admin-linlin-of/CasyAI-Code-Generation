<template>
  <div class="ai-markdown" :class="{ 'ai-markdown--typing': typing }">
    <!-- 流已开始但尚无字符：三点加载 -->
    <div v-if="waiting" class="ai-markdown__loading">
      <span /><span /><span />
    </div>
    <template v-else>
      <div v-if="html" class="ai-markdown__body" v-html="html" />
      <!-- 打字机追赶中：闪烁光标 -->
      <span v-if="typing" class="ai-markdown__cursor" />
    </template>
  </div>
</template>

<script lang="ts" setup>
/**
 * AI 消息展示组件：Markdown 渲染 + 代码高亮 + 打字机效果。
 *
 * 打字机按「原始 SSE 字符数」控制显示进度，而不是 Markdown 长度。
 * 原因：JSON 会被 aiContentToMarkdown 展开成很长的代码块，若按 Markdown 长度
 * 追赶会几帧内显示完全部内容。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { aiContentToMarkdown } from '@/utils/aiContentMarkdown'
import { renderMarkdown } from '@/utils/markdownRenderer'

/** 每帧增加的可见字符数，约 180 字/秒（60fps × 3） */
const CHARS_PER_FRAME = 3

const props = defineProps<{
  content: string // SSE 累积的原始文本
  streaming?: boolean // 是否仍在接收流
}>()

/** 当前打字机已「打出」的字符数（相对 content） */
const displayLen = ref(0)
let rafId = 0

const streamTarget = computed(() => props.content.length)

/** 等待首包 */
const waiting = computed(() => !!props.streaming && streamTarget.value === 0)

/** 已收到内容但 displayLen 尚未追上 */
const typing = computed(
  () => !!props.streaming && displayLen.value < streamTarget.value,
)

/** 按 displayLen 截断的原始文本，再转 Markdown */
const visibleRaw = computed(() => {
  const len = props.streaming ? displayLen.value : props.content.length
  return props.content.slice(0, len)
})

const html = computed(() => renderMarkdown(aiContentToMarkdown(visibleRaw.value)))

const stopAnim = () => {
  if (rafId) cancelAnimationFrame(rafId)
  rafId = 0
}

/** 每帧多显示 CHARS_PER_FRAME 个字符，直到追上 content.length */
const tick = () => {
  const target = props.content.length
  if (!props.streaming || displayLen.value >= target) {
    rafId = 0
    return
  }
  displayLen.value = Math.min(target, displayLen.value + CHARS_PER_FRAME)
  // 浏览器大约 每秒 60 次（每屏刷新一次）会执行你注册的回调。requestAnimationFrame(tick) 的意思是：下一帧刷新屏幕之前，请执行一次 tick 函数。
  rafId = requestAnimationFrame(tick)
}

/** 有新 SSE 数据且动画未在跑时启动循环（不重置 displayLen，避免闪烁） */
const ensureAnim = () => {
  if (!props.streaming) return
  if (displayLen.value < props.content.length && !rafId) {
    rafId = requestAnimationFrame(tick)
  }
}

watch(
  () => props.content.length,
  () => {
    if (props.streaming) {
      ensureAnim()
      return
    }
    displayLen.value = props.content.length
    stopAnim()
  },
)

watch(
  () => props.streaming,
  (streaming) => {
    if (streaming) {
      ensureAnim()
      return
    }
    stopAnim()
    displayLen.value = props.content.length
  },
  { immediate: true },
)

onBeforeUnmount(stopAnim)
</script>

<style scoped>
.ai-markdown {
  position: relative;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
  min-width: 56px;
  min-height: 22px;
}

.ai-markdown__loading {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 2px;
}

.ai-markdown__loading span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1677ff;
  opacity: 0.35;
  animation: ai-dot-bounce 1.2s ease-in-out infinite;
}

.ai-markdown__loading span:nth-child(2) {
  animation-delay: 0.15s;
}

.ai-markdown__loading span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes ai-dot-bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  40% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

.ai-markdown__body :deep(p) {
  margin: 0 0 8px;
}

.ai-markdown__body :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-markdown__body :deep(h1),
.ai-markdown__body :deep(h2),
.ai-markdown__body :deep(h3) {
  margin: 12px 0 8px;
  font-weight: 600;
}

.ai-markdown__body :deep(strong) {
  display: block;
  margin: 10px 0 6px;
  font-size: 13px;
  color: #1677ff;
}

.ai-markdown__body :deep(pre) {
  margin: 8px 0;
  padding: 12px;
  border-radius: 8px;
  overflow: auto;
  background: #f6f8fa;
  border: 1px solid var(--border-color, #e8e8e8);
  font-size: 12px;
  line-height: 1.5;
}

.ai-markdown__body :deep(pre code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  background: transparent;
  padding: 0;
}

.ai-markdown__body :deep(code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
}

.ai-markdown__body :deep(:not(pre) > code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(22, 119, 255, 0.08);
}

.ai-markdown__cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: #1677ff;
  animation: ai-cursor-blink 0.9s step-end infinite;
}

@keyframes ai-cursor-blink {
  50% {
    opacity: 0;
  }
}
</style>
