# Vue 项目代码预览设计方案

## 1. 背景与目标

### 1.1 现状问题

| 能力 | HTML / MULTI_FILE | VUE_PROJECT（改造前） |
|------|-------------------|----------------------|
| 代码面板 | 固定 3 文件扁平列表 | 仍尝试从 AI 聊天文本解析代码 |
| 文件结构 | index.html / style.css / script.js | 多目录多文件，无法展示 |
| 流式体验 | 聊天区 Markdown 打字机 | 工具写入后 HTTP 轮询刷新，无 Monaco 打字机 |
| 数据源 | SSE 文本 + 静态目录兜底 | 同上，不适配 Vue 工程 |

Vue 项目通过 `writeFile` 工具落盘，聊天区仅应展示「写入 `src/App.vue` 成功」等摘要；完整源码应由 **独立文件通道** 驱动右侧 Monaco 预览。

### 1.2 设计目标

1. **GitHub 式预览**：左侧目录树 + 右侧 Monaco，支持多层级路径。
2. **双数据源**：SSE 实时增量 + HTTP 全量刷新（进入页面 / 生成结束）。
3. **生成中打字机**：Monaco 区对当前写入文件做渐进展示，与聊天区 `AiMarkdownMessage` 分离。
4. **自动跟随**：生成中自动切换到正在写入的文件；用户手动点选后暂停跟随。

---

## 2. 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│ AppChat.vue                                                      │
│  ├─ SSE onmessage ──► ingestFileEvent (t=file)                  │
│  ├─ done / onMounted ──► refreshFromServer                      │
│  └─ CodeWorkspace mode=tree                                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
              ┌─────────────▼─────────────┐
              │   useProjectFileStore     │
              │   Map<path, ProjectFile>  │
              └─────────────┬─────────────┘
                            │
         ┌──────────────────┼──────────────────┐
         ▼                  ▼                  ▼
 ProjectFileTree    CodeViewerPanel    useMonacoTypewriter
 (buildFileTree)    (MonacoEditor)     (displayContent)
```

### 2.1 核心类型

```typescript
type ProjectFileStatus = 'idle' | 'generating' | 'done'

type ProjectFile = {
  path: string
  content: string           // 完整内容
  displayContent: string    // 预留；打字机由 composable 计算
  language: string          // pathToLanguage(path)
  status: ProjectFileStatus
  updatedAt: number
}
```

---

## 3. SSE 协议扩展

### 3.1 已有格式（保持不变）

| 类型 | JSON 示例 | 用途 |
|------|-----------|------|
| 普通文本 | `{"c":"片段"}` | 聊天区 Markdown |
| 深度思考 | `{"c":"...","t":"thinking"}` | 思考折叠区 |
| 结束 | SSE event `done` | 关闭流、触发打包 |

### 3.2 新增：文件事件

后端 `JsonMessageStreamHandler` 在 `TOOL_EXECUTED` 时 **额外** 推送（与聊天摘要同批）：

```json
{
  "t": "file",
  "path": "src/App.vue",
  "content": "<template>...</template>",
  "append": false,
  "done": true
}
```

| 字段 | 说明 |
|------|------|
| `t` | 固定 `"file"`，Controller 透传不包 `{"c":...}` |
| `path` | 相对项目根路径，正斜杠 |
| `content` | 本次写入块（append=true 时为追加片段） |
| `append` | 是否追加到已有 content |
| `done` | 该次工具调用是否完成 |

### 3.3 后端处理步骤

**步骤 1** — `JsonMessageStreamHandler.handle`：`map` 改为 `concatMap`，单块 TokenStream 可输出多条 SSE 字符串。

**步骤 2** — `buildToolExecutedOutputs`：
- 聊天区：`\n\n[工具调用] 写入文件 \`path\` 成功\n\n`
- 代码区：`t=file` JSON（失败或无 path 时不推 file 事件）

**步骤 3** — `AppController.chatToGenCode`：已有逻辑 `chunk.contains("\"t\"")` 时原样透传，无需改动。

---

## 4. HTTP 接口

### 4.1 文件列表

```
GET /api/app/code/files?appId={id}&codeDir={v1}
Authorization: 登录态 Cookie
```

响应：

```json
{
  "code": 0,
  "data": {
    "files": ["package.json", "src/App.vue", "src/main.js"]
  }
}
```

**步骤 1** — `AppCodeFileServiceImpl.listVueProjectFiles`：通过 `VueProjectVersionManager.getVersionDir` 定位目录。

**步骤 2** — 递归遍历，排除 `node_modules`、`dist`、`.git` 等（规则与 `ProjectDownloadServiceImpl` 一致）。

**步骤 3** — 返回相对路径列表，字典序排序。

### 4.2 文件内容

沿用现有静态资源接口：

```
GET /api/static/{deployKey}/{relativePath}
```

其中 `deployKey = vue_project_{appId}_{codeDir}`。

**步骤 4** — `StaticResourceController` 补充 `.vue`、`.json` 的 Content-Type。

---

## 5. 前端模块设计

### 5.1 文件清单

| 路径 | 职责 |
|------|------|
| `utils/projectFiles.ts` | `pathToLanguage`、`buildFileTree`、HTTP 拉单文件 |
| `composables/useProjectFileStore.ts` | 统一文件 Map、ingest / refresh / reset |
| `composables/useMonacoTypewriter.ts` | Monaco 打字机 displayContent |
| `components/ProjectFileTree.vue` | 目录树容器 |
| `components/ProjectFileTreeNode.vue` | 递归树节点 |
| `components/CodeViewerPanel.vue` | Tab + Monaco + 打字机 |
| `components/CodeWorkspace.vue` | `mode=flat|tree` 双模式 |
| `components/MonacoEditor.vue` | 新增 `displayValue` prop |
| `views/app/AppChat.vue` | Vue 项目接入 store + SSE 分支 |

### 5.2 useProjectFileStore 流程

**步骤 1 — 开始生成**

```typescript
if (isVueProject) resetProjectFiles()
```

**步骤 2 — SSE t=file**

```typescript
ingestFileEvent({ path, content, append, done })
// append: content 追加；done: status → done
// autoFollowWriting: 自动 activePath = path
```

**步骤 3 — 全量刷新**

```typescript
refreshFromServer(appId, codeDir, staticBaseUrl)
// listCodeFiles → 并行 fetch /api/static/... → upsertFileFromServer
```

**步骤 4 — 用户点树节点**

```typescript
setActivePath(path) // autoFollowWriting = false
```

### 5.3 CodeWorkspace 双模式

| mode | 场景 | 左侧 | 右侧 |
|------|------|------|------|
| `flat` | HTML / MULTI_FILE | 三文件列表 | Monaco 直接绑 content |
| `tree` | VUE_PROJECT | ProjectFileTree | CodeViewerPanel + 打字机 |

### 5.4 Monaco 打字机

仅在 `generating && file.status === 'generating'` 时启用；否则 `displayContent = content`。

步进策略：每 20ms 追加 `max(2, remain/12)` 字符，与聊天区打字机独立。

---

## 6. AppChat 集成步骤

| 步骤 | 动作 |
|------|------|
| 1 | `useProjectFileStore()` 创建 store |
| 2 | `hasCodeContent`：Vue 看 `projectHasContent \|\| projectFilePaths.length` |
| 3 | `loadSavedCodeFiles`：Vue 走 `refreshProjectFiles`，其他类型走原 `fetchSavedVirtualFiles` |
| 4 | `startStream` 开头 `resetProjectFiles()` |
| 5 | `onmessage`：`t=file` → `ingestFileEvent`；非 Vue 仍 `scheduleCodeRefresh` |
| 6 | `done` 后照旧 `buildVersion` → `loadSavedCodeFiles` 全量对齐落盘 |
| 7 | 模板：`CodeWorkspace` 传 `mode="tree"` + `v-model:active-path` |

---

## 7. 分阶段实施（已完成 P0 + P1 + P2）

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | `GET /app/code/files` + 树形 CodeWorkspace + HTTP 全量加载 | ✅ |
| P1 | SSE `t=file` + `ingestFileEvent` | ✅ |
| P2 | Monaco 打字机 + 自动切当前写入文件 | ✅ |
| P3 | 大文件懒加载、二进制资源图标化 | 待做 |

### P3 预留

- 单文件 >100KB 不在 SSE 中带 content，仅推 path，前端按需 fetch。
- 图片/font 等在树中显示图标，Monaco 区提示「二进制文件不可预览」。

---

## 8. 与聊天区的边界

```
┌──────────────────┐     ┌──────────────────┐
│ 左侧聊天区        │     │ 右侧代码区        │
│ AiMarkdownMessage│     │ CodeWorkspace    │
│ 工具摘要 Markdown │     │ t=file 驱动      │
│ 无完整源码        │     │ 完整文件内容      │
└──────────────────┘     └──────────────────┘
```

历史消息加载时 `streaming: false`，聊天区不打字机；代码区直接展示 `refreshFromServer` 全量内容。

---

## 9. 测试要点

1. Vue 项目流式生成：左侧聊天见工具摘要，右侧树节点递增、Monaco 打字机跟随当前文件。
2. 刷新页面：历史对话正常，代码区通过 `listCodeFiles` + static 恢复。
3. 生成结束：打包后轮询预览 iframe，代码区与磁盘一致。
4. HTML / MULTI_FILE 应用：`mode=flat` 行为与改造前一致。
5. `append=true` 分块写入：多次 SSE file 事件 content 正确拼接。

---

## 10. 变更文件索引

### 后端

- `AppCodeFileService.java` / `AppCodeFileServiceImpl.java`
- `AppCodeFileListVO.java`
- `AppController.java` — `GET /app/code/files`
- `JsonMessageStreamHandler.java` — 双路输出
- `StaticResourceController.java` — `.vue` MIME

### 前端

- `utils/projectFiles.ts`
- `composables/useProjectFileStore.ts`
- `composables/useMonacoTypewriter.ts`
- `components/ProjectFileTree.vue`
- `components/ProjectFileTreeNode.vue`
- `components/CodeViewerPanel.vue`
- `components/CodeWorkspace.vue`
- `components/MonacoEditor.vue`
- `views/app/AppChat.vue`
- `api/appController.ts` — `listCodeFiles`
- `api/typings.d.ts`
