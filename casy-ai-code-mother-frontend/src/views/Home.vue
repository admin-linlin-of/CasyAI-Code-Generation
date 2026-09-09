<template>
  <div class="home-page">
    <!-- ============ 氛围背景层 ============ -->
    <div class="bg" aria-hidden="true">
      <span class="bg__orb bg__orb--blue"></span>
      <span class="bg__orb bg__orb--cyan"></span>
      <span class="bg__orb bg__orb--violet"></span>
      <span class="bg__grid"></span>
      <span class="bg__noise"></span>
    </div>

    <div class="home-container">
      <!-- ============ Hero ============ -->
      <section class="hero">
        <div class="hero__badge">
          <span class="hero__badge-dot"></span>
          AI 零代码应用生成平台
        </div>

        <h1 class="hero__title">
          一句话，
          <span class="hero__title-accent">生成你的应用</span>
        </h1>
        <p class="hero__sub">无需写代码，与 AI 对话即可快速打造网页、博客与管理系统</p>

        <div class="hero__panel">
          <a-textarea
            v-model:value="initPrompt"
            :maxlength="500"
            :rows="4"
            class="hero__textarea"
            placeholder="例如：做一个个人博客，包含首页、文章列表和文章详情页"
          />
          <div class="hero__actions">
            <a-space :size="8" wrap class="hero__selects">
              <a-select
                v-model:value="codeGenType"
                :bordered="false"
                :options="codeGenTypeOptions"
                class="hero-select"
                popup-class-name="hero-select-dropdown"
                style="width: 158px"
              />
              <a-select
                v-model:value="modelType"
                :bordered="false"
                class="hero-select"
                popup-class-name="hero-select-dropdown"
                style="width: 158px"
              >
                <a-select-option
                  v-for="opt in modelTypeOptions"
                  :key="opt.value || 'auto'"
                  :value="opt.value"
                  :disabled="opt.disabled"
                >
                  <a-tooltip :title="opt.title">
                    <span>{{ opt.label }}</span>
                  </a-tooltip>
                </a-select-option>
              </a-select>
              <a-select
                v-model:value="agentMode"
                :bordered="false"
                :options="agentModeOptions"
                class="hero-select"
                popup-class-name="hero-select-dropdown"
                style="width: 150px"
              />
            </a-space>
            <a-tooltip title="生成应用">
              <a-button
                :class="{ 'generate-fab--ready': Boolean(trimmedPrompt) }"
                :loading="creating"
                class="generate-fab"
                shape="circle"
                type="primary"
                @click="createAppByPrompt"
              >
                <template #icon>
                  <ArrowUpOutlined />
                </template>
              </a-button>
            </a-tooltip>
          </div>
        </div>

        <div class="hero__tags">
          <button
            v-for="tag in quickTags"
            :key="tag.label"
            class="home-chip"
            type="button"
            @click="handleTagClick(tag.prompt)"
          >
            {{ tag.label }}
          </button>
        </div>
        <p class="hero__hint">点击上方示例，或输入你的想法后点击右侧按钮开始生成</p>
      </section>

      <!-- ============ 作品 / 精选 ============ -->
      <section class="showcase">
        <!-- 我的作品 -->
        <div v-if="isLogin" class="showcase-block">
          <div class="showcase-block__head">
            <div class="showcase-block__intro">
              <div class="eyebrow">MY APPS</div>
              <h2>我的作品</h2>
              <p>在这里回到每一个应用，继续对话、打磨并发布你的想法</p>
            </div>
            <a-space :size="10" class="showcase-block__toolbar">
              <a-input-search
                v-model:value="mySearchName"
                allow-clear
                class="showcase-search"
                placeholder="按名称搜索"
                @search="loadMyApps(1)"
              />
              <a-button class="ghost-btn" @click="loadMyApps(1)">刷新</a-button>
            </a-space>
          </div>

          <div class="app-grid">
            <div v-for="app in myApps" :key="app.id" class="app-card">
              <div class="app-card__cover" @click="goChat(toAppId(app.id))">
                <img v-if="app.cover" :src="app.cover" :alt="app.appName || '作品'" />
                <span v-else class="app-card__placeholder">
                  {{ displayAppName(app.appName, app.initPrompt).slice(0, 1) }}
                </span>
                <span v-if="app.appTypes?.length" class="app-card__tag">{{ app.appTypes[0] }}</span>
                <div class="app-card__mask">
                  <a-button class="card-hover-button" @click.stop="goChat(toAppId(app.id))">
                    查看对话
                  </a-button>
                </div>
              </div>
              <div class="app-card__body">
                <div class="app-card__title" :title="displayAppName(app.appName, app.initPrompt)">
                  {{ displayAppName(app.appName, app.initPrompt) }}
                </div>
                <div class="app-card__meta">
                  <span>{{ app.user?.userName || '我' }}</span>
                  <span class="app-card__dot">·</span>
                  <span>{{ formatDate(app.updateTime) }}</span>
                </div>
              </div>
            </div>
          </div>

          <a-empty v-if="myApps.length === 0" description="还没有创建应用" />
          <div v-if="myTotal > 0" class="showcase__pagination">
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

        <!-- 精选案例 -->
        <div class="showcase-block">
          <div class="showcase-block__head">
            <div class="showcase-block__intro">
              <div class="eyebrow eyebrow--alt">FEATURED</div>
              <h2>精选案例</h2>
              <p>看看别人用一句话生成了什么，点击即可预览并在此基础上继续创作</p>
            </div>
            <a-space :size="10" class="showcase-block__toolbar">
              <a-input-search
                v-model:value="goodSearchName"
                allow-clear
                class="showcase-search"
                placeholder="按名称搜索"
                @search="loadGoodApps(1)"
              />
              <a-button class="ghost-btn" @click="loadGoodApps(1)">刷新</a-button>
            </a-space>
          </div>

          <div class="showcase-block__filters">
            <span class="home-pill home-pill--active">全部</span>
            <span class="home-pill">网站</span>
            <span class="home-pill">工具</span>
            <span class="home-pill">博客</span>
            <span class="home-pill">管理后台</span>
          </div>

          <div class="app-grid">
            <div
              v-for="app in goodApps"
              :key="app.id"
              class="app-card"
              @click="goChat(toAppId(app.id))"
            >
              <div class="app-card__cover">
                <img v-if="app.cover" :src="app.cover" :alt="app.appName || '案例'" />
                <span v-else class="app-card__placeholder">
                  {{ displayAppName(app.appName, app.initPrompt).slice(0, 1) }}
                </span>
                <span v-if="app.appTypes?.length" class="app-card__tag">{{ app.appTypes[0] }}</span>
                <div class="app-card__mask">
                  <a-button class="card-hover-button" @click.stop="goChat(toAppId(app.id))">
                    预览
                  </a-button>
                </div>
              </div>
              <div class="app-card__body">
                <div class="app-card__title" :title="displayAppName(app.appName, app.initPrompt)">
                  {{ displayAppName(app.appName, app.initPrompt) }}
                </div>
                <div class="app-card__meta">
                  <span>{{ app.user?.userName || 'NoCode 官方' }}</span>
                  <span class="app-card__dot">·</span>
                  <span>{{ formatDate(app.updateTime) }}</span>
                </div>
              </div>
            </div>
          </div>

          <a-empty v-if="goodApps.length === 0" description="暂无精选应用" />
          <div v-if="goodTotal > 0" class="showcase__pagination">
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
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ArrowUpOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useRouter } from 'vue-router'
import { addApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { useAiModelOptions } from '@/composables/useAiModelOptions'
import { deriveAppName, displayAppName } from '@/utils/appName'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const isLogin = computed(() => Boolean(loginUserStore.loginUser.id))
const creating = ref(false)
const initPrompt = ref('')
const codeGenType = ref('')
const modelType = ref('')
const agentMode = ref('0')

const codeGenTypeOptions = [
  { value: '', label: '自动选择生成类型' },
  { value: 'multi_file', label: '多文件模式' },
  { value: 'html', label: 'HTML 模式' },
  { value: 'vue_project', label: 'Vue 工程模式' },
]
const { modelTypeOptions, loadAiModels } = useAiModelOptions()
const agentModeOptions = [
  { value: '0', label: '传统生成' },
  { value: '1', label: 'Agent 工作流' },
]

const quickTags = [
  {
    label: '波普风电商页面',
    prompt:
      '做一个波普风潮流电商品牌官网。视觉：亮黄、洋红、纯黑强撞色，粗描边、半调网点、漫画爆炸框和手写标语。页面含顶部导航（Logo、新品、系列、关于、购物袋）、全屏海报轮播、当季热卖 6 件商品卡片（图片、名称、价格、「加入购物袋」）、品牌故事、页脚订阅。点击商品弹出简易详情浮层，购物袋可加减数量。必须响应式，图片用占位图，交互用原生 JS，不要登录和后台。',
  },
  {
    label: '企业网站',
    prompt:
      '做一个面向 B 端的科技公司官网，深空蓝配电光青，干净现代。包含顶部导航（产品、解决方案、客户案例、关于我们、联系我们）、Hero 大标题与「预约演示」按钮、三块核心产品介绍、客户 logo 墙、三则案例卡片、团队简介、底部联系表单（提交后前端提示成功）。滚动有轻微入场动画，必须响应式，不要后台系统。',
  },
  {
    label: '电商运营后台',
    prompt:
      '用 Vue3 做一个电商运营后台，侧边栏+顶栏布局。页面：登录页（任意账号可进入）、数据概览（今日订单、销售额、待发货、退款，以及用 CSS 模拟的简易柱状图）、商品管理（表格含图、名称、价格、库存、上下架，支持搜索和新增弹窗）、订单列表（状态筛选：待付款/待发货/已完成）。全部 mock 数据，hash 路由。风格浅蓝灰企业风，表格清晰，不要真实后端。',
  },
  {
    label: '暗黑话题社区',
    prompt:
      '用 Vue3 做一个暗黑风格话题社区。深黑背景、紫红强调色、卡片微光边。页面：信息流首页（帖子卡片含头像、标题、摘要、点赞与评论数）、帖子详情（正文、评论列表、发表评论）、话题广场（标签云，点击筛选）、个人主页（我的帖子）。顶部导航含搜索。mock 至少 10 条帖子。hash 路由，无需登录后端。',
  },
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
const toAppId = (id?: string | number) => {
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
      appName: deriveAppName(trimmedPrompt.value),
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
          agent: agentMode.value,
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
  console.log('isLogin.value: ', isLogin.value)
  console.log('loginUserStore.loginUser.id: ', loginUserStore.loginUser.id)
  if (isLogin.value) {
    await Promise.all([loadMyApps(), loadGoodApps(), loadAiModels()])
  } else {
    await loadGoodApps()
  }
})
</script>

<style scoped>
/* ================= 主题局部变量 ================= */
.home-page {
  --accent: #1677ff;
  --accent-2: #06b6d4;
  --accent-3: #8b5cf6;
  --accent-grad: linear-gradient(135deg, #1677ff 0%, #00b8d9 100%);
  --glass-bg: rgba(255, 255, 255, 0.78);
  --glass-border: rgba(255, 255, 255, 0.9);
  --panel-bg: rgba(255, 255, 255, 0.72);
  --card-bg: rgba(255, 255, 255, 0.9);
  --chip-bg: rgba(22, 119, 255, 0.08);
  --chip-text: #33507a;
  --mask-bg: rgba(10, 28, 60, 0.35);
  --shadow-soft: 0 18px 50px -18px rgba(30, 80, 200, 0.22);
  --shadow-hover: 0 26px 60px -22px rgba(30, 90, 220, 0.34);
  --orb-a: rgba(22, 119, 255, 0.5);
  --orb-b: rgba(6, 182, 212, 0.42);
  --orb-c: rgba(139, 92, 246, 0.4);
  --grid-line: rgba(100, 116, 139, 0.09);
}

html[data-theme='dark'] .home-page {
  --accent: #4d8dff;
  --accent-2: #2dd4ff;
  --accent-3: #a78bfa;
  --accent-grad: linear-gradient(135deg, #2a7cff 0%, #00a6ff 100%);
  --glass-bg: rgba(10, 20, 46, 0.5);
  --glass-border: rgba(255, 255, 255, 0.08);
  --panel-bg: rgba(11, 24, 56, 0.62);
  --card-bg: rgba(11, 24, 48, 0.78);
  --chip-bg: rgba(96, 129, 255, 0.16);
  --chip-text: #b7c8f2;
  --mask-bg: rgba(2, 8, 22, 0.5);
  --shadow-soft: 0 22px 60px -18px rgba(0, 0, 0, 0.55);
  --shadow-hover: 0 30px 70px -20px rgba(0, 80, 255, 0.35);
  --orb-a: rgba(0, 120, 255, 0.5);
  --orb-b: rgba(0, 200, 255, 0.32);
  --orb-c: rgba(150, 86, 255, 0.4);
  --grid-line: rgba(140, 170, 255, 0.07);
}

/* ================= 页面骨架 ================= */
.home-page {
  position: relative;
  min-height: calc(100vh - 88px);
  overflow: hidden;
  padding: 28px 16px 96px;
  background: var(--bg-page);
  color: var(--text-main);
}

.home-container {
  position: relative;
  z-index: 1;
  max-width: 1280px;
  margin: 0 auto;
}

/* ================= 氛围背景层 ================= */
.bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.bg__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.7;
  will-change: transform;
}

.bg__orb--blue {
  width: 560px;
  height: 560px;
  top: -160px;
  left: -120px;
  background: radial-gradient(circle, var(--orb-a), transparent 66%);
  animation: orb-drift 18s ease-in-out infinite alternate;
}

.bg__orb--cyan {
  width: 460px;
  height: 460px;
  top: 120px;
  right: -140px;
  background: radial-gradient(circle, var(--orb-b), transparent 66%);
  animation: orb-drift 22s ease-in-out -6s infinite alternate-reverse;
}

.bg__orb--violet {
  width: 520px;
  height: 520px;
  top: 46%;
  left: 42%;
  background: radial-gradient(circle, var(--orb-c), transparent 68%);
  animation: orb-drift 26s ease-in-out -12s infinite alternate;
}

.bg__grid {
  position: absolute;
  inset: -2px;
  background-image:
    linear-gradient(var(--grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid-line) 1px, transparent 1px);
  background-size: 54px 54px;
  -webkit-mask-image: radial-gradient(ellipse 90% 62% at 50% 0%, #000 30%, transparent 78%);
  mask-image: radial-gradient(ellipse 90% 62% at 50% 0%, #000 30%, transparent 78%);
}

.bg__noise {
  position: absolute;
  inset: 0;
  opacity: 0.5;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  background-size: 160px 160px;
  opacity: 0.035;
}

@keyframes orb-drift {
  0% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  100% {
    transform: translate3d(60px, 40px, 0) scale(1.12);
  }
}

/* ================= Hero ================= */
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: clamp(36px, 9vh, 96px) 0 44px;
}

.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 16px;
  margin-bottom: 22px;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: var(--chip-text);
  box-shadow: var(--shadow-soft);
  animation: fade-up 0.7s ease both;
}

.hero__badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-grad);
  box-shadow: 0 0 10px var(--accent);
}

.hero__title {
  margin: 0;
  font-size: clamp(36px, 5.6vw, 60px);
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: 0.5px;
  color: var(--text-main);
  animation: fade-up 0.7s 0.06s ease both;
}

.hero__title-accent {
  background: linear-gradient(96deg, var(--accent) 0%, var(--accent-2) 55%, var(--accent-3) 110%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}

html[data-theme='dark'] .hero__title-accent {
  filter: drop-shadow(0 0 22px rgba(0, 170, 255, 0.35));
}

.hero__sub {
  margin: 16px 0 30px;
  max-width: 620px;
  font-size: clamp(15px, 1.8vw, 18px);
  line-height: 1.7;
  color: var(--text-sub);
  animation: fade-up 0.7s 0.12s ease both;
}

/* ----- 生成面板 ----- */
.hero__panel {
  width: min(760px, 100%);
  padding: 8px;
  border: 1px solid var(--glass-border);
  background: var(--panel-bg);
  backdrop-filter: blur(18px) saturate(1.4);
  border-radius: 22px;
  box-shadow: var(--shadow-soft);
  text-align: left;
  animation: fade-up 0.7s 0.18s ease both;
}

.hero__panel:focus-within {
  border-color: color-mix(in srgb, var(--accent) 55%, transparent);
  box-shadow:
    0 0 0 4px color-mix(in srgb, var(--accent) 16%, transparent),
    var(--shadow-soft);
}

:deep(.hero__textarea.ant-input) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  color: var(--text-main);
  font-size: 16px;
  line-height: 1.7;
  padding: 8px 10px 4px;
  resize: none;
}

:deep(.hero__textarea.ant-input::placeholder) {
  color: var(--text-sub);
  opacity: 0.85;
}

.hero__actions {
  margin-top: 4px;
  padding: 6px 6px 2px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid color-mix(in srgb, var(--border-color) 60%, transparent);
}

/* 面板内的模式选择 pills */
:deep(.hero-select .ant-select-selector) {
  height: 34px !important;
  padding: 0 12px !important;
  border-radius: 999px !important;
  background: var(--chip-bg) !important;
  box-shadow: none !important;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}

:deep(.hero-select .ant-select-selection-item),
:deep(.hero-select .ant-select-selection-placeholder) {
  line-height: 34px !important;
  font-size: 13px;
  color: var(--chip-text) !important;
}

:deep(.hero-select .ant-select-arrow) {
  color: var(--chip-text);
}

:deep(.hero-select:hover .ant-select-selector) {
  border-color: color-mix(in srgb, var(--accent) 40%, transparent) !important;
}

html[data-theme='dark'] :deep(.hero-select .ant-select-selector) {
  background: var(--chip-bg) !important;
}

.generate-fab {
  width: 40px !important;
  height: 40px !important;
  min-width: 40px !important;
  border: none !important;
  background: var(--accent-grad) !important;
  color: #fff !important;
  opacity: 0.35;
  box-shadow: 0 8px 18px -6px color-mix(in srgb, var(--accent) 60%, transparent) !important;
  transition:
    opacity 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.generate-fab--ready {
  opacity: 1;
}

.generate-fab:hover,
.generate-fab:focus {
  opacity: 0.94;
  transform: translateY(-1px) scale(1.04);
  box-shadow: 0 12px 24px -6px color-mix(in srgb, var(--accent) 65%, transparent) !important;
}

/* 示例 chips */
.hero__tags {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  animation: fade-up 0.7s 0.24s ease both;
}

.home-chip {
  padding: 7px 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--chip-bg);
  color: var(--chip-text);
  font-size: 13px;
  line-height: 1.4;
  cursor: pointer;
  transition: all 0.2s ease;
}

.home-chip:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--accent) 40%, transparent);
  color: var(--text-main);
  box-shadow: 0 8px 18px -10px color-mix(in srgb, var(--accent) 45%, transparent);
}

.hero__hint {
  margin: 14px 0 0;
  font-size: 12px;
  letter-spacing: 0.3px;
  color: var(--text-sub);
  opacity: 0.75;
  animation: fade-up 0.7s 0.3s ease both;
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ================= 展示区 ================= */
.showcase {
  padding: 36px;
  border: 1px solid var(--glass-border);
  border-radius: 28px;
  background: var(--glass-bg);
  backdrop-filter: blur(16px);
  box-shadow: var(--shadow-soft);
}

.showcase-block + .showcase-block {
  margin-top: 44px;
  padding-top: 36px;
  border-top: 1px solid color-mix(in srgb, var(--border-color) 70%, transparent);
}

.showcase-block__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2.5px;
  text-transform: uppercase;
  color: var(--accent);
  margin-bottom: 6px;
}

.eyebrow--alt {
  color: var(--accent-2);
}

.showcase-block__intro h2 {
  margin: 0;
  font-size: clamp(22px, 3vw, 28px);
  font-weight: 800;
  line-height: 1.3;
  color: var(--text-main);
}

.showcase-block__intro p {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--text-sub);
}

.showcase-block__toolbar {
  flex-shrink: 0;
  padding-bottom: 4px;
}

/* 搜索框：圆角两段 */
:deep(.showcase-search) {
  width: 220px;
}

:deep(.showcase-search .ant-input-affix-wrapper) {
  border-radius: 12px 0 0 12px !important;
  background: var(--panel-bg) !important;
  border-color: color-mix(in srgb, var(--border-color) 80%, transparent) !important;
}

:deep(.showcase-search .ant-input-group-addon .ant-input-search-button) {
  border-radius: 0 12px 12px 0 !important;
  background: var(--chip-bg) !important;
  border-color: color-mix(in srgb, var(--border-color) 80%, transparent) !important;
  color: var(--chip-text) !important;
}

html[data-theme='dark'] :deep(.showcase-search .ant-input-affix-wrapper),
html[data-theme='dark'] :deep(.showcase-search .ant-input-group-addon .ant-input-search-button) {
  background: var(--chip-bg) !important;
  border-color: var(--control-border) !important;
}

:deep(.showcase-search .ant-input-affix-wrapper input) {
  background: transparent !important;
  color: var(--text-main) !important;
}

.ghost-btn {
  border-radius: 10px;
}

/* 筛选 pills */
.showcase-block__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.home-pill {
  padding: 8px 18px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--chip-bg);
  color: var(--chip-text);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  cursor: default;
  transition: all 0.2s ease;
}

.home-pill--active {
  background: var(--accent-grad);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 8px 18px -8px color-mix(in srgb, var(--accent) 55%, transparent);
}

/* 卡片墙 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 22px 18px;
}

.app-card {
  border-radius: 16px;
  background: var(--card-bg);
  border: 1px solid color-mix(in srgb, var(--border-color) 70%, transparent);
  cursor: pointer;
  overflow: hidden;
  transition:
    transform 0.28s ease,
    box-shadow 0.28s ease,
    border-color 0.28s ease;
}

.app-card:hover {
  transform: translateY(-6px);
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  box-shadow: var(--shadow-hover);
}

.app-card__cover {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--accent) 24%, #fff),
    color-mix(in srgb, var(--accent-2) 22%, #f4fbff)
  );
}

html[data-theme='dark'] .app-card__cover {
  background: linear-gradient(135deg, rgba(42, 92, 190, 0.4), rgba(10, 24, 60, 0.9));
}

.app-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
}

.app-card:hover .app-card__cover img {
  transform: scale(1.06);
}

.app-card__placeholder {
  font-size: clamp(34px, 5vw, 48px);
  font-weight: 800;
  color: color-mix(in srgb, var(--accent) 55%, #fff);
  -webkit-text-fill-color: color-mix(in srgb, var(--accent) 55%, #fff);
  text-shadow: 0 4px 18px color-mix(in srgb, var(--accent) 30%, transparent);
}

html[data-theme='dark'] .app-card__placeholder {
  color: rgba(160, 200, 255, 0.85);
  -webkit-text-fill-color: rgba(160, 200, 255, 0.85);
}

.app-card__tag {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.86);
  color: #23425f;
  backdrop-filter: blur(6px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

html[data-theme='dark'] .app-card__tag {
  background: rgba(13, 27, 58, 0.82);
  color: #cfe0ff;
}

.app-card__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--mask-bg);
  opacity: 0;
  backdrop-filter: blur(2px);
  transition: opacity 0.22s ease;
}

.app-card__cover:hover .app-card__mask {
  opacity: 1;
}

.card-hover-button {
  height: 38px;
  min-width: 120px;
  border: none;
  border-radius: 10px;
  color: #23425f;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.2);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.card-hover-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 32px rgba(0, 0, 0, 0.26);
}

html[data-theme='dark'] .card-hover-button {
  color: #dbe8ff;
  background: rgba(30, 50, 95, 0.9);
}

.app-card__body {
  padding: 13px 14px 15px;
}

.app-card__title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--text-main);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.app-card__meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  font-size: 12px;
  color: var(--text-sub);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-card__dot {
  margin: 0 6px;
  opacity: 0.5;
}

.showcase__pagination {
  margin-top: 26px;
  display: flex;
  justify-content: center;
}

.showcase :deep(.ant-empty) {
  margin: 30px 0;
}

/* ================= 响应式 ================= */
@media (max-width: 1200px) {
  .app-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .showcase {
    padding: 24px 18px;
    border-radius: 22px;
  }

  .showcase-block__head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .showcase-block__toolbar {
    width: 100%;
    display: flex;
    justify-content: space-between;
  }

  :deep(.showcase-search) {
    flex: 1;
    width: auto;
  }

  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px 14px;
  }
}

@media (max-width: 560px) {
  .home-page {
    padding: 18px 12px 72px;
  }

  .hero {
    padding-top: 26px;
  }

  .hero__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .hero__selects {
    justify-content: center;
  }

  .generate-fab {
    align-self: flex-end;
  }

  .app-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bg__orb {
    animation: none;
  }

  .hero__badge,
  .hero__title,
  .hero__sub,
  .hero__panel,
  .hero__tags,
  .hero__hint {
    animation: none;
  }
}
</style>
