<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, FilePlus2, FolderUp, MessageSquareText, Search, ShieldCheck, TrendingUp } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiStepper from '@/shared/ui/UiStepper.vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { fetchCareerProfile, saveCareerProfile } from '@/features/career-library/services/careerLibraryApi'
import type { CareerProfile } from '@/features/career-library/types'

const router = useRouter()
const steps = [
  { key: 'direction', label: '求职方向' },
  { key: 'basics', label: '基础信息' },
  { key: 'materials', label: '添加资料' },
]
const step = ref(0)
const saving = ref(false)
const formError = ref('')
const notice = ref('')
useToastFeedback(notice, 'success', 'onboarding-page-notice')

const direction = reactive({ targetJob: '', city: '', workMode: '', graduationDate: '', major: '' })
const directionErrors = reactive({ targetJob: '', city: '', workMode: '' })
const basics = reactive({ name: '', email: '', phone: '', location: '' })
const basicsErrors = reactive({ email: '', phone: '' })

// Same codes as the career library profile, so both pages show the same choice.
const workModeOptions = [
  { value: 'ONSITE', label: '现场办公' },
  { value: 'HYBRID', label: '混合办公' },
  { value: 'REMOTE', label: '远程办公' },
]
const legacyWorkModes: Record<string, string> = {
  线下办公: 'ONSITE', 现场办公: 'ONSITE', 混合办公: 'HYBRID', 线下或混合: 'HYBRID', 远程: 'REMOTE', 远程办公: 'REMOTE',
}

const profileLoad = useLoadState(async (signal) => {
  const value = await fetchCareerProfile()
  if (!signal.aborted) hydrate(value)
  return value
})
const profile = profileLoad.data

function text(source: Record<string, unknown> | undefined, key: string): string {
  const value = source?.[key]
  return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
}

function hydrate(value: CareerProfile): void {
  const mode = text(value.preferences, 'workMode')
  Object.assign(direction, {
    targetJob: text(value.intentions, 'targetJob'),
    city: text(value.preferences, 'targetCity'),
    workMode: workModeOptions.some(option => option.value === mode) ? mode : legacyWorkModes[mode] ?? '',
    graduationDate: text(value.preferences, 'graduationDate'),
    major: text(value.preferences, 'major'),
  })
  Object.assign(basics, {
    name: text(value.basics, 'name'),
    email: text(value.basics, 'email'),
    phone: text(value.basics, 'phone'),
    location: text(value.basics, 'location'),
  })
}

function goTo(index: number): void {
  formError.value = ''
  step.value = Math.max(0, Math.min(steps.length - 1, index))
}

async function save(patch: (current: CareerProfile) => CareerProfile, failure: string): Promise<boolean> {
  const current = profile.value
  if (!current) return false
  saving.value = true
  formError.value = ''
  try {
    profile.value = await saveCareerProfile(patch(current))
    return true
  } catch (reason) {
    formError.value = errorMessage(reason, failure)
    return false
  } finally {
    saving.value = false
  }
}

function validateDirection(): boolean {
  directionErrors.targetJob = direction.targetJob.trim() ? '' : '请填写目标岗位'
  directionErrors.city = direction.city.trim() ? '' : '请填写目标城市'
  directionErrors.workMode = direction.workMode ? '' : '请选择工作方式'
  return !directionErrors.targetJob && !directionErrors.city && !directionErrors.workMode
}

async function saveDirection(): Promise<void> {
  if (!validateDirection()) return
  const saved = await save(current => ({
    ...current,
    intentions: { ...current.intentions, targetJob: direction.targetJob.trim() },
    preferences: {
      ...current.preferences,
      targetCity: direction.city.trim(), workMode: direction.workMode,
      graduationDate: direction.graduationDate.trim(), major: direction.major.trim(),
    },
  }), '求职方向保存失败')
  if (!saved) return
  notice.value = '求职方向已保存。'
  goTo(1)
}

function validateBasics(): boolean {
  const email = basics.email.trim()
  const phone = basics.phone.trim()
  basicsErrors.email = !email || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? '' : '邮箱格式不正确'
  basicsErrors.phone = !phone || /^\+?[\d\s-]{6,20}$/.test(phone) ? '' : '手机号格式不正确'
  return !basicsErrors.email && !basicsErrors.phone
}

async function saveBasics(): Promise<void> {
  if (!validateBasics()) return
  const saved = await save(current => ({
    ...current,
    basics: {
      ...current.basics,
      name: basics.name.trim(), email: basics.email.trim(), phone: basics.phone.trim(), location: basics.location.trim(),
    },
  }), '基础信息保存失败')
  if (!saved) return
  notice.value = '基础信息已保存。'
  goTo(2)
}

onMounted(() => void profileLoad.load())
</script>

<template>
  <div class="onboarding-page">
    <div class="onboarding-steps"><UiStepper :steps="steps" :current="step" /></div>

    <PageState :loading="profileLoad.loading.value" :error="profileLoad.error.value" :loaded="profileLoad.loaded.value" error-title="求职资料没有读取成功" @retry="profileLoad.load">
      <div class="onboarding-grid">
        <main class="onboarding-card">
          <UiBanner v-if="formError" tone="danger" class="form-error">{{ formError }}</UiBanner>

          <form v-if="step === 0" novalidate @submit.prevent="saveDirection">
            <h1>先确定你的求职方向</h1>
            <p class="lead">只填写当前需要的信息，之后可以在求职资料库继续修改。</p>
            <UiField v-slot="{ id }" label="目标岗位" required :error="directionErrors.targetJob" hint="可填写多个方向，便于生成更相关的简历内容">
              <UiInput :id="id" v-model="direction.targetJob" placeholder="例如：数据分析 / 商业分析" maxlength="120" :disabled="saving" :invalid="!!directionErrors.targetJob" />
            </UiField>
            <UiField v-slot="{ id }" label="目标城市" required :error="directionErrors.city">
              <UiInput :id="id" v-model="direction.city" placeholder="例如：杭州" maxlength="60" :disabled="saving" :invalid="!!directionErrors.city" />
            </UiField>
            <UiField v-slot="{ id }" label="工作方式" required :error="directionErrors.workMode">
              <UiSelect :id="id" v-model="direction.workMode" :options="workModeOptions" aria-label="工作方式" placeholder="请选择工作方式" :disabled="saving" :invalid="!!directionErrors.workMode" />
            </UiField>
            <UiField v-slot="{ id }" label="毕业时间" optional hint="暂不确定可留空">
              <UiInput :id="id" v-model="direction.graduationDate" placeholder="例如：2025-06" maxlength="20" :disabled="saving" />
            </UiField>
            <UiField v-slot="{ id }" label="专业" optional hint="暂不确定可留空">
              <UiInput :id="id" v-model="direction.major" placeholder="例如：统计学" maxlength="60" :disabled="saving" />
            </UiField>
            <UiBanner tone="info">求职方向会用于简历目标与 AI 对话上下文。</UiBanner>
            <div class="actions">
              <UiButton type="submit" :pending="saving" :icon-right="ArrowRight">保存并继续</UiButton>
              <UiButton variant="ghost" :disabled="saving" @click="goTo(1)">跳过，暂不保存</UiButton>
            </div>
          </form>

          <form v-else-if="step === 1" novalidate @submit.prevent="saveBasics">
            <h1>补充基础信息</h1>
            <p class="lead">这些信息保存在你的求职资料库，都可以留空，之后随时修改或删除。</p>
            <UiField v-slot="{ id }" label="姓名" optional>
              <UiInput :id="id" v-model="basics.name" autocomplete="name" placeholder="填写用于求职的姓名" maxlength="50" :disabled="saving" />
            </UiField>
            <UiField v-slot="{ id }" label="邮箱" optional :error="basicsErrors.email">
              <UiInput :id="id" v-model="basics.email" type="email" inputmode="email" autocomplete="email" placeholder="name@example.com" maxlength="254" :disabled="saving" :invalid="!!basicsErrors.email" />
            </UiField>
            <UiField v-slot="{ id }" label="手机号" optional :error="basicsErrors.phone">
              <UiInput :id="id" v-model="basics.phone" type="tel" inputmode="tel" autocomplete="tel" placeholder="例如：138 0000 0000" maxlength="20" :disabled="saving" :invalid="!!basicsErrors.phone" />
            </UiField>
            <UiField v-slot="{ id }" label="所在城市" optional>
              <UiInput :id="id" v-model="basics.location" autocomplete="address-level2" placeholder="例如：杭州" maxlength="60" :disabled="saving" />
            </UiField>
            <div class="actions">
              <UiButton type="submit" :pending="saving" :icon-right="ArrowRight">保存并继续</UiButton>
              <UiButton variant="ghost" :disabled="saving" @click="goTo(2)">跳过，暂不保存</UiButton>
              <UiButton variant="link" :icon="ArrowLeft" :disabled="saving" @click="goTo(0)">上一步</UiButton>
            </div>
          </form>

          <section v-else aria-labelledby="onboarding-materials-title">
            <h1 id="onboarding-materials-title">添加你的求职资料</h1>
            <p class="lead">选择一种方式开始。资料越真实完整，后续的简历和建议越贴合你。</p>
            <nav class="material-actions" aria-label="添加资料的方式">
              <RouterLink class="material-action" to="/career-library?view=files">
                <span><FolderUp :size="20" aria-hidden="true" /></span>
                <p><strong>上传简历或作品文件</strong><small>在资料库上传已有简历、证书或作品文件</small></p>
                <ArrowRight :size="16" aria-hidden="true" />
              </RouterLink>
              <RouterLink class="material-action" to="/career-library?view=records">
                <span><FilePlus2 :size="20" aria-hidden="true" /></span>
                <p><strong>记录经历与成果</strong><small>逐条填写实习、项目和获奖经历</small></p>
                <ArrowRight :size="16" aria-hidden="true" />
              </RouterLink>
              <RouterLink class="material-action" to="/ai-resume/new">
                <span><MessageSquareText :size="20" aria-hidden="true" /></span>
                <p><strong>和 AI 对话创建简历</strong><small>回答几个问题，由 AI 帮你整理成简历初稿</small></p>
                <ArrowRight :size="16" aria-hidden="true" />
              </RouterLink>
            </nav>
            <div class="actions">
              <UiButton variant="ghost" @click="router.push('/dashboard')">稍后再说，回到工作台</UiButton>
              <UiButton variant="link" :icon="ArrowLeft" @click="goTo(1)">上一步</UiButton>
            </div>
          </section>
        </main>

        <aside class="guide-rail">
          <section class="guide-card">
            <h3>为什么先确认求职方向？</h3>
            <div class="benefit"><Search :size="18" aria-hidden="true" /><p><strong>更聚焦的内容</strong><span>方向明确后，AI 能优先梳理与目标岗位相关的经历</span></p></div>
            <div class="benefit"><TrendingUp :size="18" aria-hidden="true" /><p><strong>更高效的准备</strong><span>优先补齐关键经历与成果，提升简历的岗位相关性</span></p></div>
            <div class="benefit"><ShieldCheck :size="18" aria-hidden="true" /><p><strong>你的数据由你掌控</strong><span>仅在你的操作下使用，不会对外共享或用于其他用途</span></p></div>
          </section>
          <section class="guide-card"><h3>隐私与数据使用说明</h3><p>我们仅使用你填写的信息来提供简历和求职准备建议。你可以随时在求职资料库和数据权利页面查看、更正或删除数据。</p><RouterLink class="text-link" to="/account/data-rights">查看隐私与数据使用政策</RouterLink></section>
        </aside>
      </div>
    </PageState>
  </div>
</template>

<style scoped>
.onboarding-page { max-width: 1180px; margin: 0 auto; padding: 28px 32px 48px; }
.onboarding-steps { max-width: 640px; margin: 0 0 28px; }
.onboarding-grid { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 20px; }
.onboarding-card, .guide-card { border: 1px solid var(--border-default); border-radius: var(--radius-lg); background: var(--surface-1); box-shadow: var(--shadow-sm); }
.onboarding-card { padding: 32px; }
.onboarding-card form { display: grid; gap: 16px; }
.onboarding-card h1 { font-size: 30px; line-height: 1.3; }
.lead { margin: -8px 0 8px; color: var(--text-secondary); }
section > .lead { margin: 8px 0 24px; }
.form-error { margin-bottom: 16px; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 8px; }
.material-actions { display: grid; gap: 12px; margin-bottom: 24px; }
.material-action { display: grid; grid-template-columns: 40px minmax(0, 1fr) 16px; align-items: center; gap: 14px; padding: 16px; border: 1px solid var(--border-default); border-radius: var(--radius-md); color: var(--text-primary); background: var(--surface-2); text-decoration: none; transition: border-color var(--dur-fast) var(--ease-standard), background-color var(--dur-fast) var(--ease-standard); }
.material-action:hover { border-color: var(--color-primary-border); background: var(--color-primary-soft); }
.material-action:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.material-action > span { width: 40px; height: 40px; display: grid; place-items: center; border-radius: var(--radius-sm); color: var(--color-primary-text); background: var(--color-primary-soft); }
.material-action p { display: grid; gap: 3px; min-width: 0; }
.material-action small { color: var(--text-secondary); font-size: var(--fs-xs); line-height: 1.5; }
.material-action > svg:last-child { color: var(--text-tertiary); }
.guide-rail { display: flex; flex-direction: column; gap: 18px; }
.guide-card { padding: 24px; }
.guide-card h3 { margin-bottom: 18px; font-size: 15px; }
.guide-card > p { margin-bottom: 14px; color: var(--text-secondary); font-size: 13px; line-height: 1.7; }
.benefit { display: flex; gap: 12px; margin: 16px 0; color: var(--color-primary); }
.benefit p { display: flex; flex-direction: column; gap: 3px; color: var(--text-primary); }
.benefit span { color: var(--text-secondary); font-size: 12px; line-height: 1.5; }
@media (max-width: 900px) {
  .onboarding-page { padding: 20px; }
  .onboarding-grid { grid-template-columns: 1fr; }
  .onboarding-card { padding: 22px; }
  .onboarding-card h1 { font-size: 24px; }
}
@media (max-width: 580px) {
  .onboarding-page { padding: 16px; }
  .actions { flex-direction: column; align-items: stretch; }
}
</style>
