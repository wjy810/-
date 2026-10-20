<script setup lang="ts">
/**
 * Every resume link in one place (docs/phase2/01): the ones made for an application and "general" ones
 * for a job fair, a referral group or a personal page. Each keeps its own receipts.
 */
import { computed, onMounted, ref, shallowRef } from 'vue'
import { ChevronDown, Copy, ExternalLink, Link2, Link2Off, Monitor, Plus, Send, Smartphone, Tablet } from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSelect, { type SelectOption } from '@/shared/ui/UiSelect.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { confirm } from '@/shared/ui/confirm'
import { toast } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { copyText } from '@/shared/lib/clipboard'
import { formatRelative, formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import { createResumeLink, fetchLinkViews, fetchResumeLinks, fetchShareableVersions, revokeResumeLink } from '../services/applicationsApi'
import { clientLabel, deviceLabel, readTime } from '../labels'
import { filterLinks, isGeneral, linkState, linkTitle, sortLinks, type LinkFilter } from '../utils/links'
import type { LinkViewRecord, ResumeLink, ShareableVersion } from '../types'

const props = withDefaults(defineProps<{ now?: Date; createOnMount?: boolean }>(), { now: () => new Date(), createOnMount: false })
const emit = defineEmits<{ 'open-application': [id: string]; changed: [] }>()

const page = useLoadState(() => fetchResumeLinks())
/** The link just created stays on top (and highlighted) until the list is read again. */
const freshId = ref<string | null>(null)
const links = computed(() => {
  const sorted = sortLinks(page.data.value ?? [])
  const fresh = sorted.find(item => item.id === freshId.value)
  return fresh ? [fresh, ...sorted.filter(item => item !== fresh)] : sorted
})
const filter = ref<LinkFilter>('all')
const shown = computed(() => filterLinks(links.value, filter.value))
const filterItems = computed(() => [
  { value: 'all' as const, label: '全部', count: links.value.length },
  { value: 'active' as const, label: '有效', count: filterLinks(links.value, 'active').length },
  { value: 'general' as const, label: '通用', count: filterLinks(links.value, 'general').length },
])
const pending = ref('')
const expanded = ref<string | null>(null)
const views = shallowRef<Record<string, LinkViewRecord[]>>({})

const creating = ref(false)
const submitting = ref(false)
const touched = ref(false)
const versions = shallowRef<ShareableVersion[]>([])
const form = ref({ label: '', versionId: '', expiry: '60', hideContact: false })
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
const labelError = computed(() => (touched.value && !form.value.label.trim() ? '写一下这条链接发到哪里，方便以后区分' : null))

const url = (link: ResumeLink) => `${window.location.origin}${link.path}`

function deviceIcon(device: string) {
  if (device === 'MOBILE') return Smartphone
  if (device === 'TABLET') return Tablet
  return Monitor
}

async function copy(link: ResumeLink): Promise<void> {
  if (await copyText(url(link))) toast.success('链接已复制')
  else toast.error('浏览器不允许复制，请打开预览后从地址栏复制')
}

async function toggleViews(link: ResumeLink): Promise<void> {
  if (expanded.value === link.id) {
    expanded.value = null
    return
  }
  expanded.value = link.id
  if (views.value[link.id]) return
  try {
    views.value = { ...views.value, [link.id]: await fetchLinkViews(link.id) }
  } catch (reason) {
    expanded.value = null
    toast.error(errorMessage(reason, '打开记录读取失败'))
  }
}

async function revoke(link: ResumeLink): Promise<void> {
  if (!(await confirm({
    title: `撤回「${linkTitle(link)}」？`,
    message: '对方再打开时会看到“链接已撤回”。已有的打开记录会保留。',
    confirmText: '撤回链接',
    tone: 'danger',
  }))) return
  pending.value = `revoke-${link.id}`
  try {
    const updated = await revokeResumeLink(link.id)
    page.data.value = (page.data.value ?? []).map(item => (item.id === updated.id ? updated : item))
    emit('changed')
    toast.success('链接已撤回')
  } catch (reason) {
    toast.error(errorMessage(reason, '撤回失败'))
  } finally {
    pending.value = ''
  }
}

async function openCreate(): Promise<void> {
  form.value = { label: '', versionId: '', expiry: '60', hideContact: false }
  touched.value = false
  creating.value = true
  try {
    versions.value = await fetchShareableVersions()
    form.value.versionId = versions.value[0]?.versionId ?? ''
  } catch (reason) {
    versions.value = []
    toast.error(errorMessage(reason, '简历版本读取失败'))
  }
}

async function submit(): Promise<void> {
  touched.value = true
  if (labelError.value || !form.value.versionId || submitting.value) return
  submitting.value = true
  try {
    const created = await createResumeLink({
      resumeVersionId: form.value.versionId,
      label: form.value.label.trim(),
      hideContact: form.value.hideContact,
      expiresInDays: form.value.expiry === 'none' ? null : Number(form.value.expiry),
    })
    page.data.value = [created, ...(page.data.value ?? [])]
    freshId.value = created.id
    creating.value = false
    filter.value = 'all'
    emit('changed')
    toast.success(`已生成「${linkTitle(created)}」`, {
      description: '复制后发出去，有人打开时你会收到提醒。',
      action: { label: '复制链接', onClick: () => { void copy(created) } },
      duration: 8000,
    })
  } catch (reason) {
    toast.error(errorMessage(reason, '生成链接失败'))
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await page.load()
  if (props.createOnMount) void openCreate()
})

defineExpose({ openCreate, reload: () => { freshId.value = null; return page.load() } })
</script>

<template>
  <section class="links" aria-labelledby="links-title">
    <header class="links__head">
      <div>
        <h2 id="links-title">专属链接</h2>
        <p>每条链接单独统计打开次数和读者。不属于某次投递的“通用链接”，适合招聘会、内推群或个人主页。</p>
      </div>
      <UiButton :icon="Plus" @click="openCreate">新建通用链接</UiButton>
    </header>

    <div v-if="page.loading.value && !page.loaded.value" class="links__skeleton">
      <UiSkeleton v-for="n in 3" :key="n" height="84px" radius="var(--radius-lg)" />
    </div>
    <UiErrorState v-else-if="page.error.value && !page.loaded.value" compact title="链接读取失败" :error="page.error.value" @retry="page.load" />
    <template v-else>
      <UiSegmented v-if="links.length" v-model="filter" :items="filterItems" aria-label="筛选链接" size="sm" />

      <ul v-if="shown.length" class="links__list">
        <li v-for="link in shown" :key="link.id" class="link-row" :class="{ 'is-off': link.status !== 'ACTIVE', 'is-fresh': link.id === freshId }">
          <span class="link-row__icon" aria-hidden="true"><component :is="isGeneral(link) ? Link2 : Send" :size="16" /></span>
          <div class="link-row__main">
            <div class="link-row__title">
              <strong>{{ linkTitle(link) }}</strong>
              <UiBadge :tone="linkState(link, now).tone" size="sm">{{ linkState(link, now).label }}</UiBadge>
              <UiBadge v-if="isGeneral(link)" tone="primary" size="sm">通用</UiBadge>
            </div>
            <small>
              {{ link.resumeTitle || '简历' }}{{ link.hideContact ? ' · 已隐藏联系方式' : '' }} · 创建于 {{ formatRelative(link.createdAt, now) }}
            </small>
            <p class="link-row__receipt" :class="{ 'is-seen': link.viewCount }">
              <template v-if="link.viewCount">
                打开 {{ link.viewCount }} 次 · {{ link.visitorCount }} 位读者 · 累计阅读 {{ readTime(link.readSeconds) }}
                <span v-if="link.lastViewedAt"> · 最近 {{ formatRelative(link.lastViewedAt, now) }}</span>
              </template>
              <template v-else>还没有被打开</template>
            </p>
          </div>
          <div class="link-row__actions">
            <UiButton v-if="link.status === 'ACTIVE'" size="sm" variant="secondary" :icon="Copy" @click="copy(link)">复制</UiButton>
            <UiButton size="sm" variant="ghost" :icon="ExternalLink" :href="link.path" target="_blank" :aria-label="`预览「${linkTitle(link)}」`">预览</UiButton>
            <UiButton v-if="link.applicationId" size="sm" variant="ghost" @click="emit('open-application', link.applicationId)">投递详情</UiButton>
            <UiButton
              v-if="link.viewCount"
              size="sm"
              variant="ghost"
              :icon="ChevronDown"
              class="link-row__toggle"
              :class="{ 'is-open': expanded === link.id }"
              :aria-expanded="expanded === link.id"
              @click="toggleViews(link)"
            >打开记录</UiButton>
            <UiButton
              v-if="link.status === 'ACTIVE'"
              size="sm"
              variant="ghost"
              :icon="Link2Off"
              :pending="pending === `revoke-${link.id}`"
              @click="revoke(link)"
            >撤回</UiButton>
          </div>
          <ul v-if="expanded === link.id" class="link-row__views" aria-label="打开记录">
            <li v-if="!views[link.id]" class="is-loading"><UiSkeleton height="14px" width="60%" /></li>
            <li v-for="view in views[link.id] ?? []" :key="view.id">
              <component :is="deviceIcon(view.device)" :size="14" aria-hidden="true" />
              <span>{{ deviceLabel(view.device) }} · {{ clientLabel(view.client) }}<small v-if="view.referrerHost"> · 来自 {{ view.referrerHost }}</small></span>
              <em>{{ view.readSeconds ? `读了 ${readTime(view.readSeconds)}` : '刚打开' }}</em>
              <time :datetime="view.startedAt" :title="formatWhen(view.startedAt)">{{ formatRelative(view.startedAt, now) }}</time>
            </li>
          </ul>
        </li>
      </ul>

      <p v-else-if="links.length" class="links__none">没有符合条件的链接</p>
      <UiEmptyState
        v-else
        illustration="resume-shared"
        title="还没有专属链接"
        description="记录投递时可以为每家公司生成一条；也可以先建一条通用链接，发到招聘会或内推群里。"
        bordered
      >
        <UiButton :icon="Plus" @click="openCreate">新建通用链接</UiButton>
      </UiEmptyState>
    </template>

    <UiDialog
      :open="creating"
      title="新建通用链接"
      description="不属于某一次投递的简历链接，同样会统计打开、读者和阅读时长。"
      width="md"
      :dismissible="!submitting"
      @close="creating = false"
    >
      <form id="general-link-form" class="link-form" novalidate @submit.prevent="submit">
        <UiField v-slot="{ id }" label="发到哪里" required :error="labelError">
          <UiInput :id="id" v-model="form.label" maxlength="80" placeholder="例如：秋招双选会、校友内推群、个人主页" />
        </UiField>
        <UiField v-slot="{ id }" label="简历版本">
          <UiSelect v-if="versions.length" :id="id" v-model="form.versionId" :options="versionOptions" aria-label="简历版本" />
          <p v-else class="link-form__note">还没有冻结的简历版本。先在简历工作台导出一次 PDF，或在“版本与导出”里冻结一个版本。</p>
        </UiField>
        <UiField v-slot="{ id }" label="有效期">
          <UiSelect :id="id" v-model="form.expiry" :options="expiryOptions" aria-label="有效期" />
        </UiField>
        <UiCheckbox v-model="form.hideContact" label="隐藏手机号、邮箱和照片（发到公开场合时建议勾选）" />
      </form>
      <template #footer>
        <UiButton variant="secondary" :disabled="submitting" @click="creating = false">取消</UiButton>
        <UiButton type="submit" form="general-link-form" :pending="submitting" :disabled="!versions.length" :icon="Link2">生成链接</UiButton>
      </template>
    </UiDialog>
  </section>
</template>

<style scoped>
.links {
  display: grid;
  gap: var(--space-4);
}

.links__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.links__head h2 {
  font-size: var(--fs-h3);
  font-weight: 650;
}

.links__head p {
  max-width: 62ch;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.links__skeleton,
.links__list {
  display: grid;
  gap: var(--space-2);
}

.links__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.link-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.link-row.is-fresh {
  border-color: var(--color-primary-border);
  box-shadow: var(--shadow-sm), 0 0 0 3px var(--color-primary-soft);
  animation: jp-rise-in var(--dur-slow) var(--ease-out);
}

.link-row.is-off {
  background: var(--surface-2);
  box-shadow: none;
}

.link-row__icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
}

.link-row.is-off .link-row__icon {
  color: var(--text-tertiary);
  background: var(--surface-3);
}

.link-row__main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.link-row__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.link-row__title strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--fs-body);
  font-weight: 600;
}

.link-row__main small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.link-row__receipt {
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  font-variant-numeric: tabular-nums;
}

.link-row__receipt.is-seen {
  color: var(--color-success-text);
  font-weight: 550;
}

.link-row__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}

.link-row__toggle :deep(svg) {
  transition: transform var(--dur-base) var(--ease-out);
}

.link-row__toggle.is-open :deep(svg) {
  transform: rotate(180deg);
}

.link-row__views {
  grid-column: 2 / -1;
  display: grid;
  gap: 6px;
  margin: 0;
  padding: var(--space-3) 0 0;
  border-top: 1px dashed var(--border-subtle);
  list-style: none;
}

.link-row__views li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
}

.link-row__views span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-row__views em {
  color: var(--text-primary);
  font-style: normal;
  font-variant-numeric: tabular-nums;
}

.link-row__views time {
  min-width: 64px;
  color: var(--text-tertiary);
  text-align: right;
}

.links__none {
  padding: var(--space-6);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  text-align: center;
}

.link-form {
  display: grid;
  gap: var(--space-4);
}

.link-form__note {
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

@media (max-width: 720px) {
  .link-row {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .link-row__actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .link-row__views {
    grid-column: 1 / -1;
  }
}
</style>
