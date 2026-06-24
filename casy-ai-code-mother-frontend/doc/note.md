"dompurify": "^3.4.5",
"highlight.js": "^11.11.1",
"markdown-it": "^14.1.1",这个三个组件的功能分别是什么，在项目中有什么用

三个库分别做什么

库	                    作用
markdown-it         把 Markdown 字符串转成 HTML（标题、列表、代码块等）
highlight.js        给代码块做语法高亮（HTML/CSS/JS 等着色）
DOMPurify           清洗 HTML，去掉危险标签/脚本，降低 XSS 风险

在本项目里的用法
数据流：
SSE 原文 → aiContentMarkdown.ts（JSON 转 Markdown）
→ markdownRenderer.ts（markdown-it 渲染 + highlight.js 高亮）
→ DOMPurify 消毒
→ AiMarkdownMessage.vue 用 v-html 展示
对应文件：src/utils/markdownRenderer.ts、src/components/AiMarkdownMessage.vue。

markdown-it：解析 **HTML**、```html ... ``` 等，生成 <pre><code> 等结构。
highlight.js：在 highlight() 回调里给 html / css / javascript 代码块上色。
DOMPurify：v-html 前过滤，只保留安全标签（如 pre、code），避免 AI 输出里的恶意脚本执行。
用户消息仍是纯文本；只有 AI 回复走这套 Markdown + 高亮 + 消毒流程。




## 使用Mono对流再执行一次收尾逻辑
Mono 是 Project Reactor（Spring WebFlux 用的响应式库）里的类型，表示 最多发出 0 或 1 个元素 的异步流。

对比：

Flux：0 ~ N 个元素（比如模型逐 token 推送）
Mono：0 ~ 1 个元素（比如流结束后的单次收尾）
- Mono.defer(...)：延迟到上游结束时才执行，能读到完整的 StringBuilder
- Mono.just("生成失败：...")：再发 1 条错误文本给前端
- Mono.empty()：正常结束，不再发数据
- concatWith(...)：把这段「收尾 Mono」接到主流后面
为什么不用 doOnComplete？

原来在 doOnComplete 里调 saveAiMessage，内容为空会抛异常，异常无法作为 SSE 数据推给前端，只能断连。

改成 concatWith(Mono.defer(...)) 后：

主流正常结束
收尾逻辑判断空/非空
空响应 → Mono.just 把错误文本推出去 → 前端 onmessage 能收到
中途异常 → doOnError 存库 + Controller 的 onErrorResume 推 SSE
AppController 里也有类似用法：

                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done").data("").build()
                ))
这里 Mono.just 表示主流结束后 再发一个 done 事件，告诉前端生成结束。


## concatWith(Mono.defer) 和 doOnComplete 存对话记忆有区别吗？

**正常完成时，时机和 doOnComplete 一样**，都是在主流全部处理完后才执行。

### 执行顺序

```
originFlux 逐条推送
    ↓
.map / .doOnNext  ← 这里实时收集到 StringBuilder
    ↓
.filter
    ↓
主流 onComplete（全部 chunk 处理完）
    ↓
Mono.defer(() -> { ... })  ← 这时才读 StringBuilder 并 save
    ↓
Mono.just / Mono.empty
    ↓
整个 Flux onComplete → Controller 发 done
```

收集发生在流进行中，`concatWith` 只负责**收尾存库**：

| Handler | 收集中 | 收尾存库 |
|---------|--------|----------|
| JsonMessageStreamHandler | `.map` → `handleJsonMessageChunk` 写 StringBuilder | `defer` 里 `saveAiMessage` |
| SimpleTextStreamHandler | `.doOnNext` append | `defer` 里 `saveAiMessage` |

### 为什么用 Mono.defer 而不是 Mono.just

`defer` 会**推迟到上游结束后再执行**，此时 StringBuilder 已是完整内容。若在上游未完成时就读取，可能拿到半成品。

### 和 doOnComplete 的区别

| | doOnComplete | concatWith(Mono.defer) |
|--|----------------|--------------------------|
| 执行时机 | 主流正常结束 | 主流正常结束（相同） |
| 能否再发数据 | 不能 | 能（空响应时 Mono.just 推错误给前端） |
| 内部抛异常 | 流中断，前端收不到 | Controller onErrorResume 可转成 SSE |

### 异常场景

主流**中途报错**时，`defer` **不会执行**（和原来 doOnComplete 也不执行一样），由 `doOnError` 存错误记录。

### 小结

- 正常流：收集逻辑不变，存库时机与 doOnComplete 等价
- 空响应：以前抛异常、前端看不到；现在存库 + 推错误文本
- 中途异常：仍走 doOnError，行为与原先一致

不是换了一种收集方式，而是**收集仍在 map/doOnNext，只是把收尾从副作用回调改成了可发数据的流尾段**。

