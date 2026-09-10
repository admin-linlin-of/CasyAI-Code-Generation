import { computed, ref } from 'vue'
import { listCodeFiles } from '@/api/appController'
import {
  fetchProjectFileContent,
  pathToLanguage,
  type ProjectFile,
  type ProjectFileStatus,
} from '@/utils/projectFiles'

/**
 * Vue 项目代码预览的统一文件状态仓库（composable）。
 *
 * 职责：维护 Map<path, ProjectFile>，供 ProjectFileTree + CodeViewerPanel 消费。
 *
 * 双数据源：
 *   实时 — SSE {"t":"file"} → ingestFileEvent（生成中增量更新）
 *   全量 — GET /app/code/files + /api/static → refreshFromServer（进入页 / 生成结束）
 *
 * 调用方：AppChat.vue
 *   startStream 前 reset()
 *   onmessage t=file → ingestFileEvent()
 *   done / onMounted → refreshFromServer()
 */
export function useProjectFileStore() {
  /** 核心存储：path → ProjectFile，Map 内对象可变，需 touchMap 触发 Vue 响应式 */
  const filesMap = ref(new Map<string, ProjectFile>())

  /** 当前 Monaco 正在展示的文件路径 */
  const activePath = ref('')

  /**
   * 是否自动跟随「正在写入」的文件。
   * true：SSE 写入时自动切换 activePath；false：用户手动点树节点后暂停跟随。
   */
  const autoFollowWriting = ref(true)

  /** 全部文件列表，按 path 字典序，供目录树渲染 */
  const files = computed(() =>
    Array.from(filesMap.value.values()).sort((a, b) => a.path.localeCompare(b.path)),
  )

  /** 扁平路径数组，供 buildFileTree 构建目录树 */
  const filePaths = computed(() => files.value.map((file) => file.path))

  /** 是否存在非空文件内容，控制 CodeWorkspace 是否显示 */
  const hasContent = computed(() => files.value.some((file) => file.content.trim().length > 0))

  /** 当前选中文件对象，供 CodeViewerPanel 绑定 Monaco */
  const activeFile = computed(() =>
    activePath.value ? filesMap.value.get(activePath.value) : undefined,
  )

  /**
   * 触发 Vue 对 Map 的依赖更新。
   * 原因：直接 filesMap.value.set() 修改内部对象，Vue 3 无法感知 Map 引用未变。
   * 流程：浅拷贝 Map → 赋回 filesMap → computed / watch 重新计算。
   */
  const touchMap = () => {
    filesMap.value = new Map(filesMap.value)
  }

  /**
   * 获取或创建指定 path 的 ProjectFile 条目。
   *
   * 流程：
   * 1. 从 filesMap 查找
   * 2. 不存在则初始化空文件（status=idle，language 由扩展名推断）
   * 3. 写入 Map 并返回同一引用（后续直接改 file.content 等字段）
   */
  const ensureFile = (path: string): ProjectFile => {
    let file = filesMap.value.get(path)
    if (!file) {
      file = {
        path,
        content: '',
        displayContent: '',
        language: pathToLanguage(path),
        status: 'idle',
        updatedAt: Date.now(),
      }
      filesMap.value.set(path, file)
    }
    return file
  }

  /**
   * 用户点击目录树切换当前文件。
   *
   * @param path 目标文件相对路径
   * @param followWriting true 恢复自动跟随写入；false 用户手动选择，暂停跟随
   */
  const setActivePath = (path: string, followWriting = false) => {
    activePath.value = path
    if (followWriting) autoFollowWriting.value = true
    else autoFollowWriting.value = false
  }

  /**
   * 处理 SSE 文件事件：{"t":"file","path","content","append","done"}
   *
   * 流程：
   * 1. 校验 path，ensureFile 拿到/创建条目
   * 2. append=true → content 拼接到已有文本；append=false → 覆盖
   * 3. done=true → status=done；否则 status=generating（触发 Monaco 打字机）
   * 4. touchMap 通知视图更新
   * 5. autoFollowWriting 开启时，将 activePath 切到本次写入的文件
   */
  type FileEventPayload = {
    path: string
    content?: string
    append?: boolean
    done?: boolean
  }

  const applyFileEvent = (payload: FileEventPayload): string | null => {
    if (!payload.path) return null
    const file = ensureFile(payload.path)
    const chunk = payload.content ?? ''
    file.content = payload.append ? file.content + chunk : chunk
    file.language = pathToLanguage(payload.path)
    file.status = payload.done ? 'done' : 'generating'
    file.updatedAt = Date.now()
    return payload.path
  }

  const ingestFileEvent = (payload: FileEventPayload) => {
    const path = applyFileEvent(payload)
    if (!path) return
    touchMap()
    if (autoFollowWriting.value && (payload.done || filesMap.value.get(path)?.status === 'generating')) {
      activePath.value = path
    }
  }

  /** 同一帧内多条 SSE 文件事件只触发一次 Map 拷贝，避免生成中卡死 */
  const ingestFileEvents = (payloads: FileEventPayload[]) => {
    if (!payloads.length) return
    let lastPath = ''
    for (const payload of payloads) {
      const path = applyFileEvent(payload)
      if (path) lastPath = path
    }
    if (!lastPath) return
    touchMap()
    if (autoFollowWriting.value) {
      activePath.value = lastPath
    }
  }

  /**
   * HTTP 全量写入单个文件（不触发 SSE 增量逻辑）。
   *
   * 流程：ensureFile → 写入 content/displayContent → status 默认 done → touchMap
   * 用于 refreshFromServer 从静态目录拉取已落盘文件。
   */
  const upsertFileFromServer = (path: string, content: string, status: ProjectFileStatus = 'done') => {
    const file = ensureFile(path)
    file.content = content
    file.displayContent = content
    file.language = pathToLanguage(path)
    file.status = status
    file.updatedAt = Date.now()
    touchMap()
  }

  /**
   * 从后端全量刷新文件列表与内容。
   *
   * 流程：
   * 1. 校验 appId / codeDir / staticBaseUrl
   * 2. GET /app/code/files 获取相对路径列表
   * 3. 并行 GET /api/static/{deployKey}/{path} 拉每个文件正文
   * 4. upsertFileFromServer 写入 store
   * 5. 若尚未选中文件，默认打开列表第一项
   *
   * 失败时静默忽略，保留 SSE 已写入的内存数据。
   */
  const refreshFromServer = async (appId: string, codeDir: string, staticBaseUrl: string) => {
    if (!appId || !codeDir || !staticBaseUrl) return
    try {
      const res = await listCodeFiles({ appId, codeDir })
      if (res.data.code !== 0) return
      const paths = res.data.data?.files ?? []
      if (!paths.length) return
      await Promise.all(
        paths.map(async (path) => {
          const content = await fetchProjectFileContent(staticBaseUrl, path)
          upsertFileFromServer(path, content)
        }),
      )
      if (!activePath.value && paths.length) {
        activePath.value = paths[0]!
      }
    } catch {
      // 静默失败，保留 SSE 已写入的内容
    }
  }

  /**
   * 新一轮 SSE 生成开始前清空状态。
   *
   * 流程：清空 Map → activePath 置空 → 恢复 autoFollowWriting
   * 调用时机：AppChat.startStream 开头
   */
  const reset = () => {
    filesMap.value = new Map()
    activePath.value = ''
    autoFollowWriting.value = true
  }

  return {
    files,
    filePaths,
    filesMap,
    activePath,
    activeFile,
    autoFollowWriting,
    hasContent,
    setActivePath,
    ingestFileEvent,
    ingestFileEvents,
    refreshFromServer,
    reset,
  }
}
