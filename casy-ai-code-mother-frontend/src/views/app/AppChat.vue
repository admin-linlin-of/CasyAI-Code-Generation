<template>
  <div class="app-chat-page">
    <header class="top-bar">
      <div class="top-bar__name">{{ appInfo?.appName || `应用 #${appId}` }}</div>
      <a-space>
        <a-select v-model:value="modelType" :options="modelTypeOptions" style="width: 180px" />
        <a-button @click="openDetailModal">详情</a-button>
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
            <div class="message-item__content">
              <!-- AI 消息：Markdown + 高亮 + 打字机；用户消息：纯文本 -->
              <AiMarkdownMessage
                v-if="msg.role === 'ai'"
                :content="msg.content"
                :streaming="msg.streaming"
              />
              <template v-else>{{ msg.content }}</template>
            </div>
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
    <a-modal v-model:open="detailVisible" title="应用详情" :footer="null" width="560px">
      <div class="detail-modal">
        <div class="detail-row">
          <span class="detail-label">创建者：</span>
          <span>{{ appInfo?.user?.userName || appInfo?.user?.userAccount || appInfo?.userId || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">创建时间：</span>
          <span>{{ appInfo?.createTime || '-' }}</span>
        </div>
        <a-form layout="vertical" class="detail-form" @finish="doUpdateAppName">
          <a-form-item label="应用名称" required>
            <a-input v-model:value="detailForm.appName" :maxlength="40" show-count />
          </a-form-item>
          <a-space>
            <a-button type="primary" :loading="updating" @click="doUpdateAppName">修改</a-button>
            <a-button danger :loading="deleting" @click="confirmDeleteApp">删除</a-button>
          </a-space>
        </a-form>
      </div>
    </a-modal>

    <a-modal v-model:open="deploySuccessVisible" title="部署成功" :footer="null" width="640px">
      <div class="deploy-success">
        <CheckCircleOutlined class="deploy-success__icon" />
        <h2>网站部署成功！</h2>
        <p>你的网站已经成功部署，可以通过以下链接访问：</p>
        <a-input-group compact class="deploy-success__link">
          <a-input :value="deployUrl" readonly style="width: calc(100% - 60px)" />
          <a-button @click="copyDeployUrl">复制</a-button>
        </a-input-group>
        <a-space class="deploy-success__ops">
          <a-button type="primary" @click="openDeployUrl">访问网站</a-button>
          <a-button @click="deploySuccessVisible = false">关闭</a-button>
        </a-space>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { deleteApp, deployApp, getAppVoById, updateApp } from '@/api/appController'
import { CheckCircleOutlined } from '@ant-design/icons-vue'
import request from '@/axios/request'
import AiMarkdownMessage from '@/components/AiMarkdownMessage.vue'

type ChatMessage = {
  role: 'user' | 'ai'
  content: string
  /** true 表示 SSE 进行中，AiMarkdownMessage 启用打字机与加载动画 */
  streaming?: boolean
}

const route = useRoute()
const router = useRouter()
const appId = computed(() => {
  console.log('route.params.id: ', route.params.id)
  const id = route.params.id
  return (Array.isArray(id) ? String(id[0]) : String(id)) ?? ''
})
const inputMessage = ref('')
const generating = ref(false)
const deploying = ref(false)
const updating = ref(false)
const deleting = ref(false)
const detailVisible = ref(false)
const deploySuccessVisible = ref(false)
const deployUrl = ref('')
const detailForm = reactive({
  appName: '',
})
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
  message.error(res.data.message || '鑾峰彇搴旂敤淇℃伅澶辫触')
}

const openDetailModal = () => {
  detailForm.appName = appInfo.value?.appName || ''
  detailVisible.value = true
}

const doUpdateAppName = async () => {
  const appName = detailForm.appName.trim()
  if (!appName) {
    message.warning('请输入应用名称')
    return
  }
  updating.value = true
  try {
    const res = await updateApp({ id: appId.value, appName })
    if (res.data.code === 0) {
      message.success('修改成功')
      if (appInfo.value) {
        appInfo.value.appName = appName
      }
      detailVisible.value = false
      return
    }
    message.error(res.data.message || '修改失败')
  } finally {
    updating.value = false
  }
}

const confirmDeleteApp = () => {
  Modal.confirm({
    title: '确认删除应用？',
    content: '删除后不可恢复，是否继续？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      deleting.value = true
      try {
        const res = await deleteApp({ id: appId.value })
        if (res.data.code === 0) {
          message.success('删除成功')
          detailVisible.value = false
          router.replace('/')
          return
        }
        message.error(res.data.message || '删除失败')
        return Promise.reject()
      } finally {
        deleting.value = false
      }
    },
  })
}

const copyDeployUrl = async () => {
  if (!deployUrl.value) return
  try {
    await navigator.clipboard.writeText(deployUrl.value)
    message.success('链接已复制')
  } catch {
    message.warning('复制失败，请手动复制')
  }
}

const openDeployUrl = () => {
  if (!deployUrl.value) return
  window.open(deployUrl.value, '_blank', 'noopener,noreferrer')
}

/** 通过 EventSource 接收生成流，data 为 {"c":"片段"}，done 事件表示结束 */
const startStream = (messageText: string) => {
  generating.value = true
  showPreview.value = false
  const aiMsg: ChatMessage = { role: 'ai', content: '', streaming: true }
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
      // 后端包装为 JSON，避免 EventSource 丢空格
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
    // 关闭打字机，展示完整内容并刷新预览
    aiMsg.streaming = false
    generating.value = false
    showPreview.value = true
    closeEventSource()
    buildPreviewUrl()
  })

  eventSource.onerror = () => {
    closeEventSource()
    if (!finished) {
      aiMsg.streaming = false
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
      deployUrl.value = res.data.data
      deploySuccessVisible.value = true
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
  word-break: break-word;
  line-height: 1.6;
}

.message-item--user .message-item__content {
  white-space: pre-wrap;
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

.detail-modal {
  padding-top: 4px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--text-main);
}

.detail-label {
  min-width: 72px;
  color: var(--text-secondary);
}

.detail-form {
  margin-top: 18px;
}

.deploy-success {
  text-align: center;
  padding: 18px 28px 8px;
}

.deploy-success__icon {
  color: #52c41a;
  font-size: 56px;
  margin-bottom: 12px;
}

.deploy-success h2 {
  margin: 0 0 14px;
}

.deploy-success p {
  margin-bottom: 20px;
  color: var(--text-secondary);
}

.deploy-success__link {
  margin-bottom: 24px;
  display:flex;
}

.deploy-success__ops {
  justify-content: center;
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
