import { ref } from 'vue'
import { listAiModels } from '@/api/aiModelController'

export type ModelSelectOption = {
  value: string
  label: string
  disabled?: boolean
  title?: string
}

export function useAiModelOptions() {
  const modelTypeOptions = ref<ModelSelectOption[]>([{ value: '', label: '自动选择模型' }])

  const loadAiModels = async () => {
    try {
      const res = await listAiModels()
      if (res.data.code !== 0 || !res.data.data) {
        return
      }
      modelTypeOptions.value = [
        { value: '', label: '自动选择模型' },
        ...res.data.data.map((model) => {
          const enabled = model.enabled === 1
          const name = model.modelName || model.modelCode || ''
          return {
            value: name,
            label: enabled ? name : `${name}（不可用）`,
            disabled: !enabled,
            title: enabled ? undefined : '该模型不可用',
          }
        }),
      ]
    } catch {
      // 未登录或接口失败时保留「自动选择」
    }
  }

  return { modelTypeOptions, loadAiModels }
}
