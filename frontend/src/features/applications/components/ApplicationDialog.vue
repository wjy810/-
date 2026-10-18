<script setup lang="ts">
/**
 * Record an application, or edit one. Picking a frozen resume version offers a per-company link:
 * the user learns when that company opens the resume.
 */
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { BellRing, EyeOff, Link2 } from 'lucide-vue-next'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSelect, { type SelectOption } from '@/shared/ui/UiSelect.vue'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
import { errorMessage, isApiClientError } from '@/shared/api/types'
import { listJobMatches } from '@/features/job-match/services/jobMatchApi'
import { createApplication, fetchShareableVersions, updateApplication } from '../services/applicationsApi'
import { CHANNELS, STAGES } from '../labels'
import type { ApplicationChannel, ApplicationStage, ApplicationWrite, JobApplication, ShareableVersion } from '../types'

export type ApplicationPrefill = { company?: string; positionTitle?: string; location?: string; jobMatchId?: string; jobUrl?: string }

const props = defineProps<{ open: boolean; application?: JobApplication | null; prefill?: ApplicationPrefill | null }>()
const emit = defineEmits<{ close: []; saved: [application: JobApplication] }>()

const NO_VERSION = '__none__'
const NO_MATCH = '__none__'

const form = reactive({
  company: '',
  positionTitle: '',
  location: '',
  channel: 'JOB_BOARD' as ApplicationChannel,
  stage: 'APPLIED' as ApplicationStage,
  appliedOn: '',
  jobUrl: '',
  salaryText: '',
  resumeVersionId: NO_VERSION,
  createLink: true,
  hideContact: false,
  jobMatchId: NO_MATCH,
  nextStep: '',
  nextStepAt: '',
  notes: '',
})
const versions = shallowRef<ShareableVersion[]>([])
const matches = shallowRef<SelectOption[]>([])
const touched = ref(false)
const submitting = ref(false)
const error = ref('')
/** The free plan's link limit refused the link: offer to record the application without one. */
const linkLimited = ref(false)

const editing = computed(() => Boolean(props.application))
const versionOptions = computed<SelectOption[]>(() => [
  { value: NO_VERSION, label: '暂不附简历', description: '之后可以在详情里生成链接' },
  ...versions.value.map(item => ({
    value: item.versionId,
    label: `${item.resumeTitle} · 版本 ${item.ordinal}`,
    description: `${item.anonymous ? '匿名版 · ' : ''}冻结于 ${new Date(item.frozenAt).toLocaleDateString('zh-CN')}`,
  })),
])
const matchOptions = computed<SelectOption[]>(() => [{ value: NO_MATCH, label: '不关联' }, ...matches.value])
const stageOptions = STAGES.filter(stage => stage.value !== 'CLOSED').map(stage => ({ value: stage.value, label: stage.label }))
const channelOptions = CHANNELS.map(channel => ({ value: channel.value, label: channel.label }))
const hasVersion = computed(() => form.resumeVersionId !== NO_VERSION)
const urlInvalid = computed(() => Boolean(form.jobUrl.trim()) && !/^https?:\/\/\S+$/i.test(form.jobUrl.trim()))
const missing = computed(() => {
  if (!form.company.trim()) return '请填写公司名称'
  if (!form.positionTitle.trim()) return '请填写岗位名称'
  if (urlInvalid.value) return '岗位链接需要以 http:// 或 https:// 开头'
  return ''
})

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
}

/** The picker works in local time without a zone (`YYYY-MM-DDTHH:mm`); the API takes an instant. */
function toLocalInput(iso?: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function reset(): void {
  const source = props.application
  const prefill = props.prefill ?? {}
  Object.assign(form, {
    company: source?.company ?? prefill.company ?? '',
    positionTitle: source?.positionTitle ?? prefill.positionTitle ?? '',
    location: source?.location ?? prefill.location ?? '',
    channel: source?.channel ?? 'JOB_BOARD',
    stage: 'APPLIED',
    appliedOn: source?.appliedOn ?? (source ? '' : today()),
    jobUrl: source?.jobUrl ?? prefill.jobUrl ?? '',
    salaryText: source?.salaryText ?? '',
    resumeVersionId: source?.resumeVersionId ?? NO_VERSION,
    createLink: true,
    hideContact: false,
    jobMatchId: source?.jobMatchId ?? prefill.jobMatchId ?? NO_MATCH,
    nextStep: source?.nextStep ?? '',
    nextStepAt: toLocalInput(source?.nextStepAt),
    notes: source?.notes ?? '',
  })
  touched.value = false
  error.value = ''
  linkLimited.value = false
}

async function loadOptions(): Promise<void> {
  try {
    versions.value = await fetchShareableVersions()
    // A new application goes out with the latest frozen, non-anonymous version unless chosen otherwise.
    if (!props.application && form.resumeVersionId === NO_VERSION) {
      form.resumeVersionId = versions.value.find(item => !item.anonymous)?.versionId ?? NO_VERSION
    }
  } catch {
    versions.value = []
  }
  try {
    const list = await listJobMatches()
    matches.value = list.filter(item => item.status === 'COMPLETED').map(item => ({
      value: item.id,
      label: [item.title || '岗位', item.company].filter(Boolean).join(' · '),
      description: item.score != null ? `匹配 ${item.score} 分` : undefined,
    }))
  } catch {
    matches.value = []
  }
}

async function submit(): Promise<void> {
  touched.value = true
  if (missing.value || submitting.value) return
  submitting.value = true
  error.value = ''
  linkLimited.value = false
  const write: ApplicationWrite = {
    company: form.company.trim(),
    positionTitle: form.positionTitle.trim(),
    location: form.location.trim() || null,
    channel: form.channel,
    jobUrl: form.jobUrl.trim() || null,
    salaryText: form.salaryText.trim() || null,
    resumeVersionId: hasVersion.value ? form.resumeVersionId : null,
    jobMatchId: form.jobMatchId === NO_MATCH ? null : form.jobMatchId,
    appliedOn: form.appliedOn || null,
    nextStep: form.nextStep.trim() || null,
    nextStepAt: form.nextStepAt ? new Date(form.nextStepAt).toISOString() : null,
    notes: form.notes.trim() || null,
  }
  try {
    const saved = props.application
      ? await updateApplication(props.application.id, { ...write, expectedVersion: props.application.version })
      : await createApplication({ ...write, stage: form.stage, createLink: hasVersion.value && form.createLink, hideContact: form.hideContact })
    emit('saved', saved)
  } catch (reason) {
    linkLimited.value = isApiClientError(reason) && reason.reason === 'RESUME_LINK_LIMIT'
    error.value = errorMessage(reason, editing.value ? '保存失败' : '记录投递失败')
  } finally {
    submitting.value = false
  }
}

function recordWithoutLink(): void {
  form.createLink = false
  void submit()
}

watch(() => props.open, open => {
  if (!open) return
  reset()
  void loadOptions()
}, { immediate: true })
</script>

<template>
  <UiDialog
    :open="open"
    :title="editing ? '编辑投递' : '记录一次投递'"
    :description="editing ? '修改公司、岗位和下一步安排。阶段在详情里调整。' : '附上专属简历链接后，对方打开简历时你会收到提醒。'"
    width="lg"
    :dismissible="!submitting"
    @close="emit('close')"
  >
    <form id="application-form" class="app-form" novalidate @submit.prevent="submit">
      <div class="app-form__grid">
        <UiField v-slot="{ id }" label="公司" required :error="touched && !form.company.trim() ? '请填写公司名称' : null">
          <UiInput :id="id" v-model="form.company" maxlength="120" placeholder="例如：合成科技" autocomplete="organization" />
        </UiField>
        <UiField v-slot="{ id }" label="岗位" required :error="touched && !form.positionTitle.trim() ? '请填写岗位名称' : null">
          <UiInput :id="id" v-model="form.positionTitle" maxlength="120" placeholder="例如：Java 后端工程师" />
        </UiField>
        <UiField v-slot="{ id }" label="投递渠道">
          <UiSelect :id="id" v-model="form.channel" :options="channelOptions" aria-label="投递渠道" />
        </UiField>
        <UiField v-if="!editing" v-slot="{ id }" label="当前阶段">
          <UiSelect :id="id" v-model="form.stage" :options="stageOptions" aria-label="当前阶段" />
        </UiField>
        <UiField v-slot="{ id }" label="投递日期" optional>
          <AppDatePicker :id="id" v-model="form.appliedOn" mode="date" label="投递日期" placeholder="选择投递日期" />
        </UiField>
        <UiField v-slot="{ id }" label="工作城市" optional>
          <UiInput :id="id" v-model="form.location" maxlength="80" placeholder="例如：上海" />
        </UiField>
      </div>

      <section v-if="!editing" class="app-form__link" :class="{ 'is-on': hasVersion && form.createLink }">
        <UiField v-slot="{ id }" label="投出的简历">
          <UiSelect :id="id" v-model="form.resumeVersionId" :options="versionOptions" aria-label="投出的简历版本" />
        </UiField>
        <p v-if="!versions.length" class="app-form__note">还没有冻结的简历版本。在简历工作台导出一次 PDF，或在“版本与导出”里冻结一个版本后就能附上。</p>
        <template v-else-if="hasVersion">
          <UiCheckbox v-model="form.createLink" label="生成这家公司的专属简历链接，对方打开时提醒我" />
          <UiCheckbox v-if="form.createLink" v-model="form.hideContact" label="链接里隐藏手机号、邮箱和照片" />
          <ul class="app-form__perks" aria-label="专属链接能告诉你什么">
            <li><BellRing :size="14" />首次打开、转给新读者时通知你</li>
            <li><Link2 :size="14" />记录打开时间、设备和阅读时长</li>
            <li><EyeOff :size="14" />不记录对方 IP，不需要对方登录</li>
          </ul>
        </template>
      </section>

      <details class="app-form__more" :open="editing">
        <summary>更多信息</summary>
        <div class="app-form__grid">
          <UiField v-slot="{ id }" label="岗位链接" optional :error="touched && urlInvalid ? '需要以 http:// 或 https:// 开头' : null">
            <UiInput :id="id" v-model="form.jobUrl" type="url" maxlength="500" placeholder="https://" />
          </UiField>
          <UiField v-slot="{ id }" label="薪资" optional>
            <UiInput :id="id" v-model="form.salaryText" maxlength="60" placeholder="例如：20-30K · 14 薪" />
          </UiField>
          <UiField v-slot="{ id }" label="下一步" optional hint="例如：周四二面、等测评结果">
            <UiInput :id="id" v-model="form.nextStep" maxlength="200" />
          </UiField>
          <UiField v-slot="{ id }" label="下一步时间" optional>
            <AppDatePicker :id="id" v-model="form.nextStepAt" mode="datetime" label="下一步时间" placeholder="选择日期和时间" />
          </UiField>
          <UiField v-slot="{ id }" label="关联岗位匹配" optional class="app-form__wide">
            <UiSelect :id="id" v-model="form.jobMatchId" :options="matchOptions" aria-label="关联岗位匹配" searchable />
          </UiField>
          <UiField v-slot="{ id }" label="备注" optional class="app-form__wide">
            <UiTextarea :id="id" v-model="form.notes" autosize :min-rows="2" :max-rows="6" maxlength="4000" placeholder="面试官、准备要点、内推人……" />
          </UiField>
        </div>
      </details>

      <div v-if="error" class="app-form__error" role="alert">
        <p>{{ error }}</p>
        <div v-if="linkLimited" class="app-form__error-actions">
          <UiButton size="sm" variant="secondary" :disabled="submitting" @click="recordWithoutLink">先不附链接，直接记录</UiButton>
          <UiButton size="sm" variant="link" to="/account/membership">了解专业版</UiButton>
        </div>
      </div>
    </form>

    <template #footer>
      <UiButton variant="secondary" :disabled="submitting" @click="emit('close')">取消</UiButton>
      <UiButton type="submit" form="application-form" :pending="submitting">{{ editing ? '保存' : '记录投递' }}</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.app-form {
  display: grid;
  gap: var(--space-4);
}

.app-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
}

.app-form__wide {
  grid-column: 1 / -1;
}

.app-form__link {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
  transition: border-color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out);
}

.app-form__link.is-on {
  border-color: var(--color-primary-border);
  background: linear-gradient(135deg, var(--color-primary-soft), transparent 70%), var(--surface-1);
}

.app-form__note {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.app-form__perks {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  color: var(--text-secondary);
  font-size: var(--fs-xs);
}

.app-form__perks li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.app-form__perks svg {
  color: var(--color-primary-text);
}

.app-form__more summary {
  width: max-content;
  margin-bottom: var(--space-3);
  color: var(--color-primary-text);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
}

.app-form__error {
  display: grid;
  gap: var(--space-2);
  color: var(--color-danger-text);
  font-size: var(--fs-sm);
}

.app-form__error-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

@media (max-width: 640px) {
  .app-form__grid {
    grid-template-columns: 1fr;
  }
}
</style>
