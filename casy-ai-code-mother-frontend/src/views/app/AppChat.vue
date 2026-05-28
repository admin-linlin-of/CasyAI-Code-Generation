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
          <div v-if="historyHasMore" class="load-more">
            <a-button :loading="loadingMoreHistory" type="link" @click="loadMoreHistory">
              加载更多
            </a-button>
          </div>
          <div
            v-for="(msg, index) in messages"
            :key="msg.id ? `h-${msg.id}` : `${msg.role}-${index}`"
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

      <!-- 右侧：代码 / 预览 共用同一区域，通过 Segmented 切换 -->
      <section class="preview-panel">
        <div class="preview-panel__head">
          <a-segmented v-model:value="rightViewMode" :options="rightViewOptions" size="small" />
          <a
            v-if="rightViewMode === 'preview' && previewUrl && showPreview"
            :href="previewUrl"
            rel="noreferrer"
            target="_blank"
          >
            新窗口打开
          </a>
        </div>
        <div class="preview-panel__body">
          <!-- 代码模式：Monaco + 文件树 -->
          <CodeWorkspace
            v-if="rightViewMode === 'code' && hasCodeContent"
            :files="displayVirtualFiles"
            :read-only="generating"
          />
          <!-- 预览模式：iframe 展示后端静态资源 -->
          <iframe
            v-else-if="rightViewMode === 'preview' && showPreview"
            :key="previewUrl"
            :src="previewUrl"
            title="app-preview"
          />
          <a-empty
            v-else
            :description="
              generating
                ? '代码生成中，可切换到代码查看实时输出'
                : '发送消息开始生成，或切换到预览查看效果'
            "
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
        <a-form layout="vertical" class="detail-form" @finish="doUpdateApp">
          <a-form-item label="应用名称" required>
            <a-input v-model:value="detailForm.appName" :maxlength="40" show-count />
          </a-form-item>
          <a-form-item label="应用类型">
            <a-select
              v-model:value="detailForm.appTypes"
              mode="multiple"
              :options="APP_TYPE_OPTIONS"
              placeholder="选择应用类型"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="是否公布">
            <a-switch
              v-model:checked="detailPublished"
              checked-children="公布"
              un-checked-children="不公布"
            />
          </a-form-item>
          <a-space>
            <a-button type="primary" :loading="updating" @click="doUpdateApp">修改</a-button>
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
import { listAppChatHistoryByPage } from '@/api/chatHistoryController'
import { useLoginUserStore } from '@/stores/loginUser'
import { CheckCircleOutlined } from '@ant-design/icons-vue'
import request from '@/axios/request'
import AiMarkdownMessage from '@/components/AiMarkdownMessage.vue'
import CodeWorkspace from '@/components/CodeWorkspace.vue'
import { APP_TYPE_OPTIONS } from '@/constant/appType'
import { APP_NOT_PUBLISH, APP_PUBLISHED } from '@/constant/constant'
import {
  fetchSavedVirtualFiles,
  hasVirtualFileContent,
  parseAiContentToVirtualFiles,
  type VirtualFile,
} from '@/utils/virtualFiles'

type ChatMessage = {
  id?: number
  role: 'user' | 'ai'
  content: string
  createTime?: string
  streaming?: boolean
}

const HISTORY_PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const appId = computed(() => {
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
  appTypes: [] as string[],
  isPublish: APP_NOT_PUBLISH,
})

const detailPublished = computed({
  get: () => detailForm.isPublish === APP_PUBLISHED,
  set: (checked: boolean) => {
    detailForm.isPublish = checked ? APP_PUBLISHED : APP_NOT_PUBLISH
  },
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
const loadingHistory = ref(false)
const loadingMoreHistory = ref(false)
const historyHasMore = ref(false)
const historyCursor = ref<string>()
let eventSource: EventSource | null = null

const isOwnApp = computed(() => {
  const loginUserId = loginUserStore.loginUser.id
  const ownerId = appInfo.value?.userId
  return loginUserId != null && ownerId != null && loginUserId === ownerId
})

/** 右侧面板视图：code = Monaco 代码，preview = iframe 预览 */
type RightViewMode = 'code' | 'preview'
const rightViewMode = ref<RightViewMode>('preview')
const rightViewOptions = [
  { value: 'code', label: '代码' },
  { value: 'preview', label: '预览' },
]

/** 从后端静态目录加载的代码（刷新页面或 SSE 解析失败时使用） */
const savedVirtualFiles = ref<VirtualFile[]>([])

/** 取最后一条 AI 消息的内容，用于解析虚拟文件 */
const latestAiContent = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const msg = messages.value[i]
    if (msg?.role === 'ai') return msg.content
  }
  return ''
})

/** 优先从 SSE 聊天内容解析，解析不到则用静态目录文件 */
const displayVirtualFiles = computed(() => {
  const fromChat = parseAiContentToVirtualFiles(latestAiContent.value)
  if (hasVirtualFileContent(fromChat)) return fromChat
  return savedVirtualFiles.value
})

/** 是否已有可展示的代码（控制 CodeWorkspace 显示） */
const hasCodeContent = computed(() => hasVirtualFileContent(displayVirtualFiles.value))

const loadSavedCodeFiles = async () => {
  if (!previewUrl.value) return
  const files = await fetchSavedVirtualFiles(previewUrl.value)
  if (files.length) savedVirtualFiles.value = files
}

const toChatMessage = (item: API.ChatHistoryVO): ChatMessage => ({
  id: item.id,
  role: item.messageType === 'user' ? 'user' : 'ai',
  content: item.message || '',
  createTime: item.createTime,
})

const applyHistoryRecords = (records: API.ChatHistoryVO[], prepend: boolean) => {
  // 关键：反转让老消息在前
  const sorted = [...records].reverse().map(toChatMessage)
  if (!sorted.length) return
  messages.value = prepend ? [...sorted, ...messages.value] : sorted
  historyCursor.value = messages.value[0]?.createTime
  historyHasMore.value = records.length >= HISTORY_PAGE_SIZE
}

const loadChatHistory = async (lastCreateTime?: string) => {
  const loadingMore = Boolean(lastCreateTime)
  if (loadingMore) loadingMoreHistory.value = true
  else loadingHistory.value = true
  try {
    const res = await listAppChatHistoryByPage({
      appId: appId.value,
      pageNum: 1,
      pageSize: HISTORY_PAGE_SIZE,
      sortField: 'create_time',
      sortOrder: 'descend',
      lastCreateTime,
    })
    if (res.data.code === 0 && res.data.data) {
      applyHistoryRecords(res.data.data.records ?? [], loadingMore)
      return
    }
    if (!loadingMore) message.error(res.data.message || '获取对话历史失败')
  } finally {
    if (loadingMore) loadingMoreHistory.value = false
    else loadingHistory.value = false
  }
}

const loadMoreHistory = async () => {
  if (!historyHasMore.value || loadingMoreHistory.value || !historyCursor.value) return
  const el = messageRef.value
  const prevHeight = el?.scrollHeight ?? 0
  await loadChatHistory(historyCursor.value)
  await nextTick()
  if (el) el.scrollTop = el.scrollHeight - prevHeight
}

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

const openDetailModal = () => {
  detailForm.appName = appInfo.value?.appName || ''
  detailForm.appTypes = appInfo.value?.appTypes ? [...appInfo.value.appTypes] : []
  detailForm.isPublish = appInfo.value?.isPublish ?? APP_NOT_PUBLISH
  detailVisible.value = true
}

const doUpdateApp = async () => {
  const appName = detailForm.appName.trim()
  if (!appName) {
    message.warning('请输入应用名称')
    return
  }
  updating.value = true
  try {
    const res = await updateApp({
      id: appId.value,
      appName,
      appTypes: detailForm.appTypes,
      isPublish: detailForm.isPublish,
    })
    if (res.data.code === 0) {
      message.success('修改成功')
      if (appInfo.value) {
        appInfo.value.appName = appName
        appInfo.value.appTypes = [...detailForm.appTypes]
        appInfo.value.isPublish = detailForm.isPublish
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
  // 开始生成时自动切到代码视图，实时看 Monaco 流式输出
  rightViewMode.value = 'code'
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

  eventSource.addEventListener('done', async () => {
    if (finished) return
    finished = true
    // 关闭打字机，刷新预览并自动切到预览视图
    aiMsg.streaming = false
    generating.value = false
    showPreview.value = true
    rightViewMode.value = 'preview'
    closeEventSource()
    buildPreviewUrl()
    await loadSavedCodeFiles()
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
  await loadChatHistory()
  if (messages.value.length > 0) {
    showPreview.value = true
    rightViewMode.value = 'preview'
    await loadSavedCodeFiles()
    await scrollToBottom()
  }
  if (
    route.query.autoStart === '1' &&
    isOwnApp.value &&
    messages.value.length === 0
  ) {
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

.load-more {
  text-align: center;
  margin-bottom: 8px;
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
  gap: 12px;
  border-bottom: 1px solid var(--border-color);
}

.preview-panel__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
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
