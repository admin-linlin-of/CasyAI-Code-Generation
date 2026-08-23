<template>
  <div class="ai-model-manage-page">
    <a-card :bordered="false">
      <a-space style="margin-bottom: 16px">
        <a-button type="primary" @click="openAdd">新增模型</a-button>
        <a-button :loading="loading" @click="fetchData">刷新</a-button>
      </a-space>
      <a-table row-key="id" :columns="columns" :data-source="data" :loading="loading" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'enabled'">
            <a-switch
              :checked="record.enabled === 1"
              checked-children="启用"
              un-checked-children="停用"
              @change="(checked) => toggleEnabled(record, !!checked)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'isDefault'">
            <a-tag v-if="record.isDefault === 1" color="green">默认</a-tag>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除该模型？" @confirm="doDelete(record.id)">
                <a-button type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="formVisible"
      :title="form.id ? '编辑模型' : '新增模型'"
      :confirm-loading="submitting"
      @ok="submitForm"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="模型编码" required>
          <a-input v-model:value="form.modelCode" placeholder="须与 ModelTypeEnum 一致，如 GPT" />
        </a-form-item>
        <a-form-item label="调用名称" required>
          <a-input v-model:value="form.modelName" placeholder="如 gpt-5.5" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="formEnabled" />
        </a-form-item>
        <a-form-item label="默认模型">
          <a-switch v-model:checked="formDefault" />
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item label="路由规则">
          <a-textarea v-model:value="form.routingRule" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  addAiModel,
  deleteAiModel,
  listAiModels,
  updateAiModel,
  updateAiModelEnabled,
} from '@/api/aiModelController'

const columns = [
  { title: '编码', dataIndex: 'modelCode', width: 160 },
  { title: '调用名', dataIndex: 'modelName', width: 180 },
  { title: '启用', dataIndex: 'enabled', width: 100 },
  { title: '默认', dataIndex: 'isDefault', width: 80 },
  { title: '排序', dataIndex: 'sortOrder', width: 80 },
  { title: '说明', dataIndex: 'description', ellipsis: true },
  { title: '操作', key: 'action', width: 140 },
]

const data = ref<API.AiModel[]>([])
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const form = reactive<API.AiModelUpdateRequest>({
  id: undefined,
  modelCode: '',
  modelName: '',
  enabled: 1,
  isDefault: 0,
  sortOrder: 100,
  description: '',
  routingRule: '',
})

const formEnabled = computed({
  get: () => form.enabled === 1,
  set: (v: boolean) => {
    form.enabled = v ? 1 : 0
  },
})
const formDefault = computed({
  get: () => form.isDefault === 1,
  set: (v: boolean) => {
    form.isDefault = v ? 1 : 0
  },
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAiModels()
    if (res.data.code === 0) {
      data.value = res.data.data ?? []
      return
    }
    message.error(res.data.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.id = undefined
  form.modelCode = ''
  form.modelName = ''
  form.enabled = 1
  form.isDefault = 0
  form.sortOrder = 100
  form.description = ''
  form.routingRule = ''
}

const openAdd = () => {
  resetForm()
  formVisible.value = true
}

const openEdit = (record: API.AiModel) => {
  form.id = record.id
  form.modelCode = record.modelCode
  form.modelName = record.modelName
  form.enabled = record.enabled
  form.isDefault = record.isDefault
  form.sortOrder = record.sortOrder
  form.description = record.description
  form.routingRule = record.routingRule
  formVisible.value = true
}

const submitForm = async () => {
  if (!form.modelCode?.trim() || !form.modelName?.trim()) {
    message.warning('请填写模型编码和调用名称')
    return
  }
  submitting.value = true
  try {
    const payload = { ...form }
    const res = form.id ? await updateAiModel(payload) : await addAiModel(payload)
    if (res.data.code === 0) {
      message.success('保存成功')
      formVisible.value = false
      fetchData()
      return
    }
    message.error(res.data.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const toggleEnabled = async (record: API.AiModel, checked: boolean) => {
  const res = await updateAiModelEnabled({ id: record.id, enabled: checked ? 1 : 0 })
  if (res.data.code === 0) {
    message.success(checked ? '已启用' : '已停用')
    fetchData()
    return
  }
  message.error(res.data.message || '操作失败')
}

const doDelete = async (id?: string) => {
  if (!id) return
  const res = await deleteAiModel({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
    return
  }
  message.error(res.data.message || '删除失败')
}

onMounted(fetchData)
</script>

<style scoped>
.ai-model-manage-page {
  padding: 14px;
}
</style>
