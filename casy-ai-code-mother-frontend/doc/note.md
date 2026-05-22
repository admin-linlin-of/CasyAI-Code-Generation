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