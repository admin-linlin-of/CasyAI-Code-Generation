const LONG_ID_FIELD = /"(id|appId)"\s*:\s*(\d{15,})/g
const LONG_DATA_FIELD = /"data"\s*:\s*(\d{15,})(?=\s*[,}])/g

export function parseJsonWithLongIds(text: string): unknown {
  const patched = text
    .replace(LONG_ID_FIELD, (_, key, num) => `"${key}":"${num}"`)
    .replace(LONG_DATA_FIELD, (_, num) => `"data":"${num}"`)
  return JSON.parse(patched)
}
