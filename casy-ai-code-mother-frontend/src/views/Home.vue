<template>
  <div class="home-page">
    <section class="hero">
      <h1 class="hero__title">一句话，生成你的应用</h1>
      <p class="hero__sub">输入提示词，自动创建应用并开始代码生成</p>
      <div class="hero__panel">
        <a-textarea
          v-model:value="initPrompt"
          :maxlength="500"
          :rows="4"
          placeholder="例如：做一个个人博客，包含首页、文章列表和文章详情页"
          show-count
        />
        <div class="hero__actions">
          <a-space>
            <a-select
              v-model:value="codeGenType"
              :options="codeGenTypeOptions"
              style="width: 150px"
            />
            <a-select v-model:value="modelType" :options="modelTypeOptions" style="width: 180px" />
          </a-space>
          <a-button
            :loading="creating"
            size="large"
            style="margin-right: -50px; margin-top: 10px"
            type="primary"
            @click="createAppByPrompt"
          >
            创建并开始对话
          </a-button>
        </div>
      </div>
    </section>

    <section v-if="isLogin" class="section-card">
      <div class="section-card__head">
        <h2>我的应用</h2>
        <a-space>
          <a-input-search
            v-model:value="mySearchName"
            allow-clear
            placeholder="按名称搜索"
            style="width: 260px"
            @search="loadMyApps(1)"
          />
          <a-button @click="loadMyApps(1)">刷新</a-button>
        </a-space>
      </div>
      <a-row :gutter="[16, 16]">
        <a-col v-for="app in myApps" :key="app.id" :lg="8" :sm="12" :xs="24">
          <a-card :bordered="false" class="app-card">
            <template #cover>
              <div class="app-card__cover">
                <img v-if="app.cover" :src="app.cover" alt="cover" />
                <span v-else>{{ app.appName || '未命名应用' }}</span>
              </div>
            </template>
            <a-card-meta :title="app.appName || '未命名应用'">
              <template #description>
                <div class="app-card__meta">
                  <span>{{ app.codeGenType || '-' }}</span>
                  <span>{{ formatDate(app.updateTime) }}</span>
                </div>
              </template>
            </a-card-meta>
            <div class="app-card__ops">
              <a-button type="link" @click="goChat(app.id)">继续对话</a-button>
              <a-button type="link" @click="goEdit(app.id)">编辑</a-button>
              <a-popconfirm title="确认删除该应用？" @confirm="doDelete(app.id)">
                <a-button danger type="link">删除</a-button>
              </a-popconfirm>
            </div>
          </a-card>
        </a-col>
      </a-row>
      <a-empty v-if="myApps.length === 0" />
      <div class="section-card__pagination">
        <a-pagination
          :current="myPageNum"
          :page-size="myPageSize"
          :page-size-options="['8', '12', '20']"
          :total="myTotal"
          show-size-changer
          @change="loadMyApps"
        />
      </div>
    </section>

    <section class="section-card">
      <div class="section-card__head">
        <h2>精选应用</h2>
        <a-space>
          <a-input-search
            v-model:value="goodSearchName"
            allow-clear
            placeholder="按名称搜索"
            style="width: 260px"
            @search="loadGoodApps(1)"
          />
          <a-button @click="loadGoodApps(1)">刷新</a-button>
        </a-space>
      </div>
      <a-row :gutter="[16, 16]">
        <a-col v-for="app in goodApps" :key="app.id" :lg="8" :sm="12" :xs="24">
          <a-card :bordered="false" class="app-card">
            <template #cover>
              <div class="app-card__cover">
                <img v-if="app.cover" :src="app.cover" alt="cover" />
                <span v-else>{{ app.appName || '未命名应用' }}</span>
              </div>
            </template>
            <a-card-meta :title="app.appName || '未命名应用'">
              <template #description>
                <div class="app-card__meta">
                  <span>作者：{{ app.user?.userName || '-' }}</span>
                  <span>{{ formatDate(app.updateTime) }}</span>
                </div>
              </template>
            </a-card-meta>
            <div class="app-card__ops">
              <a-button type="link" @click="goChat(app.id)">查看详情</a-button>
            </div>
          </a-card>
        </a-col>
      </a-row>
      <a-empty v-if="goodApps.length === 0" />
      <div class="section-card__pagination">
        <a-pagination
          :current="goodPageNum"
          :page-size="goodPageSize"
          :page-size-options="['8', '12', '20']"
          :total="goodTotal"
          show-size-changer
          @change="loadGoodApps"
        />
      </div>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { useRouter } from 'vue-router'
import { addApp, deleteApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const isLogin = computed(() => Boolean(loginUserStore.loginUser.id))
const creating = ref(false)
const initPrompt = ref('')
const codeGenType = ref('multi_file')
const modelType = ref('deepseek-v4-flash')

const codeGenTypeOptions = [
  { value: 'multi_file', label: '多文件模式' },
  { value: 'html', label: 'HTML 模式' },
]
const modelTypeOptions = [
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { value: 'gpt-5.5', label: 'GPT 5.5' },
]

const myApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const myPageNum = ref(1)
const myPageSize = ref(8)
const mySearchName = ref('')

const goodApps = ref<API.AppVO[]>([])
const goodTotal = ref(0)
const goodPageNum = ref(1)
const goodPageSize = ref(8)
const goodSearchName = ref('')

const trimmedPrompt = computed(() => initPrompt.value.trim())

const formatDate = (time?: string) => {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

const createAppByPrompt = async () => {
  if (!trimmedPrompt.value) {
    message.warning('请输入提示词')
    return
  }
  creating.value = true
  try {
    const res = await addApp({
      initPrompt: trimmedPrompt.value,
      appName: trimmedPrompt.value.slice(0, 12),
      codeGenType: codeGenType.value,
      modelType: modelType.value,
    })
    console.log("res.data.data", res.data.data)
    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      await router.push({
        path: `/app/chat/${res.data.data}`,
        query: {
          initPrompt: trimmedPrompt.value,
          autoStart: '1',
          modelType: modelType.value,
        },
      })
      initPrompt.value = ''
      return
    }
    message.error(res.data.message || '创建失败')
  } finally {
    creating.value = false
  }
}

const loadMyApps = async (page = 1, pageSize = myPageSize.value) => {
  myPageNum.value = page
  myPageSize.value = pageSize
  const res = await listMyAppVoByPage({
    pageNum: myPageNum.value,
    pageSize: myPageSize.value,
    appName: mySearchName.value || undefined,
    sortField: 'update_time',
    sortOrder: 'descend',
  })
  console.log("我的应用：", res.data.data);
  if (res.data.code === 0 && res.data.data) {
    myApps.value = res.data.data.records ?? []
    myTotal.value = res.data.data.totalRow ?? 0
  }
}

const loadGoodApps = async (page = 1, pageSize = goodPageSize.value) => {
  goodPageNum.value = page
  goodPageSize.value = pageSize
  const res = await listGoodAppVoByPage({
    pageNum: goodPageNum.value,
    pageSize: goodPageSize.value,
    appName: goodSearchName.value || undefined,
    sortField: 'priority',
    sortOrder: 'descend',
  })
  if (res.data.code === 0 && res.data.data) {
    goodApps.value = res.data.data.records ?? []
    goodTotal.value = res.data.data.totalRow ?? 0
  }
}

const doDelete = async (id?: string) => {
  if (!id) return
  const res = await deleteApp({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    await loadMyApps(myPageNum.value, myPageSize.value)
  } else {
    message.error(res.data.message || '删除失败')
  }
}

const goChat = (id?: string) => {
  if (!id) return
  router.push(`/app/chat/${id}`)
}

const goEdit = (id?: string) => {
  if (!id) return
  router.push(`/app/edit/${id}`)
}

onMounted(() => {
  if (isLogin.value) {
    loadMyApps()
    loadGoodApps()
  }
})
</script>

<style scoped>
.home-page {
  min-height: calc(100vh - 140px);
  padding: 8px 8px 24px;
  background: var(--hero-bg);
  color: var(--text-main);
  border-radius: 12px;
}

.hero {
  text-align: center;
  margin: 8px auto 22px;
  max-width: 1100px;
}

.hero__title {
  margin-top: 50px;
  font-size: 42px;
  font-weight: 700;
  color: var(--text-main);
}

.hero__sub {
  margin: 14px 0 24px;
  color: var(--text-sub);
  font-size: 16px;
}

.hero__panel {
  border: 1px solid var(--border-color);
  border-radius: 20px;
  padding: 16px;
  background: var(--bg-card);
  backdrop-filter: blur(10px);
}

.hero__actions {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.section-card {
  max-width: 1200px;
  margin: 0 auto 18px;
  border: 1px solid var(--border-color);
  border-radius: 18px;
  padding: 18px;
  background: var(--bg-card);
}

.section-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.section-card__head h2 {
  margin: 0;
  color: var(--text-main);
}

.app-card {
  height: 100%;
  border-radius: 14px;
  background: transparent;
  border: 1px solid var(--border-color);
}

:deep(.app-card .ant-card-body) {
  padding: 14px;
}

.app-card__cover {
  height: 140px;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(22, 119, 255, 0.35), rgba(17, 171, 132, 0.22));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-main);
  font-weight: 600;
}

.app-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.app-card__ops {
  margin-top: 8px;
}

.section-card__pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .hero__title {
    font-size: 30px;
  }

  .hero__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .section-card__head {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
}
</style>
