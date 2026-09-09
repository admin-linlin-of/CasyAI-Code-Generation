<template>
  <div class="about-author">
    <div class="bg" aria-hidden="true">
      <span class="bg__orb bg__orb--blue"></span>
      <span class="bg__orb bg__orb--cyan"></span>
      <span class="bg__orb bg__orb--violet"></span>
    </div>

    <div class="about-wrap">
      <section class="hero-card">

        <div class="hero-card__body">
          <div class="hero-card__badge">Java / Vue / AI 全栈</div>
          <h1>杨佳霖</h1>
          <p class="hero-card__role">软件开发工程师 · 3 年经验 · 北京</p>
          <p class="hero-card__bio">
            河北师范大学本科。日常做高并发交易与仓储系统，也独立做过 AI
            Agent、RAG 知识库和本站这样的代码生成平台。喜欢把复杂业务拆成可复用的组件、缓存和线程模型。
          </p>
          <div class="hero-card__contacts">
            <a class="contact-chip" href="mailto:3468389387@qq.com">邮箱</a>
            <a class="contact-chip" href="tel:15652971665">电话</a>
            <a
              class="contact-chip"
              href="https://github.com/CasyDev"
              target="_blank"
              rel="noopener noreferrer"
            >GitHub</a>
            <span class="contact-chip contact-chip--plain">北京 · 河北师范大学</span>
          </div>
        </div>
      </section>

      <section class="block">
        <h2>技能栈</h2>
        <div class="skill-grid">
          <article v-for="group in skillGroups" :key="group.title" class="skill-card">
            <h3>{{ group.title }}</h3>
            <div class="tags">
              <span v-for="item in group.items" :key="item">{{ item }}</span>
            </div>
          </article>
        </div>
      </section>

      <section class="block">
        <h2>工作经历</h2>
        <ol class="timeline">
          <li v-for="job in jobs" :key="job.company">
            <div class="timeline__dot"></div>
            <div class="timeline__card">
              <div class="timeline__head">
                <strong>{{ job.company }}</strong>
                <span>{{ job.time }}</span>
              </div>
              <p>{{ job.role }}</p>
            </div>
          </li>
        </ol>
      </section>

      <section class="block">
        <h2>项目经历</h2>
        <div class="project-grid">
          <article v-for="project in projects" :key="project.title" class="project-card">
            <div class="project-card__meta">
              <span>{{ project.period }}</span>
              <span v-if="project.kind" class="project-card__kind">{{ project.kind }}</span>
            </div>
            <h3>{{ project.title }}</h3>
            <p>{{ project.summary }}</p>
            <ul>
              <li v-for="point in project.points" :key="point">{{ point }}</li>
            </ul>
            <div class="tags tags--compact">
              <span v-for="tech in project.tech" :key="tech">{{ tech }}</span>
            </div>
            <a
              v-if="project.link"
              class="project-card__link"
              :href="project.link"
              target="_blank"
              rel="noopener noreferrer"
            >查看仓库</a>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
const skillGroups = [
  {
    title: '后端',
    items: ['Java', 'Spring Boot 3/4', 'Spring Cloud', 'MyBatis-Plus / Flex', '虚拟线程'],
  },
  {
    title: '数据与中间件',
    items: ['MySQL', 'PostgreSQL', 'Redis', 'Caffeine', 'RabbitMQ', '分布式锁'],
  },
  {
    title: '前端',
    items: ['Vue 2/3', 'Vite', 'TypeScript', 'Ant Design Vue', 'Element Plus', '性能优化'],
  },
  {
    title: 'AI 工程',
    items: ['LangChain4j', 'Spring AI', 'RAG / PGvector', 'MCP', 'Prompt', 'Cursor Agent'],
  },
]

const jobs = [
  {
    company: '深联雁联计算机系统有限公司',
    role: 'Java 开发工程师',
    time: '2023.12 — 至今',
  },
  {
    company: '北京中软融鑫计算机系统工程有限公司',
    role: 'Java 开发工程师',
    time: '2023.05 — 2023.11',
  },
]

const projects = [
  {
    title: '贵金属业务系统',
    period: '2024.05 — 至今',
    kind: '任职',
    summary: '服务 130 万客户、日业务量 1500 万的黄金白银交易平台，对接手机银行、网银、柜面与云商城。',
    points: [
      '清算数据按交易库并行 Feign 准备，缩短日终窗口',
      'SPI 可插拔组件与业务隔离线程池，降低 20+ 微服务扩展耦合',
      'Caffeine + Redis 多级缓存、双中心分布式锁，支撑高并发幂等',
    ],
    tech: ['Spring Cloud', 'Nacos', 'Redis', 'Caffeine', 'PostgreSQL', 'Vue3'],
  },
  {
    title: '贵金属仓储管理平台',
    period: '2026.05 — 至今',
    kind: '任职',
    summary: '面向交易所/银行的黄金白银条块仓储，覆盖库存、出入库、交割结算与风控。',
    points: [
      '主导实物调拨状态机与有序行锁，避免超卖、重复冻结和死锁',
      '异步导出：虚拟线程 + EasyExcel 流式写盘，大表并行分页',
      'MyBatis-Flex 多数据源适配，XML 分页与 CRUD 共用同一会话工厂',
    ],
    tech: ['Spring Boot', 'MyBatis-Flex', 'Vue3', 'Vite', 'Pinia'],
  },
  {
    title: '人民币账户管控系统',
    period: '2023.12 — 2024.05',
    kind: '任职',
    summary: '个人 / 对公 / 网银微服务，覆盖账户分级、涉案统计、日间台账与电子档案。',
    points: [
      '多数据源定时汇总库表元信息',
      'ECharts 超长柱状图分页导出 PDF',
      'Redis 主从 + 哨兵，企业微信扫码登录',
    ],
    tech: ['Spring Cloud', 'Eureka', 'Zuul', 'Vue2', 'ECharts'],
  },
  {
    title: '办公自动化系统',
    period: '2023.05 — 2023.11',
    kind: '任职',
    summary: '工作流审批、邮件提醒、流程监控与档案管理，打通部门间审批流转。',
    points: [
      'Flowable 动态分配角色并跟踪催办',
      'Spark-MD5 分片上传与断点续传',
      '按年分表 + 索引重构，4 千万级查询从约 10 分钟降到 10 秒',
    ],
    tech: ['Spring Boot', 'Flowable', 'MySQL', '达梦'],
  },
  {
    title: '智能网站生成平台',
    period: '个人项目',
    kind: '独立开发',
    summary: '自然语言描述后，Agent 并发完成素材搜集、代码生成、质检与构建，一键部署站点。',
    points: [
      'LangChain4j / LangGraph4j 多模型与工具调用',
      '输入护轨、限流与 Redis 多轮记忆隔离',
      '可拆为用户 / AI / 截图微服务并监控 Token 质量',
    ],
    tech: ['Spring Boot', 'LangChain4j', 'Vue3'],
    link: 'https://github.com/CasyDev/Casy-AI-Code-Generation-Platform',
  },
  {
    title: 'AI 智能客服',
    period: '个人项目',
    kind: '独立开发',
    summary: '企业业务助手：知识库检索、多轮记忆，ReAct 调工具完成搜网页、下载、生成 PDF。',
    points: [
      'Markdown 切片入库 PGvector，支持向量检索',
      '分层 Agent + 死循环检测，接入高德 MCP',
      '国密链路保护敏感字段，明文不落库',
    ],
    tech: ['Spring AI', 'RAG', 'MCP', 'PGvector'],
    link: 'https://github.com/CasyDev/Casy-AI-Intelligent-Assistant',
  },
]
</script>

<style scoped>
.about-author {
  position: relative;
  min-height: calc(100vh - 88px);
  padding: 32px 16px 96px;
  color: var(--text-main);
  overflow: hidden;
}

.bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.55;
}

.bg__orb--blue {
  width: 320px;
  height: 320px;
  top: -80px;
  left: -40px;
  background: rgba(22, 119, 255, 0.45);
}

.bg__orb--cyan {
  width: 260px;
  height: 260px;
  top: 120px;
  right: 8%;
  background: rgba(6, 182, 212, 0.35);
}

.bg__orb--violet {
  width: 280px;
  height: 280px;
  bottom: 10%;
  left: 30%;
  background: rgba(139, 92, 246, 0.28);
}

.about-wrap {
  position: relative;
  z-index: 1;
  max-width: 1080px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.hero-card,
.skill-card,
.timeline__card,
.project-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 18px;
  box-shadow: 0 18px 50px -24px rgba(30, 80, 200, 0.22);
}

.hero-card {
  display: flex;
  gap: 24px;
  padding: 28px;
  align-items: flex-start;
}

.hero-card__avatar {
  flex-shrink: 0;
  width: 84px;
  height: 84px;
  border-radius: 24px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 34px;
  font-weight: 700;
  background: linear-gradient(135deg, #1677ff, #06b6d4);
}

.hero-card__badge {
  display: inline-flex;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--tag-text);
  background: var(--tag-bg);
  border: 1px solid var(--tag-border);
}

.hero-card h1 {
  margin: 8px 0 4px;
  font-size: 32px;
  letter-spacing: -0.03em;
}

.hero-card__role {
  margin: 0 0 12px;
  color: var(--text-sub);
}

.hero-card__bio {
  margin: 0 0 16px;
  line-height: 1.7;
  max-width: 640px;
}

.hero-card__contacts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.contact-chip {
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  color: #fff;
  background: #1677ff;
  text-decoration: none;
  font-size: 13px;
}

.contact-chip--plain {
  color: var(--text-main);
  background: transparent;
  border: 1px solid var(--border-color);
}

.block h2 {
  margin: 0 0 14px;
  font-size: 20px;
}

.skill-grid,
.project-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.skill-card,
.project-card {
  padding: 18px 20px;
}

.skill-card h3,
.project-card h3 {
  margin: 0 0 10px;
  font-size: 16px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tags span {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--tag-text);
  background: var(--tag-bg);
}

.tags--compact {
  margin-top: 10px;
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.timeline li {
  display: grid;
  grid-template-columns: 16px 1fr;
  gap: 12px;
  align-items: stretch;
}

.timeline__dot {
  width: 12px;
  height: 12px;
  margin-top: 18px;
  border-radius: 50%;
  background: #1677ff;
  box-shadow: 0 0 0 4px color-mix(in srgb, #1677ff 18%, transparent);
}

.timeline__card {
  padding: 14px 18px;
}

.timeline__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.timeline__card p {
  margin: 4px 0 0;
  color: var(--text-sub);
}

.project-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-sub);
  font-size: 12px;
  margin-bottom: 6px;
}

.project-card__kind {
  color: #1677ff;
}

.project-card p {
  margin: 0 0 8px;
  line-height: 1.65;
  color: var(--text-sub);
}

.project-card ul {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 4px;
  color: var(--text-main);
}

.project-card__link {
  display: inline-block;
  margin-top: 12px;
  color: #1677ff;
}

@media (max-width: 800px) {
  .hero-card {
    flex-direction: column;
  }

  .skill-grid,
  .project-grid {
    grid-template-columns: 1fr;
  }
}
</style>
