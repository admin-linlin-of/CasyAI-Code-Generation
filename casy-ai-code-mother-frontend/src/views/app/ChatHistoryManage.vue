<template>
  <div class="chat-history-manage-page">
    <a-card :bordered="false">
      <a-form :model="searchParams" layout="inline" @finish="doSearch">
        <a-form-item label="ID">
          <a-input v-model:value="searchParams.id" style="width: 180px" />
        </a-form-item>
        <a-form-item label="消息内容">
          <a-input v-model:value="searchParams.message" placeholder="输入消息内容" />
        </a-form-item>
        <a-form-item label="消息类型">
          <a-select
            v-model:value="searchParams.messageType"
            allow-clear
            style="width: 140px"
            :options="MESSAGE_TYPE_OPTIONS"
          />
        </a-form-item>
        <a-form-item label="应用ID">
          <a-input v-model:value="searchParams.appId" style="width: 180px" />
        </a-form-item>
        <a-form-item label="用户ID">
          <a-input-number v-model:value="searchParams.userId" :min="1" style="width: 140px" />
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
          <template v-if="column.dataIndex === 'message'">
            <a-typography-paragraph
              :content="record.message || '-'"
              :ellipsis="{ rows: 2, expandable: true, symbol: '展开' }"
              style="margin: 0; max-width: 360px"
            />
          </template>
          <template v-else-if="column.dataIndex === 'messageType'">
            <a-tag :color="record.messageType === 'user' ? 'blue' : 'green'">
              {{ MESSAGE_TYPE_LABEL_MAP[record.messageType] || record.messageType || '-' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'appName'">
            {{ record.app?.appName || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'userName'">
            {{ record.user?.userName || record.user?.userAccount || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatDate(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" @click="goChat(record.appId)">查看应用</a-button>
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
import { listChatHistoryByPage } from '@/api/chatHistoryController'

const MESSAGE_TYPE_OPTIONS = [
  { value: 'user', label: '用户消息' },
  { value: 'ai', label: 'AI 消息' },
]

const MESSAGE_TYPE_LABEL_MAP: Record<string, string> = {
  user: '用户消息',
  ai: 'AI 消息',
}

const router = useRouter()
const columns = [
  { title: 'ID', dataIndex: 'id', width: 180 },
  { title: '消息内容', dataIndex: 'message', width: 380 },
  { title: '消息类型', dataIndex: 'messageType', width: 110 },
  { title: '应用ID', dataIndex: 'appId', width: 180 },
  { title: '应用名称', dataIndex: 'appName', width: 160 },
  { title: '用户ID', dataIndex: 'userId', width: 180 },
  { title: '用户名称', dataIndex: 'userName', width: 140 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', fixed: 'right', width: 120 },
]

const data = ref<API.ChatHistoryVO[]>([])
const total = ref(0)
const loading = ref(false)
const searchParams = reactive<API.ChatHistoryQueryRequest>({
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
    const res = await listChatHistoryByPage({ ...searchParams })
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
  searchParams.message = undefined
  searchParams.messageType = undefined
  searchParams.appId = undefined
  searchParams.userId = undefined
  searchParams.pageNum = 1
  searchParams.pageSize = 10
  fetchData()
}

const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const goChat = (appId?: string) => {
  if (!appId) return
  router.push(`/app/chat/${appId}`)
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.chat-history-manage-page {
  padding: 14px;
}

:deep(.ant-form) {
  gap: 2px 6px;
}

:deep(.ant-table-wrapper) {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}
</style>
