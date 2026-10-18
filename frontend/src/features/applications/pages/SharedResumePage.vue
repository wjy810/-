<script setup lang="ts">
/**
 * /r/:token — the resume behind a link, for whoever holds it (docs/phase2/01). No sign-in. The page
 * tells the reader plainly what is recorded: when it was opened, the kind of device and how long the
 * page was visible; never the reader's address.
 */
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import { Info, Printer, ShieldCheck } from 'lucide-vue-next'
import BrandMark from '@/shared/ui/BrandMark.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { isApiClientError } from '@/shared/api/types'
import ResumeDocument from '@/resume-render/components/ResumeDocument.vue'
import { loadTemplate } from '@/resume-render/templates/registry'
import type { TemplateModule } from '@/resume-render/templates/manifest'
import { fetchSharedResume, sendReadTime, startResumeView } from '../services/applicationsApi'
import type { SharedResume } from '../types'

const HEARTBEAT_SECONDS = 15

const route = useRoute()
const token = computed(() => String(route.params.token ?? ''))
const resume = shallowRef<SharedResume | null>(null)
const template = shallowRef<TemplateModule | null>(null)
const failure = ref<{ title: string; description: string } | null>(null)
const printing = ref(false)

let viewId: string | null = null
let viewKey: string | null = null
let baseSeconds = 0
let visibleSeconds = 0
let sentSeconds = -1
let ticker: number | undefined

const ownerName = computed(() => {
  const basics = resume.value?.content?.basics
  const name = basics && typeof basics === 'object' ? String((basics as Record<string, unknown>).name ?? '').trim() : ''
  return name
})

function describe(reason: unknown): { title: string; description: string } {
  const code = isApiClientError(reason) ? reason.reason : ''
  if (code === 'RESUME_LINK_REVOKED') return { title: '求职者已撤回这个链接', description: '如果还需要这份简历，请直接联系对方获取新的链接。' }
  if (code === 'RESUME_LINK_EXPIRED') return { title: '这个简历链接已过期', description: '链接设置了有效期。请联系求职者获取新的链接。' }
  if (code === 'RESUME_LINK_CONTENT_GONE') return { title: '分享的简历版本已不可用', description: '求职者可能已删除或归档了这个版本。' }
  if (code === 'RESUME_LINK_NOT_FOUND') return { title: '找不到这个简历链接', description: '请检查链接是否完整复制，或联系求职者重新发送。' }
  return { title: '简历暂时打不开', description: '网络或服务暂时不可用，请稍后刷新重试。' }
}

function markUnlisted(): void {
  const meta = document.createElement('meta')
  meta.name = 'robots'
  meta.content = 'noindex, nofollow'
  meta.dataset.sharedResume = 'true'
  document.head.append(meta)
}

async function load(): Promise<void> {
  try {
    const shared = await fetchSharedResume(token.value)
    template.value = await loadTemplate(shared.templateId)
    resume.value = shared
    document.title = ownerName.value ? `${ownerName.value}的简历` : '简历'
    if (!shared.ownerPreview) void startReceipt()
  } catch (reason) {
    failure.value = describe(reason)
  }
}

/** The receipt: one visit, then the seconds the page stays visible (capped server-side). */
async function startReceipt(): Promise<void> {
  try {
    const started = await startResumeView(token.value, document.referrer)
    if (!started.recorded || !started.viewId || !started.viewKey) return
    viewId = started.viewId
    viewKey = started.viewKey
    baseSeconds = started.readSeconds
    ticker = window.setInterval(tick, 1000)
  } catch { /* a missing receipt never blocks reading */ }
}

function tick(): void {
  if (document.visibilityState !== 'visible') return
  visibleSeconds += 1
  if (visibleSeconds % HEARTBEAT_SECONDS === 0) flush()
}

function flush(): void {
  if (!viewId || !viewKey) return
  const total = baseSeconds + visibleSeconds
  if (total === sentSeconds) return
  sentSeconds = total
  sendReadTime(token.value, viewId, viewKey, total)
}

function onVisibility(): void {
  if (document.visibilityState === 'hidden') flush()
}

async function print(): Promise<void> {
  printing.value = true
  await nextTick()
  await new Promise(resolve => window.setTimeout(resolve, 400))
  window.print()
}

function beforePrint(): void { printing.value = true }
function afterPrint(): void { printing.value = false }

onMounted(() => {
  markUnlisted()
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('pagehide', flush)
  window.addEventListener('beforeprint', beforePrint)
  window.addEventListener('afterprint', afterPrint)
  void load()
})

onUnmounted(() => {
  flush()
  window.clearInterval(ticker)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('pagehide', flush)
  window.removeEventListener('beforeprint', beforePrint)
  window.removeEventListener('afterprint', afterPrint)
  document.head.querySelector('meta[data-shared-resume]')?.remove()
})
</script>

<template>
  <div class="shared" :class="{ 'is-printing': printing }">
    <header class="shared__bar">
      <a class="shared__brand" href="/" target="_blank" rel="noopener" aria-label="JobProof">
        <BrandMark />
      </a>
      <span class="shared__badge"><ShieldCheck :size="14" />求职者通过 JobProof 分享</span>
      <UiButton v-if="resume" size="sm" variant="secondary" :icon="Printer" class="shared__print" @click="print">打印 / 存为 PDF</UiButton>
    </header>

    <div v-if="resume?.ownerPreview" class="shared__owner" role="status">
      <Info :size="16" />这是你分享出去的页面。你自己打开不会计入回执。
    </div>

    <main class="shared__main">
      <div v-if="!resume && !failure" class="shared__paper"><UiSkeleton height="1100px" radius="var(--radius-sm)" /></div>
      <UiEmptyState v-else-if="failure" size="lg" illustration="empty-search" :title="failure.title" :description="failure.description" bordered class="shared__failure">
        <UiButton variant="secondary" href="/" target="_blank">了解 JobProof</UiButton>
      </UiEmptyState>
      <div v-else-if="resume && template" class="shared__paper">
        <ResumeDocument
          :template="template"
          :content="resume.content"
          :design="resume.design"
          :photo="resume.photo ?? null"
          :mode="printing ? 'print' : 'preview'"
          :fit="!printing"
        />
      </div>
    </main>

    <footer v-if="resume" class="shared__foot">
      <p>
        <ShieldCheck :size="14" />
        为了让求职者知道简历已经送达，这个页面会记录打开时间、设备类型和停留时长；不记录你的 IP 地址，也不需要登录。
      </p>
      <p class="shared__growth">也在找工作？<a href="/" target="_blank" rel="noopener">用 JobProof 做一份有证据支撑的简历 →</a></p>
    </footer>
  </div>
</template>

<style scoped>
.shared {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(60% 40% at 15% 0%, var(--color-primary-soft), transparent 70%),
    radial-gradient(50% 40% at 100% 10%, color-mix(in srgb, var(--color-success-soft) 70%, transparent), transparent 70%),
    var(--surface-2);
  color: var(--text-primary);
}

.shared__bar {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px max(var(--space-4), calc((100% - 900px) / 2));
  border-bottom: 1px solid var(--border-subtle);
  background: color-mix(in srgb, var(--surface-1) 86%, transparent);
  backdrop-filter: blur(12px);
}

.shared__brand {
  display: inline-flex;
  align-items: center;
  color: inherit;
  text-decoration: none;
}

.shared__badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  color: var(--color-success-text);
  background: var(--color-success-soft);
  font-size: var(--fs-xs);
}

.shared__print {
  margin-left: auto;
}

.shared__owner {
  display: flex;
  align-items: center;
  gap: 8px;
  width: min(900px, calc(100% - 32px));
  margin: var(--space-4) auto 0;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
  font-size: var(--fs-sm);
}

.shared__main {
  flex: 1;
  width: min(900px, calc(100% - 32px));
  margin: var(--space-5) auto;
}

.shared__paper {
  border-radius: var(--radius-md);
  filter: drop-shadow(0 18px 40px rgb(15 23 42 / 0.12));
}

.shared__failure {
  margin-top: var(--space-10);
  background: var(--surface-1);
}

.shared__foot {
  display: grid;
  gap: 6px;
  width: min(900px, calc(100% - 32px));
  margin: 0 auto var(--space-8);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.7;
  text-align: center;
}

.shared__foot p:first-child {
  display: inline-flex;
  justify-content: center;
  gap: 6px;
}

.shared__foot svg {
  flex: none;
  margin-top: 3px;
}

.shared__growth a {
  color: var(--color-primary-text);
  font-weight: 600;
}

@media (max-width: 640px) {
  .shared__badge { display: none; }
  .shared__main { width: calc(100% - 16px); margin-top: var(--space-3); }
}

@media print {
  .shared { background: none; }
  .shared__bar,
  .shared__owner,
  .shared__foot { display: none; }
  .shared__main { width: auto; margin: 0; }
  .shared__paper { filter: none; }
}
</style>

<style>
@media print {
  @page { size: A4; margin: 0; }
}
</style>
