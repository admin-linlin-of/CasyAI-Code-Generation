<template>
  <div class="ai-markdown" :class="{ 'ai-markdown--typing': typing }">
    <!-- 流已开始但尚无字符：三点加载（工具执行阶段常见，Vue 项目 LLM 长时间无文本输出） -->
    <div v-if="waiting" class="ai-markdown__loading">
      <span /><span /><span />
    </div>
    <template v-else>
      <!-- 按 displayLen 截断后的 Markdown HTML -->
      <div v-if="html" class="ai-markdown__body" v-html="html" />
      <!-- displayLen 尚未追上 content.length 时显示闪烁光标 -->
      <span v-if="typing" class="ai-markdown__cursor" />
    </template>
  </div>
</template>

<script lang="ts" setup>
/**
 * AI 消息展示组件：Markdown 渲染 + 代码高亮 + 打字机效果。
 * <p>
 * <b>调用方</b>：{@link AppChat.vue} 的 {@code startStream}，通过 SSE 累积 {@code aiMsg.content}，
 * 并传入 {@code streaming} 表示是否仍在接收流。
 * <p>
 * <b>为何按「原始 SSE 字符数」而非 Markdown 长度控制打字机</b>：
 * {@link aiContentToMarkdown} 会把 JSON / 代码块展开成很长的 Markdown；
 * 若按 Markdown 长度追赶，几帧内就会显示完全部渲染结果，失去打字机效果。
 * 因此用 {@code displayLen} 跟踪 {@code props.content}（SSE 原文）的可见前缀长度。
 * <p>
 * <b>历史消息 vs 实时流</b>：
 * <ul>
 *   <li>历史（{@code loadChatHistory}）：{@code streaming} 始终为 false/undefined，{@code everStreamed} 为 false → 挂载即 {@code displayLen = content.length}，无打字机</li>
 *   <li>实时 SSE（{@code startStream}）：{@code streaming=true} 时 {@code everStreamed=true} → 打字机；流结束后继续追到全文</li>
 * </ul>
 *
 * @see AppChat.vue#startStream
 * @see aiContentToMarkdown
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { aiContentToMarkdown } from '@/utils/aiContentMarkdown'
import { renderMarkdown } from '@/utils/markdownRenderer'

/** 每帧增加的可见字符数，约 180 字/秒（60fps × 3） */
const CHARS_PER_FRAME = 3

const props = defineProps<{
  /** SSE 累积的 AI 回复原文（AppChat 中 aiMsg.content） */
  content: string
  /** 是否仍在接收 SSE；false 表示 done 事件已触发，但打字机可能仍在追赶 */
  streaming?: boolean
}>()

/**
 * 打字机当前已「打出」的字符数（相对 props.content 的下标，不含）。
 * 渲染时只展示 content.slice(0, displayLen)。
 */
const displayLen = ref(0)

/** 本条消息是否经历过 streaming=true（区分历史加载与实时 SSE） */
const everStreamed = ref(false)

/** requestAnimationFrame 句柄；非 0 表示动画循环正在运行 */
let rafId = 0

/**
 * 等待首包：流已开始但 content 仍为空。
 * Vue 项目生成时，LLM 可能先执行 writeFile 工具，数十秒内无文本 SSE。
 */
const waiting = computed(() => !!props.streaming && props.content.length === 0)

/**
 * 是否处于打字机追赶中（仅实时流式消息；历史消息 everStreamed=false 不参与）。
 */
const typing = computed(
  () => everStreamed.value && displayLen.value < props.content.length,
)

/** 当前应展示的 SSE 原文前缀（打字机截断结果） */
const visibleRaw = computed(() => props.content.slice(0, displayLen.value))

/** 可见原文 → Markdown → HTML，供 v-html 渲染 */
const html = computed(() => renderMarkdown(aiContentToMarkdown(visibleRaw.value)))

/** 取消未完成的 rAF 循环，组件卸载或无需动画时调用 */
const stopAnim = () => {
  if (rafId) cancelAnimationFrame(rafId)
  rafId = 0
}

/**
 * 单帧动画：displayLen 向 content.length 靠近 CHARS_PER_FRAME 个字符。
 * 追上目标后停止调度下一帧。
 */
const tick = () => {
  const target = props.content.length
  if (displayLen.value >= target) {
    rafId = 0
    return
  }
  displayLen.value = Math.min(target, displayLen.value + CHARS_PER_FRAME)
  rafId = requestAnimationFrame(tick)
}

/**
 * 若 displayLen 落后 content 且当前无 rAF 循环，则启动 tick。
 * 历史消息（never streamed）不启动动画。
 */
const ensureAnim = () => {
  if (!everStreamed.value) return
  if (displayLen.value < props.content.length && !rafId) {
    rafId = requestAnimationFrame(tick)
  }
}

/** 历史消息：一次性展示全文，不跑打字机 */
const showInstant = () => {
  displayLen.value = props.content.length
  stopAnim()
}

/** content 变化：历史直接全文；实时流则续跑打字机 */
watch(
  () => props.content.length,
  () => {
    if (!everStreamed.value) {
      showInstant()
      return
    }
    ensureAnim()
  },
)

/**
 * streaming 变化：
 * - true：标记 everStreamed，启动打字机
 * - false 且 everStreamed：流结束，继续追赶
 * - false 且未 everStreamed：历史消息，直接全文
 */
watch(
  () => props.streaming,
  (streaming) => {
    if (streaming) {
      everStreamed.value = true
      ensureAnim()
      return
    }
    if (!everStreamed.value) {
      showInstant()
      return
    }
    ensureAnim()
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
