<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell, CheckCircle2, Eye, LoaderCircle, Network,
  Play, Search, Sparkles, Star, Target,
} from 'lucide-vue-next'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import { jobProofIconIds } from '@/shared/ui/jobProofIcons'
import { useToastFeedback } from '@/shared/ui/toast'
import JobTaxonomyPicker from '@/features/ai-resume/components/JobTaxonomyPicker.vue'
import type { JobTaxonomySelection } from '@/features/ai-resume/types'
import { errorMessage } from '@/shared/api/types'
import {
  createCareerCanvas, fetchCareerCanvasDashboard, makeCareerCanvasPrimary,
} from '../services/careerPlanningApi'
import type { CareerCanvasDashboard, CareerCanvasSummary } from '../types'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const pending = ref('')
const error = ref('')
useToastFeedback(error, 'error', 'career-canvas-overview-error')
const dashboard = ref<CareerCanvasDashboard | null>(null)
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const status = ref(typeof route.query.status === 'string' ? route.query.status : 'ALL')
const sort = ref(typeof route.query.sort === 'string' ? route.query.sort : 'RECENT')
const newOpen = ref(false)
const selectedTargetId = ref('')
const selectedTargetName = ref('')
let reloadTimer = 0

const statusOptions = [
  { value: 'ALL', label: '全部状态' },
  { value: 'ACTIVE', label: '进行中' },
  { value: 'PAUSED', label: '已暂停' },
]
const sortOptions = [
  { value: 'RECENT', label: '最近更新' },
  { value: 'PROGRESS', label: '进度最高' },
  { value: 'TITLE', label: '职业名称' },
]
const accents = [
  { solid: '#2563eb', soft: '#edf3ff' },
  { solid: '#0e7490', soft: '#eaf7fa' },
  { solid: '#19a763', soft: '#e7f8ef' },
  { solid: '#df7b1f', soft: '#fff1df' },
]

const items = computed(() => dashboard.value?.items ?? [])
const stats = computed(() => dashboard.value?.stats ?? {
  canvasCount: 0, primaryCount: 0, abilityNodeCount: 0, pendingValidationCount: 0, versionCount: 0,
})

function accent(index: number) {
  return accents[index % accents.length]
}

function chooseTarget(selection: JobTaxonomySelection): void {
  selectedTargetId.value = selection.job.id
  selectedTargetName.value = selection.job.displayName
}

function closeNew(): void {
  if (pending.value === 'create') return
  newOpen.value = false
  selectedTargetId.value = ''
  selectedTargetName.value = ''
}

async function load(): Promise<void> {
  error.value = ''
  try {
    dashboard.value = await fetchCareerCanvasDashboard(query.value, status.value, sort.value)
  } catch (reason) {
    error.value = errorMessage(reason, '能力画布总览读取失败')
  } finally {
    loading.value = false
  }
}

function scheduleLoad(): void {
  window.clearTimeout(reloadTimer)
  reloadTimer = window.setTimeout(async () => {
    await router.replace({
      query: {
        ...(query.value.trim() ? { q: query.value.trim() } : {}),
        ...(status.value !== 'ALL' ? { status: status.value } : {}),
        ...(sort.value !== 'RECENT' ? { sort: sort.value } : {}),
      },
    })
    await load()
  }, 240)
}

async function createCanvas(): Promise<void> {
  if (!selectedTargetId.value) {
    error.value = '请先从完整岗位库中选择目标职业。'
    return
  }
  pending.value = 'create'
  error.value = ''
  try {
    const session = await createCareerCanvas(selectedTargetId.value, true)
    newOpen.value = false
    selectedTargetId.value = ''
    selectedTargetName.value = ''
    await router.push({ name: 'career-planning-session', params: { sessionId: session.id }, query: { view: 'canvas', source: 'new' } })
  } catch (reason) {
    error.value = errorMessage(reason, '新建能力画布失败')
  } finally {
    pending.value = ''
  }
}

async function makePrimary(item: CareerCanvasSummary): Promise<void> {
  if (item.primary || pending.value) return
  pending.value = `primary:${item.sessionId}`
  error.value = ''
  try {
    dashboard.value = await makeCareerCanvasPrimary(item.sessionId)
  } catch (reason) {
    error.value = errorMessage(reason, '主目标更新失败')
  } finally {
    pending.value = ''
  }
}

function openCanvas(item: CareerCanvasSummary, continuePlan = false): void {
  const view = continuePlan && item.planStatus !== 'NOT_CREATED' ? 'plan' : 'canvas'
  void router.push({ name: 'career-planning-session', params: { sessionId: item.sessionId }, query: { view } })
}

function openNew(): void {
  newOpen.value = true
}

function relativeTime(value: string): string {
  const date = new Date(value)
  const diff = Date.now() - date.getTime()
  if (!Number.isFinite(diff)) return '刚刚更新'
  const minutes = Math.max(0, Math.floor(diff / 60_000))
  if (minutes < 1) return '刚刚更新'
  if (minutes < 60) return `更新于 ${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `更新于 ${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 8) return `更新于 ${days} 天前`
  return `更新于 ${date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })}`
}

function statusLabel(item: CareerCanvasSummary): string {
  if (item.primary) return '主目标'
  return item.status === 'PAUSED' ? '已暂停' : item.overallProgress < 50 ? '探索中' : '进行中'
}

function planText(item: CareerCanvasSummary): string {
  if (item.planStatus === 'NOT_CREATED') return '尚未创建'
  if (item.planStatus === 'PAUSED') return item.currentWeek ? `暂停于第 ${item.currentWeek} 周` : '计划已暂停'
  if (item.planStatus === 'COMPLETED') return '计划已完成'
  return item.currentWeek && item.durationWeeks ? `第 ${item.currentWeek} / ${item.durationWeeks} 周` : '计划进行中'
}

watch([query, status, sort], scheduleLoad)
onMounted(load)
onBeforeUnmount(() => window.clearTimeout(reloadTimer))
</script>

<template>
    <main class="canvas-overview-page">
      <header class="overview-heading">
        <div>
          <h1>能力画布总览</h1>
          <p>一个账号可管理多个职业目标，每张画布独立保存学习计划、能力验证和版本记录。</p>
        </div>
        <nav aria-label="能力画布快捷操作">
          <RouterLink class="header-tool" to="/notifications"><Bell :size="17" /><span>通知</span></RouterLink>
          <button class="new-canvas-button" type="button" @click="openNew"><JobProofIcon :name="jobProofIconIds.newCanvas" :size="18" />新建能力画布</button>
        </nav>
      </header>

      <section class="overview-stats" aria-label="能力画布统计">
        <article><span class="is-blue"><JobProofIcon :name="jobProofIconIds.abilityCanvas" :size="20" /></span><div><strong>{{ stats.canvasCount }}</strong><p>能力画布 · {{ stats.primaryCount }} 个主目标</p></div></article>
        <article><span class="is-green"><Network :size="20" /></span><div><strong>{{ stats.abilityNodeCount }}</strong><p>已纳入能力节点</p></div></article>
        <article><span class="is-orange"><JobProofIcon :name="jobProofIconIds.abilityValidation" :size="20" /></span><div><strong>{{ stats.pendingValidationCount }}</strong><p>等待能力验证</p></div></article>
        <article><span class="is-purple"><JobProofIcon :name="jobProofIconIds.versionHistory" :size="20" /></span><div><strong>{{ stats.versionCount }}</strong><p>累计画布版本</p></div></article>
      </section>

      <section class="canvas-list-section">
        <header>
          <div><h2>我的能力画布</h2><em>{{ stats.canvasCount }}</em></div>
          <div class="canvas-filters">
            <label><Search :size="16" /><input v-model="query" type="search" placeholder="搜索职业或能力节点" aria-label="搜索职业或能力节点"></label>
            <AppSelect v-model="status" :options="statusOptions" aria-label="筛选能力画布状态" />
            <AppSelect v-model="sort" :options="sortOptions" aria-label="能力画布排序" />
          </div>
        </header>

        <div v-if="loading" class="overview-loading"><LoaderCircle class="spin" :size="24" />正在整理你的职业画布</div>
        <div v-else class="canvas-card-grid">
          <article
            v-for="(item, index) in items"
            :key="item.sessionId"
            class="canvas-summary-card"
            :style="{ '--accent': accent(index).solid, '--accent-soft': accent(index).soft }"
          >
            <header>
              <span class="canvas-monogram">{{ item.title.trim().slice(0, 2) }}</span>
              <div class="card-status-actions">
                <span class="canvas-status" :class="{ paused: item.status === 'PAUSED' }">{{ statusLabel(item) }}</span>
                <button v-if="!item.primary" type="button" title="设为主目标" :disabled="Boolean(pending)" @click="makePrimary(item)"><Star :size="15" /></button>
              </div>
            </header>
            <h3>{{ item.title }}</h3>
            <p class="canvas-meta">独立规划 · {{ relativeTime(item.updatedAt) }}</p>

            <section class="progress-block" :aria-label="`总体进度 ${item.overallProgress}%`">
              <div><strong>{{ item.overallProgress }}%</strong><span>总体进度</span></div>
              <i><b :style="{ width: `${item.overallProgress}%` }" /></i>
            </section>

            <dl class="canvas-metrics">
              <div><dt>能力画布</dt><dd>{{ item.nodeCount }} 个节点</dd></div>
              <div><dt>学习计划</dt><dd>{{ planText(item) }}</dd></div>
              <div><dt>能力验证</dt><dd>{{ item.pendingValidationCount }} 项待验证</dd></div>
              <div><dt>版本记录</dt><dd>v{{ item.canvasVersion }}<template v-if="item.planRevision"> · 计划 {{ item.planRevision }}</template></dd></div>
            </dl>

            <section v-if="item.currentFocus || item.nodeCount" class="current-focus">
              <small>当前进行</small>
              <strong>{{ item.currentFocus || (item.nodeCount > 0 ? '继续完善能力节点与证据' : '等待 AI 生成完整能力树') }}</strong>
            </section>

            <section v-if="item.recentChanges.length || item.nodeCount" class="recent-changes">
              <h4>最近变化</h4>
              <p v-for="(change, changeIndex) in item.recentChanges.slice(0, 2)" :key="`${item.sessionId}-${changeIndex}`"><i />{{ change }}</p>
              <p v-if="!item.recentChanges.length"><i />第一版目标画布已创建</p>
              <p><i class="muted" />历史版本可查看与恢复</p>
            </section>

            <footer>
              <button type="button" @click="openCanvas(item)"><Eye :size="16" />查看总览</button>
              <button class="card-primary" type="button" @click="openCanvas(item, true)"><Play :size="16" />继续推进</button>
            </footer>
          </article>

          <button class="create-canvas-card" type="button" @click="openNew">
            <span><JobProofIcon :name="jobProofIconIds.newCanvas" :size="34" /></span>
            <strong>创建新的能力画布</strong>
            <p>选择目标职业后，AI 将生成独立的能力树、学习计划与验证标准。</p>
            <em>开始新建</em>
          </button>

          <section v-if="!items.length && (query || status !== 'ALL')" class="canvas-empty-result">
            <Search :size="26" /><strong>没有匹配的能力画布</strong><p>调整关键词或状态筛选后再试。</p>
          </section>
        </div>
      </section>
    </main>

    <AppModal :open="newOpen" title="新建能力画布" :width="760" mobile-sheet @close="closeNew">
      <section class="new-canvas-dialog">
        <header><span><Sparkles :size="23" /></span><div><h2>选择新的目标职业</h2><p>新画布不会复制旧节点。确认目标后只创建根节点，由 AI 重新生成完整能力树。</p></div></header>
        <JobTaxonomyPicker :selected-name="selectedTargetName" :selected-node-id="selectedTargetId" :disabled="pending === 'create'" @select="chooseTarget" />
        <p class="dialog-safety"><CheckCircle2 :size="16" />旧画布、学习计划、验证和版本记录均会完整保留。</p>
      </section>
      <template #footer>
        <button class="dialog-secondary" type="button" :disabled="pending === 'create'" @click="router.push({ name: 'career-planning-new' })"><Target :size="16" />让 AI 帮我选择方向</button>
        <button class="dialog-cancel" type="button" :disabled="pending === 'create'" @click="closeNew">取消</button>
        <button class="dialog-create" type="button" :disabled="!selectedTargetId || pending === 'create'" @click="createCanvas"><LoaderCircle v-if="pending === 'create'" class="spin" :size="16" /><JobProofIcon v-else :name="jobProofIconIds.newCanvas" :size="16" />创建目标画布</button>
      </template>
    </AppModal>
</template>

<style scoped>
.canvas-overview-page {
  min-height: 100vh;
  padding: 28px 32px 40px;
  background: var(--bg);
  color: var(--text);
}
.mobile-overview-title { font-size: 15px; }
.overview-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.overview-heading h1 { margin: 0; font-size: 28px; line-height: 1.3; letter-spacing: 0; }
.overview-heading p { max-width: 700px; margin: 8px 0 0; color: var(--text-2); font-size: 14px; line-height: 1.65; }
.overview-heading nav { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.header-tool, .new-canvas-button {
  min-height: 44px; display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  padding: 0 16px; border: 1px solid var(--border-strong); border-radius: 8px;
  color: var(--text-2); background: var(--surface); font-size: 14px;
  transition: background-color 160ms ease, border-color 160ms ease;
}
.new-canvas-button { border-color: var(--primary); color: #fff; background: var(--primary); font-weight: 600; }
.new-canvas-button:hover { background: var(--primary-hover); border-color: var(--primary-hover); }
.header-tool:hover { border-color: var(--primary); color: var(--primary); }
.overview-stats {
  margin-top: 24px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--border); border-radius: 12px; background: var(--surface); overflow: hidden;
}
.overview-stats article { min-width: 0; min-height: 80px; padding: 16px 20px; display: flex; align-items: center; gap: 12px; }
.overview-stats article + article { border-left: 1px solid var(--border); }
.overview-stats article > span { width: 36px; height: 36px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 8px; }
.overview-stats .is-blue { color: var(--primary); background: var(--primary-soft); }
.overview-stats .is-green { color: #15803d; background: var(--success-soft); }
.overview-stats .is-orange { color: #a85e09; background: var(--warning-soft); }
.overview-stats .is-purple { color: #0e7490; background: #eaf7fa; }
.overview-stats article > div { min-width: 0; display: grid; gap: 4px; }
.overview-stats strong { font-size: 26px; line-height: 1.1; font-variant-numeric: tabular-nums; }
.overview-stats p { margin: 0; color: var(--text-2); font-size: 12px; line-height: 1.5; }
.canvas-list-section { margin-top: 28px; }
.canvas-list-section > header { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.canvas-list-section > header > div:first-child { display: flex; align-items: center; gap: 8px; }
.canvas-list-section h2 { margin: 0; font-size: 20px; }
.canvas-list-section h2 + em { min-width: 26px; padding: 2px 8px; border-radius: 6px; color: var(--text-2); background: #e9edf2; font-size: 12px; font-style: normal; text-align: center; }
.canvas-filters { display: grid; grid-template-columns: minmax(190px, 260px) 120px 120px; gap: 8px; }
.canvas-filters > label { height: 42px; padding: 0 12px; display: flex; align-items: center; gap: 8px; border: 1px solid var(--border-strong); border-radius: 8px; color: var(--text-3); background: var(--surface); }
.canvas-filters input { min-width: 0; width: 100%; height: 40px; padding: 0; border: 0; outline: 0; color: var(--text); background: transparent; font-size: 13px; }
.canvas-filters > label:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px #2563eb15; }
.canvas-filters :deep(.app-select__trigger) { min-height: 42px; border-radius: 8px; }
.overview-loading { min-height: 300px; display: flex; align-items: center; justify-content: center; gap: 12px; color: var(--text-2); }
.canvas-card-grid { margin-top: 16px; display: grid; grid-template-columns: repeat(auto-fill, minmax(285px, 1fr)); gap: 16px; align-items: start; }
.canvas-summary-card {
  position: relative; min-width: 0; min-height: 300px; height: 100%; padding: 20px;
  display: flex; flex-direction: column; overflow: hidden; border: 1px solid var(--border); border-radius: 12px;
  background: var(--surface); box-shadow: var(--shadow-s); transition: border-color 180ms ease, box-shadow 180ms ease;
}
.canvas-summary-card::before { position: absolute; inset: 0 0 auto; height: 3px; background: var(--accent); content: ''; }
.canvas-summary-card:hover { border-color: #b7c7db; box-shadow: var(--shadow-m); }
.canvas-summary-card > header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.canvas-monogram { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 8px; color: var(--accent); background: var(--accent-soft); font-size: 12px; font-weight: 700; }
.card-status-actions { display: flex; align-items: center; gap: 4px; }
.canvas-status { padding: 3px 8px; border-radius: 6px; color: var(--accent); background: var(--accent-soft); font-size: 12px; font-weight: 600; }
.canvas-status.paused { color: var(--text-2); background: #edf1f6; }
.card-status-actions button { width: 32px; height: 32px; display: grid; place-items: center; border: 0; border-radius: 8px; color: var(--text-3); background: transparent; }
.card-status-actions button:hover { color: var(--primary); background: var(--primary-soft); }
.canvas-summary-card h3 { margin: 12px 0 0; font-size: 19px; line-height: 1.45; overflow-wrap: anywhere; }
.canvas-meta { margin: 4px 0 0; color: var(--text-3); font-size: 12px; }
.progress-block { margin-top: 16px; }
.progress-block > div { display: flex; align-items: baseline; gap: 8px; }
.progress-block strong { font-size: 26px; font-weight: 650; line-height: 1.2; font-variant-numeric: tabular-nums; }
.progress-block span { color: var(--text-2); font-size: 12px; }
.progress-block > i { height: 6px; margin-top: 8px; display: block; overflow: hidden; border-radius: 3px; background: #e8edf3; }
.progress-block b { height: 100%; display: block; border-radius: inherit; background: var(--accent); transition: width 220ms ease; }
.canvas-metrics { margin: 16px 0 0; display: grid; grid-template-columns: 1fr 1fr; gap: 12px 16px; }
.canvas-metrics div { min-width: 0; }
.canvas-metrics dt { color: var(--text-3); font-size: 12px; }
.canvas-metrics dd { margin: 3px 0 0; color: var(--text); font-size: 13px; font-weight: 500; overflow-wrap: anywhere; }
.current-focus { margin-top: 16px; padding: 10px 12px; display: grid; gap: 4px; border-left: 2px solid var(--primary); border-radius: 0 8px 8px 0; background: var(--primary-soft); }
.current-focus small { color: var(--text-2); font-size: 12px; }
.current-focus strong { color: #254b87; font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.recent-changes { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--border); }
.recent-changes h4 { margin: 0 0 6px; color: var(--text-2); font-size: 12px; font-weight: 500; }
.recent-changes p { margin: 5px 0 0; display: flex; align-items: flex-start; gap: 8px; color: var(--text-2); font-size: 12px; line-height: 1.55; overflow-wrap: anywhere; }
.recent-changes p i { width: 5px; height: 5px; margin-top: 7px; flex: 0 0 auto; border-radius: 50%; background: var(--accent); }
.recent-changes p i.muted { background: #a7b4c4; }
.canvas-summary-card > footer { margin-top: auto; padding-top: 20px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.canvas-summary-card > footer button { min-height: 40px; display: flex; align-items: center; justify-content: center; gap: 6px; border: 1px solid var(--border-strong); border-radius: 8px; color: var(--text-2); background: #fff; font-size: 13px; transition: background-color 160ms ease, border-color 160ms ease; }
.canvas-summary-card > footer button:hover { border-color: var(--primary); color: var(--primary); }
.canvas-summary-card > footer .card-primary { border-color: var(--primary); color: #fff; background: var(--primary); font-weight: 600; }
.canvas-summary-card > footer .card-primary:hover { color: #fff; background: var(--primary-hover); }
.create-canvas-card {
  grid-column: 1 / -1; min-height: 88px; padding: 16px 20px; display: grid; grid-template-columns: 40px minmax(0, 1fr) auto;
  grid-template-areas: 'icon title action' 'icon copy action'; align-items: center; column-gap: 16px; row-gap: 4px;
  border: 1px dashed #b4c5de; border-radius: 12px; color: var(--text); background: var(--surface-warm); text-align: left;
  transition: border-color 160ms ease, background-color 160ms ease;
}
.create-canvas-card:hover { border-color: var(--primary); background: #f9fbff; }
.create-canvas-card > span { grid-area: icon; width: 40px; height: 40px; display: grid; place-items: center; border-radius: 8px; color: var(--primary); background: var(--primary-soft); }
.create-canvas-card > span :deep(svg) { width: 22px; height: 22px; }
.create-canvas-card strong { grid-area: title; font-size: 15px; align-self: end; }
.create-canvas-card p { grid-area: copy; margin: 0; color: var(--text-2); font-size: 13px; line-height: 1.6; align-self: start; }
.create-canvas-card em { grid-area: action; padding: 8px 14px; border: 1px solid var(--border-strong); border-radius: 8px; color: var(--primary); background: #fff; font-size: 13px; font-style: normal; font-weight: 600; white-space: nowrap; }
.canvas-empty-result { grid-column: 1 / -1; min-height: 200px; display: grid; place-items: center; align-content: center; gap: 8px; color: var(--text-3); }
.canvas-empty-result strong { color: var(--text); }
.canvas-empty-result p { margin: 0; }
.new-canvas-dialog { display: grid; gap: 20px; }
.new-canvas-dialog > header { display: flex; gap: 12px; }
.new-canvas-dialog > header > span { width: 44px; height: 44px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 8px; color: var(--primary); background: var(--primary-soft); }
.new-canvas-dialog h2 { margin: 0; font-size: 18px; }
.new-canvas-dialog header p { margin: 6px 0 0; color: var(--text-2); font-size: 13px; line-height: 1.7; }
.dialog-safety { margin: 0; display: flex; align-items: flex-start; gap: 8px; color: #39785b; font-size: 13px; }
.dialog-safety svg { flex-shrink: 0; margin-top: 3px; }
.dialog-secondary, .dialog-cancel, .dialog-create { min-height: 44px; padding: 0 16px; display: flex; align-items: center; justify-content: center; gap: 8px; border: 1px solid var(--border-strong); border-radius: 8px; background: #fff; }
.dialog-secondary { margin-right: auto; color: var(--text-2); }
.dialog-create { border-color: var(--primary); color: #fff; background: var(--primary); }
.dialog-create:disabled { opacity: .5; }
.spin { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1200px) {
  .overview-heading { flex-wrap: wrap; }
  .overview-stats article { padding: 16px; }
  .canvas-list-section > header { flex-wrap: wrap; }
}
@media (max-width: 760px) {
  .canvas-overview-page { padding: 20px 16px calc(32px + env(safe-area-inset-bottom)); }
  .overview-heading { display: grid; gap: 16px; }
  .overview-heading h1 { font-size: 26px; }
  .overview-heading nav { display: grid; grid-template-columns: 44px minmax(0, 1fr); }
  .header-tool { padding: 0; }
  .header-tool span { display: none; }
  .overview-stats { grid-template-columns: 1fr 1fr; margin-top: 20px; }
  .overview-stats article { min-height: 82px; padding: 12px; gap: 8px; }
  .overview-stats article:nth-child(3) { border-left: 0; }
  .overview-stats article:nth-child(n+3) { border-top: 1px solid var(--border); }
  .overview-stats article > span { width: 32px; height: 32px; }
  .overview-stats strong { font-size: 23px; }
  .canvas-list-section > header { display: grid; gap: 16px; }
  .canvas-filters { grid-template-columns: 1fr 1fr; }
  .canvas-filters > label { grid-column: 1 / -1; height: 44px; }
  .canvas-filters :deep(.app-select__trigger) { min-height: 44px; }
  .canvas-card-grid { grid-template-columns: 1fr; }
  .canvas-summary-card { min-height: 0; padding: 16px; }
  .canvas-summary-card > footer button { min-height: 44px; }
  .card-status-actions button { width: 44px; height: 44px; }
  .create-canvas-card { min-height: 0; padding: 16px; grid-template-columns: 36px minmax(0, 1fr); grid-template-areas: 'icon title' 'copy copy' 'action action'; gap: 12px; }
  .create-canvas-card strong { align-self: center; }
  .create-canvas-card em { min-height: 44px; display: grid; place-items: center; }
  .dialog-secondary { width: 100%; margin: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .canvas-summary-card, .create-canvas-card, .progress-block b, .header-tool, .new-canvas-button, .canvas-summary-card > footer button { transition: none; }
  .spin { animation: none; }
}

/* Online release visual baseline. Kept in source selectors so Vue can emit
   the current scope id instead of coupling the app to a compiled data-v hash. */
.canvas-overview-page { color: #132348; background: #f4f7fc; padding: 28px 32px 40px; }
.overview-heading { gap: 24px; }
.overview-heading h1 { font-size: 29px; line-height: 1.2; }
.overview-heading p { color: #7787a4; margin: 6px 0 0; font-size: 13px; }
.overview-heading nav { gap: 10px; }
.new-canvas-button { min-width: 180px; padding: 0 20px; color: #fff; background: #2468f2; border-color: #2468f2; font-weight: 650; box-shadow: 0 8px 18px #2468f229; }
.new-canvas-button:hover { background: #1f5fdc; border-color: #1f5fdc; }
.overview-stats { gap: 14px; margin-top: 22px; }
.overview-stats article { min-height: 82px; gap: 15px; padding: 14px; border-radius: 8px; background: #fff; border: 1px solid #d9e2ef; box-shadow: 0 7px 20px #18366209; }
.overview-stats article > span { width: 46px; height: 46px; border-radius: 8px; }
.overview-stats strong { font-size: 27px; line-height: 1; }
.overview-stats p { color: #7c8ba5; font-size: 12px; }
.canvas-list-section { margin-top: 20px; }
.canvas-list-section > header { min-height: 54px; gap: 20px; align-items: center; }
.canvas-list-section h2 + em { min-width: 27px; height: 27px; display: grid; place-items: center; border-radius: 50%; color: #61728d; background: #e9eef6; font-size: 11px; font-style: normal; }
.canvas-list-section > header > div:first-child { display: flex; align-items: center; gap: 12px; }
.canvas-filters > label { height: 42px; border-radius: 8px; background: #fff; }
.canvas-filters input { height: 40px; padding: 0; color: #1c2d4c; background: transparent; font-size: 12px; }
.canvas-filters :deep(.app-select__trigger) { min-height: 42px; border-radius: 8px; }
.overview-loading { min-height: 420px; gap: 10px; color: #71819c; }
.canvas-card-grid { grid-template-columns: repeat(auto-fill, minmax(285px, 1fr)); gap: 14px; margin-top: 12px; }
.canvas-summary-card { min-height: 500px; padding: 18px; border-radius: 8px; border-color: #d9e2ef; box-shadow: 0 9px 26px #1836620b; transition: transform .25s cubic-bezier(.2,.8,.2,1), box-shadow .25s, border-color .25s; }
.canvas-summary-card::before { content: ''; position: absolute; inset: 0 0 auto; height: 5px; background: var(--accent); }
.canvas-summary-card:hover { transform: translateY(-3px); box-shadow: 0 16px 35px #1836621a; }
.canvas-summary-card > header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.canvas-monogram { width: 48px; height: 48px; border-radius: 11px; font-size: 13px; font-weight: 800; }
.canvas-status { padding: 5px 9px; border-radius: 999px; font-size: 11px; font-weight: 650; }
.card-status-actions { gap: 5px; }
.card-status-actions button { width: 30px; height: 30px; display: grid; place-items: center; border: 0; border-radius: 8px; color: #8b9ab0; background: transparent; }
.card-status-actions button:hover { color: var(--accent); background: var(--accent-soft); }
.canvas-summary-card h3 { margin-top: 16px; font-size: 18px; }
.canvas-meta { margin: 7px 0 0; color: #8795ab; font-size: 11px; }
.progress-block { margin-top: 18px; }
.progress-block strong { font: 700 30px/1 Georgia, serif; }
.progress-block span { padding-bottom: 3px; color: #8a99b0; font-size: 10px; }
.progress-block > i { height: 9px; margin-top: 7px; border-radius: 999px; }
.canvas-metrics { margin-top: 18px; gap: 10px; }
.canvas-metrics div { min-height: 67px; padding: 12px; border: 1px solid #dfe6f0; border-radius: 8px; background: #f7f9fc; }
.canvas-metrics dt { font-size: 11px; font-weight: 750; }
.canvas-metrics dd { margin: 7px 0 0; color: #8290a7; font-size: 10px; }
.current-focus { min-height: 64px; margin-top: 16px; padding: 14px; border: 1px solid #dbe5f2; border-radius: 8px; background: #f7faff; }
.current-focus small { color: #8796ad; font-size: 10px; }
.current-focus strong { font-size: 12px; line-height: 1.5; }
.recent-changes { min-height: 88px; margin-top: 16px; padding-top: 0; border-top: 0; }
.recent-changes h4 { margin: 0 0 10px; padding-bottom: 10px; border-bottom: 1px solid #e4eaf2; font-size: 11px; }
.recent-changes p { margin: 8px 0; color: #64758e; font-size: 10px; line-height: 1.45; }
.recent-changes p i { width: 7px; height: 7px; margin-top: 3px; }
.recent-changes p i.muted { background: #c4cedd; }
.canvas-summary-card > footer { gap: 9px; padding-top: 15px; }
.canvas-summary-card > footer button { height: 44px; border-radius: 9px; font-size: 12px; }
.canvas-summary-card > footer .card-primary { color: #fff; background: var(--accent); border-color: var(--accent); font-weight: 650; }
.create-canvas-card {
  grid-column: 1 / -1;
  min-height: 94px;
  padding: 18px 20px;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  grid-template-areas: 'icon title action' 'icon copy action';
  align-items: center;
  column-gap: 14px;
  row-gap: 4px;
  border: 1px solid #d7e2f0;
  border-left: 3px solid #2468f2;
  border-radius: 8px;
  color: #173b72;
  background: #fff;
  box-shadow: 0 6px 18px #1836620d;
  text-align: left;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}
.create-canvas-card:hover {
  transform: translateY(-1px);
  border-color: #a9c3ee;
  border-left-color: #2468f2;
  background: #fbfdff;
  box-shadow: 0 10px 24px #18366214;
}
.create-canvas-card > span {
  grid-area: icon;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  color: #2468f2;
  background: #edf4ff;
}
.create-canvas-card strong { grid-area: title; align-self: end; margin: 0; color: #173b72; font-size: 15px; }
.create-canvas-card p { grid-area: copy; align-self: start; max-width: 720px; margin: 0; color: #7787a4; font-size: 12px; line-height: 1.55; text-align: left; }
.create-canvas-card em {
  grid-area: action;
  width: auto;
  height: 38px;
  margin: 0;
  padding: 0 15px;
  display: grid;
  place-items: center;
  border: 1px solid #b8cff8;
  border-radius: 7px;
  color: #2468f2;
  background: #f7faff;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
  white-space: nowrap;
  transition: color .18s ease, border-color .18s ease, background-color .18s ease;
}
.create-canvas-card:hover em { color: #fff; border-color: #2468f2; background: #2468f2; }
.canvas-empty-result { min-height: 240px; color: #8190a8; }
.canvas-empty-result strong { color: #334761; }
.new-canvas-dialog { gap: 18px; }
.new-canvas-dialog > header > span { border-radius: 8px; }
.new-canvas-dialog h2 { font-size: 16px; }
.new-canvas-dialog header p { margin: 5px 0 0; color: #74849e; font-size: 12px; line-height: 1.65; }
.dialog-safety { align-items: center; gap: 7px; font-size: 11px; }
.dialog-secondary, .dialog-cancel, .dialog-create { min-height: 41px; border-radius: 9px; }
.dialog-create { color: #fff; background: #2468f2; border-color: #2468f2; }

@media (max-width: 760px) {
  .canvas-overview-page { padding: 18px 14px 28px; }
  .overview-heading { display: grid; }
  .overview-heading h1 { font-size: 24px; }
  .overview-stats { gap: 9px; }
  .overview-stats article { min-height: 74px; padding: 10px; gap: 10px; }
  .overview-stats article > span { width: 38px; height: 38px; }
  .overview-stats strong { font-size: 20px; }
  .overview-stats p { font-size: 10px; line-height: 1.35; }
  .canvas-summary-card, .create-canvas-card { min-height: 0; }
  .canvas-summary-card { padding: 16px; }
  .canvas-summary-card > footer { margin-top: 20px; }
  .create-canvas-card {
    padding: 16px;
    grid-template-columns: 40px minmax(0, 1fr);
    grid-template-areas: 'icon title' 'copy copy' 'action action';
    gap: 10px 12px;
    border-left-width: 1px;
  }
  .create-canvas-card > span { width: 40px; height: 40px; }
  .create-canvas-card strong { align-self: center; }
  .create-canvas-card p { max-width: none; }
  .create-canvas-card em { width: 100%; min-height: 44px; }
}
@media (max-width: 420px) {
  .overview-stats { grid-template-columns: 1fr 1fr; }
  .overview-stats article { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 8px; }
  .overview-stats article > span { width: 34px; height: 34px; }
  .canvas-summary-card > footer { grid-template-columns: 1fr 1fr; }
}
</style>
