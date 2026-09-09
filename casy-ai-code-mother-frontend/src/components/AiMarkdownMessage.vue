<template>
  <div
    class="ai-md"
    :class="{ 'ai-md--typing': typing, 'ai-md--live': live }"
  >
    <!-- ══ Agent 工作流：始终同一张「步骤列表」卡片 ══ -->
    <div v-if="isAgent && !waiting && !waitingClassic" class="wf">
      <div class="wf__head">
        <span class="wf__mark" :class="{ 'is-run': live }" />
        <div class="wf__head-main">
          <div class="wf__title">代码生成工作流</div>
          <div class="wf__sub">{{ live ? `已运行 ${formatElapsed(elapsed)}` : workflowSub }}</div>
        </div>
        <span class="wf__count">{{ live ? liveCountText : `${wfSteps.length} 步` }}</span>
      </div>

      <!-- 生成中：以“当前执行步骤”行承载实时输出（工具徽标/文字），样式与结束态一致 -->
      <div v-if="live" class="wf__list">
        <div class="wf-step is-open is-now">
          <div class="wf-step__row">
            <span class="wf-step__dot" />
            <span class="wf-step__name">{{ currentHint }}</span>
            <span class="wf-step__chev" />
          </div>
          <div class="wf-step__detail">
            <div class="wf-step__md ai-md__body">
              <div v-if="html" v-html="html" />
              <span v-if="typing" class="ai-md__cursor" />
            </div>
          </div>
        </div>
      </div>

      <!-- 结束：步骤行收起，可点击展开 -->
      <div v-else-if="wfSteps.length" class="wf__list">
        <div
          v-for="(s, i) in wfSteps"
          :key="s.no"
          class="wf-step"
          :class="{
            'is-open': isStepOpen(s.no),
            'is-done': i < wfSteps.length - 1,
          }"
        >
          <button type="button" class="wf-step__row" @click="toggleStep(s.no)">
            <span class="wf-step__dot" />
            <span class="wf-step__name">{{ s.title }}</span>
            <span class="wf-step__chev" />
          </button>
          <div v-show="isStepOpen(s.no)" class="wf-step__detail">
            <div v-if="s.body" class="wf-step__md ai-md__body" v-html="toHtml(s.body)" />
            <div v-else class="wf-step__pending">本步无文字记录</div>
          </div>
        </div>
      </div>
      <div v-else class="wf__plain ai-md__body">
        <div v-if="html" v-html="html" />
      </div>
      <div v-if="!live && workflow?.footer" class="wf__result" v-html="toHtml(workflow.footer)" />
    </div>

    <!-- ══ 生成中 & 尚无正文：Agent 等待卡片 ══ -->
    <div v-else-if="waiting" class="ai-md__think">
      <div class="ai-md__think-head">
        <span class="ai-md__orb" />
        <span :key="currentHint" class="ai-md__shimmer">{{ currentHint }}</span>
      </div>
      <ul class="ai-md__wait-steps">
        <li
          v-for="(step, i) in WAITING_STEPS"
          :key="step"
          :class="{ 'is-done': i < visualStep, 'is-now': i === visualStep }"
        >
          {{ step }}
        </li>
      </ul>
      <div class="ai-md__bar" />
      <div class="ai-md__elapsed">已运行 {{ formatElapsed(elapsed) }}</div>
    </div>

    <!-- ══ 生成中 & 尚无正文：传统模式等待卡片 ══ -->
    <div v-else-if="waitingClassic" class="ai-md__think ai-md__think--classic">
      <div class="ai-md__think-head">
        <span class="ai-md__orb" />
        <span class="ai-md__shimmer">{{ currentHint }}</span>
      </div>
      <div class="ai-md__bar" />
      <div class="ai-md__elapsed">已运行 {{ formatElapsed(elapsed) }}</div>
    </div>

    <!-- ══ 传统模式（agent=false）：普通气泡，生成中带状态条 ══ -->
    <template v-else>
      <div v-if="live" class="ai-md__status">
        <div class="ai-md__status-row">
          <span class="ai-md__orb ai-md__orb--sm" />
          <span :key="currentHint" class="ai-md__status-text">{{ currentHint }}</span>
          <span class="ai-md__status-time">{{ formatElapsed(elapsed) }}</span>
        </div>
        <div v-if="toolActionCount > 0" class="ai-md__status-meta">
          已完成 {{ toolActionCount }} 次工具调用
        </div>
        <div class="ai-md__bar" />
      </div>
      <div v-if="html" class="ai-md__body" v-html="html" />
      <span v-if="typing" class="ai-md__cursor" />
    </template>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { aiContentToMarkdown } from '@/utils/aiContentMarkdown'
import { renderMarkdown } from '@/utils/markdownRenderer'

const WAITING_STEPS = ['理解需求', '规划结构', '准备素材', '生成代码']
const WAITING_HINTS = ['正在理解需求', '正在规划页面结构', '正在准备素材', '正在生成代码']
const STREAM_CPS = 36
const CATCHUP_CPS = 72

type WfStep = { no: string; title: string; body: string }
type WfView = { title: string; status: string; steps: WfStep[]; footer: string; head: string }

const props = defineProps<{
  content: string
  streaming?: boolean
  agent?: boolean
}>()

const stripHeartbeat = (raw: string) =>
  raw.replace(/\n(?:\.)+/g, '').replace(/(代码生成中[^\n]*)\.+/g, '$1')

const TOOL_TAG_LABELS: Record<string, string> = {
  fileWrite: '写入',
  fileModify: '修改',
  fileRead: '读取',
  fileDelete: '删除',
  dirRead: '浏览目录',
}

/** 从流式原文解析最近一次工具调用，用于动态状态文案 */
const parseLatestToolHint = (raw: string): string | null => {
  const tagRe = /<(fileWrite|fileModify|fileRead|fileDelete|dirRead)>([\s\S]*?)<\/\1>/gi
  let last: string | null = null
  let m: RegExpExecArray | null
  while ((m = tagRe.exec(raw)) !== null) {
    const label = TOOL_TAG_LABELS[m[1]] ?? '处理'
    const pathMatch = m[2].match(/[`']([^`']+)[`']/)
    last = pathMatch?.[1] ? `正在${label} ${pathMatch[1]}` : `正在${label}文件`
  }
  const toolRe = /\[Tool\]\s*(?:write|modify|read|delete)\s*(?:file|dir)?\s*[`']([^`']+)[`']/gi
  while ((m = toolRe.exec(raw)) !== null) {
    last = `正在处理 ${m[1]}`
  }
  return last
}

const formatElapsed = (sec: number) => {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  if (m <= 0) return `${s} 秒`
  return `${m}:${String(s).padStart(2, '0')}`
}

const parseWorkflow = (raw: string): WfView | null => {
  if (!raw.includes('代码生成工作流') && !/\*\*步骤\s+\d+/.test(raw)) return null
  const matches = [...raw.matchAll(/\*\*步骤\s+(\d+)\s+[·.]\s+(.+?)\*\*/g)]
  const head = matches[0] ? raw.slice(0, matches[0].index) : raw
  const title = '代码生成'
  const status = head.replace(/^#+\s*.+$/m, '').trim()
  const steps = matches.map((m, i) => {
    const start = (m.index ?? 0) + m[0].length
    const end = matches[i + 1]?.index ?? raw.length
    return { no: m[1], title: m[2].trim(), body: raw.slice(start, end).trim() }
  })
  let footer = ''
  if (steps.length) {
    const last = steps[steps.length - 1]
    const cut = last.body.search(/\n#{1,3}\s|\n\*\*工作流执行完成/)
    if (cut >= 0) {
      footer = last.body.slice(cut).trim()
      last.body = last.body.slice(0, cut).trim()
    }
  }
  // head：首个步骤之前的全部原始 Markdown（含各节点实时推送的工具徽标/进度）
  return { title, status, steps, footer, head: head.trim() }
}

const cleanContent = computed(() => stripHeartbeat(props.content))
const displayLen = ref(0)
const everStreamed = ref(false)
let rafId = 0
let lastTs = 0

const live = computed(() => !!props.streaming)
/** 假步骤进度仅 Agent 工作流在尚无 Markdown 输出时使用 */
const waiting = computed(() => !!props.agent && live.value && cleanContent.value.trim().length === 0)
/** 传统模式首包未到：展示计时卡片，不显示工作流假步骤 */
const waitingClassic = computed(
  () => !props.agent && live.value && cleanContent.value.trim().length === 0,
)
const visibleRaw = computed(() => cleanContent.value.slice(0, displayLen.value))
const workflow = computed(() => parseWorkflow(cleanContent.value))
const wfSteps = computed(() => workflow.value?.steps ?? [])
const isAgent = computed(() => !!props.agent)
/** 首个步骤之前的过程记录（工具徽标、节点实时输出），渲染在步骤列表上方 */
const wfIntroHtml = computed(() => {
  const head = workflow.value?.head
  if (!head) return ''
  // 去掉与 wf 头部重复的标题行
  return toHtml(head.replace(/^\s*#+\s*代码生成工作流\s*\n?/, ''))
})
const html = computed(() => renderMarkdown(aiContentToMarkdown(visibleRaw.value)))
const typing = computed(
  () => everStreamed.value && displayLen.value < cleanContent.value.length,
)

const hintIndex = ref(0)
const elapsed = ref(0)
let hintTimer = 0
let elapsedTimer = 0
const opened = ref<Record<string, boolean>>({})

const visualStep = computed(() => Math.min(WAITING_STEPS.length - 1, Math.floor(elapsed.value / 4)))
const latestToolHint = computed(() => parseLatestToolHint(cleanContent.value))
const toolActionCount = computed(() => {
  const m = cleanContent.value.match(/<(?:fileWrite|fileModify|fileRead|fileDelete|dirRead)>/g)
  return m?.length ?? 0
})
/** Agent 卡片头部右侧计数：生成中显示已调用工具数，结束显示步骤数 */
const liveCountText = computed(() =>
  toolActionCount.value > 0 ? `已调用 ${toolActionCount.value} 次工具` : '执行中',
)
const currentHint = computed(() => {
  const last = workflow.value?.steps.at(-1)?.title
  if (last) return `正在${last}`
  if (latestToolHint.value) return latestToolHint.value
  if (cleanContent.value.includes('代码生成中')) return '正在生成代码'
  if (!props.agent) return '模型正在生成项目代码…'
  return WAITING_HINTS[hintIndex.value] ?? WAITING_HINTS[0]
})
const workflowSub = computed(() => {
  if (live.value) return `已运行 ${formatElapsed(elapsed.value)}`
  return '已完成'
})

const isStepOpen = (no: string) => {
  if (opened.value[no] !== undefined) return opened.value[no]
  return false
}

const toggleStep = (no: string) => {
  opened.value = { ...opened.value, [no]: !isStepOpen(no) }
}

const toHtml = (md: string) => renderMarkdown(aiContentToMarkdown(md))

watch(
  live,
  (isLive) => {
    if (hintTimer) {
      clearInterval(hintTimer)
      hintTimer = 0
    }
    if (elapsedTimer) {
      clearInterval(elapsedTimer)
      elapsedTimer = 0
    }
    if (!isLive) return
    hintIndex.value = 0
    elapsed.value = 0
    opened.value = {}
    if (props.agent) {
      hintTimer = window.setInterval(() => {
        hintIndex.value = (hintIndex.value + 1) % WAITING_HINTS.length
      }, 1800)
    }
    elapsedTimer = window.setInterval(() => {
      elapsed.value += 1
    }, 1000)
  },
  { immediate: true },
)

const stopAnim = () => {
  if (rafId) cancelAnimationFrame(rafId)
  rafId = 0
  lastTs = 0
}

const tick = (ts: number) => {
  const target = cleanContent.value.length
  const lag = target - displayLen.value
  if (lag <= 0) {
    rafId = 0
    lastTs = 0
    return
  }
  if (!lastTs) lastTs = ts
  const dt = Math.min(48, ts - lastTs)
  lastTs = ts
  const cps = props.streaming ? STREAM_CPS : CATCHUP_CPS
  const step = Math.max(1, Math.round((cps * dt) / 1000))
  displayLen.value = Math.min(target, displayLen.value + step)
  rafId = requestAnimationFrame(tick)
}

const ensureAnim = () => {
  if (!everStreamed.value) return
  if (displayLen.value < cleanContent.value.length && !rafId) {
    rafId = requestAnimationFrame(tick)
  }
}

const showInstant = () => {
  displayLen.value = cleanContent.value.length
  stopAnim()
}

watch(
  () => cleanContent.value.length,
  () => {
    if (!everStreamed.value || !props.streaming) {
      showInstant()
      return
    }
    ensureAnim()
  },
)

watch(
  () => props.streaming,
  (streaming) => {
    if (streaming) {
      everStreamed.value = true
      ensureAnim()
      return
    }
    showInstant()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopAnim()
  if (hintTimer) clearInterval(hintTimer)
  if (elapsedTimer) clearInterval(elapsedTimer)
})
</script>

<style scoped>
.ai-md {
  position: relative;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
  min-width: 56px;
  min-height: 22px;
}

.ai-md__think {
  min-width: 220px;
  padding: 2px 0 4px;
}

.ai-md__think--classic {
  min-width: 200px;
}

.ai-md__status {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(22, 119, 255, 0.05);
  border: 1px solid rgba(22, 119, 255, 0.16);
}

.ai-md__status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-md__status-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: #1677ff;
  animation: ai-hint-fade 0.35s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-md__status-time {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 600;
  color: #595959;
  font-variant-numeric: tabular-nums;
}

.ai-md__status-meta {
  margin-top: 4px;
  font-size: 11px;
  color: #8c8c8c;
}

.ai-md__orb--sm {
  width: 14px;
  height: 14px;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.12);
}

.ai-md__think-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.ai-md__orb {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: conic-gradient(from 0deg, #69b1ff, #1677ff, #95de64, #69b1ff);
  animation: ai-orb-spin 1.2s linear infinite;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.ai-md__shimmer {
  font-size: 14px;
  font-weight: 650;
  background: linear-gradient(90deg, #1677ff 0%, #69b1ff 40%, #1677ff 80%);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: ai-shimmer 1.4s linear infinite, ai-hint-fade 0.35s ease;
}

.ai-md__wait-steps {
  margin: 0 0 10px;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 6px;
}

.ai-md__wait-steps li {
  position: relative;
  padding-left: 18px;
  font-size: 12px;
  color: #bfbfbf;
}

.ai-md__wait-steps li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 5px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid #d9d9d9;
  background: #fff;
}

.ai-md__wait-steps li.is-done {
  color: #389e0d;
}

.ai-md__wait-steps li.is-done::before {
  border-color: #52c41a;
  background: #52c41a;
}

.ai-md__wait-steps li.is-now {
  color: #1677ff;
  font-weight: 600;
}

.ai-md__wait-steps li.is-now::before {
  border-color: #1677ff;
  background: #1677ff;
  animation: ai-step-pulse 1.2s ease-out infinite;
}

.ai-md__live {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-md__live-text {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: #1677ff;
  animation: ai-hint-fade 0.35s ease;
}

.ai-md__elapsed {
  font-size: 12px;
  color: #8c8c8c;
  font-variant-numeric: tabular-nums;
}

.ai-md__spinner {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  border: 2px solid rgba(22, 119, 255, 0.22);
  border-top-color: #1677ff;
  border-radius: 50%;
  animation: ai-spin 0.7s linear infinite;
}

.ai-md__bar {
  height: 3px;
  border-radius: 99px;
  overflow: hidden;
  background: rgba(22, 119, 255, 0.12);
  margin-bottom: 8px;
}

.ai-md__bar::after {
  content: '';
  display: block;
  width: 38%;
  height: 100%;
  border-radius: 99px;
  background: linear-gradient(90deg, #69b1ff, #1677ff);
  animation: ai-bar-slide 1.15s ease-in-out infinite;
}

.wf {
  min-width: 240px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.wf__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
}

.wf__mark {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 5px;
  background: #111;
  position: relative;
}

.wf__mark::after {
  content: '';
  position: absolute;
  inset: 5px;
  border-radius: 1px;
  background: #fff;
  clip-path: polygon(15% 0, 100% 50%, 15% 100%);
}

.wf__mark.is-run {
  background: #111;
}

.wf__head-main {
  min-width: 0;
  flex: 1;
}

.wf__title {
  font-size: 14px;
  font-weight: 650;
  color: #1f1f1f;
  line-height: 1.3;
}

.wf__sub {
  margin-top: 2px;
  font-size: 12px;
  color: #8c8c8c;
}

.wf__count {
  flex: 0 0 auto;
  font-size: 12px;
  color: #bfbfbf;
}

.wf__list {
  padding: 0;
}

.wf-step {
  border-bottom: 1px solid #f5f5f5;
}

.wf-step:last-child {
  border-bottom: 0;
}

.wf-step__row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 36px;
  padding: 0 14px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.wf-step__row:hover {
  background: #fafafa;
}

.wf-step__dot {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  border-radius: 50%;
  border: 1.5px solid #d9d9d9;
  background: #fff;
  position: relative;
}

.wf-step.is-done .wf-step__dot {
  border-color: #52c41a;
  background: #52c41a;
}

.wf-step.is-done .wf-step__dot::after {
  content: '';
  position: absolute;
  left: 3px;
  top: 1px;
  width: 5px;
  height: 8px;
  border: solid #fff;
  border-width: 0 1.5px 1.5px 0;
  transform: rotate(45deg);
}

.wf-step.is-now .wf-step__dot {
  border-color: #1677ff;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.wf-step.is-now .wf-step__dot::after {
  content: '';
  position: absolute;
  inset: 3px;
  border-radius: 50%;
  background: #1677ff;
}

.wf-step.is-now .wf-step__name {
  color: #1677ff;
}

.wf-step__name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-step__chev {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  border-right: 1.5px solid #bfbfbf;
  border-bottom: 1.5px solid #bfbfbf;
  transform: rotate(-45deg);
  transition: transform 0.18s ease;
  opacity: 0.7;
}

.wf-step.is-open .wf-step__chev {
  transform: rotate(45deg);
  margin-top: -2px;
}

.wf-step__detail {
  padding: 0 14px 10px 40px;
}

.wf-step__md,
.wf-step__pending {
  padding: 8px 10px;
  border-radius: 8px;
  background: #fafafa;
}

.wf-step__pending {
  font-size: 12px;
  color: #8c8c8c;
}

.wf-step__md :deep(p),
.wf-step__md :deep(ul) {
  margin: 0;
  padding: 0;
}

.wf-step__md :deep(ul) {
  list-style: none;
  display: grid;
  gap: 3px;
}

.wf-step__md :deep(li) {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.5;
}

.wf-step__md :deep(ul ul) {
  margin: 4px 0 0 8px;
  gap: 2px;
}

.wf-step__md :deep(strong) {
  color: #434343;
  font-weight: 600;
}

.wf-step__md :deep(img) {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 6px;
  margin: 6px 6px 0 0;
  display: inline-block;
  vertical-align: top;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.wf-step__md :deep(code) {
  font-size: 11px;
  padding: 1px 4px;
  border-radius: 4px;
  background: #f5f5f5;
  color: #595959;
}

/* 完成视图顶部的过程记录区：滚动展示各节点实时推送内容（含工具徽标） */
.wf__intro {
  margin: 0 12px 4px;
  padding: 10px 12px;
  max-height: 240px;
  overflow: auto;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.025);
  border: 1px solid rgba(0, 0, 0, 0.06);
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary, #595959);
}

.wf__intro :deep(.ai-tool-call) {
  margin: 3px 0;
}

/* 生成中实时过程区：不限制高度、无底色块，跟卡片同底色随内容自然生长 */
.wf__intro--live {
  max-height: none;
  margin-bottom: 2px;
  border: 0;
  background: transparent;
  padding: 4px 12px;
}

.wf__running-tip {
  color: #8c8c8c;
  font-size: 13px;
}

/* 无步骤标记时的兜底正文区（仍在同一张工作流卡片内） */
.wf__plain {
  padding: 6px 14px 12px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-main);
}

.wf__result {
  margin: 0 12px 12px;
  padding: 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.wf__result :deep(h3) {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 650;
  color: #8c8c8c;
}

.wf__result :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  display: block;
}

.wf__result :deep(p) {
  margin: 0;
  font-size: 12px;
  color: #8c8c8c;
}

.wf__result :deep(strong) {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #389e0d;
}

.ai-md__body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 8px 0;
  display: block;
}

.ai-md__body :deep(p) {
  margin: 0 0 8px;
}

.ai-md__body :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-md__body :deep(h1),
.ai-md__body :deep(h2),
.ai-md__body :deep(h3) {
  margin: 12px 0 8px;
  font-weight: 600;
}

.ai-md__body :deep(strong) {
  display: block;
  margin: 10px 0 6px;
  font-size: 13px;
  color: #1677ff;
}

.ai-md__body :deep(pre) {
  margin: 8px 0;
  padding: 12px;
  border-radius: 8px;
  overflow: auto;
  background: #f6f8fa;
  border: 1px solid var(--border-color, #e8e8e8);
  font-size: 12px;
  line-height: 1.5;
}

.ai-md__body :deep(pre code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  background: transparent;
  padding: 0;
}

.ai-md__body :deep(code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
}

.ai-md__body :deep(:not(pre) > code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(22, 119, 255, 0.08);
}

.ai-md__body :deep(.ai-tool-call) {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 100%;
  margin: 2px 0;
  padding: 6px 9px;
  border: 1px solid rgba(22, 119, 255, 0.22);
  border-radius: 8px;
  background: rgba(22, 119, 255, 0.07);
  color: var(--text-main);
  vertical-align: middle;
}

.ai-md__body :deep(.ai-tool-call__icon) {
  width: 10px;
  height: 10px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #1677ff;
  animation: ai-tool-pulse 1.15s ease-out infinite;
}

.ai-md__body :deep(.ai-tool-call__label) {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 700;
  color: #1677ff;
}

.ai-md__body :deep(.ai-tool-call__content) {
  min-width: 0;
  overflow-wrap: anywhere;
}

.ai-md__body :deep(.ai-tool-call--file-modify) {
  border-color: rgba(250, 173, 20, 0.28);
  background: rgba(250, 173, 20, 0.08);
}

.ai-md__body :deep(.ai-tool-call--file-modify .ai-tool-call__icon) {
  background: #faad14;
}

.ai-md__body :deep(.ai-tool-call--file-modify .ai-tool-call__label) {
  color: #ad6800;
}

.ai-md__body :deep(.ai-tool-call--file-delete) {
  border-color: rgba(255, 77, 79, 0.26);
  background: rgba(255, 77, 79, 0.07);
}

.ai-md__body :deep(.ai-tool-call--file-delete .ai-tool-call__icon) {
  background: #ff4d4f;
}

.ai-md__body :deep(.ai-tool-call--file-delete .ai-tool-call__label) {
  color: #cf1322;
}

.ai-md__body :deep(.ai-tool-call--file-read),
.ai-md__body :deep(.ai-tool-call--dir-read) {
  border-color: rgba(82, 196, 26, 0.28);
  background: rgba(82, 196, 26, 0.07);
}

.ai-md__body :deep(.ai-tool-call--file-read .ai-tool-call__icon),
.ai-md__body :deep(.ai-tool-call--dir-read .ai-tool-call__icon) {
  background: #52c41a;
}

.ai-md__body :deep(.ai-tool-call--file-read .ai-tool-call__label),
.ai-md__body :deep(.ai-tool-call--dir-read .ai-tool-call__label) {
  color: #389e0d;
}

.ai-md__cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: #1677ff;
  animation: ai-cursor-blink 0.9s step-end infinite;
}

@keyframes ai-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes ai-orb-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes ai-shimmer {
  to {
    background-position: -200% 0;
  }
}

@keyframes ai-hint-fade {
  from {
    opacity: 0.45;
    transform: translateY(3px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes ai-step-pulse {
  70% {
    box-shadow: 0 0 0 6px transparent;
  }
  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}

@keyframes ai-bar-slide {
  0% {
    transform: translateX(-20%);
  }
  100% {
    transform: translateX(220%);
  }
}

@keyframes ai-cursor-blink {
  50% {
    opacity: 0;
  }
}

@keyframes ai-tool-pulse {
  70% {
    box-shadow: 0 0 0 7px transparent;
  }
  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}
</style>
