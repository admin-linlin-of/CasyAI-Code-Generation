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

    <div ref="layoutRef" class="core-layout">
      <section class="chat-panel" :style="{ width: `${chatWidth}px` }">
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

      <div
        class="resize-handle"
        title="拖拽调整宽度"
        @mousedown.prevent="startResize('chat', $event)"
      />

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

      <div
        v-if="!versionCollapsed"
        class="resize-handle"
        title="拖拽调整宽度"
        @mousedown.prevent="startResize('version', $event)"
      />

      <aside
        v-show="!versionCollapsed"
        class="version-panel"
        :style="{ width: `${versionWidth}px` }"
      >
        <div class="version-panel__head">
          <span>版本列表</span>
          <a-button
            class="version-panel__collapse"
            size="small"
            type="text"
            title="折叠版本列表"
            @click="toggleVersionPanel"
          >
            <RightOutlined />
          </a-button>
        </div>
        <div class="version-panel__list">
          <div
            v-for="version in versionList"
            :key="version.id"
            :class="[
              'version-item',
              { 'version-item--active': selectedVersionCodeDir === version.codeDir },
            ]"
            @click="selectVersion(version)"
          >
            <div class="version-item__thumb">
              <iframe :src="getVersionPreviewUrl(version)" tabindex="-1" title="version-preview" />
            </div>
            <div class="version-item__meta">
              <span class="version-item__label">{{ formatVersionLabel(version) }}</span>
              <span v-if="version.modelType" class="version-item__model">{{ version.modelType }}</span>
            </div>
          </div>
          <a-empty v-if="versionList.length === 0" description="暂无版本" />
        </div>
      </aside>

      <button
        v-if="versionCollapsed"
        class="version-panel-expand"
        title="展开版本列表"
        type="button"
        @click="toggleVersionPanel"
      >
        <LeftOutlined />
        <span>版本</span>
      </button>
    </div>
    <div v-if="resizingActive" class="resize-overlay" />
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
import { getAppVersionsByAppId } from '@/api/appVersionController'
import { listAppChatHistoryByPage } from '@/api/chatHistoryController'
import { useLoginUserStore } from '@/stores/loginUser'
import { CheckCircleOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import request from '@/axios/request'
import { API_BASE_URL } from '@/config'
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

const versionList = ref<API.AppVersion[]>([])
const selectedVersionCodeDir = ref('')

const layoutRef = ref<HTMLElement>()
const chatWidth = ref(420)
const versionWidth = ref(152)
const versionCollapsed = ref(false)
const versionWidthBeforeCollapse = ref(152)
const DEFAULT_VERSION_WIDTH = 152
const MAX_VERSION_WIDTH = 200
const MIN_CHAT_WIDTH = 280
const MIN_PREVIEW_WIDTH = 320
const MIN_VERSION_WIDTH = 120
const RESIZE_HANDLE_WIDTH = 6
const RESIZE_HANDLE_GAP = 4
const LAYOUT_PADDING = 12

let resizingTarget: 'chat' | 'version' | null = null
let layoutLeft = 0
let layoutRight = 0
const resizingActive = ref(false)

const getHandleTotalWidth = () => RESIZE_HANDLE_WIDTH + RESIZE_HANDLE_GAP

const getHandlesTotalWidth = () => getHandleTotalWidth() * getResizeHandleCount()

const getEffectiveVersionWidth = () => (versionCollapsed.value ? 0 : versionWidth.value)

const getResizeHandleCount = () => (versionCollapsed.value ? 1 : 2)

const getLayoutInnerWidth = () => {
  const layoutWidth = layoutRef.value?.clientWidth ?? 0
  return Math.max(0, layoutWidth - LAYOUT_PADDING * 2)
}

const updateLayoutBounds = () => {
  const rect = layoutRef.value?.getBoundingClientRect()
  if (!rect) return
  layoutLeft = rect.left + LAYOUT_PADDING
  layoutRight = rect.right - LAYOUT_PADDING
}

const getMaxChatWidth = () =>
  getLayoutInnerWidth() - getHandlesTotalWidth() - MIN_PREVIEW_WIDTH - getEffectiveVersionWidth()

const getMaxVersionWidth = () =>
  Math.min(
    MAX_VERSION_WIDTH,
    getLayoutInnerWidth() - getHandlesTotalWidth() - MIN_PREVIEW_WIDTH - chatWidth.value,
  )

const startResize = (target: 'chat' | 'version', event: MouseEvent) => {
  resizingTarget = target
  updateLayoutBounds()
  resizingActive.value = true
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const onResizeMove = (event: MouseEvent) => {
  if (!resizingTarget) return
  updateLayoutBounds()
  if (resizingTarget === 'chat') {
    const maxChat = getMaxChatWidth()
    const nextChatWidth = event.clientX - layoutLeft - getHandleTotalWidth() / 2
    chatWidth.value = Math.max(MIN_CHAT_WIDTH, Math.min(maxChat, nextChatWidth))
    return
  }
  const rawVersionWidth = layoutRight - event.clientX - getHandleTotalWidth() / 2
  if (rawVersionWidth < 48) {
    versionWidthBeforeCollapse.value = Math.max(versionWidth.value, DEFAULT_VERSION_WIDTH)
    versionCollapsed.value = true
    stopResize()
    return
  }
  versionWidth.value = Math.max(
    MIN_VERSION_WIDTH,
    Math.min(getMaxVersionWidth(), rawVersionWidth),
  )
}

const stopResize = () => {
  resizingTarget = null
  resizingActive.value = false
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

const toggleVersionPanel = () => {
  if (versionCollapsed.value) {
    versionCollapsed.value = false
    versionWidth.value = versionWidthBeforeCollapse.value || DEFAULT_VERSION_WIDTH
    return
  }
  versionWidthBeforeCollapse.value = versionWidth.value
  versionCollapsed.value = true
}

const initLayoutWidth = () => {
  const layoutWidth = getLayoutInnerWidth()
  if (!layoutWidth) return
  chatWidth.value = Math.max(MIN_CHAT_WIDTH, Math.round(layoutWidth * 0.32))
  const nextVersionWidth = Math.min(
    MAX_VERSION_WIDTH,
    Math.max(DEFAULT_VERSION_WIDTH, Math.round(layoutWidth * 0.14)),
  )
  versionWidth.value = nextVersionWidth
  versionWidthBeforeCollapse.value = nextVersionWidth
}

const formatVersionLabel = (version: API.AppVersion) => {
  if (version.versionNum != null) return `v${version.versionNum}`
  return version.codeDir || '未知版本'
}

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

const buildPreviewUrl = (codeDir?: string) => {
  const codeGenType = appInfo.value?.codeGenType || 'multi_file'
  const dir = codeDir || selectedVersionCodeDir.value
  const deployKey = dir ? `${codeGenType}_${appId.value}_${dir}` : `${codeGenType}_${appId.value}`
  previewUrl.value = `${API_BASE_URL}/static/${deployKey}/`
}

const getVersionPreviewUrl = (version: API.AppVersion) => {
  const codeGenType = appInfo.value?.codeGenType || 'multi_file'
  return `${API_BASE_URL}/static/${codeGenType}_${appId.value}_${version.codeDir}/`
}

const loadVersions = async (selectLatest = false) => {
  try {
    const res = await getAppVersionsByAppId({ appid: appId.value })
    const list = Array.isArray(res.data) ? res.data : []
    versionList.value = list
    if (list.length) {
      const latest = list[0]
      if (
        selectLatest ||
        !selectedVersionCodeDir.value ||
        !list.some((v) => v.codeDir === selectedVersionCodeDir.value)
      ) {
        selectedVersionCodeDir.value = latest.codeDir || ''
        savedVirtualFiles.value = []
      }
      buildPreviewUrl(selectedVersionCodeDir.value)
    }
  } catch {
    versionList.value = []
  }
}

const selectVersion = async (version: API.AppVersion) => {
  if (!version.codeDir || selectedVersionCodeDir.value === version.codeDir) return
  selectedVersionCodeDir.value = version.codeDir
  buildPreviewUrl(version.codeDir)
  savedVirtualFiles.value = []
  if (showPreview.value) {
    await loadSavedCodeFiles()
  }
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
    aiMsg.streaming = false
    generating.value = false
    showPreview.value = true
    rightViewMode.value = 'preview'
    closeEventSource()
    await loadVersions(true)
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
  await nextTick()
  initLayoutWidth()
  await fetchAppInfo()
  await loadVersions()
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
  stopResize()
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
  display: flex;
  align-items: stretch;
  padding: 12px;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.chat-panel,
.preview-panel,
.version-panel {
  border-radius: 14px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  min-height: 0;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.preview-panel {
  flex: 1;
  min-width: 320px;
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

.resize-handle {
  width: 6px;
  margin: 0 2px;
  flex-shrink: 0;
  cursor: col-resize;
  border-radius: 999px;
  position: relative;
  touch-action: none;
}

.resize-handle::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: -4px;
  right: -4px;
}

.resize-handle:hover,
.resize-handle:active {
  background: rgba(22, 119, 255, 0.08);
}

.resize-handle::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 2px;
  height: 36px;
  transform: translate(-50%, -50%);
  border-radius: 999px;
  background: var(--border-color);
}

.resize-handle:hover::after,
.resize-handle:active::after {
  background: #1677ff;
}

.resize-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  cursor: col-resize;
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

.version-panel {
  flex-shrink: 0;
  align-self: stretch;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  max-height: 100%;
}

.version-panel__head {
  height: 46px;
  padding: 0 8px 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.version-panel__collapse {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.version-panel__list {
  flex: 1 1 0;
  height: 0;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
}

.version-panel__list::-webkit-scrollbar {
  width: 8px;
}

.version-panel__list::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.04);
  border-radius: 999px;
}

.version-panel__list::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.22);
  border-radius: 999px;
}

.version-panel__list::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.32);
}

.version-item {
  cursor: pointer;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: var(--bg-card);
  flex-shrink: 0;
}

.version-item--active {
  border-color: #1677ff;
  box-shadow: 0 0 0 1px rgba(22, 119, 255, 0.35);
}

.version-item__thumb {
  width: 100%;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: #fff;
  pointer-events: none;
}

.version-item__thumb iframe {
  width: 400%;
  height: 400%;
  border: none;
  transform: scale(0.25);
  transform-origin: top left;
}

.version-item__meta {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.version-item__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.version-item__model {
  font-size: 11px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-panel-expand {
  width: 28px;
  flex-shrink: 0;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  font-size: 12px;
  transition: border-color 0.2s, color 0.2s;
}

.version-panel-expand span {
  writing-mode: vertical-rl;
  letter-spacing: 2px;
}

.version-panel-expand:hover {
  color: #1677ff;
  border-color: rgba(22, 119, 255, 0.45);
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
    flex-direction: column;
    height: auto;
  }

  .chat-panel,
  .preview-panel,
  .version-panel {
    width: 100% !important;
    min-height: 420px;
  }

  .preview-panel {
    min-width: 0;
  }

  .resize-handle {
    display: none;
  }

  .version-panel__list {
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .version-item {
    min-width: 160px;
    flex-shrink: 0;
  }
}
</style>
