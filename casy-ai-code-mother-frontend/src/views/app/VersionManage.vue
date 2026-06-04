<template>
  <div class="version-manage-page">
    <a-card :bordered="false">
      <a-form :model="searchParams" layout="inline" @finish="doSearch">
        <a-form-item label="ID">
          <a-input v-model:value="searchParams.id" style="width: 180px" />
        </a-form-item>
        <a-form-item label="应用ID">
          <a-input v-model:value="searchParams.appId" style="width: 180px" />
        </a-form-item>
        <a-form-item label="版本号">
          <a-input-number v-model:value="searchParams.versionNum" :min="1" style="width: 120px" />
        </a-form-item>
        <a-form-item label="代码目录">
          <a-input v-model:value="searchParams.codeDir" placeholder="如 v1" />
        </a-form-item>
        <a-form-item label="模型">
          <a-input v-model:value="searchParams.modelType" />
        </a-form-item>
        <a-form-item label="用户ID">
          <a-input v-model:value="searchParams.userId" style="width: 180px" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button html-type="submit" type="primary" :loading="loading">查询</a-button>
            <a-button :disabled="loading" @click="resetSearch">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card :bordered="false" style="margin-top: 14px">
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1200 }"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'createTime'">
            {{ formatDate(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="goChat(record.appId)">查看应用</a-button>
              <a-popconfirm title="确认删除该版本？" @confirm="doDelete(record.id)">
                <a-button type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { listAppVersionByPage, remove1 } from '@/api/appVersionController'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 180 },
  { title: '应用ID', dataIndex: 'appId', width: 180 },
  { title: '版本号', dataIndex: 'versionNum', width: 90 },
  { title: '代码目录', dataIndex: 'codeDir', width: 100 },
  { title: '模型', dataIndex: 'modelType', width: 160 },
  { title: '对话ID', dataIndex: 'chatHistoryId', width: 180 },
  { title: '用户ID', dataIndex: 'userId', width: 180 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', fixed: 'right', width: 180 },
]

const data = ref<API.AppVersion[]>([])
const total = ref(0)
const loading = ref(false)
const searchParams = reactive<API.AppVersionRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'create_time',
  sortOrder: 'descend',
})

const pagination = computed(() => ({
  current: searchParams.pageNum,
  pageSize: searchParams.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (all: number) => `共 ${all} 条`,
}))

const formatDate = (date?: string) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAppVersionByPage({ ...searchParams })
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }
    message.error(res.data.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

const resetSearch = () => {
  searchParams.id = undefined
  searchParams.appId = undefined
  searchParams.versionNum = undefined
  searchParams.codeDir = undefined
  searchParams.modelType = undefined
  searchParams.userId = undefined
  searchParams.pageNum = 1
  fetchData()
}

const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const doDelete = async (id?: number) => {
  if (!id) return
  const res = await remove1({ id })
  if (res.data === true || res.data?.code === 0) {
    message.success('删除成功')
    fetchData()
    return
  }
  message.error('删除失败')
}

const goChat = (appId?: number) => {
  if (!appId) return
  router.push(`/app/chat/${appId}`)
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.version-manage-page {
  padding: 14px;
}
</style>
