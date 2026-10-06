<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  ArrowRight, Bell, CheckCircle2, ChevronRight, Circle, Compass, FileText, Mic, Plus, Sparkles, Target,
} from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import UiProgressRing from '@/shared/ui/UiProgressRing.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import type { IllustrationName } from '@/shared/ui/illustrations'
import { formatRelative, greeting } from '@/shared/lib/datetime'
import { useSessionStore } from '@/stores/session'
import { listNotifications } from '@/features/notification/services/notificationApi'
import { notificationKeys } from '@/features/notification/queries'
import { useWorkspaceOverview } from '../api'
import { isNewUser, nextStep, readiness } from '../nextStep'
import ResumeMiniCard from '../components/ResumeMiniCard.vue'
import { summaryRoute } from '@/features/job-match/utils/stage'

const session = useSessionStore()
const overview = useWorkspaceOverview()
const notifications = useQuery({
  queryKey: notificationKeys.list('recent', 0),
  queryFn: () => listNotifications(0, 5),
  staleTime: 30_000,
})

const data = computed(() => overview.data.value)
const step = computed(() => (data.value ? nextStep(data.value) : null))
const ready = computed(() => (data.value ? readiness(data.value) : null))
const fresh = computed(() => (data.value ? isNewUser(data.value) : false))
const firstName = computed(() => {
  const name = session.displayName
  return name.includes('@') ? name.split('@')[0] : name
})
const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long', timeZone: 'Asia/Shanghai' }).format(new Date())

const START_STEPS: Array<{ title: string; text: string; art: IllustrationName; to: string; cta: string }> = [
  { title: '和 AI 聊出一份简历', text: '一次只问一个问题，确认后才写入，右侧实时排版。', art: 'hero-resume', to: '/ai-resume/new', cta: '开始写简历' },
  { title: '用真实 JD 测匹配度', text: '逐条对照岗位要求与简历证据，看清优势和缺口。', art: 'dash-match', to: '/job-match/new', cta: '新建匹配' },
  { title: '针对岗位模拟面试', text: '结合你的简历出题，文字或语音作答，逐题反馈。', art: 'dash-interview', to: '/mock-interviews/new', cta: '开始练习' },
]

const QUICK: Array<{ title: string; text: string; art: IllustrationName; to: string }> = [
  { title: '新建简历', text: 'AI 引导 · 实时 A4', art: 'empty-resume', to: '/ai-resume/new' },
  { title: '岗位匹配', text: '粘贴 JD 逐条对照', art: 'dash-match', to: '/job-match/new' },
  { title: '模拟面试', text: '文字 / 语音作答', art: 'empty-interview', to: '/mock-interviews/new' },
  { title: '能力画布', text: '规划成长路径', art: 'dash-planning', to: '/career-planning/new' },
]

const MATCH_STATUS: Record<string, { label: string; tone: 'success' | 'primary' | 'warning' | 'neutral' | 'danger' }> = {
  COMPLETED: { label: '已完成', tone: 'success' },
  ANALYZING: { label: '分析中', tone: 'primary' },
  NEEDS_CLARIFICATION: { label: '待补充', tone: 'warning' },
  ANALYSIS_PAUSED: { label: '已暂停', tone: 'neutral' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
  JD_PARSE_FAILED: { label: '解析失败', tone: 'danger' },
}

function matchStatus(status: string) {
  return MATCH_STATUS[status] ?? { label: '进行中', tone: 'primary' as const }
}

const matchLink = summaryRoute
</script>

<template>
  <div class="page-container dashboard">
    <!-- Loading -->
    <template v-if="overview.isPending.value">
      <section class="hero hero--skeleton surface-card">
        <div class="hero__copy">
          <UiSkeleton width="40%" height="16px" />
          <UiSkeleton width="70%" height="30px" />
          <UiSkeleton :lines="2" />
          <UiSkeleton width="140px" height="40px" radius="10px" />
        </div>
        <UiSkeleton circle height="120px" />
      </section>
      <div class="grid-auto" style="--grid-min: 240px">
        <UiSkeleton v-for="n in 4" :key="n" height="150px" radius="var(--radius-lg)" />
      </div>
    </template>

    <UiErrorState
      v-else-if="overview.isError.value"
      title="工作台加载失败"
      :error="overview.error.value"
      :retrying="overview.isFetching.value"
      @retry="overview.refetch()"
    />

    <template v-else-if="data && step && ready">
      <!-- Hero -->
      <section class="hero surface-card animate-rise" :class="{ 'hero--fresh': fresh }">
        <div class="hero__glow" aria-hidden="true" />
        <div class="hero__copy">
          <p class="hero__eyebrow">{{ today }}</p>
          <h1 class="hero__title">{{ greeting() }}，{{ firstName }}</h1>
          <div class="hero__next">
            <span class="hero__next-label"><Sparkles :size="14" /> 下一步建议</span>
            <h2>{{ step.title }}</h2>
            <p>{{ step.description }}</p>
            <div class="hero__actions">
              <UiButton size="lg" :to="step.to" :icon-right="ArrowRight">{{ step.cta }}</UiButton>
              <UiButton v-if="!fresh" size="lg" variant="ghost" to="/resumes">查看全部简历</UiButton>
            </div>
          </div>
        </div>
        <UiIllustration v-if="fresh" name="welcome-desk" :size="340" class="hero__art" eager />
        <aside v-else class="readiness" aria-label="求职准备度">
          <UiProgressRing :value="ready.score" :size="128" :stroke="10" tone="ai" label="求职准备度" suffix="%">
            <span class="readiness__value">
              <strong>{{ ready.score }}<small>%</small></strong>
              <span>准备度</span>
            </span>
          </UiProgressRing>
          <ul class="readiness__list">
            <li v-for="item in ready.items" :key="item.key">
              <RouterLink :to="item.to" class="readiness__item" :class="{ 'is-done': item.done }">
                <CheckCircle2 v-if="item.done" :size="16" />
                <Circle v-else :size="16" />
                <span>{{ item.label }}</span>
                <ChevronRight :size="14" class="readiness__go" />
              </RouterLink>
            </li>
          </ul>
        </aside>
      </section>

      <p v-if="data.degraded.length" class="degraded">部分模块暂时没有加载成功，显示的数据可能不完整。</p>

      <!-- New user: three steps -->
      <section v-if="fresh" class="block">
        <header class="block__head">
          <h2>三步开始你的求职准备</h2>
        </header>
        <ol class="start stagger">
          <li v-for="(item, index) in START_STEPS" :key="item.title" class="start__card surface-card" :style="{ '--i': index }">
            <span class="start__num">{{ index + 1 }}</span>
            <UiIllustration :name="item.art" :size="104" class="start__art" />
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
            <UiButton :variant="index === 0 ? 'primary' : 'secondary'" size="sm" :to="item.to" :icon-right="ArrowRight">{{ item.cta }}</UiButton>
          </li>
        </ol>
      </section>

      <!-- Continue working -->
      <section v-else-if="data.resumes?.recent.length" class="block">
        <header class="block__head">
          <h2>继续上次的工作</h2>
          <RouterLink to="/resumes" class="block__link">全部简历<ChevronRight :size="14" /></RouterLink>
        </header>
        <div class="resumes stagger">
          <ResumeMiniCard v-for="(resume, index) in data.resumes.recent.slice(0, 4)" :key="resume.id" :resume="resume" :style="{ '--i': index }" />
          <RouterLink to="/ai-resume/new" class="resume-new" :style="{ '--i': data.resumes.recent.length }">
            <span class="resume-new__icon"><Plus :size="20" /></span>
            <strong>新建简历</strong>
            <small>AI 引导，从零开始</small>
          </RouterLink>
        </div>
      </section>

      <div class="columns">
        <!-- In progress -->
        <section class="block">
          <header class="block__head"><h2>进行中</h2></header>
          <div class="surface-card progress-list">
            <RouterLink
              v-for="match in data.jobMatches?.recent.slice(0, 3) ?? []"
              :key="match.id"
              :to="matchLink(match)"
              class="progress-row"
            >
              <span class="progress-row__icon progress-row__icon--match"><Target :size="17" /></span>
              <span class="progress-row__main">
                <strong>{{ match.title }}</strong>
                <small>{{ match.company || '岗位匹配' }} · {{ formatRelative(match.updatedAt) }}</small>
              </span>
              <span v-if="match.score != null" class="progress-row__score num">{{ match.score }}<small>分</small></span>
              <UiBadge :tone="matchStatus(match.status).tone" size="sm">{{ matchStatus(match.status).label }}</UiBadge>
            </RouterLink>
            <RouterLink
              v-if="data.mockInterviews?.resumable"
              :to="`/mock-interviews/${encodeURIComponent(data.mockInterviews.resumable.id)}/session`"
              class="progress-row"
            >
              <span class="progress-row__icon progress-row__icon--interview"><Mic :size="17" /></span>
              <span class="progress-row__main">
                <strong>{{ data.mockInterviews.resumable.title }}</strong>
                <small>模拟面试 · 已答 {{ data.mockInterviews.resumable.answeredCount }}/{{ data.mockInterviews.resumable.questionCount }}</small>
              </span>
              <UiBadge tone="warning" size="sm">未完成</UiBadge>
            </RouterLink>
            <RouterLink
              v-for="canvas in data.careerCanvases?.recent.slice(0, 2) ?? []"
              :key="canvas.sessionId"
              :to="`/career-planning/${encodeURIComponent(canvas.sessionId)}`"
              class="progress-row"
            >
              <span class="progress-row__icon progress-row__icon--plan"><Compass :size="17" /></span>
              <span class="progress-row__main">
                <strong>{{ canvas.title }}</strong>
                <small>能力画布 · {{ canvas.nodeCount }} 个节点 · 进度 {{ canvas.progress }}%</small>
              </span>
              <span class="progress-row__bar" :style="{ '--p': (canvas.progress || 0) / 100 }" />
            </RouterLink>
            <div
              v-if="!(data.jobMatches?.recent.length) && !data.mockInterviews?.resumable && !(data.careerCanvases?.recent.length)"
              class="progress-empty"
            >
              <UiIllustration name="hero-sparkles" :size="72" />
              <p>还没有进行中的匹配、面试或规划。</p>
            </div>
          </div>
        </section>

        <!-- Quick create + activity -->
        <section class="block">
          <header class="block__head"><h2>快捷创建</h2></header>
          <div class="quick">
            <RouterLink v-for="item in QUICK" :key="item.title" :to="item.to" class="quick__tile">
              <UiIllustration :name="item.art" :size="56" />
              <span><strong>{{ item.title }}</strong><small>{{ item.text }}</small></span>
            </RouterLink>
          </div>

          <header class="block__head block__head--spaced">
            <h2>最近动态</h2>
            <RouterLink to="/notifications" class="block__link">通知中心<ChevronRight :size="14" /></RouterLink>
          </header>
          <div class="surface-card activity">
            <template v-if="notifications.isPending.value">
              <div v-for="n in 3" :key="n" class="activity__row"><UiSkeleton :lines="2" /></div>
            </template>
            <template v-else-if="notifications.data.value?.items.length">
              <component
                :is="item.actionPath ? 'RouterLink' : 'div'"
                v-for="item in notifications.data.value.items"
                :key="item.id"
                :to="item.actionPath || undefined"
                class="activity__row"
                :class="{ 'is-unread': item.status === 'DELIVERED' }"
              >
                <span class="activity__dot" aria-hidden="true"><Bell :size="14" /></span>
                <span class="activity__text">
                  <strong>{{ item.title }}</strong>
                  <small>{{ formatRelative(item.createdAt) }}</small>
                </span>
              </component>
            </template>
            <p v-else class="activity__empty"><FileText :size="15" />暂时没有新动态，完成导出或分析后会通知你。</p>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard {
  display: grid;
  gap: var(--space-8);
}

/* ---------- Hero ---------- */
.hero {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-8);
  padding: var(--space-8) var(--space-10);
  border-radius: var(--radius-2xl);
}

.hero--skeleton .hero__copy {
  display: grid;
  gap: 14px;
}

.hero__glow {
  position: absolute;
  inset: 0;
  background: var(--gradient-hero-glow);
  pointer-events: none;
}

.hero__copy,
.readiness {
  position: relative;
}

.hero__eyebrow {
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
}

.hero__title {
  margin-top: 4px;
  font-size: 30px;
  line-height: 38px;
  letter-spacing: -0.02em;
}

.hero__next {
  margin-top: var(--space-6);
  max-width: 560px;
}

.hero__next-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: var(--fs-xs);
  font-weight: 650;
}

.hero__next h2 {
  margin-top: var(--space-3);
  font-size: 22px;
  line-height: 30px;
}

.hero__next p {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: var(--fs-body);
  line-height: 1.7;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.hero__art {
  position: relative;
  width: 340px;
  height: auto;
  margin: -16px -8px -28px 0;
  filter: drop-shadow(0 18px 30px rgba(74, 68, 217, 0.14));
  animation: jp-float 7s ease-in-out infinite;
  --float-distance: -8px;
}

.readiness {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--surface-1) 80%, transparent);
  backdrop-filter: blur(6px);
}

.readiness__value {
  display: grid;
  justify-items: center;
  line-height: 1.1;
}

.readiness__value strong {
  font-size: 30px;
  font-weight: 750;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.readiness__value strong small {
  font-size: 15px;
  color: var(--text-tertiary);
}

.readiness__value span {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.readiness__list {
  list-style: none;
  display: grid;
  gap: 2px;
}

.readiness__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  white-space: nowrap;
}

.readiness__item:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}

.readiness__item.is-done {
  color: var(--text-primary);
}

.readiness__item.is-done svg:first-child {
  color: var(--color-success);
}

.readiness__go {
  margin-left: auto;
  opacity: 0;
  transition: opacity var(--dur-fast);
}

.readiness__item:hover .readiness__go {
  opacity: 1;
}

.degraded {
  margin-top: calc(var(--space-6) * -1);
  color: var(--color-warning-text);
  font-size: var(--fs-sm);
}

/* ---------- Blocks ---------- */
.block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.block__head--spaced {
  margin-top: var(--space-6);
}

.block__head h2 {
  font-size: var(--fs-h3);
  line-height: var(--lh-h3);
  font-weight: 650;
}

.block__link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.block__link:hover {
  color: var(--color-primary-text);
}

.start {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

.start__card {
  position: relative;
  display: grid;
  justify-items: start;
  gap: var(--space-2);
  padding: var(--space-6);
  border-radius: var(--radius-xl);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out);
}

.start__card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.start__num {
  position: absolute;
  top: var(--space-5);
  right: var(--space-5);
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-3);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  font-weight: 700;
}

.start__art {
  width: 104px;
  height: 96px;
  margin: -6px 0 6px -6px;
  object-fit: contain;
  object-position: left bottom;
  transition: transform var(--dur-slow) var(--ease-spring);
}

.start__card:hover .start__art {
  transform: translateY(-4px) rotate(-3deg) scale(1.04);
}

.start__card h3 {
  font-size: var(--fs-h3);
}

.start__card p {
  min-height: 44px;
  margin-bottom: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.resumes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-4);
}

.resume-new {
  min-height: 236px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 6px;
  border: 1.5px dashed var(--border-default);
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
  transition: border-color var(--dur-base), background-color var(--dur-base), color var(--dur-base);
}

.resume-new:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.resume-new__icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.resume-new small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.columns {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: var(--space-6);
  align-items: start;
}

.progress-list {
  padding: 6px;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 12px;
  border-radius: var(--radius-md);
  color: var(--text-primary);
  transition: background-color var(--dur-fast);
}

.progress-row + .progress-row {
  border-top: 1px solid var(--border-subtle);
}

.progress-row:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}

.progress-row__icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
}

.progress-row__icon--match {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.progress-row__icon--interview {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.progress-row__icon--plan {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.progress-row__main {
  flex: 1;
  min-width: 0;
  display: grid;
}

.progress-row__main strong {
  overflow: hidden;
  font-size: var(--fs-body);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-row__main small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.progress-row__score {
  font-size: 18px;
  font-weight: 700;
}

.progress-row__score small {
  margin-left: 1px;
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 500;
}

.progress-row__bar {
  position: relative;
  width: 64px;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-3);
}

.progress-row__bar::after {
  content: '';
  position: absolute;
  inset: 0;
  width: calc(var(--p) * 100%);
  border-radius: inherit;
  background: var(--color-success);
}

.progress-empty {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding: var(--space-8) var(--space-4);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
}

.quick {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.quick__tile {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  color: var(--text-primary);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out), border-color var(--dur-base);
}

.quick__tile:hover {
  transform: translateY(-2px);
  border-color: var(--border-default);
  box-shadow: var(--shadow-md);
  color: var(--text-primary);
}

.quick__tile :deep(img) {
  width: 48px;
  height: 48px;
  transition: transform var(--dur-slow) var(--ease-spring);
}

.quick__tile:hover :deep(img) {
  transform: scale(1.1) rotate(-4deg);
}

.quick__tile span {
  display: grid;
  min-width: 0;
}

.quick__tile strong {
  font-size: var(--fs-body);
  font-weight: 600;
}

.quick__tile small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.activity {
  padding: 6px;
}

.activity__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: 10px;
  border-radius: var(--radius-md);
  color: var(--text-primary);
}

a.activity__row:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}

.activity__dot {
  position: relative;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-3);
  color: var(--text-tertiary);
}

.activity__row.is-unread .activity__dot {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.activity__row.is-unread .activity__dot::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-accent);
  box-shadow: 0 0 0 2px var(--surface-1);
}

.activity__text {
  display: grid;
  min-width: 0;
}

.activity__text strong {
  font-size: var(--fs-sm);
  font-weight: 550;
}

.activity__text small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.activity__empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: var(--space-5);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
}

@media (max-width: 1279px) {
  .hero__art {
    width: 260px;
  }
}

@media (max-width: 900px) {
  .hero__art {
    display: none;
  }
}

@media (max-width: 1100px) {
  .hero {
    grid-template-columns: 1fr;
    padding: var(--space-6);
  }

  .columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .start {
    grid-template-columns: 1fr;
  }

  .readiness {
    flex-direction: column;
    align-items: stretch;
  }

  .readiness :deep(.ui-ring) {
    align-self: center;
  }

  .quick {
    grid-template-columns: 1fr;
  }
}
</style>
