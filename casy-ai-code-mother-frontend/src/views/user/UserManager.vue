<template>
  <div id="userManagePage">
    <a-form :model="searchParams" layout="inline" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" />
      </a-form-item>
      <a-form-item>
        <a-button html-type="submit" type="primary" :loading="loading">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <a-table
      row-key="id"
      :columns="columns"
      :data-source="data"
      :loading="loading"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <!--
          使用ExternalImage，有的网站开启了防盗链。地址栏直接打开不带 Referer，嵌入页面会带上你站点 Referer，CDN 拒绝返回图片。
          已修复： 头像加载加 referrerpolicy="no-referrer"，不发送 Referer 即可正常显示。
          -->
          <ExternalAvatar v-if="record.userAvatar" :src="record.userAvatar" :size="48" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <a-tag v-if="record.userRole === 'admin'" color="green">管理员</a-tag>
          <a-tag v-else color="blue">普通用户</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确认删除该用户？" @confirm="doDelete(record.id)">
              <a-button type="link" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="editVisible"
      title="编辑用户"
      :confirm-loading="submitting"
      @ok="submitEdit"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="用户名" required>
          <a-input v-model:value="editForm.userName" :maxlength="40" show-count />
        </a-form-item>
        <a-form-item label="头像地址">
          <a-input v-model:value="editForm.userAvatar" />
        </a-form-item>
        <a-form-item label="简介">
          <a-textarea v-model:value="editForm.userProfile" :rows="3" :maxlength="200" show-count />
        </a-form-item>
        <a-form-item label="用户角色">
          <a-select v-model:value="editForm.userRole" :options="ROLE_OPTIONS" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteUser, listUserVoByPage, updateUser } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import ROLE_ENUM from '@/constant/constant'
import ExternalAvatar from '@/components/ExternalAvatar.vue'

const ROLE_OPTIONS = [
  { value: ROLE_ENUM.USER, label: '普通用户' },
  { value: ROLE_ENUM.ADMIN, label: '管理员' },
]

const columns = [
  { title: 'id', dataIndex: 'id' },
  { title: '账号', dataIndex: 'userAccount' },
  { title: '用户名', dataIndex: 'userName' },
  { title: '头像', dataIndex: 'userAvatar' },
  { title: '简介', dataIndex: 'userProfile' },
  { title: '用户角色', dataIndex: 'userRole' },
  { title: '创建时间', dataIndex: 'createTime' },
  { title: '操作', key: 'action' },
]

const data = ref<API.UserVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const editVisible = ref(false)
const editForm = reactive<API.UserUpdateRequest>({
  id: undefined,
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: ROLE_ENUM.USER,
})

const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (all: number) => `共 ${all} 条`,
}))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPage({ ...searchParams })
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

const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const openEdit = (record: API.UserVO) => {
  editForm.id = record.id
  editForm.userName = record.userName || ''
  editForm.userAvatar = record.userAvatar || ''
  editForm.userProfile = record.userProfile || ''
  editForm.userRole = record.userRole || ROLE_ENUM.USER
  editVisible.value = true
}

const submitEdit = async () => {
  if (!editForm.userName?.trim()) {
    message.warning('请输入用户名')
    return
  }
  submitting.value = true
  try {
    const res = await updateUser({
      id: editForm.id,
      userName: editForm.userName.trim(),
      userAvatar: editForm.userAvatar?.trim(),
      userProfile: editForm.userProfile?.trim(),
      userRole: editForm.userRole,
    })
    if (res.data.code === 0) {
      message.success('修改成功')
      editVisible.value = false
      fetchData()
      return
    }
    message.error(res.data.message || '修改失败')
  } finally {
    submitting.value = false
  }
}

const doDelete = async (id?: number) => {
  if (!id) return
  const res = await deleteUser({ id: String(id) })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
    return
  }
  message.error(res.data.message || '删除失败')
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#userManagePage {
  padding: 14px;
}

:deep(.ant-form) {
  margin-bottom: 10px;
}

:deep(.ant-table-wrapper) {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}
</style>
