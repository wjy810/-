<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Archive, CircleX, Download, History, RefreshCw, ShieldCheck, Trash2 } from 'lucide-vue-next'
import AppIcon from '@/shared/ui/AppIcon.vue'
import ForbidState from '@/shared/ui/ForbidState.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import { downloadCareerHistory, fetchCareerHistory } from '@/features/career-library/services/careerLibraryApi'
import { useSessionStore } from '@/stores/session'
import AccountDeletionPanel from '../components/AccountDeletionPanel.vue'
import DeletionRequestList from '../components/DeletionRequestList.vue'
import ExportPanel from '../components/ExportPanel.vue'
import ObjectDeletionPanel from '../components/ObjectDeletionPanel.vue'
import { useDeletionRequests } from '../composables/useDeletionRequests'
import { useExportFlow } from '../composables/useExportFlow'
import { glossHistoryType } from '../labels'
import { fetchDeletionPreview } from '../services/dataRightsApi'
import '../data-rights.css'

const route = useRoute()
const session = useSessionStore()
const actionForbidden = ref<unknown>(null)
const onForbidden = (error: unknown): void => { actionForbidden.value = error }

const previewState = useLoadState(() => fetchDeletionPreview())
const historyState = useLoadState(() => fetchCareerHistory())
const exportFlow = useExportFlow(onForbidden)
const deletions = useDeletionRequests(onForbidden)
useToastFeedback(exportFlow.notice, 'success', 'data-rights-export-notice')

const careerHistory = computed(() => historyState.data.value)
const forbidden = computed(() => actionForbidden.value
  ?? [previewState.error.value, historyState.error.value].find(error => isForbidden(error)) ?? null)
const loading = computed(() => previewState.loading.value || historyState.loading.value)
const accountBusy = computed(() => (deletions.accountOpen.value ? '账号注销正在处理，暂不能再导出或提交新的删除申请。' : ''))
const historyPending = ref('')
const historyError = ref('')

const EXPORT_FACT: Record<string, string> = {
  none: '还没有导出过',
  running: '正在导出',
  ready: '可以下载',
  expired: '已过期',
  failed: '上次失败',
  cancelled: '上次已取消',
}
const facts = computed(() => [
  { key: 'export', icon: Download, label: '最近一次导出', value: exportFlow.loaded.value ? EXPORT_FACT[exportFlow.phase.value] : '…' },
  { key: 'deletion', icon: Trash2, label: '处理中的删除', value: deletions.loaded.value ? `${deletions.requests.value.filter(item => item.status === 'SUBMITTED' || item.status === 'PROCESSING').length} 项` : '…' },
  { key: 'history', icon: Archive, label: '历史求职记录', value: careerHistory.value ? `${careerHistory.value.total} 条` : '…' },
])
const LINKS = [
  { href: '#export', icon: Download, label: '导出数据' },
  { href: '#object', icon: Trash2, label: '删除单项' },
  { href: '#career-history', icon: History, label: '历史记录' },
  { href: '#deletion', icon: CircleX, label: '注销账号' },
]

function queryValue(key: string): string {
  const raw = route.query[key]
  const value = Array.isArray(raw) ? raw[0] : raw
  return typeof value === 'string' ? value.trim() : ''
}

const targetType = computed(() => queryValue('targetType'))
const targetId = computed(() => queryValue('targetId'))

function jumpTo(id: string): void {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function loadPage(): Promise<void> {
  actionForbidden.value = null
  historyError.value = ''
  await Promise.all([
    previewState.load(),
    historyState.load(),
    exportFlow.load(queryValue('exportId') || undefined),
    deletions.load(queryValue('deletionId') || undefined),
  ])
}

async function onDownloadCareerHistory(format: 'json' | 'md'): Promise<void> {
  historyPending.value = format
  historyError.value = ''
  try {
    await downloadCareerHistory(format)
  } catch (error) {
    historyError.value = errorMessage(error, '历史记录下载失败')
  } finally {
    historyPending.value = ''
  }
}

/** Links from notifications and the career library land on the panel they are about. */
async function focusFromQuery(): Promise<void> {
  await nextTick()
  if (targetType.value && targetId.value) jumpTo('object')
  else if (queryValue('deletionId')) jumpTo('deletion-progress')
  else if (queryValue('exportId')) jumpTo('export')
}

watch(() => [route.query.exportId, route.query.deletionId], () => {
  void loadPage().then(focusFromQuery)
})
watch(() => [route.query.targetType, route.query.targetId], () => void focusFromQuery())

onMounted(() => {
  void loadPage()
  void focusFromQuery()
})
</script>

<template>
  <ForbidState v-if="forbidden" :error="forbidden" />
  <div v-else class="settings-section data-rights">
    <header class="settings-section__head">
      <div class="settings-section__title">
        <span class="settings-section__icon"><AppIcon name="shield" :size="18" /></span>
        <div>
          <h2>授权与数据权利</h2>
          <p>导出个人数据、删除不需要的内容，或注销账号。</p>
        </div>
      </div>
      <div class="settings-section__actions">
        <UiButton variant="secondary" :icon="RefreshCw" :pending="loading" @click="loadPage">刷新</UiButton>
      </div>
    </header>

    <section class="dr-hero" aria-label="数据权利概览">
      <UiIllustration name="decor-scatter" :size="300" class="dr-hero__decor" />
      <div class="dr-hero__copy">
        <span class="dr-hero__eyebrow"><ShieldCheck :size="14" />你的数据，由你决定</span>
        <h3>导出、删除、注销，都在这里完成</h3>
        <p>资料只在你授权的范围内使用。每一次删除都会先列出影响范围，处理结果会通过通知中心告诉你。</p>
        <nav class="dr-hero__links" aria-label="跳转到">
          <a v-for="link in LINKS" :key="link.href" :href="link.href" @click.prevent="jumpTo(link.href.slice(1))">
            <component :is="link.icon" :size="14" />{{ link.label }}
          </a>
        </nav>
      </div>
      <UiIllustration name="privacy-shield" :size="168" class="dr-hero__art" eager float />
      <dl class="dr-hero__facts">
        <div v-for="fact in facts" :key="fact.key">
          <dt><component :is="fact.icon" :size="13" />{{ fact.label }}</dt>
          <dd>{{ fact.value }}</dd>
        </div>
      </dl>
    </section>

    <div class="dr-grid">
      <ExportPanel :flow="exportFlow" :blocked-reason="accountBusy" />

      <section id="career-history" class="dr-card" aria-labelledby="history-title">
        <header class="dr-card__head">
          <span class="dr-card__icon"><History :size="18" /></span>
          <div class="dr-card__heading">
            <h3 id="history-title">历史求职记录</h3>
            <p>已下线功能留下的投递、面试和复盘记录只读保留，可以下载备份。</p>
          </div>
          <UiBadge v-if="careerHistory" size="sm">{{ careerHistory.total }} 条</UiBadge>
        </header>
        <PageState :loading="historyState.loading.value" :error="historyState.error.value" :loaded="historyState.loaded.value" :empty="!careerHistory?.total" error-title="历史记录读取失败" compact @retry="historyState.load">
          <template #empty>
            <div class="history-empty">
              <UiIllustration name="empty-history" :size="88" />
              <p>你的账号没有需要保留的历史求职记录。</p>
            </div>
          </template>
          <div v-if="careerHistory" class="history-counts">
            <div v-for="(count, type) in careerHistory.counts" :key="type"><span>{{ glossHistoryType(String(type)) }}</span><strong>{{ count }}</strong></div>
          </div>
          <p v-if="careerHistory?.archivedAt" class="dr-fine history-archived">最近归档：{{ formatWhen(careerHistory.archivedAt) }}</p>
          <div class="history-actions">
            <UiButton variant="secondary" size="sm" :icon="Download" :pending="historyPending === 'json'" @click="onDownloadCareerHistory('json')">下载 JSON</UiButton>
            <UiButton variant="secondary" size="sm" :icon="Download" :pending="historyPending === 'md'" @click="onDownloadCareerHistory('md')">下载 Markdown</UiButton>
          </div>
          <p v-if="historyError" class="dr-note dr-note--bad" role="alert">{{ historyError }}</p>
        </PageState>
      </section>
    </div>

    <ObjectDeletionPanel
      :requests="deletions"
      :account-id="session.account?.id ?? ''"
      :busy-reason="accountBusy"
      :initial-type="targetType"
      :initial-id="targetId"
      @forbidden="onForbidden"
    />

    <Transition name="rise">
      <DeletionRequestList v-if="deletions.requests.value.length" :requests="deletions" :focus-id="queryValue('deletionId') || deletions.open.value?.id" />
    </Transition>

    <AccountDeletionPanel :preview-state="previewState" :requests="deletions" :busy-reason="accountBusy" @forbidden="onForbidden" @export="jumpTo('export')" />
  </div>
</template>

<style scoped>
.data-rights {
  display: grid;
  gap: var(--space-5);
}

.data-rights > .settings-section__head {
  margin-bottom: 0;
}

.dr-hero {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas: 'copy art' 'facts art';
  gap: var(--space-4) var(--space-6);
  padding: var(--space-6);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--color-primary) 14%, transparent), transparent 60%),
    radial-gradient(80% 120% at 0% 100%, color-mix(in srgb, var(--color-accent) 10%, transparent), transparent 55%),
    var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.dr-hero__decor {
  position: absolute;
  z-index: -1;
  right: 200px;
  top: -86px;
  width: 280px;
  height: auto;
  opacity: 0.22;
  transform: rotate(-8deg);
}

.dr-hero__copy {
  grid-area: copy;
  display: grid;
  gap: var(--space-2);
  max-width: 560px;
}

.dr-hero__eyebrow {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
  font-size: var(--fs-xs);
  font-weight: 600;
}

.dr-hero__copy h3 {
  font-size: var(--fs-h2);
  line-height: var(--lh-h2);
  font-weight: 700;
  letter-spacing: -0.01em;
}

.dr-hero__copy p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.dr-hero__links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.dr-hero__links a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  background: var(--surface-glass);
  color: var(--text-primary);
  font-size: var(--fs-xs);
  font-weight: 600;
  backdrop-filter: blur(6px);
  transition: border-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.dr-hero__links a:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-text);
  transform: translateY(-1px);
}

.dr-hero__art {
  grid-area: art;
  align-self: center;
  width: 168px;
  height: auto;
  filter: drop-shadow(0 18px 24px color-mix(in srgb, var(--color-primary) 22%, transparent));
}

.dr-hero__facts {
  grid-area: facts;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.dr-hero__facts > div {
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-glass);
  backdrop-filter: blur(6px);
}

.dr-hero__facts dt {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.dr-hero__facts dd {
  margin-top: 2px;
  font-size: var(--fs-body);
  font-weight: 650;
}

.dr-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.history-counts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--space-2);
}

.history-counts > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

.history-counts span {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.history-counts strong {
  font-size: var(--fs-body);
  font-variant-numeric: tabular-nums;
}

.history-archived {
  margin-top: var(--space-2);
}

.history-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.history-empty {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding: var(--space-4) 0;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  text-align: center;
}

@media (max-width: 1180px) {
  .dr-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .dr-hero__decor {
    display: none;
  }

  .dr-hero {
    grid-template-columns: 1fr;
    grid-template-areas: 'copy' 'facts';
    padding: var(--space-5);
  }

  .dr-hero__art {
    position: absolute;
    right: -12px;
    top: -8px;
    width: 104px;
    opacity: 0.9;
  }

  .dr-hero__copy {
    padding-right: 72px;
  }

  .dr-hero__copy h3 {
    font-size: var(--fs-h3);
    line-height: var(--lh-h3);
  }

  .dr-hero__facts {
    grid-template-columns: 1fr;
    gap: var(--space-2);
  }

  .dr-hero__facts > div {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .dr-hero__facts dd {
    margin-top: 0;
  }
}
</style>
