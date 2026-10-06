<script setup lang="ts">
/** Public landing page (docs/04 §9.1): composited 3D hero, flow, features, templates, principles, FAQ. */
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWindowScroll } from '@vueuse/core'
import {
  ArrowRight, BadgeCheck, Check, ChevronDown, Compass, FileText, Menu, MessageSquareText, Mic, PlayCircle, ShieldCheck,
  Sparkles, Target, Undo2, X,
} from 'lucide-vue-next'
import BrandMark from '@/shared/ui/BrandMark.vue'
import MiniResume from '@/shared/ui/MiniResume.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import type { IllustrationName } from '@/shared/ui/illustrations'
import { vReveal } from '@/shared/directives/reveal'
import { authLandingMode } from '@/features/identity/authLanding'
import { useSessionStore } from '@/stores/session'
import { usePreferencesStore } from '@/stores/preferences'

const AuthDialog = defineAsyncComponent(() => import('@/features/identity/components/AuthDialog.vue'))

const session = useSessionStore()
const preferences = usePreferencesStore()
const route = useRoute()
const router = useRouter()
const { y: scrollY } = useWindowScroll()
const scrolled = computed(() => scrollY.value > 8)
const mobileNav = ref(false)

const authMode = computed(() => (!session.signedIn ? authLandingMode(route.query.auth) : null))
const loginReason = computed(() => (typeof route.query.reason === 'string' ? route.query.reason : undefined))
const loginNext = computed(() => (typeof route.query.next === 'string' ? route.query.next : undefined))
const primaryTo = computed(() => (session.signedIn ? '/dashboard' : { name: 'home', query: { auth: 'register' } }))
const primaryLabel = computed(() => (session.signedIn ? '进入工作台' : '免费开始'))

async function closeAuth(): Promise<void> {
  const { auth: _auth, reason: _reason, next: _next, ...query } = route.query
  await router.replace({ name: 'home', query, hash: route.hash })
}

// Gentle parallax on the hero composition (disabled for reduced motion and touch).
const hero = ref<HTMLElement | null>(null)
const tilt = ref({ x: 0, y: 0 })
let frame = 0
function onPointerMove(event: PointerEvent): void {
  if (event.pointerType !== 'mouse' || preferences.motion === 'reduce' || !hero.value) return
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(() => {
    const rect = hero.value!.getBoundingClientRect()
    tilt.value = {
      x: ((event.clientX - rect.left) / rect.width - 0.5) * 2,
      y: ((event.clientY - rect.top) / rect.height - 0.5) * 2,
    }
  })
}
function parallax(depth: number): Record<string, string> {
  return { transform: `translate3d(${tilt.value.x * depth}px, ${tilt.value.y * depth}px, 0)` }
}

onMounted(() => window.addEventListener('pointermove', onPointerMove, { passive: true }))
onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  cancelAnimationFrame(frame)
})

const STEPS: Array<{ title: string; text: string; art: IllustrationName }> = [
  { title: '聊出经历', text: 'AI 一次只问一个问题，把课程、实习、项目里的细节挖出来。', art: 'hero-chat' },
  { title: '逐条确认', text: '每条建议标注来源事实，你点对勾才写入，随时撤销。', art: 'hero-check' },
  { title: '实时排版', text: '右侧 A4 预览即时成形，12 款模板一键换版，导出 PDF。', art: 'hero-resume' },
  { title: '对照岗位', text: '粘贴真实 JD，逐条对照证据，补强后再去模拟面试。', art: 'hero-target' },
]

const TEMPLATES: Array<{ variant: 'banner' | 'sidebar' | 'classic' | 'minimal' | 'split'; accent: string; name: string; role: string; label: string }> = [
  { variant: 'banner', accent: '#13786b', name: '林晓', role: '后端开发实习', label: '校园新锐' },
  { variant: 'classic', accent: '#29286f', name: '周杰', role: '市场运营', label: '经典专业' },
  { variant: 'sidebar', accent: '#4a44d9', name: 'Mia Chen', role: 'Data Analyst', label: '技术双栏' },
  { variant: 'minimal', accent: '#ef7339', name: '陈一', role: '产品经理', label: 'ATS 极简' },
  { variant: 'split', accent: '#2f6fb3', name: '王蕾', role: '财务分析', label: '金融稳健' },
  { variant: 'banner', accent: '#9b6bf2', name: '许诺', role: '咨询顾问', label: '咨询精简' },
  { variant: 'sidebar', accent: '#0b7451', name: '赵研', role: '科研助理', label: '学术研究' },
  { variant: 'classic', accent: '#ae461c', name: 'Leo Wang', role: 'Software Engineer', label: '英文单页' },
]

const PRINCIPLES = [
  { icon: Sparkles, title: 'AI 只出候选', text: '帮写、润色、翻译、推荐都只是建议。没有你的确认，正式简历一个字都不会变。' },
  { icon: BadgeCheck, title: '事实来自你', text: '每条修改都附上来源：你的原话、资料库记录，或“需要你确认”。不编造公司、数字和证书。' },
  { icon: Undo2, title: '随时可撤销', text: '每次确认都生成新版本，历史永远保留。分支、对比、恢复，都只需一次点击。' },
]

const FAQ = [
  { q: '我完全没写过简历，能用吗？', a: '能。选择“我是学生”或“应届生”，AI 会从教育经历开始，一次只问一个问题，你只需要像聊天一样回答，右侧会实时排出一份 A4 简历。' },
  { q: 'AI 会不会编造我没有的经历？', a: '不会。系统规则要求 AI 只能基于你提供的事实生成候选，并在每条建议上标注来源；无法证明的数字会改成定性描述或标记“待补证”。' },
  { q: '导出的 PDF 能直接投递吗？', a: '可以。导出前会检查页数溢出、空模块和未确认内容，并提供保留联系方式的正式版与隐藏身份信息的匿名版。' },
  { q: '我的资料安全吗？', a: '资料只有你本人可见。首次使用 AI 前需要你授权，且只发送已确认的事实；照片不会发送给模型，你可以随时导出或删除全部数据。' },
  { q: '收费吗？', a: '核心功能免费使用，AI 调用按月发放额度，额度用量在设置中随时可查。' },
]
const openFaq = ref<number | null>(0)

const year = new Date().getFullYear()
</script>

<template>
  <div class="landing">
    <!-- ================= NAV ================= -->
    <header class="nav" :class="{ 'is-scrolled': scrolled, 'is-open': mobileNav }">
      <div class="nav__inner">
        <RouterLink to="/" class="nav__brand" aria-label="JobProof AI 首页"><BrandMark :size="30" /></RouterLink>
        <nav class="nav__links" aria-label="页面导航">
          <a href="#flow" @click="mobileNav = false">如何使用</a>
          <a href="#features" @click="mobileNav = false">功能</a>
          <a href="#templates" @click="mobileNav = false">模板</a>
          <a href="#faq" @click="mobileNav = false">常见问题</a>
          <RouterLink to="/updates">更新日志</RouterLink>
        </nav>
        <div class="nav__actions">
          <UiButton v-if="!session.signedIn" variant="ghost" :to="{ name: 'home', query: { auth: 'login' } }">登录</UiButton>
          <UiButton :to="primaryTo" :icon-right="ArrowRight">{{ primaryLabel }}</UiButton>
          <button class="nav__toggle" type="button" :aria-label="mobileNav ? '关闭菜单' : '打开菜单'" :aria-expanded="mobileNav" @click="mobileNav = !mobileNav">
            <X v-if="mobileNav" :size="20" /><Menu v-else :size="20" />
          </button>
        </div>
      </div>
    </header>

    <main>
      <!-- ================= HERO ================= -->
      <section ref="hero" class="hero">
        <div class="hero__bg" aria-hidden="true">
          <span class="hero__blob hero__blob--ink" />
          <span class="hero__blob hero__blob--apricot" />
          <span class="hero__grid" />
        </div>
        <div class="hero__inner">
          <div class="hero__copy">
            <p class="hero__badge animate-rise"><Sparkles :size="14" />证据驱动的 AI 求职工作台</p>
            <h1 class="hero__title animate-rise" style="animation-delay: 60ms">
              写一份<br /><span class="text-ai">每一句都站得住</span>的简历
            </h1>
            <p class="hero__lede animate-rise" style="animation-delay: 120ms">
              和 AI 聊几句，经历自动整理成结构化简历，右侧实时排版成 A4。每条建议都标注来源，你确认后才写入 —— 不编造、不注水，面试时句句说得清。
            </p>
            <div class="hero__cta animate-rise" style="animation-delay: 180ms">
              <UiButton size="lg" :to="primaryTo" :icon-right="ArrowRight">{{ primaryLabel }}</UiButton>
              <UiButton size="lg" variant="secondary" :icon="PlayCircle" href="#flow">看看怎么用</UiButton>
            </div>
            <ul class="hero__trust animate-rise" style="animation-delay: 240ms">
              <li><ShieldCheck :size="15" />AI 只出候选</li>
              <li><FileText :size="15" />实时 A4 预览</li>
              <li><Check :size="15" />免费使用</li>
            </ul>
          </div>

          <div class="hero__art" aria-hidden="true">
            <div class="art-layer" :style="parallax(6)">
              <UiIllustration name="hero-resume" :size="360" class="art art--resume" eager />
            </div>
            <div class="art-layer" :style="parallax(14)">
              <UiIllustration name="hero-check" :size="130" class="art art--check" eager />
              <UiIllustration name="hero-sparkles" :size="96" class="art art--sparkles" eager />
            </div>
            <div class="art-layer" :style="parallax(-10)">
              <UiIllustration name="hero-chat" :size="130" class="art art--chat" eager />
              <UiIllustration name="hero-target" :size="118" class="art art--target" eager />
            </div>
            <div class="art-layer" :style="parallax(20)">
              <UiIllustration name="hero-plane" :size="104" class="art art--plane" eager />
              <UiIllustration name="hero-pencil" :size="92" class="art art--pencil" eager />
            </div>
            <div class="art-layer" :style="parallax(-4)">
              <div class="chip chip--suggest">
                <span class="chip__icon"><Sparkles :size="14" /></span>
                <div>
                  <small>AI 候选 · 来自你的描述</small>
                  <p><del>负责数据整理</del><ins>用 SQL 清洗 3 万条问卷数据，报表出具提速 50%</ins></p>
                </div>
              </div>
              <div class="chip chip--score">
                <span class="chip__ring"><svg viewBox="0 0 36 36"><circle cx="18" cy="18" r="15" /><circle cx="18" cy="18" r="15" class="chip__ring-bar" /></svg><strong>86</strong></span>
                <div><small>岗位匹配度</small><p>Java 后端实习</p></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ================= STATS ================= -->
      <section class="stats">
        <div class="stats__inner">
          <div v-reveal class="stat"><strong>12</strong><span>款智能模板，一键换版</span></div>
          <div v-reveal="80" class="stat"><strong>2,000+</strong><span>份 Word 模板免费下载</span></div>
          <div v-reveal="160" class="stat"><strong>4</strong><span>大模块：简历 · 匹配 · 规划 · 面试</span></div>
          <div v-reveal="240" class="stat"><strong>0</strong><span>条编造的经历</span></div>
        </div>
      </section>

      <!-- ================= FLOW ================= -->
      <section id="flow" class="section">
        <header v-reveal class="section__head">
          <p class="section__eyebrow">如何使用</p>
          <h2>四步，从“不会写”到“写得好”</h2>
          <p>不用对着空白模板发呆。AI 负责追问和排版，你只负责确认事实。</p>
        </header>
        <ol class="flow">
          <li v-for="(step, index) in STEPS" :key="step.title" v-reveal="index * 90" class="flow__step">
            <span class="flow__no">0{{ index + 1 }}</span>
            <UiIllustration :name="step.art" :size="120" class="flow__art" />
            <h3>{{ step.title }}</h3>
            <p>{{ step.text }}</p>
          </li>
        </ol>
      </section>

      <!-- ================= FEATURES ================= -->
      <section id="features" class="section section--tinted">
        <header v-reveal class="section__head">
          <p class="section__eyebrow">一站式求职准备</p>
          <h2>简历只是开始</h2>
          <p>同一份真实资料，串起简历、岗位匹配、职业规划和模拟面试。</p>
        </header>
        <div class="bento">
          <article v-reveal class="bento__card bento__card--hero">
            <div class="bento__text">
              <span class="bento__icon"><MessageSquareText :size="18" /></span>
              <h3>AI 简历工作台</h3>
              <p>左边对话与结构化编辑，右边实时 A4。确认一张卡片，预览就“落版”一块。</p>
              <ul>
                <li><Check :size="14" />对话 / 编辑 / 设计 / 模板四视图</li>
                <li><Check :size="14" />变更集逐条采纳，随时撤销</li>
                <li><Check :size="14" />中英文分支、正式版与匿名版 PDF</li>
              </ul>
            </div>
            <div class="bento__mock" aria-hidden="true">
              <div class="mock-chat">
                <div class="mock-bubble mock-bubble--ai"><UiIllustration name="ai-orb" :size="22" />说说你在这个项目里具体负责什么？</div>
                <div class="mock-bubble mock-bubble--me">我负责后端接口，用 Redis 做缓存</div>
                <div class="mock-card">
                  <small><Sparkles :size="12" />候选修改 · 来自你的回答</small>
                  <p>设计并实现订单查询接口，引入 Redis 缓存热点数据</p>
                  <span class="mock-card__actions"><i>采纳</i><i>修改</i></span>
                </div>
              </div>
              <div class="mock-paper"><MiniResume variant="sidebar" accent="#4a44d9" /></div>
            </div>
          </article>
          <article v-reveal="80" class="bento__card">
            <span class="bento__icon bento__icon--accent"><Target :size="18" /></span>
            <h3>岗位匹配</h3>
            <p>粘贴 JD，逐条对照要求与证据，硬性缺口醒目标出，补强建议一键跳回简历。</p>
            <UiIllustration name="dash-match" :size="120" class="bento__art" />
          </article>
          <article v-reveal="160" class="bento__card">
            <span class="bento__icon bento__icon--success"><Compass :size="18" /></span>
            <h3>职业规划</h3>
            <p>基于真实经历生成能力画布，拆成学习计划，再用验证任务确认掌握程度。</p>
            <UiIllustration name="dash-planning" :size="120" class="bento__art" />
          </article>
          <article v-reveal="240" class="bento__card">
            <span class="bento__icon bento__icon--violet"><Mic :size="18" /></span>
            <h3>模拟面试</h3>
            <p>结合你的简历和目标岗位出题，文字或语音作答，逐题给出亮点与改进。</p>
            <UiIllustration name="empty-interview" :size="120" class="bento__art" />
          </article>
        </div>
      </section>

      <!-- ================= TEMPLATES ================= -->
      <section id="templates" class="section section--flush">
        <header v-reveal class="section__head">
          <p class="section__eyebrow">模板中心</p>
          <h2>一份内容，随心换版</h2>
          <p>12 款智能模板共用同一份结构化内容；另有 2,000+ 份开源 Word 模板可预览下载。</p>
        </header>
        <div class="marquee" aria-label="智能模板示例">
          <div class="marquee__track">
            <figure v-for="(item, index) in [...TEMPLATES, ...TEMPLATES]" :key="index" class="marquee__item" :aria-hidden="index >= TEMPLATES.length">
              <MiniResume :variant="item.variant" :accent="item.accent" :name="item.name" :role="item.role" />
              <figcaption>{{ item.label }}</figcaption>
            </figure>
          </div>
        </div>
        <div class="section__more">
          <UiButton variant="secondary" to="/resume-templates" :icon-right="ArrowRight">浏览全部模板</UiButton>
        </div>
      </section>

      <!-- ================= PRINCIPLES ================= -->
      <section class="section">
        <div class="principles">
          <div v-reveal class="principles__intro">
            <p class="section__eyebrow">我们的原则</p>
            <h2>简历上的每一句，<br />面试时都要说得清</h2>
            <p>JobProof 的“Proof”是证据。我们宁可让简历朴素一点，也不让你在面试时被一句夸张的描述问住。</p>
            <UiIllustration name="ink-pen" :size="220" class="principles__art" />
          </div>
          <div class="principles__list">
            <article v-for="(item, index) in PRINCIPLES" :key="item.title" v-reveal="index * 100" class="principle">
              <span class="principle__icon"><component :is="item.icon" :size="20" /></span>
              <div>
                <h3>{{ item.title }}</h3>
                <p>{{ item.text }}</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <!-- ================= FAQ ================= -->
      <section id="faq" class="section section--narrow">
        <header v-reveal class="section__head">
          <p class="section__eyebrow">常见问题</p>
          <h2>还有疑问？</h2>
        </header>
        <div class="faq">
          <div v-for="(item, index) in FAQ" :key="item.q" v-reveal="index * 60" class="faq__item" :class="{ 'is-open': openFaq === index }">
            <button class="faq__q" type="button" :aria-expanded="openFaq === index" :aria-controls="`faq-${index}`" @click="openFaq = openFaq === index ? null : index">
              <span>{{ item.q }}</span>
              <ChevronDown :size="18" class="faq__chevron" />
            </button>
            <div :id="`faq-${index}`" class="faq__a" role="region">
              <div><p>{{ item.a }}</p></div>
            </div>
          </div>
        </div>
      </section>

      <!-- ================= CTA ================= -->
      <section class="cta-wrap">
        <div v-reveal class="cta">
          <div class="cta__glow" aria-hidden="true" />
          <div class="cta__copy">
            <h2>今天就写好你的第一份简历</h2>
            <p>注册只需一分钟。从一次对话开始，把经历变成证据。</p>
            <UiButton size="lg" variant="secondary" class="cta__btn" :to="primaryTo" :icon-right="ArrowRight">{{ primaryLabel }}</UiButton>
          </div>
          <UiIllustration name="rocket" :size="240" class="cta__art" />
        </div>
      </section>
    </main>

    <footer class="footer">
      <div class="footer__inner">
        <div class="footer__brand">
          <BrandMark :size="26" />
          <p>证据驱动的 AI 求职工作台</p>
        </div>
        <nav class="footer__links" aria-label="页脚导航">
          <RouterLink to="/resume-templates">模板中心</RouterLink>
          <RouterLink to="/updates">更新日志</RouterLink>
          <RouterLink to="/terms">用户协议</RouterLink>
          <RouterLink to="/privacy">隐私政策</RouterLink>
        </nav>
        <p class="footer__copy">© {{ year }} JobProof AI</p>
      </div>
    </footer>

    <AuthDialog v-if="authMode" :mode="authMode" :next="loginNext" :reason="loginReason" @close="closeAuth" />
  </div>
</template>

<style scoped>
.landing {
  --landing-max: 1200px;
  overflow-x: clip;
}

/* ---------- Nav ---------- */
.nav {
  position: sticky;
  top: 0;
  z-index: var(--z-topbar);
  border-bottom: 1px solid transparent;
  transition: background-color var(--dur-base), border-color var(--dur-base), backdrop-filter var(--dur-base);
}

.nav.is-scrolled,
.nav.is-open {
  background: color-mix(in srgb, var(--bg-app) 82%, transparent);
  backdrop-filter: saturate(1.4) blur(14px);
  border-bottom-color: var(--border-subtle);
}

.nav__inner {
  max-width: var(--landing-max);
  height: 68px;
  margin: 0 auto;
  padding: 0 var(--space-6);
  display: flex;
  align-items: center;
  gap: var(--space-8);
}

.nav__brand {
  display: flex;
  color: var(--text-primary);
}

.nav__links {
  flex: 1;
  display: flex;
  gap: var(--space-1);
}

.nav__links a {
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: var(--fs-body);
  font-weight: 500;
}

.nav__links a:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}

.nav__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.nav__toggle {
  display: none;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
}

/* ---------- Hero ---------- */
.hero {
  position: relative;
  padding: var(--space-12) var(--space-6) var(--space-16);
}

.hero__bg {
  position: absolute;
  inset: -68px 0 0;
  overflow: hidden;
  pointer-events: none;
}

.hero__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.55;
}

.hero__blob--ink {
  top: 4%;
  right: 6%;
  width: 560px;
  height: 480px;
  background: radial-gradient(circle, rgba(94, 89, 232, 0.42), transparent 70%);
  animation: jp-float 14s ease-in-out infinite;
}

.hero__blob--apricot {
  bottom: -10%;
  left: 18%;
  width: 520px;
  height: 380px;
  background: radial-gradient(circle, rgba(250, 140, 85, 0.32), transparent 70%);
  animation: jp-float 16s ease-in-out -4s infinite;
  --float-distance: 24px;
}

.hero__grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--dot-color) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: radial-gradient(70% 70% at 60% 40%, #000, transparent 75%);
}

.hero__inner {
  position: relative;
  max-width: var(--landing-max);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: var(--space-10);
}

.hero__badge {
  width: fit-content;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px 5px 10px;
  border: 1px solid var(--color-primary-border);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface-1) 70%, transparent);
  color: var(--color-primary-text);
  font-size: var(--fs-sm);
  font-weight: 600;
  box-shadow: var(--shadow-xs);
}

.hero__badge svg {
  color: var(--color-accent);
}

.hero__title {
  margin-top: var(--space-5);
  font-size: clamp(36px, 5vw, 60px);
  line-height: 1.12;
  font-weight: 800;
  letter-spacing: -0.035em;
}

.hero__lede {
  max-width: 540px;
  margin-top: var(--space-5);
  color: var(--text-secondary);
  font-size: var(--fs-body-lg);
  line-height: 1.8;
}

.hero__cta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-8);
}

.hero__trust {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-5);
  margin-top: var(--space-6);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
}

.hero__trust li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.hero__trust svg {
  color: var(--color-success);
}

.hero__art {
  position: relative;
  height: 560px;
}

.art-layer {
  position: absolute;
  inset: 0;
  transition: transform 600ms var(--ease-out);
}

.art {
  position: absolute;
  height: auto;
  filter: drop-shadow(0 18px 26px rgba(49, 45, 144, 0.16));
  animation: jp-float 7s ease-in-out infinite;
}

.art--resume {
  top: 50%;
  left: 50%;
  width: 330px;
  margin: -235px 0 0 -165px;
  --float-rotate: -4deg;
  filter: drop-shadow(0 30px 40px rgba(49, 45, 144, 0.22));
}

.art--check {
  top: 7%;
  right: 4%;
  width: 124px;
  animation-delay: -1.5s;
  --float-distance: -14px;
}

.art--sparkles {
  top: 4%;
  left: 12%;
  width: 84px;
  animation-duration: 5s;
}

.art--chat {
  top: 22%;
  left: -2%;
  width: 128px;
  animation-delay: -3s;
}

.art--target {
  bottom: 9%;
  right: 6%;
  width: 116px;
  animation-delay: -2s;
  --float-distance: -12px;
}

.art--plane {
  top: 30%;
  right: -4%;
  width: 96px;
  animation-delay: -4s;
  --float-rotate: 6deg;
}

.art--pencil {
  bottom: 5%;
  left: 10%;
  width: 90px;
  animation-delay: -2.5s;
}

.chip {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface-overlay) 88%, transparent);
  backdrop-filter: blur(10px);
  box-shadow: var(--shadow-lg);
  animation: jp-float 8s ease-in-out infinite;
}

.chip small {
  color: var(--text-tertiary);
  font-size: 11px;
}

.chip p {
  font-size: var(--fs-sm);
  font-weight: 600;
  line-height: 1.5;
}

.chip--suggest {
  bottom: 26%;
  left: -6%;
  max-width: 300px;
  animation-delay: -1s;
}

.chip--suggest del {
  display: block;
  color: var(--text-tertiary);
  font-weight: 400;
  text-decoration-color: var(--color-danger);
}

.chip--suggest ins {
  display: block;
  text-decoration: none;
  background: linear-gradient(transparent 62%, rgba(250, 140, 85, 0.28) 62%);
}

.chip__icon {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-accent-soft);
  color: var(--color-accent);
}

.chip--score {
  top: 16%;
  right: 22%;
  animation-delay: -3.5s;
}

.chip__ring {
  position: relative;
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
}

.chip__ring svg {
  position: absolute;
  inset: 0;
  transform: rotate(-90deg);
}

.chip__ring circle {
  fill: none;
  stroke: var(--surface-3);
  stroke-width: 4;
}

.chip__ring .chip__ring-bar {
  stroke: var(--color-success);
  stroke-linecap: round;
  stroke-dasharray: 94.2;
  stroke-dashoffset: 13.2;
}

.chip__ring strong {
  position: relative;
  font-size: 13px;
  font-weight: 750;
}

/* ---------- Stats ---------- */
.stats {
  padding: 0 var(--space-6);
}

.stats__inner {
  max-width: var(--landing-max);
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.stat {
  display: grid;
  gap: 4px;
  padding: var(--space-6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--surface-1) 86%, transparent);
  backdrop-filter: blur(6px);
  box-shadow: var(--shadow-sm);
  text-align: center;
}

.stat strong {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  background: var(--gradient-ai);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.stat span {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

/* ---------- Sections ---------- */
.section {
  max-width: var(--landing-max);
  margin: 0 auto;
  padding: var(--space-20) var(--space-6) 0;
}

.section--tinted {
  max-width: none;
  margin-top: var(--space-20);
  padding: var(--space-16) var(--space-6);
  background: linear-gradient(180deg, transparent, color-mix(in srgb, var(--color-primary-soft) 70%, transparent) 30%, transparent);
}

.section--tinted > * {
  max-width: var(--landing-max);
  margin-left: auto;
  margin-right: auto;
}

.section--flush {
  max-width: none;
  padding-left: 0;
  padding-right: 0;
}

.section--narrow {
  max-width: 820px;
}

.section__head {
  max-width: 640px;
  margin: 0 auto var(--space-10);
  text-align: center;
}

.section__eyebrow {
  color: var(--color-primary-text);
  font-size: var(--fs-sm);
  font-weight: 650;
  letter-spacing: 0.04em;
}

.section__head h2,
.principles__intro h2 {
  margin-top: var(--space-2);
  font-size: clamp(26px, 3.4vw, 38px);
  line-height: 1.25;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.section__head > p:last-child {
  margin-top: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--fs-body-lg);
  line-height: 1.7;
}

.section__more {
  display: flex;
  justify-content: center;
  margin-top: var(--space-8);
}

/* ---------- Flow ---------- */
.flow {
  list-style: none;
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-5);
}

.flow::before {
  content: '';
  position: absolute;
  top: 78px;
  left: 12%;
  right: 12%;
  height: 2px;
  background: repeating-linear-gradient(90deg, var(--border-strong) 0 6px, transparent 6px 14px);
}

.flow__step {
  position: relative;
  display: grid;
  justify-items: center;
  padding: var(--space-5) var(--space-4);
  text-align: center;
}

.flow__no {
  color: var(--text-disabled);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 0.1em;
}

.flow__art {
  width: 120px;
  height: 110px;
  margin: var(--space-2) 0 var(--space-3);
  object-fit: contain;
  transition: transform var(--dur-slow) var(--ease-spring);
}

.flow__step:hover .flow__art {
  transform: translateY(-6px) rotate(-4deg) scale(1.06);
}

.flow__step h3 {
  font-size: var(--fs-h3);
  font-weight: 700;
}

.flow__step p {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

/* ---------- Bento ---------- */
.bento {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

.bento__card {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-rows: auto auto auto 1fr;
  gap: var(--space-2);
  min-height: 280px;
  padding: var(--space-6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out);
}

.bento__card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.bento__card--hero {
  grid-column: 1 / -1;
  grid-template-rows: none;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  align-items: center;
  gap: var(--space-8);
  min-height: 360px;
  padding: var(--space-8);
}

.bento__card h3 {
  font-size: var(--fs-h2);
  font-weight: 750;
}

.bento__card p {
  max-width: 420px;
  color: var(--text-secondary);
  font-size: var(--fs-body);
  line-height: 1.75;
}

.bento__text {
  display: grid;
  gap: var(--space-3);
}

.bento__text ul {
  list-style: none;
  display: grid;
  gap: 8px;
  margin-top: var(--space-2);
}

.bento__text li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-sm);
}

.bento__text li svg {
  color: var(--color-success);
}

.bento__icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.bento__icon--accent {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.bento__icon--success {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.bento__icon--violet {
  background: var(--color-primary-soft);
  color: var(--violet-400);
}

.bento__art {
  justify-self: end;
  align-self: end;
  width: 116px;
  height: auto;
  margin: -8px -6px -14px 0;
  transition: transform var(--dur-slow) var(--ease-spring);
}

.bento__card:hover .bento__art {
  transform: translateY(-6px) rotate(-5deg) scale(1.06);
}

.bento__mock {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 190px;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background-color: var(--bg-sunken);
  background-image: radial-gradient(var(--dot-color) 1px, transparent 1px);
  background-size: 18px 18px;
}

.mock-chat {
  display: grid;
  gap: 10px;
}

.mock-bubble {
  width: fit-content;
  max-width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border-radius: 14px;
  font-size: 12px;
  line-height: 1.5;
}

.mock-bubble--ai {
  background: var(--surface-1);
  box-shadow: var(--shadow-xs);
}

.mock-bubble--me {
  justify-self: end;
  background: var(--color-primary);
  color: #fff;
}

.mock-card {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--color-accent-border);
  border-radius: 12px;
  background: var(--color-accent-soft);
}

.mock-card small {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-accent-text);
  font-size: 11px;
  font-weight: 600;
}

.mock-card p {
  max-width: none;
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.6;
}

.mock-card__actions {
  display: flex;
  gap: 6px;
}

.mock-card__actions i {
  padding: 3px 10px;
  border-radius: 6px;
  background: var(--surface-1);
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
}

.mock-card__actions i:first-child {
  background: var(--color-primary);
  color: #fff;
}

.mock-paper {
  transform: rotate(3deg);
  transition: transform var(--dur-slow) var(--ease-out);
}

.bento__card--hero:hover .mock-paper {
  transform: rotate(0deg) translateY(-4px);
}

/* ---------- Marquee ---------- */
.marquee {
  overflow: hidden;
  padding: var(--space-4) 0 var(--space-6);
  mask-image: linear-gradient(90deg, transparent, #000 10%, #000 90%, transparent);
}

.marquee__track {
  display: flex;
  gap: var(--space-6);
  width: max-content;
  animation: marquee 48s linear infinite;
}

.marquee:hover .marquee__track {
  animation-play-state: paused;
}

.marquee__item {
  width: 190px;
  margin: 0;
  display: grid;
  gap: var(--space-3);
  transition: transform var(--dur-base) var(--ease-out);
}

.marquee__item:hover {
  transform: translateY(-6px);
}

.marquee__item figcaption {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 600;
  text-align: center;
}

@keyframes marquee {
  to {
    transform: translateX(calc(-50% - var(--space-3)));
  }
}

/* ---------- Principles ---------- */
.principles {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: var(--space-12);
  align-items: center;
}

.principles__intro > p:not(.section__eyebrow) {
  margin-top: var(--space-4);
  color: var(--text-secondary);
  font-size: var(--fs-body-lg);
  line-height: 1.8;
}

.principles__art {
  width: 220px;
  height: auto;
  margin-top: var(--space-6);
  animation: jp-float 7s ease-in-out infinite;
}

.principles__list {
  display: grid;
  gap: var(--space-4);
}

.principle {
  display: flex;
  gap: var(--space-4);
  padding: var(--space-6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base);
}

.principle:hover {
  transform: translateX(4px);
  box-shadow: var(--shadow-md);
}

.principle__icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--gradient-ai);
  color: #fff;
  box-shadow: var(--shadow-primary);
}

.principle h3 {
  font-size: var(--fs-h3);
  font-weight: 700;
}

.principle p {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.75;
}

/* ---------- FAQ ---------- */
.faq {
  display: grid;
  gap: var(--space-3);
}

.faq__item {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  transition: box-shadow var(--dur-base), border-color var(--dur-base);
}

.faq__item.is-open {
  border-color: var(--border-default);
  box-shadow: var(--shadow-sm);
}

.faq__q {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  font-size: var(--fs-h3);
  font-weight: 600;
  text-align: left;
}

.faq__chevron {
  flex-shrink: 0;
  color: var(--text-tertiary);
  transition: transform var(--dur-base) var(--ease-out);
}

.faq__item.is-open .faq__chevron {
  transform: rotate(180deg);
  color: var(--color-primary);
}

.faq__a {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows var(--dur-slow) var(--ease-out);
}

.faq__item.is-open .faq__a {
  grid-template-rows: 1fr;
}

.faq__a > div {
  overflow: hidden;
}

.faq__a p {
  padding: 0 var(--space-6) var(--space-5);
  color: var(--text-secondary);
  font-size: var(--fs-body);
  line-height: 1.8;
}

/* ---------- CTA ---------- */
.cta-wrap {
  max-width: var(--landing-max);
  margin: 0 auto;
  padding: var(--space-20) var(--space-6);
}

.cta {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-12) var(--space-12);
  border-radius: var(--radius-2xl);
  background: linear-gradient(135deg, #312d90 0%, #4a44d9 50%, #6f6af0 100%);
  color: #fff;
  box-shadow: 0 30px 60px -24px rgba(49, 45, 144, 0.55);
}

.cta__glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(40% 70% at 85% 60%, rgba(250, 140, 85, 0.5), transparent 70%);
}

.cta__copy {
  position: relative;
}

.cta h2 {
  color: #fff;
  font-size: clamp(24px, 3vw, 34px);
  line-height: 1.3;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.cta p {
  margin-top: var(--space-3);
  color: rgba(255, 255, 255, 0.82);
  font-size: var(--fs-body-lg);
}

.cta__btn {
  margin-top: var(--space-6);
}

.cta__art {
  position: relative;
  width: 220px;
  height: auto;
  margin: -40px 0 -60px;
  animation: jp-float 6s ease-in-out infinite;
  --float-distance: -16px;
}

/* ---------- Footer ---------- */
.footer {
  border-top: 1px solid var(--border-subtle);
}

.footer__inner {
  max-width: var(--landing-max);
  margin: 0 auto;
  padding: var(--space-8) var(--space-6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-5);
}

.footer__brand {
  display: grid;
  gap: 6px;
  color: var(--text-primary);
}

.footer__brand p,
.footer__copy {
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
}

.footer__links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-5);
}

.footer__links a {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

/* ---------- Responsive ---------- */
@media (max-width: 1100px) {
  .hero__inner {
    grid-template-columns: 1fr;
  }

  .hero__art {
    height: 520px;
    max-width: 620px;
    width: 100%;
    margin: 0 auto;
  }

  .bento__card--hero {
    grid-template-columns: 1fr;
  }

  .principles {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .nav__links {
    position: absolute;
    top: 68px;
    left: 0;
    right: 0;
    display: none;
    flex-direction: column;
    padding: var(--space-3) var(--space-4) var(--space-5);
    background: var(--surface-overlay);
    border-bottom: 1px solid var(--border-subtle);
    box-shadow: var(--shadow-md);
  }

  .nav.is-open .nav__links {
    display: flex;
    animation: jp-rise-in var(--dur-base) var(--ease-out);
  }

  .nav__toggle {
    display: grid;
  }

  .flow {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .flow::before {
    display: none;
  }

  .bento {
    grid-template-columns: 1fr;
  }

  .stats__inner {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .nav__inner {
    gap: var(--space-3);
    padding: 0 var(--space-4);
  }

  .nav__actions :deep(.ui-btn--ghost) {
    display: none;
  }

  .hero {
    padding: var(--space-6) var(--space-4) var(--space-12);
  }

  .hero__art {
    height: 380px;
  }

  .art--resume {
    width: 220px;
    margin: -155px 0 0 -110px;
  }

  .art--chat,
  .art--plane,
  .art--pencil {
    display: none;
  }

  .art--check {
    width: 86px;
  }

  .art--target {
    width: 84px;
  }

  .chip--suggest {
    left: 0;
    bottom: 4%;
    max-width: 250px;
  }

  .chip--score {
    top: 2%;
    right: auto;
    left: 0;
  }

  .section,
  .cta-wrap {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }

  .bento__mock {
    grid-template-columns: 1fr;
  }

  .mock-paper {
    display: none;
  }

  .cta {
    flex-direction: column;
    align-items: flex-start;
    padding: var(--space-8) var(--space-6);
  }

  .cta__art {
    width: 140px;
    margin: 0;
    align-self: flex-end;
  }

  .faq__q {
    padding: var(--space-4);
    font-size: var(--fs-body);
  }

  .faq__a p {
    padding: 0 var(--space-4) var(--space-4);
  }
}
</style>
