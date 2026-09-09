<template>
  <div class="about-project">
    <div class="about-project__panel">
      <header class="about-project__head">
        <p class="eyebrow">PROJECT README</p>
        <h1>关于项目</h1>
        <p>以下内容来自仓库根目录 README.md</p>
      </header>
      <article class="readme" v-html="html"></article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import bash from 'highlight.js/lib/languages/bash'
import markdown from 'highlight.js/lib/languages/markdown'
import 'highlight.js/styles/github.min.css'
import readmeRaw from '../../../../README.md?raw'
import { prepareReadme } from '@/utils/readmePrepare'

const ensureLang = (name: string, def: Parameters<typeof hljs.registerLanguage>[1]) => {
  if (!hljs.getLanguage(name)) hljs.registerLanguage(name, def)
}
ensureLang('xml', xml)
ensureLang('html', xml)
ensureLang('css', css)
ensureLang('javascript', javascript)
ensureLang('js', javascript)
ensureLang('bash', bash)
ensureLang('shell', bash)
ensureLang('markdown', markdown)
ensureLang('md', markdown)

const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true,
  highlight(str, lang): string {
    const language = lang && hljs.getLanguage(lang) ? lang : undefined
    if (language) {
      return `<pre class="hljs"><code>${hljs.highlight(str, { language }).value}</code></pre>`
    }
    return `<pre class="hljs"><code>${escapeHtml(str)}</code></pre>`
  },
})

const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

const html = computed(() =>
  DOMPurify.sanitize(md.render(prepareReadme(readmeRaw)), {
    ADD_TAGS: ['pre', 'code', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td'],
    ADD_ATTR: ['class', 'src', 'alt', 'title', 'target', 'rel', 'href'],
  }),
)
</script>

<style scoped>
.about-project {
  min-height: calc(100vh - 88px);
  padding: 28px 16px 96px;
}

.about-project__panel {
  max-width: 920px;
  margin: 0 auto;
  padding: 28px 32px 40px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 18px;
  color: var(--text-main);
}

.about-project__head {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: #1677ff;
}

.about-project__head h1 {
  margin: 0 0 6px;
  font-size: 28px;
}

.about-project__head p {
  margin: 0;
  color: var(--text-sub);
}

.readme :deep(h1),
.readme :deep(h2),
.readme :deep(h3) {
  margin: 1.4em 0 0.6em;
  line-height: 1.35;
}

.readme :deep(h1) {
  font-size: 26px;
}

.readme :deep(h2) {
  font-size: 20px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border-color);
}

.readme :deep(p),
.readme :deep(li) {
  line-height: 1.75;
}

.readme :deep(a) {
  color: #1677ff;
}

.readme :deep(pre) {
  overflow: auto;
  padding: 12px 14px;
  border-radius: 10px;
  background: #0f172a0d;
}

.readme :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.readme :deep(.md-table-wrap) {
  overflow: auto;
  margin: 12px 0 18px;
}

.readme :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.readme :deep(th),
.readme :deep(td) {
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  text-align: left;
}

.readme :deep(th) {
  background: color-mix(in srgb, var(--tag-bg) 70%, transparent);
}

.readme :deep(img) {
  max-width: 100%;
  border-radius: 12px;
}
</style>
