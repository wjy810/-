<script setup lang="ts">
/** One application in full: its stage, the resume link's receipts, what happened and when. */
import { computed, ref, shallowRef, watch } from 'vue'
import {
  Archive, ArrowRight, ArrowUpRight, BellRing, Check, CheckCircle2, Copy, ExternalLink, Eye, FileText, Flag, Link2, Link2Off,
  ArchiveRestore, Mic, Monitor, Pencil, RefreshCw, Settings2, Smartphone, Tablet, Target, UserCheck, X,
} from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import UiSelect, { type SelectOption } from '@/shared/ui/UiSelect.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { confirm } from '@/shared/ui/confirm'
import { toast } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { copyText } from '@/shared/lib/clipboard'
import { formatRelative, formatWhen } from '@/shared/lib/datetime'
import {
  archiveApplication, completeNextStep, createResumeLink, fetchApplication, fetchShareableVersions, markOwnView,
  moveApplication, restoreApplication, revokeResumeLink, updateResumeLink,
} from '../services/applicationsApi'
import { OUTCOMES, STAGES, channelLabel, clientLabel, deviceLabel, outcomeLabel, readTime, stageLabel } from '../labels'
import type {
  ApplicationDetail, ApplicationEvent, ApplicationOutcome, ApplicationStage, JobApplication, LinkViewRecord, ShareableVersion,
} from '../types'
import { receiptOf } from '../utils/receipts'
import { interviewPrep } from '../utils/prep'

const props = defineProps<{ open: boolean; applicationId: string | null }>()
const emit = defineEmits<{
  close: []; changed: [application: JobApplication]; archived: [application: JobApplication]
  restored: [application: JobApplication]; edit: [application: JobApplication]
}>()

const detail = shallowRef<ApplicationDetail | null>(null)
const loading = ref(false)
const loadError = ref<unknown>(null)
const pending = ref('')
const closing = ref(false)
const outcome = ref<ApplicationOutcome>('REJECTED')
const versions = shallowRef<ShareableVersion[]>([])
const linkVersion = ref('')
const linkHideContact = ref(false)
const linkExpiry = ref<string>('60')
const copied = ref(false)
/** Settings of the active link, changed in place (same address). */
const settingsExpiry = ref<string>('keep')
const settingsHideContact = ref(false)
const stepDone = ref(false)
const stepNote = ref('')

const application = computed(() => detail.value?.application ?? null)
const prep = computed(() => (application.value ? interviewPrep(application.value) : null))
const link = computed(() => detail.value?.links.find(item => item.status === 'ACTIVE') ?? detail.value?.links[0] ?? null)
const activeLink = computed(() => (link.value?.status === 'ACTIVE' ? link.value : null))
/** Archived: off the board, its link off with it. Opened from the archive or a notification. */
const archivedApplication = computed(() => Boolean(application.value?.archivedAt))
/** Restored while the plan's links were all taken: the link is still off and can be turned back on here. */
const waitingLink = computed(() => (!archivedApplication.value && link.value?.status === 'SUSPENDED' ? link.value : null))
const receipt = computed(() => receiptOf(link.value, new Date(), application.value?.receipts))
const totals = computed(() => application.value?.receipts ?? null)
const linkUrl = computed(() => (activeLink.value ? `${window.location.origin}${activeLink.value.path}` : ''))
const openStages = STAGES.filter(stage => stage.value !== 'CLOSED')
const versionOptions = computed<SelectOption[]>(() => versions.value.map(item => ({
  value: item.versionId,
  label: `${item.resumeTitle} · 版本 ${item.ordinal}`,
  description: item.anonymous ? '匿名版' : `冻结于 ${new Date(item.frozenAt).toLocaleDateString('zh-CN')}`,
})))
const expiryOptions: SelectOption[] = [
  { value: '7', label: '7 天' },
  { value: '30', label: '30 天' },
  { value: '60', label: '60 天（推荐）' },
  { value: '90', label: '90 天' },
  { value: 'none', label: '长期有效' },
]
const settingsExpiryOptions: SelectOption[] = [{ value: 'keep', label: '有效期不变' },
  ...expiryOptions.map(item => ({ ...item, label: item.value === 'none' ? '改为长期有效' : `从今天起 ${item.label}` }))]
/** 接受 / 婉拒 Offer only once an offer was reached; the server refuses it otherwise. */
const outcomeOptions = computed(() => OUTCOMES
  .filter(item => application.value?.furthestStage === 'OFFER' || (item.value !== 'ACCEPTED' && item.value !== 'DECLINED'))
  .map(item => ({ value: item.value, label: item.label })))

async function load(): Promise<void> {
  if (!props.applicationId) return
  loading.value = true
  loadError.value = null
  try {
    detail.value = await fetchApplication(props.applicationId)
    closing.value = false
    stepDone.value = false
    stepNote.value = ''
    settingsExpiry.value = 'keep'
    settingsHideContact.value = detail.value.links.find(item => item.status === 'ACTIVE')?.hideContact ?? false
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

async function saveLinkSettings(): Promise<void> {
  const target = activeLink.value
  if (!target) return
  const patch: { expiresInDays?: number; neverExpires?: boolean; hideContact?: boolean } = {}
  if (settingsExpiry.value === 'none') patch.neverExpires = true
  else if (settingsExpiry.value !== 'keep') patch.expiresInDays = Number(settingsExpiry.value)
  if (settingsHideContact.value !== target.hideContact) patch.hideContact = settingsHideContact.value
  if (!Object.keys(patch).length) { toast.info('设置没有变化'); return }
  await run('settings', async () => {
    await updateResumeLink(target.id, patch)
    await load()
    if (detail.value) emit('changed', detail.value.application)
    toast.success('链接设置已更新，地址不变')
  }, '链接设置更新失败')
}

async function markOwn(view: LinkViewRecord): Promise<void> {
  if (!(await confirm({
    title: '这次是你自己打开的？',
    message: '这位读者的打开记录会从回执里去掉，之后同一个浏览器再打开也不会计入。',
    confirmText: '是我自己',
  }))) return
  await run(`own-${view.id}`, async () => {
    await markOwnView(view.linkId, view.id)
    await load()
    if (detail.value) emit('changed', detail.value.application)
    toast.success('回执已更正')
  }, '标记失败')
}

async function finishStep(): Promise<void> {
  const current = application.value
  if (!current) return
  await run('step', async () => {
    const updated = await completeNextStep(current.id, current.version, stepNote.value.trim())
    emit('changed', updated)
    await load()
    toast.success('已记录完成。可以在“编辑信息”里填下一步')
  }, '记录失败')
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
  if (!(await copyText(linkUrl.value))) {
    toast.error('浏览器不允许复制，请长按或选中链接手动复制')
    return
  }
  copied.value = true
  toast.success('链接已复制，可以粘贴到邮件、招聘平台或微信里')
  window.setTimeout(() => { copied.value = false }, 2000)
}

async function reactivateLink(): Promise<void> {
  const target = waitingLink.value
  if (!target) return
  await run('reactivate', async () => {
    await updateResumeLink(target.id, { reactivate: true })
    await load()
    if (detail.value) emit('changed', detail.value.application)
    toast.success('专属链接已重新启用，地址不变')
  }, '重新启用失败')
}

async function restore(): Promise<void> {
  const current = application.value
  if (!current) return
  await run('restore', async () => {
    const restored = await restoreApplication(current.id)
    await load()
    emit('restored', restored)
  }, '恢复失败')
}

/** Moves the application to the archive; it can be restored from there, or deleted for good. */
async function archive(): Promise<void> {
  const current = application.value
  if (!current) return
  await run('archive', async () => {
    emit('archived', await archiveApplication(current.id))
  }, '移入归档失败')
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
  if (event.type === 'LINK_UPDATED') return Settings2
  if (event.type === 'OWN_VIEW_MARKED') return UserCheck
  if (event.type === 'NEXT_STEP_DONE') return CheckCircle2
  if (event.type === 'ARCHIVED' || event.type === 'RESTORED') return Archive
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

      <section v-if="prep" class="prep" aria-labelledby="prep-title">
        <span class="prep__icon" aria-hidden="true"><Mic :size="18" /></span>
        <div class="prep__body">
          <h3 id="prep-title">{{ prep.title }}</h3>
          <p>{{ prep.detail }}</p>
        </div>
        <UiButton size="sm" :to="{ path: '/mock-interviews/new', query: prep.query }" :icon-right="ArrowRight">模拟面试</UiButton>
      </section>

      <section class="receipt" :class="`is-${receipt.tone}`" aria-labelledby="receipt-title">
        <header class="receipt__head">
          <span class="receipt__icon"><Eye :size="18" /></span>
          <div>
            <h3 id="receipt-title">简历回执</h3>
            <p>{{ receipt.label }}<template v-if="(totals?.views ?? link?.viewCount ?? 0) > 0"> · {{ receipt.detail }}</template></p>
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

        <dl v-if="totals && totals.views" class="receipt__stats">
          <div><dt>打开</dt><dd>{{ totals.views }} 次</dd></div>
          <div><dt>读者</dt><dd>{{ totals.visitors }} 位</dd></div>
          <div><dt>累计阅读</dt><dd>{{ readTime(totals.readSeconds) }}</dd></div>
          <div><dt>首次打开</dt><dd>{{ formatRelative(totals.firstViewedAt) }}</dd></div>
        </dl>

        <ul v-if="detail.views.length" class="receipt__views" aria-label="打开记录">
          <li v-for="view in detail.views" :key="view.id">
            <component :is="deviceIcon(view.device)" :size="15" aria-hidden="true" />
            <span class="receipt__who">{{ deviceLabel(view.device) }} · {{ clientLabel(view.client) }}<small v-if="view.referrerHost"> · 来自 {{ view.referrerHost }}</small></span>
            <span class="receipt__read">{{ view.readSeconds ? `读了 ${readTime(view.readSeconds)}` : '刚打开' }}</span>
            <time :datetime="view.startedAt" :title="formatWhen(view.startedAt)">{{ formatRelative(view.startedAt) }}</time>
            <button type="button" class="receipt__own" :disabled="Boolean(pending)" :aria-label="`${formatRelative(view.startedAt)}这次是我自己打开的`" title="这是我自己打开的" @click="markOwn(view)"><UserCheck :size="14" />是我</button>
          </li>
        </ul>
        <p v-if="detail.views.length" class="receipt__note">自己点开检查过链接？点“是我”把那次从回执里去掉。30 天内打开过投递追踪、且没有退出登录的浏览器上，你自己打开不会被计入。</p>

        <div v-if="activeLink" class="receipt__actions">
          <UiButton size="sm" variant="secondary" :icon="ExternalLink" :href="activeLink.path" target="_blank">预览对方看到的页面</UiButton>
          <UiButton size="sm" variant="ghost" :icon="Link2Off" :pending="pending === 'revoke'" @click="revoke">撤回</UiButton>
        </div>

        <p v-if="archivedApplication" class="receipt__off" data-testid="archived-link-note">
          这条投递在归档里：专属链接已停用，对方打开会看到“链接已停用”，也不占有效链接名额。恢复投递后会自动重新启用。
        </p>
        <div v-else-if="waitingLink" class="receipt__off" data-testid="waiting-link">
          <p>有效链接名额已满，这条投递的专属链接还停用着。撤回不再使用的链接后，就可以重新启用，地址不变。</p>
          <UiButton size="sm" variant="secondary" :icon="RefreshCw" :pending="pending === 'reactivate'" @click="reactivateLink">重新启用</UiButton>
        </div>

        <div v-if="!archivedApplication" class="receipt__create" :class="{ 'is-secondary': activeLink }">
          <div v-if="!activeLink" class="receipt__intro">
            <UiIllustration name="resume-shared" :size="72" />
            <p class="receipt__pitch"><BellRing :size="14" />给这家公司一条专属链接：对方打开时提醒你，还能看到阅读时长。</p>
          </div>
          <div v-else class="receipt__settings">
            <p class="receipt__pitch"><Settings2 :size="14" />调整这条链接（地址不变，已发出的链接继续可用）</p>
            <div class="receipt__form">
              <UiSelect v-model="settingsExpiry" :options="settingsExpiryOptions" size="sm" aria-label="调整有效期" />
              <UiButton size="sm" variant="secondary" :pending="pending === 'settings'" @click="saveLinkSettings">保存设置</UiButton>
            </div>
            <UiCheckbox v-model="settingsHideContact" label="隐藏手机号、邮箱和照片" />
            <p class="receipt__pitch">要换一份简历版本，就生成新链接（旧链接会停用，打开记录保留）。</p>
          </div>
          <p v-if="!versions.length" class="receipt__note">还没有冻结的简历版本。先在简历工作台导出一次 PDF。</p>
          <template v-else>
            <div class="receipt__form">
              <UiSelect v-model="linkVersion" :options="versionOptions" size="sm" aria-label="分享的简历版本" />
              <UiSelect v-model="linkExpiry" :options="expiryOptions" size="sm" aria-label="有效期" />
            </div>
            <UiCheckbox v-if="!activeLink" v-model="linkHideContact" label="隐藏手机号、邮箱和照片" />
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
          <div v-if="application.nextStep || application.nextStepAt"><dt>下一步</dt><dd>{{ application.nextStep || '下一步' }}<template v-if="application.nextStepAt"> · {{ formatWhen(application.nextStepAt) }}</template>
            <button v-if="!stepDone" type="button" class="step-done" @click="stepDone = true"><CheckCircle2 :size="13" />已完成</button></dd></div>
          <div v-if="application.resumeTitle"><dt>简历</dt><dd><RouterLink :to="`/resumes/${application.resumeMasterId}/manual`"><FileText :size="13" />{{ application.resumeTitle }}</RouterLink></dd></div>
          <div v-if="application.jobMatchId"><dt>岗位匹配</dt><dd><RouterLink :to="`/job-match/${application.jobMatchId}/report`"><Target :size="13" />{{ application.matchTitle || '查看报告' }}<template v-if="application.matchScore != null"> · {{ application.matchScore }} 分</template></RouterLink></dd></div>
          <div v-if="application.jobUrl"><dt>岗位链接</dt><dd><a :href="application.jobUrl" target="_blank" rel="noopener noreferrer">打开原岗位<ArrowUpRight :size="13" /></a></dd></div>
        </dl>
        <div v-if="stepDone" class="step-done-form">
          <UiInput v-model="stepNote" size="sm" :maxlength="200" placeholder="结果（可选），例如：进入二面、等通知" aria-label="下一步的结果" />
          <UiButton size="sm" :pending="pending === 'step'" @click="finishStep">记录完成</UiButton>
          <UiButton size="sm" variant="ghost" :icon="X" @click="stepDone = false">取消</UiButton>
        </div>
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
      <UiButton v-if="archivedApplication" variant="ghost" :icon="ArchiveRestore" :pending="pending === 'restore'" @click="restore">从归档恢复</UiButton>
      <UiButton v-else variant="ghost" :icon="Archive" :pending="pending === 'archive'" @click="archive">移入归档</UiButton>
      <UiButton variant="secondary" :icon="Pencil" @click="emit('edit', application)">编辑信息</UiButton>
    </template>
  </UiDrawer>
</template>

<style scoped>
.prep {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--radius-lg);
  background: linear-gradient(120deg, var(--color-primary-soft), transparent 75%), var(--surface-1);
}

.prep__icon {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary-text);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.prep__body {
  flex: 1;
  min-width: 0;
}

.prep__body h3 {
  font-size: var(--fs-body);
  font-weight: 650;
}

.prep__body p {
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

@media (max-width: 480px) {
  .prep {
    flex-wrap: wrap;
  }

  .prep > :last-child {
    width: 100%;
  }
}

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
  font-size: var(--fs-h3);
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

.receipt__own {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 28px;
  padding: 0 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  background: var(--surface-1);
  font-size: 12px;
  cursor: pointer;
}

.receipt__own:hover { color: var(--color-primary); border-color: var(--color-primary-border); }

.receipt__settings { display: grid; gap: 8px; }

.step-done {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 8px;
  padding: 2px 8px;
  border: 1px solid color-mix(in srgb, var(--color-success) 35%, var(--border-subtle));
  border-radius: 999px;
  color: var(--color-success-text);
  background: var(--color-success-soft);
  font-size: 12px;
  cursor: pointer;
}

.step-done-form { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 10px; }
.step-done-form :deep(.ui-input) { flex: 1 1 220px; }

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
  grid-template-columns: auto minmax(0, 1fr) auto auto auto;
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

.receipt__off {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px dashed var(--border-subtle);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  line-height: 1.6;
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
  .receipt__views li { grid-template-columns: auto minmax(0, 1fr) auto auto; }
  .receipt__read { display: none; }
  .stage-track { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
</style>
