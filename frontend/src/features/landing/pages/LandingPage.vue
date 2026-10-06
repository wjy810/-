<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowRight,
  BadgeCheck,
  BookOpen,
  Check,
  ChevronDown,
  Database,
  Download,
  FileCheck2,
  FileText,
  FolderOpen,
  LayoutTemplate,
  Menu,
  MessageCircle,
  PlayCircle,
  ShieldCheck,
  Sparkles,
  X,
} from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { authLandingMode } from '@/features/identity/authLanding'
import { useSessionStore } from '@/stores/session'
import brandLogo from '@/assets/jobproof-ai-logo.png'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import type { JobProofIconName } from '@/shared/ui/jobProofIcons'
import LandingLoginDialog from '../components/LandingLoginDialog.vue'
import ProductPreview from '../components/ProductPreview.vue'
import '../landing.css'

const session = useSessionStore()

type MenuName = 'product' | 'resources' | null

const mobileOpen = ref(false)
const openMenu = ref<MenuName>(null)
const navScrolled = ref(false)
const navRef = ref<HTMLElement | null>(null)
const route = useRoute()
const router = useRouter()
const primaryPath = computed(() => session.signedIn
  ? { name: 'career-library' }
  : { name: 'home', query: { auth: 'register' } })
const primaryLabel = computed(() => session.signedIn ? '进入我的工作区' : '免费开始')
const authMode = computed(() => !session.signedIn ? authLandingMode(route.query.auth) : null)
const loginReason = computed(() => typeof route.query.reason === 'string' ? route.query.reason : undefined)
const loginNext = computed(() => typeof route.query.next === 'string' ? route.query.next : undefined)
const overlayOpen = computed(() => mobileOpen.value || authMode.value != null)

const features: Array<{ icon: JobProofIconName; tone: string; title: string; text: string; to: string }> = [
  { icon: 'nav-ai-chat', tone: 'blue', title: 'AI 对话简历', text: '像聊天一样梳理经历，结构化卡片确认后再写入简历，重要事实由你决定。', to: '/ai-resume/new' },
  { icon: 'nav-career-library', tone: 'cyan', title: '求职资料库', text: '集中管理教育、项目、证书与私有文件，需要时作为 AI 可追溯的事实依据。', to: '/career-library' },
  { icon: 'resume-resume-sections', tone: 'green', title: '结构化简历编辑', text: '同一份内容在对话、手动编辑、预览和导出之间保持一致，修改即时可见。', to: '/resumes' },
  { icon: 'nav-template-center', tone: 'cyan', title: '12 款智能模板', text: '模板只改变视觉呈现，不改写内容；可按岗位与经历阶段随时切换。', to: '/resume-templates' },
  { icon: 'template-gallery', tone: 'orange', title: '2,000+ Word 资产', text: '公共模板支持关键词检索、逐页图片预览和原始 DOCX 下载。', to: '/resume-templates' },
  { icon: 'kit-pdf-file', tone: 'green', title: 'PDF 导出', text: '按真实 A4 分页预览，冻结已确认内容后生成可用于投递的 PDF。', to: '/resumes' },
  { icon: 'resume-version-history', tone: 'blue', title: '版本与事实追踪', text: '候选修改、正式修订和来源引用彼此分离，随时比较并恢复历史内容。', to: '/resumes' },
]

const workflow: Array<{ no: string; title: string; text: string; icon: JobProofIconName }> = [
  { no: '01', title: '沉淀求职资料', text: '把经历、项目、技能和文件集中整理一次。', icon: 'library-experience-achievements' },
  { no: '02', title: 'AI 创建简历', text: '对话追问缺失信息，逐项确认候选内容。', icon: 'resume-new-resume' },
  { no: '03', title: '切换模板设计', text: '用同一内容选择适合岗位的视觉呈现。', icon: 'template-layout' },
  { no: '04', title: '预览并导出', text: '核对 A4 分页，将确认版本导出为 PDF。', icon: 'kit-pdf-file' },
]

const publicAssets: Array<{ icon: JobProofIconName; value: string; label: string; note: string }> = [
  { icon: 'template-gallery', value: '2,000+', label: '公共 Word 模板', note: '支持分类检索与逐页预览' },
  { icon: 'template-resume-template', value: '12', label: '智能简历模板', note: '统一结构化内容自由换版' },
  { icon: 'dashboard-overall-progress', value: '4', label: '核心工作流', note: '资料、简历、设计与导出' },
  { icon: 'kit-pdf-file', value: 'PDF', label: '主要导出格式', note: '按真实 A4 分页生成' },
]

function toggleMenu(name: Exclude<MenuName, null>) {
  openMenu.value = openMenu.value === name ? null : name
}

function closeNavigation() {
  mobileOpen.value = false
  openMenu.value = null
}

async function closeAuth() {
  const { auth: _auth, reason: _reason, next: _next, ...query } = route.query
  await router.replace({ name: 'home', query, hash: route.hash })
}

function onDocumentPointer(event: PointerEvent) {
  if (navRef.value && !navRef.value.contains(event.target as Node)) {
    openMenu.value = null
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (authMode.value) {
    void closeAuth()
    return
  }
  closeNavigation()
}

function onScroll() {
  navScrolled.value = window.scrollY > 12
}

let revealObserver: IntersectionObserver | null = null

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointer)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()

  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    revealObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver?.unobserve(entry.target)
        }
      })
    }, { threshold: 0.12, rootMargin: '0px 0px -48px' })
    document.querySelectorAll<HTMLElement>('[data-reveal]').forEach((element) => revealObserver?.observe(element))
  }
})

watch(overlayOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
}, { immediate: true })

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointer)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onScroll)
  revealObserver?.disconnect()
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="landing-page">
    <LandingLoginDialog v-if="authMode" :mode="authMode" :reason="loginReason" :next="loginNext" @close="closeAuth" />
    <header ref="navRef" class="landing-nav" :class="{ 'is-scrolled': navScrolled }">
      <div class="landing-container landing-nav__inner">
        <RouterLink class="landing-brand" to="/" aria-label="JobProof AI 首页" @click="closeNavigation">
          <img :src="brandLogo" alt="JobProof AI" width="124" height="27">
        </RouterLink>

        <nav class="landing-nav__links" aria-label="官网导航">
          <a class="is-active" href="#home">首页</a>
          <button type="button" :aria-expanded="openMenu === 'product'" aria-controls="product-mega" @click="toggleMenu('product')">
            产品功能 <ChevronDown :size="15" :class="{ 'is-rotated': openMenu === 'product' }" />
          </button>
          <button type="button" :aria-expanded="openMenu === 'resources'" aria-controls="resource-mega" @click="toggleMenu('resources')">
            求职资源 <ChevronDown :size="15" :class="{ 'is-rotated': openMenu === 'resources' }" />
          </button>
          <RouterLink to="/resume-templates">模板中心</RouterLink>
          <a href="#workflow">使用流程</a>
        </nav>

        <div class="landing-nav__actions">
          <RouterLink v-if="!session.signedIn" class="landing-btn landing-btn--outline landing-btn--nav" :to="{ name: 'home', query: { auth: 'login' } }">登录</RouterLink>
          <RouterLink class="landing-btn landing-btn--primary landing-btn--nav" :to="primaryPath">{{ primaryLabel }}</RouterLink>
        </div>

        <button class="landing-menu-button" type="button" :aria-expanded="mobileOpen" aria-label="打开导航菜单" @click="mobileOpen = true">
          <Menu :size="27" />
        </button>
      </div>

      <Transition name="mega">
        <div v-if="openMenu === 'product'" id="product-mega" class="landing-mega">
          <div class="landing-mega__inner">
            <div class="landing-mega__group">
              <span>简历创建</span>
              <RouterLink to="/ai-resume/new" @click="closeNavigation"><MessageCircle :size="19" /><span><strong>AI 对话简历</strong><small>对话梳理并确认事实</small></span></RouterLink>
              <RouterLink to="/resumes" @click="closeNavigation"><FileText :size="19" /><span><strong>简历管理</strong><small>编辑、版本与导出</small></span></RouterLink>
            </div>
            <div class="landing-mega__group">
              <span>求职准备</span>
              <RouterLink to="/career-library" @click="closeNavigation"><FolderOpen :size="19" /><span><strong>求职资料库</strong><small>职业事实与私有文件</small></span></RouterLink>
            </div>
            <RouterLink class="landing-mega__spotlight" to="/ai-resume/new" @click="closeNavigation">
              <span class="landing-mega__spotlight-icon"><Sparkles :size="21" /></span>
              <span class="landing-mega__spotlight-copy">
                <small>AI 对话建档</small>
                <strong>从一段真实经历开始</strong>
                <span class="landing-mega__spotlight-steps"><i>对话梳理</i><i>逐项确认</i><i>实时预览</i></span>
                <em>开始创建简历 <ArrowRight :size="16" /></em>
              </span>
            </RouterLink>
          </div>
        </div>
      </Transition>

      <Transition name="mega">
        <div v-if="openMenu === 'resources'" id="resource-mega" class="landing-mega">
          <div class="landing-mega__inner landing-mega__inner--resources">
            <RouterLink class="landing-mega__resource-link" to="/resume-templates" @click="closeNavigation"><i class="landing-mega__resource-icon"><LayoutTemplate :size="21" /></i><span><strong>模板中心</strong><small>智能模板与公共 DOCX 资产</small></span><ArrowRight class="landing-mega__resource-arrow" :size="17" /></RouterLink>
            <RouterLink class="landing-mega__resource-link" to="/career-library" @click="closeNavigation"><i class="landing-mega__resource-icon"><Database :size="21" /></i><span><strong>求职资料库</strong><small>管理长期职业信息与文件</small></span><ArrowRight class="landing-mega__resource-arrow" :size="17" /></RouterLink>
            <a class="landing-mega__resource-link" href="#workflow" @click="closeNavigation"><i class="landing-mega__resource-icon"><BookOpen :size="21" /></i><span><strong>使用流程</strong><small>了解四步简历准备链路</small></span><ArrowRight class="landing-mega__resource-arrow" :size="17" /></a>
          </div>
        </div>
      </Transition>
    </header>

    <Transition name="mobile-menu">
      <div v-if="mobileOpen" class="landing-mobile-nav" role="dialog" aria-modal="true" aria-label="导航菜单">
        <div class="landing-mobile-nav__head">
          <RouterLink class="landing-brand" to="/" @click="closeNavigation"><img :src="brandLogo" alt="JobProof AI" width="124" height="27"></RouterLink>
          <button type="button" aria-label="关闭导航菜单" @click="closeNavigation"><X :size="26" /></button>
        </div>
        <nav>
          <a href="#home" @click="closeNavigation">首页 <ArrowRight :size="19" /></a>
          <a href="#features" @click="closeNavigation">产品功能 <ArrowRight :size="19" /></a>
          <a href="#workflow" @click="closeNavigation">使用流程 <ArrowRight :size="19" /></a>
          <RouterLink to="/resume-templates" @click="closeNavigation">模板中心 <ArrowRight :size="19" /></RouterLink>
          <RouterLink to="/career-library" @click="closeNavigation">求职资料库 <ArrowRight :size="19" /></RouterLink>
        </nav>
        <div class="landing-mobile-nav__actions">
          <RouterLink v-if="!session.signedIn" class="landing-btn landing-btn--outline" :to="{ name: 'home', query: { auth: 'login' } }" @click="closeNavigation">登录</RouterLink>
          <RouterLink class="landing-btn landing-btn--primary" :to="primaryPath" @click="closeNavigation">{{ primaryLabel }} <ArrowRight :size="18" /></RouterLink>
        </div>
      </div>
    </Transition>

    <main>
      <section id="home" class="landing-hero">
        <div class="landing-container landing-hero__inner">
          <div class="landing-hero__copy">
            <div class="landing-eyebrow"><span class="landing-eyebrow__dot"></span> AI 求职准备工作台</div>
            <h1>从一份简历，<br>到每一个岗位，<br><span>AI 全程陪你准备</span></h1>
            <p>用一次对话完成简历创建，把职业资料、模板设计与 PDF 导出接在同一条流程里。</p>
            <div class="landing-hero__actions">
              <RouterLink class="landing-btn landing-btn--primary landing-btn--large" :to="primaryPath">{{ primaryLabel }} <ArrowRight :size="20" /></RouterLink>
              <a class="landing-btn landing-btn--outline landing-btn--large" href="#product-preview"><PlayCircle :size="20" /> 查看产品界面</a>
            </div>
            <div class="landing-hero__assurances" aria-label="产品说明">
              <span><BadgeCheck :size="17" /> 事实需确认</span>
              <span><ShieldCheck :size="17" /> 私有资料保护</span>
              <span><FileText :size="17" /> 支持 PDF 导出</span>
            </div>
          </div>

          <ProductPreview />
        </div>
      </section>

      <section class="landing-proof-strip" aria-label="平台能力概览">
        <div class="landing-container">
          <div class="landing-proof-strip__intro"><span><ShieldCheck :size="18" /> 求职准备，有序展开</span><strong>资料、简历与成长路径，在这里连接</strong></div>
          <div class="landing-proof-strip__metrics">
            <article><span><Database :size="19" /></span><div><strong>2,000+</strong><small>公共 Word 模板</small></div></article>
            <article><span><LayoutTemplate :size="19" /></span><div><strong>12 款</strong><small>智能简历模板</small></div></article>
            <article><span><FileCheck2 :size="19" /></span><div><strong>逐页</strong><small>真实图片预览</small></div></article>
            <article><span><Download :size="19" /></span><div><strong>A4</strong><small>PDF 排版导出</small></div></article>
          </div>
        </div>
      </section>

      <section id="features" class="landing-section landing-section--features">
        <div class="landing-container">
          <header class="landing-section__head" data-reveal>
            <span class="landing-section__kicker">核心能力</span>
            <h2>一套工作台，覆盖求职准备的<span>每一步</span></h2>
            <p>所有能力围绕同一份结构化内容协作，信息只整理一次，后续可以持续复用。</p>
          </header>
          <div class="landing-feature-grid">
            <RouterLink v-for="(feature, index) in features" :key="feature.title" :to="feature.to" class="landing-feature" :class="[`is-${feature.tone}`, { 'is-primary': index === 0 }]" data-reveal>
              <span class="landing-feature__icon"><JobProofIcon :name="feature.icon" :size="25" /></span>
              <h3>{{ feature.title }}</h3>
              <p>{{ feature.text }}</p>
              <span class="landing-feature__arrow" aria-hidden="true"><span>了解功能</span><ArrowRight :size="17" /></span>
            </RouterLink>
          </div>
          <div class="landing-feature-note" data-reveal><ShieldCheck :size="21" /><span><strong>一次登录，同一份事实贯穿创建、设计与导出</strong><small>AI 候选不会自动覆盖正式简历，最终内容始终由用户确认。</small></span></div>
        </div>
      </section>

      <section id="workflow" class="landing-section landing-section--workflow">
        <div class="landing-container">
          <header class="landing-section__head" data-reveal>
            <span class="landing-section__kicker">完整链路</span>
            <h2>不是功能堆叠，而是一条清晰的<span>求职准备流程</span></h2>
            <p>从职业资料到目标岗位版本，每一步都能看见输入、确认与结果。</p>
          </header>
          <div class="landing-workflow" data-reveal>
            <article v-for="(step, index) in workflow" :key="step.no" class="landing-workflow__step">
              <div class="landing-workflow__top"><span>{{ step.no }}</span><i v-if="index < workflow.length - 1"></i></div>
              <div class="landing-workflow__visual"><JobProofIcon :name="step.icon" :size="30" /><span class="landing-workflow__mini"><i></i><i></i><b></b></span></div>
              <h3>{{ step.title }}</h3>
              <p>{{ step.text }}</p>
            </article>
          </div>
          <div class="landing-workflow__compare" data-reveal>
            <div><span>传统方式</span><strong>多个文件反复复制，内容与版式相互牵制</strong></div>
            <ArrowRight :size="24" />
            <div class="is-better"><span>JobProof AI</span><strong>统一事实持续复用，模板切换不改内容</strong></div>
          </div>
        </div>
      </section>

      <section id="templates" class="landing-section landing-section--templates">
        <div class="landing-container landing-template-layout">
          <div class="landing-template-copy" data-reveal>
            <span class="landing-section__kicker">模板与内容分离</span>
            <h2>内容只维护一次，<span>模板随时切换</span></h2>
            <p>12 款智能模板消费同一份结构化内容。你可以按岗位、经历阶段和阅读密度换版，教育、经历、项目与技能不会因模板不同而丢失。</p>
            <ul>
              <li><Check :size="17" /> 模板切换不修改内容事实</li>
              <li><Check :size="17" /> 支持短、中、长内容真实分页</li>
              <li><Check :size="17" /> 网页预览与 PDF 共用排版规则</li>
            </ul>
            <RouterLink class="landing-btn landing-btn--outline" to="/resume-templates">浏览模板中心 <ArrowRight :size="18" /></RouterLink>
          </div>
          <div class="landing-template-stack" data-reveal aria-label="智能简历模板示例">
            <article class="template-sheet template-sheet--navy"><header><strong>张一帆</strong><span>产品经理</span></header><i></i><b>个人简介</b><p></p><p></p><b>项目经历</b><p></p><p></p><p></p></article>
            <article class="template-sheet template-sheet--cyan"><header><strong>张一帆</strong><span>产品经理</span></header><i></i><b>教育经历</b><p></p><p></p><b>专业技能</b><p></p><p></p><p></p></article>
            <article class="template-sheet template-sheet--mono"><header><strong>ZHANG YIFAN</strong><span>PRODUCT MANAGER</span></header><i></i><b>EXPERIENCE</b><p></p><p></p><p></p><b>SKILLS</b><p></p></article>
          </div>
        </div>
      </section>

      <section class="landing-section landing-section--trust">
        <div class="landing-container">
          <header class="landing-section__head" data-reveal>
            <span class="landing-section__kicker">真实能力</span>
            <h2>认真准备的人，值得更清晰的工具</h2>
            <p>只展示当前已经落地、可以在产品中验证的能力与资产。</p>
          </header>
          <div class="landing-stats">
            <article v-for="asset in publicAssets" :key="asset.label" data-reveal><span class="landing-stats__icon"><JobProofIcon :name="asset.icon" :size="21" /></span><div><strong>{{ asset.value }}</strong><span>{{ asset.label }}</span><p>{{ asset.note }}</p></div></article>
          </div>
          <div class="landing-trust-principles" data-reveal>
            <article><ShieldCheck :size="23" /><div><strong>不替用户编造事实</strong><p>公司、学校、日期、技能与数字需要有输入事实支持。</p></div></article>
            <article><BadgeCheck :size="23" /><div><strong>候选与正式内容分离</strong><p>AI 先提出建议，用户确认后才进入正式简历与导出。</p></div></article>
            <article><FolderOpen :size="23" /><div><strong>文件与结构化资料分离</strong><p>原始文件保持私有，不作为模型自由读取的上下文。</p></div></article>
          </div>
        </div>
      </section>

      <section class="landing-final-cta">
        <div class="landing-container" data-reveal>
          <div><span><Sparkles :size="18" /> 从一段真实经历开始</span><h2>准备好，让下一份简历更有把握</h2><p>用对话整理事实，用模板呈现价值，用 PDF 完成最后交付。</p></div>
          <div class="landing-final-cta__actions"><RouterLink class="landing-btn landing-btn--light landing-btn--large" :to="primaryPath">{{ primaryLabel }} <ArrowRight :size="20" /></RouterLink><a class="landing-btn landing-btn--clear landing-btn--large" href="#features">查看功能</a></div>
        </div>
      </section>
    </main>

    <footer class="landing-footer">
      <div class="landing-container landing-footer__grid">
        <div class="landing-footer__brand"><RouterLink class="landing-brand" to="/"><img :src="brandLogo" alt="JobProof AI" width="124" height="27"></RouterLink><p>用结构化事实和 AI 辅助，完成简历创建、模板呈现与 PDF 导出。</p></div>
        <div><strong>产品</strong><RouterLink to="/ai-resume/new">AI 对话简历</RouterLink><RouterLink to="/resume-templates">模板中心</RouterLink></div>
        <div><strong>资料</strong><RouterLink to="/career-library">求职资料库</RouterLink><RouterLink to="/resumes">我的简历</RouterLink><RouterLink to="/account/data-rights">数据与隐私</RouterLink></div>
        <div><strong>账号</strong><RouterLink :to="{ name: 'home', query: { auth: 'login' } }">登录</RouterLink><RouterLink :to="{ name: 'home', query: { auth: 'register' } }">创建账号</RouterLink><RouterLink to="/terms">用户协议</RouterLink><RouterLink to="/privacy">隐私政策</RouterLink></div>
      </div>
      <div class="landing-container landing-footer__bottom"><span>© 2026 JobProof AI</span><span>求职准备，从可信事实开始</span></div>
    </footer>
  </div>
</template>
