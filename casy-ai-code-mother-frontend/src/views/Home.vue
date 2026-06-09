<template>
  <div class="home-page">
    <section class="hero">
      <h1 class="hero__title">一句话，生成你的应用</h1>
      <p class="hero__sub">与 AI 对话轻松创建应用和网站</p>
      <div class="hero__panel">
        <a-textarea
          v-model:value="initPrompt"
          :maxlength="500"
          :rows="4"
          placeholder="例如：做一个个人博客，包含首页、文章列表和文章详情页"
          style="border: none; box-shadow: none"
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
            class="generate-fab"
            shape="circle"
            type="primary"
            @click="createAppByPrompt"
          >
            ↑
          </a-button>
        </div>
      </div>
      <div class="hero__tags">
        <a-tag
          v-for="tag in quickTags"
          :key="tag"
          class="home-tag hero__tag--clickable"
          @click="handleTagClick(tag)"
        >
          {{ tag }}
        </a-tag>
      </div>
    </section>

    <section class="showcase">
      <div v-if="isLogin" class="showcase-block">
        <div class="showcase-block__head">
          <h2>我的作品</h2>
          <a-space>
            <a-input-search
              v-model:value="mySearchName"
              allow-clear
              placeholder="按名称搜索"
              style="width: 220px"
              @search="loadMyApps(1)"
            />
            <a-button @click="loadMyApps(1)">刷新</a-button>
          </a-space>
        </div>
        <div class="my-work-list">
          <div v-for="app in myApps" :key="app.id" class="my-work-card">
            <div class="my-work-card__cover" @click="goChat(toAppId(app.id))">
              <img v-if="app.cover" :src="app.cover" alt="cover" />
              <span v-else>{{ app.appName || '未命名应用' }}</span>
              <div class="card-hover-mask">
                <a-button
                  class="card-hover-button"
                  size="large"
                  @click.stop="goChat(toAppId(app.id))"
                >
                  查看对话
                </a-button>
              </div>
            </div>
            <div class="my-work-card__title">{{ app.appName || '未命名应用' }}</div>
            <div class="my-work-card__meta">{{ formatDate(app.updateTime) }}</div>
          </div>
        </div>
        <a-empty v-if="myApps.length === 0" description="还没有创建应用" />
        <div class="showcase__pagination">
          <a-pagination
            :current="myPageNum"
            :page-size="myPageSize"
            :page-size-options="['8', '12', '20']"
            :total="myTotal"
            size="small"
            @change="loadMyApps"
          />
        </div>
      </div>
      <div class="showcase-block">
        <div class="showcase-block__head">
          <h2>精选案例</h2>
          <a-space>
            <a-input-search
              v-model:value="goodSearchName"
              allow-clear
              placeholder="按名称搜索"
              style="width: 220px"
              @search="loadGoodApps(1)"
            />
            <a-button @click="loadGoodApps(1)">刷新</a-button>
          </a-space>
        </div>
        <div class="square-tabs">
          <a-tag class="home-tag home-tag--active">全部</a-tag>
          <a-tag class="home-tag">网站</a-tag>
          <a-tag class="home-tag">工具</a-tag>
          <a-tag class="home-tag">博客</a-tag>
          <a-tag class="home-tag">管理后台</a-tag>
        </div>
        <div class="my-work-list">
          <div
            v-for="app in goodApps"
            :key="app.id"
            class="square-card"
            @click="goChat(toAppId(app.id))"
          >
            <div class="square-card__cover">
              <img v-if="app.cover" :src="app.cover" alt="cover" />
              <span v-else>{{ app.appName || '未命名应用' }}</span>
              <div class="card-hover-mask">
                <a-button
                  class="card-hover-button"
                  size="large"
                  @click.stop="goChat(toAppId(app.id))"
                >
                  预览
                </a-button>
              </div>
            </div>
            <div class="square-card__body">
              <div class="square-card__title">{{ app.appName || '未命名应用' }}</div>
              <div class="square-card__desc">
                {{ app.user?.userName || 'NoCode 官方' }} {{ formatDate(app.updateTime) }}
              </div>
            </div>
          </div>
        </div>
        <a-empty v-if="goodApps.length === 0" description="暂无精选应用" />
        <div class="showcase__pagination">
          <a-pagination
            :current="goodPageNum"
            :page-size="goodPageSize"
            :page-size-options="['8', '12', '20']"
            :total="goodTotal"
            size="small"
            @change="loadGoodApps"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { useRouter } from 'vue-router'
import { addApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
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
  { value: 'vue_project', label: 'Vue 工程模式' },
]
const modelTypeOptions = [
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro' },
  { value: 'gpt-5.5', label: 'GPT 5.5' },
  { value: 'claude-sonnet-4-6', label: 'Claude Sonnet 4.6' },
]

const quickTags = ['波普风电商页面', '企业网站', '电商运营后台', '暗黑话题社区']

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
const toAppId = (id?: string) => {
  if (id === undefined || id === null || id === '') return undefined
  return String(id)
}

const formatDate = (time?: string) => {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

const handleTagClick = (tag: string) => {
  initPrompt.value = tag
  createAppByPrompt()
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
  if (!isLogin.value) return
  myPageNum.value = page
  myPageSize.value = pageSize
  const res = await listMyAppVoByPage({
    pageNum: myPageNum.value,
    pageSize: myPageSize.value,
    appName: mySearchName.value || undefined,
    sortField: 'update_time',
    sortOrder: 'descend',
  })
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
const goChat = (id?: string) => {
  if (!id) return
  router.push(`/app/chat/${id}`)
}
onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  console.log("isLogin.value: ", isLogin.value);
  console.log("loginUserStore.loginUser.id: ", loginUserStore.loginUser.id);
  if (isLogin.value) {
    await Promise.all([loadMyApps(), loadGoodApps()])
  } else {
    await loadGoodApps()
  }
})
</script>

<style scoped>
.home-page {
  min-height: calc(100vh - 132px);
  padding: 24px 10px 88px;
  background: var(--hero-bg);
  color: var(--text-main);
  border-radius: 20px;
}

.hero {
  text-align: center;
  margin: 20px auto 30px;
  max-width: 860px;
}

.hero__title {
  margin-top: 12px;
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
  border-radius: 16px;
  padding: 14px;
  background: var(--bg-card);
  backdrop-filter: blur(10px);
}

.hero__actions {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.generate-fab {
  width: 36px !important;
  height: 36px !important;
  min-width: 36px !important;
  border: 1px solid var(--border-color) !important;
  background: rgba(127, 127, 127, 0.16) !important;
  color: var(--text-main) !important;
  box-shadow: none !important;
}

.generate-fab:hover,
.generate-fab:focus {
  background: rgba(127, 127, 127, 0.24) !important;
}

.hero__tags {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

:deep(.ant-tag.home-tag) {
  margin-inline-end: 0;
  background: var(--tag-bg) !important;
  border-color: var(--tag-border) !important;
  color: var(--tag-text) !important;
  cursor: default;
  transition: all 0.2s ease;
}

:deep(.ant-tag.home-tag--active) {
  background: var(--tag-bg-active) !important;
  border-color: var(--tag-bg-active) !important;
  color: var(--tag-text-active) !important;
}

.hero__tag--clickable {
  cursor: pointer;
}

.hero__tag--clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  border-color: var(--tag-bg-active);
}

.showcase {
  max-width: 1260px;
  margin: 0 auto 24px;
  border: 1px solid var(--border-color);
  border-radius: 24px;
  padding: 20px;
  background: var(--bg-card);
}

.showcase-block + .showcase-block {
  margin-top: 24px;
}

.showcase-block__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.showcase-block h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1;
}

.showcase-block--tip {
  padding: 8px 0;
}

.my-work-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.my-work-card {
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
}

.my-work-card__cover {
  position: relative;
  height: 180px;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(22, 119, 255, 0.32), rgba(17, 171, 132, 0.2));
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.my-work-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s ease;
}

.my-work-card__title {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--text-main);
}

.my-work-card__meta {
  color: var(--text-sub);
  font-size: 13px;
  margin-top: 4px;
}

.my-work-card:hover .my-work-card__cover img,
.square-card:hover .square-card__cover img {
  transform: scale(1.04);
}

.card-hover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 16px 10px;
  background: rgba(0, 0, 0, 0.18);
  opacity: 0;
  transition: opacity 0.22s ease;
}

.my-work-card__cover:hover .card-hover-mask,
.square-card__cover:hover .card-hover-mask {
  opacity: 1;
}

.card-hover-button {
  width: min(300px, calc(100% - 12px));
  height: 42px;
  border: none;
  border-radius: 8px;
  color: #1f2937;
  font-weight: 500;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.18);
}

.square-tabs {
  margin-bottom: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.square-tabs :deep(.home-tag) {
  margin-inline-end: 0;
}

.square-card {
  border-radius: 10px;
  cursor: pointer;
  height: 100%;
  background: transparent;
}

.square-card__cover {
  position: relative;
  height: 180px;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(22, 119, 255, 0.35), rgba(17, 171, 132, 0.22));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-main);
}

.square-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s ease;
}

.square-card__body {
  padding-top: 12px;
}

.square-card__title {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--text-main);
}

.square-card__desc {
  color: var(--text-sub);
  font-size: 13px;
  margin-top: 4px;
}

.showcase__pagination {
  margin-top: 14px;
  display: flex;
  justify-content: center;
}

@media (max-width: 768px) {
  .hero__title {
    font-size: 30px;
  }

  .hero__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .showcase-block__head {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .showcase {
    padding: 14px;
    border-radius: 16px;
  }

  .my-work-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
