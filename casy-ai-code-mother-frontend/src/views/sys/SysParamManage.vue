<template>
  <div class="sys-param-manage-page">
    <a-card :bordered="false">
      <a-space style="margin-bottom: 16px">
        <a-button type="primary" @click="openAdd">新增参数</a-button>
        <a-button :loading="loading" @click="fetchData">刷新</a-button>
      </a-space>
      <a-table row-key="id" :columns="columns" :data-source="data" :loading="loading" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'enabled'">
            <a-tag :color="record.enabled === 1 ? 'green' : 'default'">
              {{ record.enabled === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'isPublic'">
            <a-tag :color="record.isPublic === 1 ? 'blue' : 'default'">
              {{ record.isPublic === 1 ? '公开' : '内部' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'paramValue'">
            <span :title="record.paramValue">{{ record.paramValue || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除该参数？" @confirm="doDelete(record.id)">
                <a-button type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="formVisible"
      :title="form.id ? '编辑参数' : '新增参数'"
      :confirm-loading="submitting"
      @ok="submitForm"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="参数键" required>
          <a-input v-model:value="form.paramKey" placeholder="如 site.github.url" :disabled="Boolean(form.id)" />
        </a-form-item>
        <a-form-item label="参数名称" required>
          <a-input v-model:value="form.paramName" placeholder="如 GitHub 仓库地址" />
        </a-form-item>
        <a-form-item label="参数值">
          <a-input v-model:value="form.paramValue" placeholder="链接须以 http:// 或 https:// 开头" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="formEnabled" />
        </a-form-item>
        <a-form-item label="公开（未登录可读）">
          <a-switch v-model:checked="formPublic" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { addSysParam, deleteSysParam, listSysParams, updateSysParam } from '@/api/sysParamController'

const columns = [
  { title: '名称', dataIndex: 'paramName', width: 160 },
  { title: '参数键', dataIndex: 'paramKey', width: 180 },
  { title: '参数值', dataIndex: 'paramValue', ellipsis: true },
  { title: '启用', dataIndex: 'enabled', width: 80 },
  { title: '公开', dataIndex: 'isPublic', width: 80 },
  { title: '排序', dataIndex: 'sortOrder', width: 80 },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
  { title: '操作', key: 'action', width: 140 },
]

const data = ref<API.SysParam[]>([])
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const form = reactive<API.SysParamUpdateRequest>({
  id: undefined,
  paramKey: '',
  paramValue: '',
  paramName: '',
  remark: '',
  enabled: 1,
  isPublic: 1,
  sortOrder: 100,
})

const formEnabled = computed({
  get: () => form.enabled === 1,
  set: (v: boolean) => {
    form.enabled = v ? 1 : 0
  },
})
const formPublic = computed({
  get: () => form.isPublic === 1,
  set: (v: boolean) => {
    form.isPublic = v ? 1 : 0
  },
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listSysParams()
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
  form.paramKey = ''
  form.paramValue = ''
  form.paramName = ''
  form.remark = ''
  form.enabled = 1
  form.isPublic = 1
  form.sortOrder = 100
}

const openAdd = () => {
  resetForm()
  formVisible.value = true
}

const openEdit = (record: API.SysParam) => {
  form.id = record.id
  form.paramKey = record.paramKey
  form.paramValue = record.paramValue
  form.paramName = record.paramName
  form.remark = record.remark
  form.enabled = record.enabled
  form.isPublic = record.isPublic
  form.sortOrder = record.sortOrder
  formVisible.value = true
}

const submitForm = async () => {
  if (!form.paramKey?.trim() || !form.paramName?.trim()) {
    message.warning('请填写参数键和参数名称')
    return
  }
  submitting.value = true
  try {
    const payload = { ...form }
    const res = form.id ? await updateSysParam(payload) : await addSysParam(payload)
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

const doDelete = async (id?: string) => {
  if (!id) return
  const res = await deleteSysParam({ id })
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
.sys-param-manage-page {
  padding: 14px;
}
</style>
