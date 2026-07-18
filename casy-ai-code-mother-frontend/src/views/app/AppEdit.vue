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

        <a-form-item label="应用类型" name="appTypes">
          <a-select
            v-model:value="formState.appTypes"
            mode="multiple"
            :options="APP_TYPE_OPTIONS"
            placeholder="选择应用类型"
            style="width: 100%"
          >
            <template #tagRender="{ label, closable, onClose }">
              <a-tag :closable="closable" color="blue" @close="onClose">{{ label }}</a-tag>
            </template>
          </a-select>
        </a-form-item>

        <a-form-item label="是否公布" name="isPublish">
          <a-switch
            v-model:checked="isPublished"
            checked-children="公布"
            un-checked-children="不公布"
          />
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
            <a-button v-if="isAdmin" @click="goManage">返回</a-button>
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
import { APP_TYPE_OPTIONS } from '@/constant/appType'
import ROLE_ENUM, { APP_PUBLISHED, APP_NOT_PUBLISH } from '@/constant/constant'
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
  appTypes: [],
  isPublish: APP_NOT_PUBLISH,
})

const isPublished = computed({
  get: () => formState.isPublish === APP_PUBLISHED,
  set: (checked: boolean) => {
    formState.isPublish = checked ? APP_PUBLISHED : APP_NOT_PUBLISH
  },
})
const ownerId = ref<string | number>()

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
    formState.appTypes = app.appTypes ? [...app.appTypes] : []
    formState.isPublish = app.isPublish ?? APP_NOT_PUBLISH
    ownerId.value = app.userId
    if (!isAdmin.value && String(ownerId.value) !== String(loginUserStore.loginUser.id)) {
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
        appTypes: formState.appTypes,
        isPublish: formState.isPublish,
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
      appTypes: formState.appTypes,
      isPublish: formState.isPublish,
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

const goManage = () => {
  router.push('/app/manage')
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
