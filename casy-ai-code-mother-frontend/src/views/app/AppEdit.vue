<template>
  <div class="app-edit-page">
    <a-card :bordered="false" title="应用信息修改">
      <a-form
        :model="formState"
        :label-col="{ span: 4 }"
        :wrapper-col="{ span: 12 }"
        @finish="submitForm"
      >
        <a-form-item
          label="应用名称"
          name="appName"
          :rules="[{ required: true, message: '请输入应用名称' }]"
        >
          <a-input v-model:value="formState.appName" :maxlength="40" show-count />
        </a-form-item>

        <template v-if="isAdmin">
          <a-form-item label="封面地址" name="cover">
            <a-input v-model:value="formState.cover" />
          </a-form-item>
          <a-form-item label="优先级" name="priority">
            <a-input-number v-model:value="formState.priority" :min="0" style="width: 100%" />
          </a-form-item>
        </template>

        <a-form-item :wrapper-col="{ offset: 4, span: 12 }">
          <a-space>
            <a-button type="primary" html-type="submit" :loading="submitting">保存</a-button>
            <a-button @click="goChat">查看应用</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ROLE_ENUM from '@/constant/constant'
import { useLoginUserStore } from '@/stores/loginUser'
import { getAppVoById, updateApp, updateAppByAdmin } from '@/api/appController'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const appId = computed(() => {
  const id = route.params.id
  return (Array.isArray(id) ? id[0] : id) ?? ''
})
const submitting = ref(false)

const formState = reactive<API.AppAdminUpdateRequest>({
  id: appId.value,
  appName: '',
  cover: '',
  priority: 0,
})
const ownerId = ref<number>()

const isAdmin = computed(() => {
  const roles = loginUserStore.loginUser.userRole?.split(',').map((role) => role.trim()) ?? []
  return roles.includes(ROLE_ENUM.ADMIN)
})

const fetchData = async () => {
  const res = await getAppVoById({ id: appId.value })
  if (res.data.code === 0 && res.data.data) {
    const app = res.data.data
    formState.id = app.id
    formState.appName = app.appName || ''
    formState.cover = app.cover || ''
    formState.priority = app.priority ?? 0
    ownerId.value = app.userId
    if (!isAdmin.value && ownerId.value !== loginUserStore.loginUser.id) {
      message.error('无权编辑该应用')
      router.replace('/')
    }
    return
  }
  message.error(res.data.message || '获取应用失败')
}

const submitForm = async () => {
  submitting.value = true
  try {
    if (isAdmin.value) {
      const res = await updateAppByAdmin({
        id: appId.value,
        appName: formState.appName?.trim(),
        cover: formState.cover?.trim(),
        priority: formState.priority,
      })
      if (res.data.code === 0) {
        message.success('保存成功')
        return
      }
      message.error(res.data.message || '保存失败')
      return
    }
    const res = await updateApp({
      id: appId.value,
      appName: formState.appName?.trim(),
    })
    if (res.data.code === 0) {
      message.success('保存成功')
    } else {
      message.error(res.data.message || '保存失败')
    }
  } finally {
    submitting.value = false
  }
}

const goChat = () => {
  router.push(`/app/chat/${appId.value}`)
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.app-edit-page {
  max-width: 980px;
  margin: 0 auto;
  padding-top: 12px;
}
</style>
