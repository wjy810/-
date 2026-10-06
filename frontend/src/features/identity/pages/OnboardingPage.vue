<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { fetchCareerProfile, saveCareerProfile } from '@/features/career-library/services/careerLibraryApi'
import type { CareerProfile } from '@/features/career-library/types'

const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const pageError = ref('')
const formError = ref('')
useToastFeedback(pageError, 'error', 'onboarding-page-error')
const profile = ref<CareerProfile | null>(null)
const form = reactive({ direction: '', city: '', workMode: '', graduationDate: '', major: '' })
const errors = reactive({ direction: '', city: '', workMode: '' })

const requiredReady = computed(() => Boolean(form.direction.trim() && form.city.trim() && form.workMode.trim()))

function textValue(source: Record<string, unknown> | undefined, key: string): string {
  const value = source?.[key]
  return value == null ? '' : String(value)
}

async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    profile.value = await fetchCareerProfile()
    form.direction = textValue(profile.value.intentions, 'targetJob')
    form.city = textValue(profile.value.preferences, 'targetCity')
    form.workMode = textValue(profile.value.preferences, 'workMode')
    form.graduationDate = textValue(profile.value.preferences, 'graduationDate')
    form.major = textValue(profile.value.preferences, 'major')
  } catch (error) {
    pageError.value = errorMessage(error, '求职方向读取失败')
  } finally {
    loading.value = false
  }
}

function validate(): boolean {
  errors.direction = form.direction.trim() ? '' : '请填写目标岗位'
  errors.city = form.city.trim() ? '' : '请填写目标城市'
  errors.workMode = form.workMode.trim() ? '' : '请填写工作方式'
  return !errors.direction && !errors.city && !errors.workMode
}

async function saveAndContinue(): Promise<void> {
  formError.value = ''
  if (!validate()) return
  saving.value = true
  try {
    if (!profile.value) return
    profile.value = await saveCareerProfile({ ...profile.value,
      intentions: { ...profile.value.intentions, targetJob: form.direction.trim() },
      preferences: { ...profile.value.preferences, targetCity: form.city.trim(), workMode: form.workMode.trim(),
        graduationDate: form.graduationDate.trim(), major: form.major.trim() },
    })
    await router.push('/career-library')
  } catch (error) {
    formError.value = errorMessage(error, '求职方向保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => void load())
</script>

<template>
      <div class="onboarding-page">
      <ol class="steps" aria-label="求职资料引导进度">
        <li class="is-active"><span>1</span>求职方向</li>
        <li><span>2</span>基础信息</li>
        <li><span>3</span>添加资料</li>
      </ol>

      <div v-if="loading" class="onboarding-card" aria-busy="true">
        <div class="bone" /><div class="bone bone--short" />
      </div>

      <div v-else class="onboarding-grid">
        <main class="onboarding-card">
          <h1>先确定你的求职方向</h1>
          <p class="lead">只填写当前任务需要的信息，稍后可以继续补充</p>
          <AppBanner v-if="formError" tone="bad">{{ formError }}</AppBanner>
          <form novalidate @submit.prevent="saveAndContinue">
            <AppField id="guide-direction" label="目标岗位 *" :error="errors.direction" hint="可填写多个方向，便于生成更相关的简历内容">
              <input id="guide-direction" v-model="form.direction" placeholder="例如：数据分析 / 商业分析" :disabled="saving" />
            </AppField>
            <AppField id="guide-city" label="目标城市 *" :error="errors.city">
              <input id="guide-city" v-model="form.city" placeholder="例如：杭州" :disabled="saving" />
            </AppField>
            <AppField id="guide-mode" label="工作方式 *" :error="errors.workMode">
              <AppSelect id="guide-mode" v-model="form.workMode" ariaLabel="工作方式" :disabled="saving">
                <option value="">请选择工作方式</option><option>线下办公</option><option>远程</option><option>线下或混合</option><option>混合办公</option>
              </AppSelect>
            </AppField>
            <AppField id="guide-graduation" label="毕业时间" hint="可选，暂不确定可留空">
              <input id="guide-graduation" v-model="form.graduationDate" placeholder="例如：2025-06" :disabled="saving" />
            </AppField>
            <AppField id="guide-major" label="专业" hint="可选，暂不确定可留空">
              <input id="guide-major" v-model="form.major" placeholder="例如：统计学" :disabled="saving" />
            </AppField>
            <AppBanner tone="ink"><AppIcon name="info" :size="15" /> 求职方向将用于简历目标与 AI 对话上下文。</AppBanner>
            <div class="actions">
              <AppButton type="submit" :pending="saving" :disabled="!requiredReady">{{ saving ? '正在保存…' : '保存并继续' }}</AppButton>
              <AppButton type="button" variant="ghost" :disabled="saving || !requiredReady" @click="saveAndContinue">跳过非必填项</AppButton>
            </div>
          </form>
        </main>

        <aside class="guide-rail">
          <section class="guide-card">
            <h3>为什么先确认求职方向？</h3>
            <div class="benefit"><AppIcon name="search" :size="18" /><p><strong>更聚焦的内容</strong><span>方向明确后，AI 能优先梳理与目标岗位相关的经历</span></p></div>
            <div class="benefit"><AppIcon name="trending-up" :size="18" /><p><strong>更高效的准备</strong><span>优先补齐关键经历与成果，提升简历的岗位相关性</span></p></div>
            <div class="benefit"><AppIcon name="shield" :size="18" /><p><strong>你的数据由你掌控</strong><span>仅在你的操作下使用，不会对外共享或用于其他用途</span></p></div>
          </section>
          <section class="guide-card"><h3>隐私与数据使用说明</h3><p>我们仅使用你填写的信息来提供简历和求职准备建议。你可以随时在求职资料库和数据权利页面查看、更正或删除数据。</p><RouterLink class="text-link" to="/account/data-rights">查看隐私与数据使用政策</RouterLink></section>
        </aside>
      </div>
    </div>
</template>

<style scoped>
.onboarding-page{padding:28px 32px 48px;max-width:1180px;margin:0 auto}.steps{display:flex;align-items:center;gap:34px;list-style:none;margin:0 0 28px 40px;color:var(--text-3)}.steps li{display:flex;align-items:center;gap:10px}.steps li:not(:last-child)::after{content:'';width:110px;height:1px;background:var(--border);margin-left:20px}.steps span{width:34px;height:34px;border:1px solid var(--border);border-radius:50%;display:grid;place-items:center;font-weight:700}.steps .is-active{color:var(--primary)}.steps .is-active span{background:var(--primary);color:#fff;border-color:var(--primary)}.onboarding-grid{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:20px}.onboarding-card,.guide-card{background:var(--surface);border:1px solid var(--border);border-radius:14px;box-shadow:var(--shadow-s)}.onboarding-card{padding:32px}.onboarding-card h1{font-size:30px}.lead{margin:8px 0 24px;color:var(--text-2)}.actions{display:flex;gap:12px;margin-top:20px}.guide-rail{display:flex;flex-direction:column;gap:18px}.guide-card{padding:24px}.guide-card h3{font-size:15px;margin-bottom:18px}.guide-card>p{color:var(--text-2);font-size:13px;line-height:1.7;margin-bottom:14px}.benefit{display:flex;gap:12px;color:var(--primary);margin:16px 0}.benefit p{display:flex;flex-direction:column;gap:3px;color:var(--text)}.benefit span{font-size:12px;line-height:1.5;color:var(--text-2)}@media(max-width:900px){.steps{margin-left:0;gap:12px}.steps li:not(:last-child)::after{width:24px;margin-left:4px}.onboarding-grid{grid-template-columns:1fr}.onboarding-page{padding:20px}.onboarding-card{padding:22px}}@media(max-width:580px){.steps li{font-size:0}.steps span{font-size:13px}.actions{flex-direction:column}}
</style>
