/**
 * 将 README 中的 GFM 表格转成 HTML，再交给 markdown-it。
 * 去掉无法在网页里打开的本地图片。
 */
export function prepareReadme(raw: string): string {
  let text = raw.replace(/\r\n/g, '\n')
  text = text.replace(/!\[[^\]]*]\((?:C:\\[^)]+|img\.png)\)/g, '')
  text = text.replace(/\n## 项目截图[\s\S]*$/m, '')
  return convertMarkdownTables(text).trim()
}

function convertMarkdownTables(src: string): string {
  const lines = src.split('\n')
  const out: string[] = []
  let i = 0
  while (i < lines.length) {
    if (isTableRow(lines[i]) && i + 1 < lines.length && isSeparatorRow(lines[i + 1])) {
      const rows: string[] = []
      while (i < lines.length && isTableRow(lines[i])) {
        if (!isSeparatorRow(lines[i])) rows.push(lines[i])
        i += 1
      }
      out.push(renderTable(rows))
      continue
    }
    out.push(lines[i])
    i += 1
  }
  return out.join('\n')
}

function isTableRow(line: string): boolean {
  const t = line.trim()
  return t.startsWith('|') && t.endsWith('|') && t.includes('|', 1)
}

function isSeparatorRow(line: string): boolean {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line)
}

function splitCells(line: string): string[] {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim())
}

function renderTable(rows: string[]): string {
  if (!rows.length) return ''
  const head = splitCells(rows[0])
  const body = rows.slice(1).map(splitCells)
  const th = head.map((cell) => `<th>${escapeHtml(cell)}</th>`).join('')
  const tr = body
    .map((cells) => `<tr>${cells.map((cell) => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`)
    .join('')
  return `<div class="md-table-wrap"><table><thead><tr>${th}</tr></thead><tbody>${tr}</tbody></table></div>`
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
