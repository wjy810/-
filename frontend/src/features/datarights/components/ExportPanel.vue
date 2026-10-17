<script setup lang="ts">
import { computed, ref } from 'vue'
import { CircleAlert, CircleCheck, Clock3, Download, FileJson, RefreshCw, RotateCcw, X } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge, { type BadgeTone } from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import { formatRelative, formatWhen } from '@/shared/lib/datetime'
import { exportPhase, glossTaskStatus, type ExportPhase } from '../labels'
import type { ExportFlow } from '../composables/useExportFlow'

const props = defineProps<{ flow: ExportFlow; blockedReason?: string }>()

const ack = ref(false)
const phase = computed(() => props.flow.phase.value)
const latest = computed(() => props.flow.latest.value)

const PHASE_BADGE: Record<ExportPhase, { tone: BadgeTone; text: string } | null> = {
  none: null,
  running: { tone: 'primary', text: '正在导出' },
  ready: { tone: 'success', text: '可以下载' },
  expired: { tone: 'neutral', text: '已过期' },
  failed: { tone: 'danger', text: '导出失败' },
  cancelled: { tone: 'neutral', text: '已取消' },
}
const badge = computed(() => PHASE_BADGE[phase.value])
const INCLUDED = ['职业主档与经历成果', '资料文件清单', '简历与全部版本', '历史求职记录']

function historyTone(status?: string | null): BadgeTone {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING' || status === 'RUNNING') return 'primary'
  return 'neutral'
}

function historyNote(item: NonNullable<typeof latest.value>): string {
  if (item.taskStatus !== 'SUCCEEDED') return glossTaskStatus(item.taskStatus)
  return exportPhase(item) === 'ready' ? `可下载至 ${formatWhen(item.downloadExpiresAt)}` : '下载已过期'
}

async function startNew(): Promise<void> {
  await (phase.value === 'none' ? props.flow.start() : props.flow.again())
  ack.value = false
}
</script>

<template>
  <section id="export" class="dr-card export-panel" aria-labelledby="export-title">
    <header class="dr-card__head">
      <span class="dr-card__icon"><Download :size="18" /></span>
      <div class="dr-card__heading">
        <h3 id="export-title">导出我的数据</h3>
        <p>生成一份完整的数据副本（JSON），完成后在有效期内下载。</p>
      </div>
      <UiBadge v-if="badge" :tone="badge.tone" dot>{{ badge.text }}</UiBadge>
    </header>

    <PageState :loading="!flow.loaded.value && !flow.loadError.value" :error="flow.loadError.value" :loaded="flow.loaded.value" error-title="导出记录读取失败" compact @retry="flow.load()">
      <Transition name="rise" mode="out-in">
        <div v-if="phase === 'running'" key="running" class="export-stage export-stage--running" aria-live="polite">
          <div class="export-progress">
            <div class="export-progress__top">
              <strong>正在打包你的数据…</strong>
              <span>{{ flow.progress.value }}%</span>
            </div>
            <div class="export-progress__track" role="progressbar" :aria-valuenow="flow.progress.value" aria-valuemin="0" aria-valuemax="100" aria-label="导出进度">
              <span :style="{ width: `${flow.progress.value}%` }" />
            </div>
            <p>通常只需几十秒。你可以离开这个页面，完成后会在通知中心提醒你。</p>
          </div>
          <div class="export-actions">
            <UiButton variant="secondary" :icon="X" :pending="flow.pending.value === 'cancel'" @click="flow.cancel()">取消导出</UiButton>
          </div>
        </div>

        <div v-else-if="phase === 'ready'" key="ready" class="export-stage export-stage--ready">
          <div class="export-ready">
            <span class="export-ready__icon"><FileJson :size="22" /></span>
            <div>
              <strong>导出文件已就绪</strong>
              <p>
                <Clock3 :size="13" />
                可下载至 {{ formatWhen(latest?.downloadExpiresAt) }}
                <em v-if="flow.remaining.value">（{{ flow.remaining.value }}）</em>
              </p>
            </div>
          </div>
          <div class="export-actions">
            <UiButton :icon="Download" :pending="flow.pending.value === 'download'" @click="flow.download()">下载导出文件</UiButton>
            <UiButton variant="ghost" :icon="RotateCcw" :disabled="Boolean(blockedReason)" :pending="flow.pending.value === 'retry'" @click="flow.again()">重新导出</UiButton>
          </div>
        </div>

        <div v-else-if="phase === 'failed' || phase === 'cancelled'" key="failed" class="export-stage">
          <div class="export-notice" :class="{ 'is-bad': phase === 'failed' }">
            <CircleAlert :size="18" />
            <div>
              <strong>{{ phase === 'failed' ? '这次导出没有完成' : '这次导出已取消' }}</strong>
              <p>{{ phase === 'failed' ? (flow.failureReason.value || '处理时出现问题，你的数据没有受到影响。') : '没有生成导出文件。需要时可以重新发起。' }}</p>
            </div>
          </div>
          <div class="export-actions">
            <UiButton :icon="RotateCcw" :disabled="Boolean(blockedReason)" :pending="flow.pending.value === 'retry'" @click="flow.again()">重新导出</UiButton>
          </div>
        </div>

        <div v-else key="start" class="export-stage export-stage--start">
          <UiIllustration name="data-export" :size="104" class="export-art" />
          <div class="export-start">
            <p v-if="phase === 'expired'" class="export-expired">
              <Clock3 :size="14" />{{ latest?.downloadExpired ? '上一次导出的下载期限已过，' : '上一次导出的文件已不可下载，' }}请重新导出。
            </p>
            <ul class="export-included" aria-label="导出包含的内容">
              <li v-for="item in INCLUDED" :key="item"><CircleCheck :size="14" />{{ item }}</li>
            </ul>
            <UiCheckbox v-model="ack" :disabled="flow.pending.value !== ''" label="我已知晓：导出完成后需在有效期内下载，过期后需要重新导出。" />
            <div class="export-actions">
              <UiButton :icon="Download" :disabled="!ack || Boolean(blockedReason)" :pending="flow.pending.value === 'start' || flow.pending.value === 'retry'" @click="startNew">
                {{ phase === 'expired' ? '重新导出' : '发起导出' }}
              </UiButton>
            </div>
          </div>
        </div>
      </Transition>

      <p v-if="blockedReason" class="dr-note dr-note--warn">{{ blockedReason }}</p>
      <p v-if="flow.error.value" class="dr-note dr-note--bad" role="alert">{{ flow.error.value }}</p>

      <div v-if="flow.earlier.value.length" class="export-history">
        <div class="export-history__head">
          <h4>导出记录</h4>
          <UiButton v-if="latest" variant="ghost" size="sm" :icon="RefreshCw" :pending="flow.pending.value === 'refresh'" @click="flow.refresh()">刷新</UiButton>
        </div>
        <ul>
          <li v-for="item in flow.earlier.value.slice(0, 4)" :key="item.id">
            <span>{{ formatRelative(item.createdAt) }}</span>
            <small>{{ historyNote(item) }}</small>
            <UiBadge :tone="historyTone(item.taskStatus)" size="sm">{{ glossTaskStatus(item.taskStatus) }}</UiBadge>
          </li>
        </ul>
      </div>
    </PageState>
  </section>
</template>

<style scoped>
.export-stage {
  display: grid;
  gap: var(--space-4);
}

.export-stage--start {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-5);
}

.export-art {
  filter: drop-shadow(0 10px 16px color-mix(in srgb, var(--color-primary) 16%, transparent));
}

.export-start {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.export-included {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px var(--space-4);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.export-included li {
  display: flex;
  align-items: center;
  gap: 6px;
}

.export-included svg {
  flex: none;
  color: var(--color-success);
}

.export-expired {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-warning-text);
  font-size: var(--fs-sm);
}

.export-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.export-progress {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
}

.export-progress__top {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: var(--fs-sm);
}

.export-progress__top span {
  font-variant-numeric: tabular-nums;
  color: var(--color-primary-text);
  font-weight: 600;
}

.export-progress__track {
  position: relative;
  height: 8px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
}

.export-progress__track span {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-primary), color-mix(in srgb, var(--color-primary) 70%, var(--color-accent)));
  transition: width var(--dur-slow) var(--ease-out);
}

.export-progress__track span::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.45), transparent);
  animation: jp-shimmer 1.4s linear infinite;
}

.export-progress p {
  color: var(--text-secondary);
  font-size: var(--fs-xs);
}

.export-ready {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid color-mix(in srgb, var(--color-success) 28%, transparent);
  border-radius: var(--radius-md);
  background: var(--color-success-soft);
}

.export-ready__icon {
  width: 44px;
  height: 44px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-success);
  background: var(--surface-1);
  box-shadow: var(--shadow-xs);
}

.export-ready strong {
  display: block;
  font-size: var(--fs-body);
}

.export-ready p {
  margin-top: 2px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.export-ready em {
  font-style: normal;
  color: var(--color-success-text);
  font-weight: 600;
}

.export-notice {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface-2);
  color: var(--text-secondary);
}

.export-notice.is-bad {
  background: var(--color-danger-soft);
}

.export-notice.is-bad > svg {
  color: var(--color-danger);
}

.export-notice > svg {
  flex: none;
  margin-top: 2px;
}

.export-notice strong {
  display: block;
  color: var(--text-primary);
}

.export-notice p {
  margin-top: 2px;
  font-size: var(--fs-sm);
}

.export-history {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.export-history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}

.export-history h4 {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  font-weight: 600;
}

.export-history ul {
  list-style: none;
  display: grid;
  gap: 2px;
}

.export-history li {
  display: grid;
  grid-template-columns: minmax(84px, auto) minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.export-history li:hover {
  background: var(--surface-2);
}

.export-history small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .export-stage--start {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .export-art {
    width: 84px;
    height: 84px;
  }

  .export-included {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .export-progress__track span::after {
    animation: none;
  }
}
</style>
