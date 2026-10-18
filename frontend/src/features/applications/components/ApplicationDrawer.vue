<script setup lang="ts">
/** One application in full: its stage, the resume link's receipts, what happened and when. */
import { computed, ref, shallowRef, watch } from 'vue'
import {
  ArrowUpRight, BellRing, Check, Copy, ExternalLink, Eye, FileText, Flag, Link2, Link2Off, Monitor, Pencil,
  RefreshCw, Smartphone, Tablet, Target, Trash2, X,
} from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import UiSelect, { type SelectOption } from '@/shared/ui/UiSelect.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { confirm } from '@/shared/ui/confirm'
import { toast } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { formatRelative, formatWhen } from '@/shared/lib/datetime'
import {
  createResumeLink, deleteApplication, fetchApplication, fetchShareableVersions, moveApplication, revokeResumeLink,
} from '../services/applicationsApi'
import { OUTCOMES, STAGES, channelLabel, clientLabel, deviceLabel, outcomeLabel, readTime, stageLabel } from '../labels'
import type { ApplicationDetail, ApplicationEvent, ApplicationOutcome, ApplicationStage, JobApplication, ShareableVersion } from '../types'
import { receiptOf } from '../utils/receipts'

const props = defineProps<{ open: boolean; applicationId: string | null }>()
const emit = defineEmits<{ close: []; changed: [application: JobApplication]; deleted: [id: string]; edit: [application: JobApplication] }>()

const detail = shallowRef<ApplicationDetail | null>(null)
const loading = ref(false)
const loadError = ref<unknown>(null)
const pending = ref('')
const closing = ref(false)
const outcome = ref<ApplicationOutcome>('REJECTED')
const versions = shallowRef<ShareableVersion[]>([])
const linkVersion = ref('')
const linkHideContact = ref(false)
const linkExpiry = ref<string>('none')
const copied = ref(false)

const application = computed(() => detail.value?.application ?? null)
const link = computed(() => detail.value?.links.find(item => item.status === 'ACTIVE') ?? detail.value?.links[0] ?? null)
const activeLink = computed(() => (link.value?.status === 'ACTIVE' ? link.value : null))
const receipt = computed(() => receiptOf(link.value))
const linkUrl = computed(() => (activeLink.value ? `${window.location.origin}${activeLink.value.path}` : ''))
const openStages = STAGES.filter(stage => stage.value !== 'CLOSED')
const versionOptions = computed<SelectOption[]>(() => versions.value.map(item => ({
  value: item.versionId,
  label: `${item.resumeTitle} · 版本 ${item.ordinal}`,
  description: item.anonymous ? '匿名版' : `冻结于 ${new Date(item.frozenAt).toLocaleDateString('zh-CN')}`,
})))
const expiryOptions: SelectOption[] = [
  { value: 'none', label: '长期有效' },
  { value: '7', label: '7 天' },
  { value: '30', label: '30 天' },
  { value: '90', label: '90 天' },
]
const outcomeOptions = OUTCOMES.map(item => ({ value: item.value, label: item.label }))

async function load(): Promise<void> {
  if (!props.applicationId) return
  loading.value = true
  loadError.value = null
  try {
    detail.value = await fetchApplication(props.applicationId)
    closing.value = false
  } catch (reason) {
    loadError.value = reason
  } finally {
    loading.value = false
  }
}

async function loadVersions(): Promise<void> {
  if (versions.value.length) return
  try {
    versions.value = await fetchShareableVersions()
    linkVersion.value = application.value?.resumeVersionId
      ?? versions.value.find(item => !item.anonymous)?.versionId ?? versions.value[0]?.versionId ?? ''
  } catch {
    versions.value = []
  }
}

async function run(key: string, action: () => Promise<void>, fallback: string): Promise<void> {
  if (pending.value) return
  pending.value = key
  try {
    await action()
  } catch (reason) {
    toast.error(errorMessage(reason, fallback))
  } finally {
    pending.value = ''
  }
}

function moveTo(stage: ApplicationStage, chosen?: ApplicationOutcome): Promise<void> {
  const current = application.value
  if (!current) return Promise.resolve()
  return run(`stage-${stage}`, async () => {
    const updated = await moveApplication(current.id, stage, current.version, chosen)
    emit('changed', updated)
    await load()
  }, '阶段更新失败')
}

async function createLink(): Promise<void> {
  const current = application.value
  if (!current || !linkVersion.value) return
  if (activeLink.value && !(await confirm({
    title: '换一个新链接？',
    message: '旧链接会立即失效，已经发出去的旧链接打开后会提示“已撤回”。已有的打开记录会保留。',
    confirmText: '生成新链接',
  }))) return
  await run('link', async () => {
    await createResumeLink({
      resumeVersionId: linkVersion.value,
      applicationId: current.id,
      hideContact: linkHideContact.value,
      expiresInDays: linkExpiry.value === 'none' ? null : Number(linkExpiry.value),
    })
    await load()
    if (detail.value) emit('changed', detail.value.application)
    toast.success('专属链接已生成，复制后发给对方即可')
  }, '生成链接失败')
}

async function revoke(): Promise<void> {
  const target = activeLink.value
  if (!target || !(await confirm({
    title: '撤回这个链接？',
    message: '对方再打开时会看到“链接已撤回”。已有的打开记录会保留。',
    confirmText: '撤回链接',
    tone: 'danger',
  }))) return
  await run('revoke', async () => {
    await revokeResumeLink(target.id)
    await load()
    if (detail.value) emit('changed', detail.value.application)
  }, '撤回失败')
}

async function copyLink(): Promise<void> {
  if (!linkUrl.value) return
  try {
    await navigator.clipboard.writeText(linkUrl.value)
  } catch {
    const field = document.createElement('textarea')
    field.value = linkUrl.value
    document.body.append(field)
    field.select()
    document.execCommand('copy')
    field.remove()
  }
  copied.value = true
  toast.success('链接已复制，可以粘贴到邮件、招聘平台或微信里')
  window.setTimeout(() => { copied.value = false }, 2000)
}

async function remove(): Promise<void> {
  const current = application.value
  if (!current || !(await confirm({
    title: `删除「${current.company}」的投递记录？`,
    message: '时间线、打开记录会一并删除，专属链接立即失效。这个操作不能撤销。',
    confirmText: '删除',
    tone: 'danger',
  }))) return
  await run('delete', async () => {
    await deleteApplication(current.id)
    emit('deleted', current.id)
  }, '删除失败')
}

function eventText(event: ApplicationEvent): string {
  if (event.type === 'CREATED') return `记录投递 · ${stageLabel(event.toStage)}`
  if (event.type === 'STAGE_CHANGED') {
    const to = event.toStage === 'CLOSED' ? `已结束（${outcomeLabel(event.detail) || '结束'}）` : stageLabel(event.toStage)
    return `${stageLabel(event.fromStage)} → ${to}`
  }
  return event.detail || '链接更新'
}

function eventIcon(event: ApplicationEvent) {
  if (event.type === 'FIRST_VIEWED' || event.type === 'NEW_VISITOR') return Eye
  if (event.type === 'LINK_CREATED') return Link2
  if (event.type === 'LINK_REVOKED') return Link2Off
  if (event.type === 'STAGE_CHANGED') return Flag
  return Check
}

function deviceIcon(device: string) {
  if (device === 'MOBILE') return Smartphone
  if (device === 'TABLET') return Tablet
  return Monitor
}

watch(() => [props.open, props.applicationId] as const, ([open, id]) => {
  if (!open || !id) return
  detail.value = null
  void load()
  void loadVersions()
}, { immediate: true })
</script>

<template>
  <UiDrawer :open="open" :title="application ? application.company : '投递详情'" :description="application?.positionTitle ?? ''" :width="560" @close="emit('close')">
    <div v-if="loading && !detail" class="drawer-loading"><UiSkeleton v-for="n in 4" :key="n" height="64px" radius="var(--radius-md)" /></div>
    <UiErrorState v-else-if="loadError" compact title="读取投递详情失败" :error="loadError" @retry="load" />
    <div v-else-if="application && detail" class="app-detail">
      <section class="app-detail__stage" aria-label="投递阶段">
        <div class="stage-track" role="group" aria-label="移动到阶段">
          <button
            v-for="stage in openStages"
            :key="stage.value"
            type="button"
            class="stage-track__step"
            :class="{ 'is-current': application.stage === stage.value, 'is-passed': application.stage !== 'CLOSED' && STAGES.findIndex(item => item.value === stage.value) < STAGES.findIndex(item => item.value === application!.stage) }"
            :aria-pressed="application.stage === stage.value"
            :disabled="Boolean(pending)"
            @click="application.stage !== stage.value && moveTo(stage.value)"
          >{{ stage.label }}</button>
        </div>
        <div v-if="application.stage === 'CLOSED'" class="stage-closed">
          <UiBadge tone="neutral">已结束 · {{ outcomeLabel(application.outcome) }}</UiBadge>
          <span>点上面的阶段可以重新打开。</span>
        </div>
        <div v-else-if="closing" class="stage-closed">
          <UiSelect v-model="outcome" :options="outcomeOptions" size="sm" aria-label="结束原因" />
          <UiButton size="sm" :pending="pending === 'stage-CLOSED'" @click="moveTo('CLOSED', outcome)">结束这条投递</UiButton>
          <UiButton size="sm" variant="ghost" :icon="X" @click="closing = false">取消</UiButton>
        </div>
        <button v-else type="button" class="stage-close-link" @click="closing = true">结束这条投递…</button>
      </section>

      <section class="receipt" :class="`is-${receipt.tone}`" aria-labelledby="receipt-title">
        <header class="receipt__head">
          <span class="receipt__icon"><Eye :size="18" /></span>
          <div>
            <h3 id="receipt-title">简历回执</h3>
            <p>{{ receipt.label }}<template v-if="link && link.viewCount"> · {{ receipt.detail }}</template></p>
          </div>
        </header>

        <template v-if="activeLink">
          <div class="receipt__url">
            <Link2 :size="15" aria-hidden="true" />
            <code :title="linkUrl">{{ linkUrl }}</code>
            <UiButton size="sm" :variant="copied ? 'soft' : 'primary'" :icon="copied ? Check : Copy" @click="copyLink">{{ copied ? '已复制' : '复制' }}</UiButton>
          </div>
          <p class="receipt__meta">
            {{ activeLink.resumeTitle || '简历' }}<template v-if="activeLink.hideContact"> · 已隐藏联系方式</template>
            · {{ activeLink.expiresAt ? `${formatWhen(activeLink.expiresAt)} 过期` : '长期有效' }}
          </p>
        </template>

        <dl v-if="link && link.viewCount" class="receipt__stats">
          <div><dt>打开</dt><dd>{{ link.viewCount }} 次</dd></div>
          <div><dt>读者</dt><dd>{{ link.visitorCount }} 位</dd></div>
          <div><dt>累计阅读</dt><dd>{{ readTime(link.readSeconds) }}</dd></div>
          <div><dt>首次打开</dt><dd>{{ formatRelative(link.firstViewedAt) }}</dd></div>
        </dl>

        <ul v-if="detail.views.length" class="receipt__views" aria-label="打开记录">
          <li v-for="view in detail.views" :key="view.id">
            <component :is="deviceIcon(view.device)" :size="15" aria-hidden="true" />
            <span class="receipt__who">{{ deviceLabel(view.device) }} · {{ clientLabel(view.client) }}<small v-if="view.referrerHost"> · 来自 {{ view.referrerHost }}</small></span>
            <span class="receipt__read">{{ view.readSeconds ? `读了 ${readTime(view.readSeconds)}` : '刚打开' }}</span>
            <time :datetime="view.startedAt" :title="formatWhen(view.startedAt)">{{ formatRelative(view.startedAt) }}</time>
          </li>
        </ul>

        <div v-if="activeLink" class="receipt__actions">
          <UiButton size="sm" variant="secondary" :icon="ExternalLink" :href="activeLink.path" target="_blank">预览对方看到的页面</UiButton>
          <UiButton size="sm" variant="ghost" :icon="Link2Off" :pending="pending === 'revoke'" @click="revoke">撤回</UiButton>
        </div>

        <div class="receipt__create" :class="{ 'is-secondary': activeLink }">
          <div v-if="!activeLink" class="receipt__intro">
            <UiIllustration name="resume-shared" :size="72" />
            <p class="receipt__pitch"><BellRing :size="14" />给这家公司一条专属链接：对方打开时提醒你，还能看到阅读时长。</p>
          </div>
          <p v-else class="receipt__pitch">换简历版本或调整设置，会生成新链接并停用旧链接。</p>
          <p v-if="!versions.length" class="receipt__note">还没有冻结的简历版本。先在简历工作台导出一次 PDF。</p>
          <template v-else>
            <div class="receipt__form">
              <UiSelect v-model="linkVersion" :options="versionOptions" size="sm" aria-label="分享的简历版本" />
              <UiSelect v-model="linkExpiry" :options="expiryOptions" size="sm" aria-label="有效期" />
            </div>
            <UiCheckbox v-model="linkHideContact" label="隐藏手机号、邮箱和照片" />
            <UiButton size="sm" :variant="activeLink ? 'secondary' : 'primary'" :icon="activeLink ? RefreshCw : Link2" :pending="pending === 'link'" :disabled="!linkVersion" @click="createLink">
              {{ activeLink ? '换一个新链接' : '生成专属链接' }}
            </UiButton>
          </template>
        </div>
      </section>

      <section class="app-info" aria-label="投递信息">
        <dl>
          <div><dt>渠道</dt><dd>{{ channelLabel(application.channel) }}</dd></div>
          <div v-if="application.location"><dt>城市</dt><dd>{{ application.location }}</dd></div>
          <div v-if="application.appliedOn"><dt>投递日期</dt><dd>{{ application.appliedOn }}</dd></div>
          <div v-if="application.salaryText"><dt>薪资</dt><dd>{{ application.salaryText }}</dd></div>
          <div v-if="application.nextStep || application.nextStepAt"><dt>下一步</dt><dd>{{ application.nextStep || '下一步' }}<template v-if="application.nextStepAt"> · {{ formatWhen(application.nextStepAt) }}</template></dd></div>
          <div v-if="application.resumeTitle"><dt>简历</dt><dd><RouterLink :to="`/resumes/${application.resumeMasterId}/manual`"><FileText :size="13" />{{ application.resumeTitle }}</RouterLink></dd></div>
          <div v-if="application.jobMatchId"><dt>岗位匹配</dt><dd><RouterLink :to="`/job-match/${application.jobMatchId}/report`"><Target :size="13" />{{ application.matchTitle || '查看报告' }}<template v-if="application.matchScore != null"> · {{ application.matchScore }} 分</template></RouterLink></dd></div>
          <div v-if="application.jobUrl"><dt>岗位链接</dt><dd><a :href="application.jobUrl" target="_blank" rel="noopener noreferrer">打开原岗位<ArrowUpRight :size="13" /></a></dd></div>
        </dl>
        <p v-if="application.notes" class="app-info__notes">{{ application.notes }}</p>
      </section>

      <section class="timeline" aria-labelledby="timeline-title">
        <h3 id="timeline-title">时间线</h3>
        <ol>
          <li v-for="event in detail.events" :key="event.id" :class="`is-${event.type.toLowerCase()}`">
            <span class="timeline__icon"><component :is="eventIcon(event)" :size="13" /></span>
            <span class="timeline__text">{{ eventText(event) }}</span>
            <time :datetime="event.createdAt" :title="formatWhen(event.createdAt)">{{ formatRelative(event.createdAt) }}</time>
          </li>
        </ol>
      </section>
    </div>

    <template v-if="application" #footer>
      <UiButton variant="ghost" :icon="Trash2" :pending="pending === 'delete'" @click="remove">删除</UiButton>
      <UiButton variant="secondary" :icon="Pencil" @click="emit('edit', application)">编辑信息</UiButton>
    </template>
  </UiDrawer>
</template>

<style scoped>
.drawer-loading {
  display: grid;
  gap: var(--space-3);
}

.app-detail {
  display: grid;
  gap: var(--space-5);
  min-width: 0;
}

/* Grid items default to their content's width; the link and long names must not widen the drawer. */
.app-detail > *,
.receipt > * {
  min-width: 0;
}

.stage-track {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

.stage-track__step {
  min-height: 34px;
  padding: 0 4px;
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  background: transparent;
  font: inherit;
  font-size: var(--fs-xs);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.stage-track__step:hover:not(:disabled) {
  color: var(--text-primary);
  background: var(--surface-1);
}

.stage-track__step.is-passed {
  color: var(--color-primary-text);
}

.stage-track__step.is-current {
  color: var(--text-on-primary);
  background: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.stage-track__step:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.stage-closed {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-3);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.stage-close-link {
  margin-top: var(--space-2);
  padding: 0;
  border: 0;
  color: var(--text-tertiary);
  background: none;
  font: inherit;
  font-size: var(--fs-xs);
  cursor: pointer;
}

.stage-close-link:hover {
  color: var(--color-danger-text);
}

.receipt {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
}

.receipt.is-fresh {
  border-color: color-mix(in srgb, var(--color-success) 40%, var(--border-subtle));
  background: linear-gradient(160deg, var(--color-success-soft), transparent 55%), var(--surface-1);
}

.receipt.is-seen {
  border-color: var(--color-primary-border);
  background: linear-gradient(160deg, var(--color-primary-soft), transparent 55%), var(--surface-1);
}

.receipt__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.receipt__head h3 {
  font-size: var(--fs-md, 15px);
  font-weight: 650;
}

.receipt__head p {
  color: var(--text-secondary);
  font-size: var(--fs-xs);
}

.receipt__icon {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--text-tertiary);
  background: var(--surface-2);
}

.receipt.is-fresh .receipt__icon { color: var(--color-success-text); background: var(--surface-1); }
.receipt.is-seen .receipt__icon { color: var(--color-primary-text); background: var(--surface-1); }

.receipt__url {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 6px 6px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-tertiary);
}

.receipt__url code {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-family: var(--font-mono, ui-monospace, monospace);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.receipt__meta {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.receipt__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.receipt__stats div {
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

.receipt__stats dt {
  color: var(--text-tertiary);
  font-size: 11px;
}

.receipt__stats dd {
  margin-top: 2px;
  font-size: var(--fs-sm);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.receipt__views {
  list-style: none;
  display: grid;
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overscroll-behavior: contain;
}

.receipt__views li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--space-2);
  padding: 8px 10px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.receipt__views li + li {
  border-top: 1px solid var(--border-subtle);
}

.receipt__who {
  overflow: hidden;
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.receipt__who small {
  color: var(--text-tertiary);
}

.receipt__read {
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.receipt__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.receipt__create {
  display: grid;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px dashed var(--border-subtle);
}

.receipt__create.is-secondary .receipt__pitch {
  color: var(--text-tertiary);
}

.receipt__intro {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.receipt__pitch {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.receipt__note {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.receipt__form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  gap: var(--space-2);
}

.receipt__create > :last-child {
  justify-self: start;
}

.app-info dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
}

.app-info dt {
  color: var(--text-tertiary);
  font-size: 11px;
}

.app-info dd {
  margin-top: 2px;
  font-size: var(--fs-sm);
  overflow-wrap: anywhere;
}

.app-info a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-primary-text);
}

.app-info__notes {
  margin-top: var(--space-3);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  background: var(--surface-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
  white-space: pre-line;
}

.timeline h3 {
  margin-bottom: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  font-weight: 650;
  letter-spacing: 0.04em;
}

.timeline ol {
  list-style: none;
  display: grid;
  gap: 2px;
}

.timeline li {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 0;
  font-size: var(--fs-xs);
}

.timeline li:not(:last-child)::before {
  position: absolute;
  top: 26px;
  bottom: -8px;
  left: 11px;
  border-left: 1px dashed var(--border-default);
  content: '';
}

.timeline__icon {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--text-tertiary);
  background: var(--surface-2);
}

.timeline li.is-first_viewed .timeline__icon,
.timeline li.is-new_visitor .timeline__icon {
  color: var(--color-success-text);
  background: var(--color-success-soft);
}

.timeline li.is-stage_changed .timeline__icon {
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
}

.timeline__text {
  color: var(--text-primary);
}

.timeline time {
  color: var(--text-tertiary);
}

@media (max-width: 560px) {
  .receipt__stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .receipt__views li { grid-template-columns: auto minmax(0, 1fr) auto; }
  .receipt__read { display: none; }
  .stage-track { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
</style>
