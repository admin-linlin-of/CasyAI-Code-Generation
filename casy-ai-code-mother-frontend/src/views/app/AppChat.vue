<template>
  <div class="app-chat-page">
    <header class="top-bar">
      <div class="top-bar__name">{{ appInfo?.appName || `应用 #${appId}` }}</div>
      <a-space>
        <a-select v-model:value="modelType" :options="modelTypeOptions" style="width: 180px" />
        <a-button :loading="deploying" type="primary" @click="doDeploy">部署</a-button>
      </a-space>
    </header>

    <div class="core-layout">
      <section class="chat-panel">
        <div ref="messageRef" class="message-list">
          <div
            v-for="(msg, index) in messages"
            :key="`${msg.role}-${index}`"
            :class="[
              'message-item',
              msg.role === 'user' ? 'message-item--user' : 'message-item--ai',
            ]"
          >
            <div class="message-item__content">{{ msg.content }}</div>
          </div>
          <a-empty v-if="messages.length === 0" description="发送消息开始生成" />
        </div>
        <div class="input-area">
          <a-textarea
            v-model:value="inputMessage"
            :maxlength="1200"
            :rows="3"
            placeholder="继续描述你的页面需求..."
            show-count
            @pressEnter="onPressEnter"
          />
          <div class="input-area__ops">
            <a-button :loading="generating" type="primary" @click="sendMessage">发送</a-button>
          </div>
        </div>
      </section>

      <section class="preview-panel">
        <div class="preview-panel__head">
          <span>网页展示</span>
          <a v-if="previewUrl" :href="previewUrl" rel="noreferrer" target="_blank">新窗口打开</a>
        </div>
        <div class="preview-panel__body">
          <iframe v-if="showPreview" :src="previewUrl" title="app-preview" />
          <a-empty
            v-else
            :description="generating ? '代码生成中，完成后自动展示' : '代码生成完成后展示预览'"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { deployApp, getAppVoById } from '@/api/appController'
import request from '@/axios/request'

type ChatMessage = {
  role: 'user' | 'ai'
  content: string
}

const route = useRoute()
const appId = computed(() => {
  console.log('route.params.id: ', route.params.id)
  const id = route.params.id
  return (Array.isArray(id) ? String(id[0]) : String(id)) ?? ''
})
const inputMessage = ref('')
const generating = ref(false)
const deploying = ref(false)
const modelType = ref(
  typeof route.query.modelType === 'string' ? route.query.modelType : 'deepseek-v4-flash',
)
const modelTypeOptions = [
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { value: 'gpt-5.5', label: 'GPT 5.5' },
]

const appInfo = ref<API.AppVO>()
const messages = ref<ChatMessage[]>([])
const previewUrl = ref('')
const showPreview = ref(false)
const messageRef = ref<HTMLElement>()
let eventSource: EventSource | null = null

const buildPreviewUrl = () => {
  const codeGenType = appInfo.value?.codeGenType || 'multi_file'
  previewUrl.value = `http://localhost:8124/api/static/${codeGenType}_${appId.value}/`
}

const scrollToBottom = async () => {
  await nextTick()
  if (!messageRef.value) return
  messageRef.value.scrollTop = messageRef.value.scrollHeight
}

const closeEventSource = () => {
  if (!eventSource) return
  eventSource.close()
  eventSource = null
}

const fetchAppInfo = async () => {
  const res = await getAppVoById({ id: appId.value })
  if (res.data.code === 0 && res.data.data) {
    appInfo.value = res.data.data
    buildPreviewUrl()
    return
  }
  message.error(res.data.message || '获取应用信息失败')
}

const startStream = (messageText: string) => {
  generating.value = true
  showPreview.value = false
  const aiMsg: ChatMessage = { role: 'ai', content: '' }
  messages.value.push(aiMsg)
  const baseURL = request.defaults.baseURL ?? ''
  const url = new URL('app/chat/gen/code', baseURL.endsWith('/') ? baseURL : `${baseURL}/`)
  url.searchParams.set('appId', String(appId.value))
  url.searchParams.set('message', messageText)
  url.searchParams.set('modelType', modelType.value)
  eventSource = new EventSource(url.toString(), { withCredentials: true })
  let finished = false

  eventSource.onmessage = (event) => {
    if (finished) return
    try {
      const data = JSON.parse(event.data) as { c?: string }
      aiMsg.content += data.c ?? ''
    } catch {
      aiMsg.content += event.data ?? ''
    }
    scrollToBottom()
  }

  eventSource.addEventListener('done', () => {
    if (finished) return
    finished = true
    generating.value = false
    showPreview.value = true
    closeEventSource()
    buildPreviewUrl()
  })

  eventSource.onerror = () => {
    closeEventSource()
    if (!finished) {
      // 检查是否是正常的连接关闭
      generating.value = false
      message.error('生成中断，请重试')
    }
  }
}

const sendMessage = () => {
  const messageText = inputMessage.value.trim()
  if (!messageText || generating.value) return
  messages.value.push({ role: 'user', content: messageText })
  inputMessage.value = ''
  scrollToBottom()
  startStream(messageText)
}

const onPressEnter = (event: KeyboardEvent) => {
  if (event.shiftKey) return
  event.preventDefault()
  sendMessage()
}

const doDeploy = async () => {
  if (generating.value) {
    message.warning('请等待当前生成完成')
    return
  }
  deploying.value = true
  try {
    const res = await deployApp({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      Modal.success({
        title: '部署成功',
        content: res.data.data,
      })
      return
    }
    message.error(res.data.message || '部署失败')
  } finally {
    deploying.value = false
  }
}

onMounted(async () => {
  await fetchAppInfo()
  if (route.query.autoStart === '1') {
    const initPrompt =
      typeof route.query.initPrompt === 'string' ? route.query.initPrompt.trim() : ''
    if (initPrompt) {
      inputMessage.value = initPrompt
      sendMessage()
    }
  }
})

onBeforeUnmount(() => {
  closeEventSource()
})
</script>

<style scoped>
.app-chat-page {
  height: calc(100vh - 64px);
  background: var(--bg-page);
  color: var(--text-main);
}

.top-bar {
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--bg-card);
}

.top-bar__name {
  font-size: 18px;
  font-weight: 600;
}

.core-layout {
  height: calc(100vh - 120px);
  display: grid;
  grid-template-columns: 40% 60%;
  gap: 12px;
  padding: 12px;
}

.chat-panel,
.preview-panel {
  border-radius: 14px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  min-height: 0;
}

.chat-panel {
  display: flex;
  flex-direction: column;
}

.message-list {
  flex: 1;
  overflow: auto;
  padding: 14px;
}

.message-item {
  display: flex;
  margin-bottom: 12px;
}

.message-item--ai {
  justify-content: flex-start;
}

.message-item--user {
  justify-content: flex-end;
}

.message-item__content {
  max-width: 88%;
  padding: 10px 12px;
  border-radius: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.message-item--ai .message-item__content {
  background: rgba(22, 119, 255, 0.08);
  border: 1px solid var(--border-color);
}

.message-item--user .message-item__content {
  background: #1b4ee0;
  color: #fff;
}

.input-area {
  padding: 12px;
  border-top: 1px solid var(--border-color);
}

.input-area__ops {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.preview-panel {
  display: flex;
  flex-direction: column;
}

.preview-panel__head {
  height: 46px;
  padding: 0 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
}

.preview-panel__body {
  flex: 1;
  min-height: 0;
}

.preview-panel__body iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

@media (max-width: 1200px) {
  .core-layout {
    grid-template-columns: 1fr;
    height: auto;
  }

  .chat-panel,
  .preview-panel {
    min-height: 420px;
  }
}
</style>
