<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '@/shared/api/types'
import AppIcon from '@/shared/ui/AppIcon.vue'
import { createAiResume, selectAiResumeTemplate } from '../services/aiResumeApi'
import type { AiIdentity } from '../types'

type ResumeStart = 'NEW' | 'EXISTING'

const router = useRouter()
const route = useRoute()
/** Started from the template center: apply that layout right after creating the resume. */
const requestedTemplate = computed(() => {
  const value = route.query.template
  return typeof value === 'string' && /^[a-z0-9-]{3,80}$/.test(value) ? value : ''
})
const start = ref<ResumeStart | ''>('')
const identity = ref<AiIdentity | ''>('')
const pending = ref(false)
const error = ref('')
const flow = ref<HTMLElement | null>(null)

const identityOptions: Array<{ id: AiIdentity; label: string; detail: string; icon: 'book' | 'award' | 'briefcase' }> = [
  { id: 'STUDENT', label: '学生', detail: '实习、课程、社团与项目经历', icon: 'book' },
  { id: 'GRADUATE', label: '应届生', detail: '校招与第一份全职工作', icon: 'award' },
  { id: 'PROFESSIONAL', label: '职场人士', detail: '工作成果与岗位定制', icon: 'briefcase' },
]

const selectedStartText = computed(() => start.value === 'EXISTING'
  ? '我已经有一份简历，想继续完善'
  : '我想从零新建一份简历')

function chooseStart(value: ResumeStart): void {
  if (pending.value) return
  start.value = value
  identity.value = ''
  error.value = ''
  void nextTick(scrollLatest)
}

async function chooseIdentity(value: AiIdentity): Promise<void> {
  if (!start.value || pending.value) return
  identity.value = value
  pending.value = true
  error.value = ''
  await nextTick(scrollLatest)
  try {
    const conversation = await createAiResume(value)
    if (requestedTemplate.value && requestedTemplate.value !== conversation.layout?.templateId) {
      // Best effort: the default layout still works, and the template can be switched in the workbench.
      await selectAiResumeTemplate(conversation.id, requestedTemplate.value, conversation.layout?.version).catch(() => undefined)
    }
    await router.replace({
      path: `/ai-resume/${conversation.id}`,
      query: { source: start.value === 'EXISTING' ? 'existing' : 'new' },
    })
  } catch (reason) {
    error.value = errorMessage(reason, '创建 AI 简历失败')
    pending.value = false
  }
}

function identityLabel(value: AiIdentity): string {
  return identityOptions.find((option) => option.id === value)?.label ?? value
}

function scrollLatest(): void {
  flow.value?.scrollTo({ top: flow.value.scrollHeight, behavior: 'smooth' })
}
</script>

<template>
  <main class="onboarding-workbench">
    <header class="onboarding-bar">
      <RouterLink class="icon-btn" to="/resumes" title="返回简历列表"><AppIcon name="chevron-left" :size="18" /></RouterLink>
      <strong>AI 创建简历</strong>
      <span><AppIcon name="file-text" :size="15" />简历预览</span>
    </header>

    <div class="onboarding-grid">
      <section ref="flow" class="onboarding-flow" aria-label="AI 简历建档对话">
        <article class="flow-copy">
          <span>AI 简历助手</span>
          <p>你好，我会一步一步梳理你的经历、明确求职目标，并生成一份适合投递的简历。</p>
          <p>如果你已经有简历，可以在现有内容上继续完善；如果还没有，我们就从基础信息开始创建。</p>
        </article>

        <section class="choice-card">
          <div><strong>你现在已经有一份简历了吗？</strong><span>选择后我会按对应路径继续，不会覆盖已有内容。</span></div>
          <button type="button" :disabled="Boolean(start)" @click="chooseStart('EXISTING')">有，我已经有简历了<AppIcon name="chevron-right" :size="16" /></button>
          <button type="button" :disabled="Boolean(start)" @click="chooseStart('NEW')">没有，我想新建一份<AppIcon name="chevron-right" :size="16" /></button>
        </section>

        <article v-if="start" class="user-answer"><p>{{ selectedStartText }}</p></article>

        <template v-if="start">
          <article class="flow-copy flow-copy--followup">
            <span>AI 简历助手</span>
            <p v-if="start === 'NEW'">很好。先确认你当前所处的求职阶段，我会据此安排后续提问顺序。</p>
            <p v-else>可以。先确认你的求职阶段，进入工作台后可粘贴现有简历文本，再逐项核对结构化结果。</p>
          </article>
          <section class="choice-card identity-card">
            <div><strong>选择你的身份</strong><span>身份只影响引导顺序，简历内容结构保持一致。</span></div>
            <button v-for="option in identityOptions" :key="option.id" type="button" :disabled="pending" @click="chooseIdentity(option.id)">
              <i><AppIcon :name="option.icon" :size="17" /></i><span><strong>{{ option.label }}</strong><small>{{ option.detail }}</small></span><AppIcon name="chevron-right" :size="16" />
            </button>
          </section>
        </template>

        <article v-if="identity" class="user-answer"><p>我目前是{{ identityLabel(identity) }}</p></article>
        <article v-if="pending" class="flow-copy flow-copy--loading"><span>AI 简历助手</span><p><i /><i /><i /></p></article>
        <p v-if="error" class="flow-error" role="alert">{{ error }}</p>
      </section>

      <aside class="onboarding-preview">
        <div><span><AppIcon name="file-text" :size="28" /></span><strong>简历预览将在这里生成</strong><p>确认身份后会创建统一结构化内容，并实时映射到所选模板。</p></div>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.onboarding-workbench { height: 100vh; display: grid; grid-template-rows: 56px 1fr; overflow: hidden; background: var(--surface-2); }
.onboarding-bar { display: grid; grid-template-columns: 40px 1fr 1fr; align-items: center; padding: 0 16px; background: var(--surface-1); border-bottom: 1px solid var(--border); }
.onboarding-bar > strong { text-align: center; font-size: 14px; }
.onboarding-bar > span { height: 100%; padding-left: 18px; display: flex; align-items: center; gap: 7px; color: var(--color-primary); border-left: 1px solid var(--border); font-size: 12px; font-weight: 600; }
.onboarding-grid { min-height: 0; display: grid; grid-template-columns: minmax(430px, 46%) minmax(0, 54%); }
.onboarding-flow { min-height: 0; padding: 28px max(28px, 7vw) 60px; display: flex; flex-direction: column; gap: 18px; overflow-y: auto; border-right: 1px solid var(--border); scroll-behavior: smooth; }
.flow-copy { max-width: 620px; display: grid; gap: 7px; color: var(--text-primary); }
.flow-copy > span { color: var(--text-tertiary); font-size: 12px; }
.flow-copy p { line-height: 1.7; }
.choice-card { width: min(620px, 100%); padding: 20px; display: grid; gap: 10px; background: var(--surface-1); border: 1px solid var(--border-subtle); border-radius: 8px; box-shadow: 0 8px 24px rgba(15, 23, 42, .05); }
.choice-card > div { margin-bottom: 4px; display: grid; gap: 5px; }
.choice-card > div strong { font-size: 15px; }
.choice-card > div span { color: var(--text-tertiary); font-size: 12px; }
.choice-card > button { width: fit-content; min-width: 190px; min-height: 44px; padding: 0 14px; display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--text); background: var(--surface-1); border: 1px solid var(--border-strong); border-radius: 7px; font-weight: 600; }
.choice-card > button:hover:not(:disabled) { color: var(--color-primary); border-color: var(--color-primary-border); background: var(--surface-2); }
.choice-card > button:disabled { opacity: .55; }
.user-answer { max-width: 74%; align-self: flex-end; animation: enter .18s ease-out; }
.user-answer p { padding: 12px 16px; color: var(--text-on-primary); background: var(--color-primary); border-radius: 8px; box-shadow: 0 7px 18px color-mix(in srgb, var(--color-primary) 16%, transparent); }
.flow-copy--followup { animation: enter .2s ease-out; }
.identity-card { animation: enter .22s ease-out; }
.identity-card > button { width: min(360px, 100%); display: grid; grid-template-columns: 32px 1fr 16px; text-align: left; }
.identity-card > button i { width: 30px; height: 30px; display: grid; place-items: center; color: var(--color-primary); background: var(--color-primary-soft); border-radius: 6px; }
.identity-card > button span { display: grid; }
.identity-card > button small { color: var(--text-tertiary); font-size: 11.5px; font-weight: 400; }
.flow-copy--loading p { width: 52px; height: 38px; display: flex; align-items: center; justify-content: center; gap: 5px; background: var(--surface-1); border-radius: 7px; }
.flow-copy--loading i { width: 6px; height: 6px; border-radius: 50%; background: var(--text-primary); animation: dot 1s ease-in-out infinite; }
.flow-copy--loading i:nth-child(2) { animation-delay: .14s; }.flow-copy--loading i:nth-child(3) { animation-delay: .28s; }
.flow-error { width: min(620px, 100%); padding: 10px 12px; color: var(--color-danger-text); background: var(--surface-2); border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--border-subtle)); border-radius: 6px; }
.onboarding-preview { min-width: 0; display: grid; place-items: center; padding: 24px; }
.onboarding-preview > div { max-width: 320px; display: grid; justify-items: center; gap: 9px; text-align: center; color: var(--text-secondary); }
.onboarding-preview span { width: 52px; height: 52px; display: grid; place-items: center; color: var(--color-primary); background: var(--surface-2); border-radius: 8px; }
.onboarding-preview p { color: var(--text-tertiary); font-size: 12px; line-height: 1.6; }
@keyframes enter { from { opacity: 0; transform: translateY(7px); } to { opacity: 1; transform: translateY(0); } }
@keyframes dot { 0%, 60%, 100% { opacity: .35; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-3px); } }
@media (max-width: 800px) { .onboarding-bar { grid-template-columns: 40px 1fr auto; }.onboarding-bar > span { border-left: 0; font-size: 0; }.onboarding-grid { grid-template-columns: 1fr; }.onboarding-flow { padding: 20px 16px 44px; border-right: 0; }.onboarding-preview { display: none; }.choice-card { padding: 16px; }.choice-card > button { width: 100%; }.user-answer { max-width: 88%; } }
@media (prefers-reduced-motion: reduce) { .identity-card, .flow-copy--followup, .user-answer, .flow-copy--loading i { animation: none; } }
</style>
