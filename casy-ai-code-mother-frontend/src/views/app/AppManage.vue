<template>
  <div class="app-manage-page">
    <a-card :bordered="false">
      <a-form :model="searchParams" layout="inline" @finish="doSearch">
        <a-form-item label="ID">
          <a-input v-model:value="searchParams.id" style="width: 180px" />
        </a-form-item>
        <a-form-item label="应用名">
          <a-input v-model:value="searchParams.appName" placeholder="输入应用名" />
        </a-form-item>
        <a-form-item label="生成类型">
          <a-select
            v-model:value="searchParams.codeGenType"
            allow-clear
            style="width: 160px"
            :options="[
              { value: 'multi_file', label: 'multi_file' },
              { value: 'html', label: 'html' },
            ]"
          />
        </a-form-item>
        <a-form-item label="应用类型">
          <a-select
            v-model:value="searchParams.appTypes"
            allow-clear
            mode="multiple"
            style="width: 200px"
            :options="APP_TYPE_OPTIONS"
          />
        </a-form-item>
        <a-form-item label="优先级">
          <a-input-number v-model:value="searchParams.priority" style="width: 120px" />
        </a-form-item>
        <a-form-item label="用户ID">
          <a-input-number v-model:value="searchParams.userId" :min="1" style="width: 140px" />
        </a-form-item>
        <a-form-item label="是否公布">
          <a-select
            v-model:value="searchParams.isPublish"
            allow-clear
            style="width: 120px"
            :options="[
              { value: 0, label: '不公布' },
              { value: 1, label: '公布' },
            ]"
          />
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
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'cover'">
            <a-image v-if="record.cover" :src="record.cover" :width="120" />
            <span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'codeGenType'">
            <span v-if="!record.codeGenType">-</span>
            <a-tag v-else color="green">
              {{ CODE_GEN_TYPE_LABEL_MAP[record.codeGenType] || record.codeGenType }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'appTypes'">
            <span v-if="!record.appTypes?.length">-</span>
            <a-space v-else wrap :size="4">
              <a-tag v-for="type in record.appTypes" :key="type" color="blue">
                {{ APP_TYPE_LABEL_MAP[type] || type }}
              </a-tag>
            </a-space>
          </template>
          <template v-else-if="column.dataIndex === 'isPublish'">
            <a-tag :color="record.isPublish === 1 ? 'green' : 'default'">
              {{ record.isPublish === 1 ? '公布' : '不公布' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime' || column.dataIndex === 'updateTime'">
            {{ formatDate(record[column.dataIndex]) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="goEdit(record.id)">编辑</a-button>
              <a-popconfirm title="确认删除该应用？" @confirm="doDelete(record.id)">
                <a-button type="link" danger>删除</a-button>
              </a-popconfirm>
              <a-button type="link" @click="toggleFeatured(record)">
                {{ isFeatured(record.priority) ? '取消精选' : '精选' }}
              </a-button>
              <a-button type="link" @click="togglePublish(record)">
                {{ isPublished(record.isPublish) ? '取消公布' : '公布' }}
              </a-button>
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
import { deleteApp, listAppVoByPage, updateAppByAdmin } from '@/api/appController'
import { APP_TYPE_LABEL_MAP, APP_TYPE_OPTIONS } from '@/constant/appType'
import { APP_FEATURED_PRIORITY, APP_NOT_PUBLISH, APP_PUBLISHED } from '@/constant/constant'

const CODE_GEN_TYPE_LABEL_MAP: Record<string, string> = {
  multi_file: '多文件模式',
  html: 'HTML 模式',
}

const router = useRouter()
const columns = [
  { title: 'ID', dataIndex: 'id' },
  { title: '应用名', dataIndex: 'appName' },
  { title: '封面', dataIndex: 'cover' },
  { title: '生成类型', dataIndex: 'codeGenType' },
  { title: '应用类型', dataIndex: 'appTypes' },
  { title: '优先级', dataIndex: 'priority' },
  { title: '是否公布', dataIndex: 'isPublish' },
  { title: '用户ID', dataIndex: 'userId' },
  { title: '部署Key', dataIndex: 'deployKey' },
  { title: '创建时间', dataIndex: 'createTime' },
  { title: '更新时间', dataIndex: 'updateTime' },
  { title: '操作', key: 'action' },
]

const data = ref<API.AppVO[]>([])
const total = ref(0)
const loading = ref(false)
const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'id',
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
    const res = await listAppVoByPage({ ...searchParams })
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
  searchParams.appName = undefined
  searchParams.codeGenType = undefined
  searchParams.appTypes = undefined
  searchParams.priority = undefined
  searchParams.userId = undefined
  searchParams.isPublish = undefined
  searchParams.pageNum = 1
  searchParams.pageSize = 10
  fetchData()
}

const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const doDelete = async (id?: string) => {
  if (!id) return
  const res = await deleteApp({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
    return
  }
  message.error(res.data.message || '删除失败')
}

const goEdit = (id?: string) => {
  if (!id) return
  router.push(`/app/edit/${id}?admin=1`)
}

const isFeatured = (priority?: number) => priority === APP_FEATURED_PRIORITY
const isPublished = (isPublish?: number) => isPublish === APP_PUBLISHED

const toggleFeatured = async (record: API.AppVO) => {
  if (!record.id) return
  const featured = isFeatured(record.priority)
  const res = await updateAppByAdmin({
    id: record.id,
    priority: featured ? 0 : APP_FEATURED_PRIORITY,
  })
  if (res.data.code === 0) {
    message.success(featured ? '已取消精选' : '已设为精选')
    fetchData()
    return
  }
  message.error(res.data.message || '操作失败')
}

const togglePublish = async (record: API.AppVO) => {
  if (!record.id) return
  const published = isPublished(record.isPublish)
  const res = await updateAppByAdmin({
    id: record.id,
    isPublish: published ? APP_NOT_PUBLISH : APP_PUBLISHED,
  })
  if (res.data.code === 0) {
    message.success(published ? '已取消公布' : '已设为公布')
    fetchData()
    return
  }
  message.error(res.data.message || '操作失败')
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.app-manage-page {
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
