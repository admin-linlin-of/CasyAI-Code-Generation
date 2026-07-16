<template>
  <!--
    应用对话页：左聊天 | 中代码/预览 | 右版本列表
    脚本区见文件顶部 JSDoc 流程说明
  -->
  <div class="app-chat-page">
    <header class="top-bar">
      <div class="top-bar__main">
        <div class="top-bar__name">{{ appInfo?.appName || `应用 #${appId}` }}</div>
        <div class="top-bar__meta">
          <a-tag>{{ currentCodeGenTypeLabel }}</a-tag>
          <a-tag>{{ currentModelTypeLabel }}</a-tag>
        </div>
      </div>
      <a-space>
        <a-select v-model:value="modelType" :options="modelTypeOptions" style="width: 180px" />
        <a-button @click="openDetailModal">详情</a-button>
        <a-button :loading="deploying" type="primary" @click="doDeploy">部署</a-button>
        <!-- 下载当前选中版本的代码压缩包 -->
        <a-button :loading="downloading" @click="downloadCode">下载</a-button>
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
              <!-- AI 深度思考区域：流式展示 reasoning 内容 -->
              <details
                v-if="msg.role === 'ai' && msg.thinking"
                class="message-item__thinking"
                open
              >
                <summary>深度思考</summary>
                <pre class="message-item__thinking-body">{{ msg.thinking }}</pre>
              </details>
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
            :mode="isVueProject ? 'tree' : 'flat'"
            :files="displayVirtualFiles"
            :project-files="projectFiles"
            :project-paths="projectFilePaths"
            v-model:active-path="projectActivePath"
            :generating="generating"
            :read-only="generating"
          />
          <!-- Vue 项目：后端异步 npm build，轮询 dist 就绪后再加载 iframe -->
          <div v-else-if="rightViewMode === 'preview' && showPreview && selectedVersionBuildFailed" class="preview-building">
            <a-empty>
              <template #description>
                <div class="build-fail-title">项目打包失败</div>
                <pre v-if="selectedVersionBuildError" class="build-error-text">{{ selectedVersionBuildError }}</pre>
              </template>
              <a-button type="primary" :loading="retryingBuild" @click="retryBuild()">重新打包</a-button>
            </a-empty>
          </div>
          <div v-else-if="rightViewMode === 'preview' && showPreview && previewBuilding" class="preview-building">
            <a-spin tip="项目打包中，请稍候..." />
          </div>
          <iframe
            v-else-if="rightViewMode === 'preview' && showPreview && selectedVersionPreviewReady"
            :key="`${previewUrl}-${previewRefreshKey}`"
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
              <div v-if="isVersionBuilding(version)" class="version-item__building">
                <a-spin size="small" tip="打包中" />
              </div>
              <div v-else-if="isVersionBuildFailed(version)" class="version-item__building version-item__building--retry">
                <span class="version-item__unavailable">打包失败</span>
                <p v-if="version.buildError" class="version-item__error" :title="version.buildError">
                  {{ version.buildError }}
                </p>
                <a-button
                  class="version-item__retry"
                  size="small"
                  type="link"
                  :loading="retryingBuild && selectedVersionCodeDir === version.codeDir"
                  @click.stop="retryBuild(version.codeDir)"
                >
                  重试
                </a-button>
              </div>
              <div v-else-if="!isVersionPreviewReady(version)" class="version-item__building">
                <span class="version-item__unavailable">无预览</span>
              </div>
              <iframe
                v-else
                :key="`${version.codeDir}-${getVersionPreviewKey(version)}`"
                :src="getVersionPreviewUrl(version)"
                tabindex="-1"
                title="version-preview"
              />
            </div>
            <div class="version-item__meta">
              <span class="version-item__label">{{ formatVersionLabel(version) }}</span>
              <span v-if="version.modelType" class="version-item__model">
                {{ modelTypeLabelMap[version.modelType] || version.modelType }}
              </span>
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
/**
 * 应用对话 / 代码生成页（AppChat）。
 *
 * <h3>页面布局</h3>
 * 左：聊天列表 + 输入框 | 中：代码 Monaco / iframe 预览 | 右：版本列表
 *
 * <h3>核心流程概览</h3>
 * <pre>
 * onMounted
 *   → fetchAppInfo          应用元信息（codeGenType 决定是否 Vue 工程）
 *   → loadVersions          版本列表 + 静态预览 URL
 *   → loadChatHistory       历史对话（streaming=false，无打字机）
 *   → [autoStart=1]         新建应用时自动 sendMessage
 *
 * sendMessage
 *   → 追加 user 消息
 *   → startStream           EventSource SSE 流式生成
 *
 * startStream（SSE）
 *   → onmessage             累积 aiMsg.content / thinking；Vue 项目防抖刷新代码面板
 *   → done                  结束流 → loadVersions → [Vue] buildVersion → 轮询打包 → 预览
 *   → onerror               网络异常兜底
 *
 * 版本切换 selectVersion
 *   → 重建 previewUrl → loadSavedCodeFiles
 *
 * doDeploy
 *   → 部署当前选中版本到公网访问地址
 * </pre>
 *
 * <h3>代码展示数据来源</h3>
 * 优先从最新 AI 消息解析虚拟文件；解析不到则读 staticBaseUrl 下已落盘文件（loadSavedCodeFiles）。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { deleteApp, deployApp, getAppVoById, updateApp } from '@/api/appController'
import { getAppVersionsByAppId, buildVersion, retryBuild as retryBuildApi } from '@/api/appVersionController'
import { listAppChatHistoryByPage } from '@/api/chatHistoryController'
import { useLoginUserStore } from '@/stores/loginUser'
import { CheckCircleOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import request from '@/axios/request'
import {
  CodeGenTypeEnum,
  buildDeployKey,
  getStaticBaseUrl,
  getStaticPreviewUrl,
} from '@/utils/previewUrl'
import AiMarkdownMessage from '@/components/AiMarkdownMessage.vue'
import CodeWorkspace from '@/components/CodeWorkspace.vue'
import { useProjectFileStore } from '@/composables/useProjectFileStore'
import { APP_TYPE_OPTIONS } from '@/constant/appType'
import { APP_NOT_PUBLISH, APP_PUBLISHED } from '@/constant/constant'
import {
  fetchSavedVirtualFiles,
  hasVirtualFileContent,
  parseAiContentToVirtualFiles,
  type VirtualFile,
} from '@/utils/virtualFiles'

/** 单条聊天消息（内存态；历史从 ChatHistoryVO 映射，实时 SSE 单独构造） */
type ChatMessage = {
  id?: number
  role: 'user' | 'ai'
  content: string
  /** AI 深度思考内容（reasoning 流） */
  thinking?: string
  createTime?: string
  streaming?: boolean
}

const HISTORY_PAGE_SIZE = 10

// ─── 路由与应用上下文 ─────────────────────────────────────────
const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
/** 路由 param.id，保持字符串避免雪花 ID 精度丢失 */
const appId = computed(() => {
  const id = route.params.id
  return (Array.isArray(id) ? String(id[0]) : String(id)) ?? ''
})
/** 发送 / SSE 生成中 */
const inputMessage = ref('')
const generating = ref(false)
/** 部署 / 下载 / 应用编辑 */
const deploying = ref(false)
const downloading = ref(false)
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
const modelType = ref(typeof route.query.modelType === 'string' ? route.query.modelType : '')
const modelTypeOptions = [
  { value: '', label: '自动选择模型' },
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro' },
  { value: 'gpt-5.5', label: 'GPT 5.5' },
  { value: 'claude-sonnet-4-6', label: 'Claude Sonnet 4.6' },
]
const modelTypeLabelMap = Object.fromEntries(
  modelTypeOptions.map((option) => [option.value, option.label]),
) as Record<string, string>
const codeGenTypeLabelMap: Record<string, string> = {
  [CodeGenTypeEnum.HTML]: 'HTML 模式',
  [CodeGenTypeEnum.MULTI_FILE]: '多文件模式',
  [CodeGenTypeEnum.VUE_PROJECT]: 'Vue 工程模式',
}

/** 当前应用详情；codeGenType 区分 HTML / MULTI_FILE / VUE_PROJECT */
const appInfo = ref<API.AppVO>()
const isVueProject = computed(() => appInfo.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT)
const currentCodeGenTypeLabel = computed(() => {
  const codeGenType = appInfo.value?.codeGenType
  if (!codeGenType) return '生成类型：自动选择中'
  return `生成类型：${codeGenTypeLabelMap[codeGenType] || codeGenType}`
})
const currentModelTypeLabel = computed(() => {
  const currentModelType = selectedVersion.value?.modelType || modelType.value
  return `模型：${modelTypeLabelMap[currentModelType] || currentModelType || '自动选择'}`
})

/** Vue 项目：目录树 + Monaco 的统一文件状态（SSE t=file + HTTP 全量刷新） */
const {
  files: projectFiles,
  filePaths: projectFilePaths,
  activePath: projectActivePath,
  hasContent: projectHasContent,
  ingestFileEvent,
  refreshFromServer: refreshProjectFiles,
  reset: resetProjectFiles,
} = useProjectFileStore()

// ─── 聊天消息与 SSE ───────────────────────────────────────────
const messages = ref<ChatMessage[]>([])

// ─── 预览与静态资源 URL ───────────────────────────────────────
const previewUrl = ref('')
const staticBaseUrl = ref('')
/** 是否展示右侧预览区（生成结束后或已有历史时为 true） */
const showPreview = ref(false)
/** Vue 项目：当前版本 npm build 轮询中 */
const previewBuilding = ref(false)
const retryingBuild = ref(false)
/** 强制 iframe 刷新（版本打包成功后递增） */
const previewRefreshKey = ref(0)
const messageRef = ref<HTMLElement>()

// ─── 对话历史分页 ─────────────────────────────────────────────
const loadingHistory = ref(false)
const loadingMoreHistory = ref(false)
const historyHasMore = ref(false)
/** 向上翻页游标：当前列表最早一条的 createTime */
const historyCursor = ref<string>()
let eventSource: EventSource | null = null
let codeRefreshTimer: ReturnType<typeof setTimeout> | null = null

/** Vue 项目生成中：工具写入文件后防抖刷新右侧代码面板 */
const scheduleCodeRefresh = () => {
  if (!isVueProject.value) return
  if (codeRefreshTimer) clearTimeout(codeRefreshTimer)
  codeRefreshTimer = setTimeout(() => {
    codeRefreshTimer = null
    void loadSavedCodeFiles()
  }, 400)
}

// ─── 版本列表与打包状态 ───────────────────────────────────────
const versionList = ref<API.AppVersion[]>([])
/** 当前选中版本目录名，如 v1 */
const selectedVersionCodeDir = ref('')
/** 各版本 iframe 刷新 key（build success 后 bump） */
const versionPreviewKeys = ref<Record<string, number>>({})

const selectedVersion = computed(() =>
  versionList.value.find((v) => v.codeDir === selectedVersionCodeDir.value),
)

/** Vue 版本 build_status 辅助判断（pending/building/failed/success） */
const isVersionBuildingStatus = (status?: string) => status === 'pending' || status === 'building'

const isVersionBuilding = (version: API.AppVersion) =>
  isVueProject.value && isVersionBuildingStatus(version.buildStatus)

const isVersionBuildFailed = (version: API.AppVersion) =>
  isVueProject.value && version.buildStatus === 'failed'

const isVersionPreviewReady = (version: API.AppVersion) =>
  !isVueProject.value || version.buildStatus === 'success'

const selectedVersionBuildFailed = computed(() => isVersionBuildFailed(selectedVersion.value || {}))

/** 当前选中版本的打包失败原因，来自 t_app_version.build_error */
const selectedVersionBuildError = computed(() => selectedVersion.value?.buildError?.trim() || '')

const selectedVersionPreviewReady = computed(() =>
  !isVueProject.value || selectedVersion.value?.buildStatus === 'success',
)

// ─── 三栏布局拖拽 ─────────────────────────────────────────────
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

/** 是否为当前登录用户的应用（控制 autoStart 等） */
/** 是否为当前登录用户的应用（控制 autoStart 等） */
const isOwnApp = computed(() => {
  const loginUserId = loginUserStore.loginUser.id
  const ownerId = appInfo.value?.userId
  return loginUserId != null && ownerId != null && loginUserId === ownerId
})

const getVersionPreviewKey = (version: API.AppVersion) =>
  version.codeDir ? (versionPreviewKeys.value[version.codeDir] ?? 0) : 0

const markVersionPreviewReady = (codeDir: string) => {
  versionPreviewKeys.value[codeDir] = (versionPreviewKeys.value[codeDir] ?? 0) + 1
  previewRefreshKey.value++
}

/** 轮询版本 build_status，最长约 6 分钟，直到 success / failed */
const pollVersionBuildStatus = async (codeDir: string) => {
  for (let i = 0; i < 120; i++) {
    const res = await getAppVersionsByAppId({ appid: appIdNumber.value })
    const list = Array.isArray(res.data) ? res.data : []
    versionList.value = list
    const version = list.find((v) => v.codeDir === codeDir)
    if (!version) return false
    if (version.buildStatus === 'success') {
      markVersionPreviewReady(codeDir)
      return true
    }
    if (version.buildStatus === 'failed') {
      // 优先展示后端写入的 npm 输出摘要
      message.error(version.buildError?.trim() || '项目打包失败')
      return false
    }
    await new Promise((r) => setTimeout(r, 3000))
  }
  return false
}

/** Vue 生成/重试打包后：等待 dist 就绪再允许 iframe 预览 */
const waitForVuePreviewReady = async (codeDir?: string) => {
  const dir = codeDir || selectedVersionCodeDir.value
  if (!isVueProject.value || !dir) return
  previewBuilding.value = true
  try {
    const ready = await pollVersionBuildStatus(dir)
    if (!ready && selectedVersion.value?.buildStatus !== 'failed') {
      message.warning('项目打包超时，请稍后刷新页面重试')
    }
  } finally {
    previewBuilding.value = false
  }
}

/** 版本打包失败后重新触发 /tAppVersion/build */
const retryBuild = async (codeDir?: string) => {
  const dir = codeDir || selectedVersionCodeDir.value
  if (!isVueProject.value || !dir || generating.value || retryingBuild.value) return
  retryingBuild.value = true
  try {
    const res = await retryBuildApi({ appId: appId.value, codeDir: dir })
    if (res.data.code !== 0) {
      message.error(res.data.message || '重新打包失败')
      return
    }
    if (dir !== selectedVersionCodeDir.value) {
      selectedVersionCodeDir.value = dir
      buildPreviewUrl(dir)
    }
    rightViewMode.value = 'preview'
    showPreview.value = true
    await waitForVuePreviewReady(dir)
  } finally {
    retryingBuild.value = false
  }
}

// ─── 右侧代码面板（Monaco 虚拟文件） ───────────────────────────
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
const hasCodeContent = computed(() => {
  if (isVueProject.value) {
    return projectHasContent.value || projectFilePaths.value.length > 0
  }
  return hasVirtualFileContent(displayVirtualFiles.value)
})

const loadSavedCodeFiles = async () => {
  if (isVueProject.value) {
    if (!appId.value || !selectedVersionCodeDir.value || !staticBaseUrl.value) return
    await refreshProjectFiles(appId.value, selectedVersionCodeDir.value, staticBaseUrl.value)
    return
  }
  if (!staticBaseUrl.value) return
  const files = await fetchSavedVirtualFiles(staticBaseUrl.value)
  if (files.length) savedVirtualFiles.value = files
}

/** ChatHistoryVO → 内存消息；streaming=false 供 AiMarkdownMessage 跳过打字机 */
const toChatMessage = (item: API.ChatHistoryVO): ChatMessage => ({
  id: item.id,
  role: item.messageType === 'user' ? 'user' : 'ai',
  content: item.message || '',
  createTime: item.createTime,
  streaming: false,
})

/** 合并分页结果；prepend=true 时 prepend 到列表头部（加载更早消息） */
const applyHistoryRecords = (records: API.ChatHistoryVO[], prepend: boolean) => {
  // 关键：反转让老消息在前
  const sorted = [...records].reverse().map(toChatMessage)
  if (!sorted.length) return
  messages.value = prepend ? [...sorted, ...messages.value] : sorted
  historyCursor.value = messages.value[0]?.createTime
  historyHasMore.value = records.length >= HISTORY_PAGE_SIZE
}

/**
 * 加载对话历史（分页，按 create_time 降序取一页后反转成时间正序）。
 * @param lastCreateTime 有值时为「加载更多」，取比游标更早的消息
 */
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

/** 向上加载更多历史，并保持滚动位置不跳动 */
const loadMoreHistory = async () => {
  if (!historyHasMore.value || loadingMoreHistory.value || !historyCursor.value) return
  const el = messageRef.value
  const prevHeight = el?.scrollHeight ?? 0
  await loadChatHistory(historyCursor.value)
  await nextTick()
  if (el) el.scrollTop = el.scrollHeight - prevHeight
}

/** 根据 codeGenType + appId + codeDir（如 v1）拼 deployKey，更新 previewUrl / staticBaseUrl */
const buildPreviewUrl = (codeDir?: string) => {
  const codeGenType = appInfo.value?.codeGenType || 'multi_file'
  const dir = codeDir || selectedVersionCodeDir.value
  const deployKey = buildDeployKey(codeGenType, appId.value, dir)
  if (!deployKey) return
  staticBaseUrl.value = getStaticBaseUrl(deployKey)
  previewUrl.value = getStaticPreviewUrl(codeGenType, deployKey)
}

const getVersionPreviewUrl = (version: API.AppVersion) => {
  const codeGenType = appInfo.value?.codeGenType || 'multi_file'
  const deployKey = buildDeployKey(codeGenType, appId.value, version.codeDir)
  if (!deployKey) return ''
  return getStaticPreviewUrl(codeGenType, deployKey)
}

/** 拉取版本列表；selectLatest 时选中最新版并重建预览 URL */
const loadVersions = async (selectLatest = false) => {
  try {
    const res = await getAppVersionsByAppId({ appid: appIdNumber.value })
    const list = Array.isArray(res.data) ? res.data : []
    versionList.value = list
    if (list.length) {
      const latest = list[0]
      if (
        selectLatest ||
        !selectedVersionCodeDir.value ||
        !list.some((v) => v.codeDir === selectedVersionCodeDir.value)
      ) {
        selectedVersionCodeDir.value = latest?.codeDir || ''
        savedVirtualFiles.value = []
      }
      buildPreviewUrl(selectedVersionCodeDir.value)
    }
  } catch {
    versionList.value = []
  }
}

/** 切换版本：更新预览 URL，清空 savedVirtualFiles 后重新拉取落盘代码 */
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

// ─── 应用信息与 CRUD ───────────────────────────────────────────
const fetchAppInfo = async () => {
  const res = await getAppVoById({ id: appIdNumber.value })
  if (res.data.code === 0 && res.data.data) {
    appInfo.value = res.data.data
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
      id: appIdNumber.value,
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
        const res = await deleteApp({ id: appIdNumber.value })
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

// ─── SSE 流式代码生成 ─────────────────────────────────────────
/**
 * 建立 EventSource 连接 GET /app/chat/gen/code，流式接收 AI 输出。
 *
 * 数据格式：onmessage 的 event.data 为 JSON
 *   - 普通文本：{"c":"片段"}
 *   - 深度思考：{"c":"片段","t":"thinking"}
 * 结束：自定义 SSE 事件 done
 *
 * Vue 项目 done 后额外：buildVersion → waitForVuePreviewReady → loadSavedCodeFiles
 */
const startStream = (messageText: string) => {
  generating.value = true
  showPreview.value = false
  rightViewMode.value = 'code'
  if (isVueProject.value) {
    resetProjectFiles()
  }
  const aiMsg: ChatMessage = { role: 'ai', content: '', thinking: '', streaming: true }
  messages.value.push(aiMsg)
  const baseURL = request.defaults.baseURL ?? ''
  const url = new URL('app/chat/gen/code', baseURL.endsWith('/') ? baseURL : `${baseURL}/`)
  url.searchParams.set('appId', String(appId.value))
  url.searchParams.set('message', messageText)
  url.searchParams.set('modelType', modelType.value)
  eventSource = new EventSource(url.toString(), { withCredentials: true })
  let finished = false

  /** 每收到一条 SSE data：thinking / file / 聊天文本 */
  eventSource.onmessage = (event) => {
    if (finished) return
    try {
      const data = JSON.parse(event.data) as {
        c?: string
        t?: string
        path?: string
        content?: string
        append?: boolean
        done?: boolean
      }
      if (data.t === 'thinking') {
        aiMsg.thinking = (aiMsg.thinking ?? '') + (data.c ?? '')
      } else if (data.t === 'file' && isVueProject.value) {
        ingestFileEvent({
          path: data.path ?? '',
          content: data.content ?? data.c ?? '',
          append: data.append,
          done: data.done,
        })
      } else {
        aiMsg.content += data.c ?? ''
        if (!isVueProject.value) scheduleCodeRefresh()
      }
    } catch {
      aiMsg.content += event.data ?? ''
      if (!isVueProject.value) scheduleCodeRefresh()
    }
    scrollToBottom()
  }

  /**
   * 后端推送 done 事件表示本轮生成结束：
   * 1. 关闭 streaming，断开 EventSource
   * 2. 成功：刷新版本 → [Vue] 触发打包 → 轮询 dist → 加载落盘文件 → 展示预览
   * 3. 失败（content 以「生成失败」开头）：仅滚到底部
   */
  eventSource.addEventListener('done', async () => {
    if (finished) return
    finished = true
    aiMsg.streaming = false
    generating.value = false
    closeEventSource()
    // 错误信息已通过 onmessage 写入 aiMsg.content，跳过预览加载
    const isError = aiMsg.content.startsWith('生成失败')
    if (isError) {
      scrollToBottom()
      return
    }
    showPreview.value = true
    rightViewMode.value = 'preview'
    await loadVersions(true)
    // Vue 项目：代码生成完成后调用打包接口，再轮询打包状态
    if (isVueProject.value && selectedVersionCodeDir.value) {
      const buildRes = await buildVersion({
        appId: appId.value,
        codeDir: selectedVersionCodeDir.value,
      })
      if (buildRes.data.code !== 0) {
        message.error(buildRes.data.message || '项目打包失败')
        scrollToBottom()
        return
      }
    }
    await waitForVuePreviewReady(selectedVersionCodeDir.value)
    await loadSavedCodeFiles()
  })

  // 连接异常且未收到任何内容时的兜底（如网络中断）
  eventSource.onerror = () => {
    closeEventSource()
    if (!finished) {
      finished = true
      aiMsg.streaming = false
      generating.value = false
      if (!aiMsg.content.trim()) {
        aiMsg.content = '生成失败，请重试'
      }
      scrollToBottom()
    }
  }
}

/** 用户点击发送：入队 user 消息并启动 SSE */
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

/** 部署当前选中版本到线上（/app/deploy） */
const doDeploy = async () => {
  if (generating.value) {
    message.warning('请等待当前生成完成')
    return
  }
  deploying.value = true
  try {
    const res = await deployApp({ appId: appIdNumber.value, codeDir: selectedVersionCodeDir.value })
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deploySuccessVisible.value = true
      await loadVersions()
      return
    }
    message.error(res.data.message || '部署失败')
  } finally {
    deploying.value = false
  }
}

/** 下载当前选中版本的代码压缩包 */
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  // 使用右侧版本列表当前选中的 codeDir 作为下载版本
  const version = selectedVersionCodeDir.value
  if (!version) {
    message.warning('请先选择要下载的版本')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}/${encodeURIComponent(version)}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    // 从响应头解析文件名，兜底使用 appId-version.zip
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName =
      contentDisposition?.match(/filename="(.+)"/)?.[1] || `${appId.value}-${version}.zip`
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// ─── 生命周期 ─────────────────────────────────────────────────
onMounted(async () => {
  console.log("onMounted")
  await nextTick()
  initLayoutWidth()
  await fetchAppInfo()
  await loadVersions()
  await loadChatHistory()
  // 已有历史：直接进预览并加载落盘代码
  if (messages.value.length > 0) {
    showPreview.value = true
    rightViewMode.value = 'preview'
    await loadSavedCodeFiles()
    await scrollToBottom()
  }
  // 新建应用从列表页跳转：?autoStart=1&initPrompt=... 自动发起首轮生成
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

/** 离开页面：关闭 SSE，移除布局拖拽监听 */
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

.top-bar__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.top-bar__name {
  font-size: 18px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-bar__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
}

.top-bar__meta :deep(.ant-tag) {
  margin-inline-end: 0;
  color: var(--text-secondary);
  background: transparent;
  border-color: var(--border-color);
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

.message-item__thinking {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  border: 1px dashed rgba(22, 119, 255, 0.35);
}

.message-item__thinking summary {
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: #1677ff;
  user-select: none;
}

.message-item__thinking-body {
  margin: 8px 0 0;
  padding: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.55;
  color: var(--text-secondary);
  font-family: inherit;
  max-height: 240px;
  overflow: auto;
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

.preview-building {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 240px;
  padding: 16px;
}

.build-fail-title {
  margin-bottom: 8px;
  color: var(--text-main);
  font-weight: 500;
}

.build-error-text {
  max-width: min(720px, 90vw);
  max-height: 240px;
  margin: 0;
  padding: 10px 12px;
  overflow: auto;
  text-align: left;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
  color: #cf1322;
  background: rgba(255, 77, 79, 0.06);
  border: 1px solid rgba(255, 77, 79, 0.2);
  border-radius: 6px;
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

.version-item__building {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  color: var(--text-secondary);
  font-size: 12px;
}

.version-item__building--retry {
  pointer-events: auto;
  flex-direction: column;
  gap: 6px;
}

.version-item__retry {
  padding: 0;
  height: auto;
  font-size: 12px;
}

.version-item__unavailable {
  color: var(--text-secondary);
}

.version-item__error {
  margin: 4px 0 0;
  max-height: 48px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-size: 11px;
  line-height: 1.4;
  color: #cf1322;
  text-align: center;
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
