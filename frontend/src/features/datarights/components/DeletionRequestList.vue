<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown, ListChecks, RefreshCw } from 'lucide-vue-next'
import UiBadge, { type BadgeTone } from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import { formatRelative, formatWhen } from '@/shared/lib/datetime'
import {
  deletionProgressNote,
  glossDeletionStatus,
  glossReceiptModule,
  glossReceiptStatus,
  glossTargetType,
  isOpenDeletion,
} from '../labels'
import type { DeletionRequests } from '../composables/useDeletionRequests'
import type { DeletionView } from '../types'

const props = defineProps<{ requests: DeletionRequests; focusId?: string }>()

const expanded = ref<string | null>(props.focusId || null)
const refreshing = ref('')

function tone(status?: string | null): BadgeTone {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PARTIALLY_RESTRICTED') return 'warning'
  return 'primary'
}

function receiptTone(status?: string | null): string {
  if (status === 'SUCCEEDED') return 'is-wipe'
  if (status === 'RESTRICTED') return 'is-keep'
  if (status === 'FAILED') return 'is-block'
  return ''
}

function scopeLabel(item: DeletionView): string {
  return item.scope === 'OBJECT' ? glossTargetType(item.targetType) : '整个账号'
}

function toggle(id: string): void {
  expanded.value = expanded.value === id ? null : id
}

async function refresh(id: string): Promise<void> {
  refreshing.value = id
  try {
    await props.requests.refresh(id)
  } finally {
    refreshing.value = ''
  }
}
</script>

<template>
  <section id="deletion-progress" class="dr-card" aria-labelledby="deletion-progress-title">
    <header class="dr-card__head">
      <span class="dr-card__icon"><ListChecks :size="18" /></span>
      <div class="dr-card__heading">
        <h3 id="deletion-progress-title">删除申请</h3>
        <p>每项申请的处理进度和各模块的处理结果；结果也会通过通知中心告诉你。</p>
      </div>
    </header>
    <p v-if="requests.error.value" class="dr-note dr-note--bad" role="alert">{{ requests.error.value }}</p>
    <ul class="request-list">
      <li v-for="item in requests.requests.value" :key="item.id" class="request" :class="{ 'is-open': expanded === item.id }">
        <button type="button" class="request__row" :aria-expanded="expanded === item.id" @click="toggle(item.id)">
          <span class="request__scope">{{ scopeLabel(item) }}</span>
          <span class="request__when">{{ formatRelative(item.createdAt) }}</span>
          <UiBadge :tone="tone(item.status)" size="sm" :dot="isOpenDeletion(item.status)">{{ glossDeletionStatus(item.status) }}</UiBadge>
          <ChevronDown class="request__chevron" :size="16" />
        </button>
        <div v-if="expanded === item.id" class="request__detail">
          <p class="request__note">{{ deletionProgressNote(item) }}</p>
          <ul v-if="item.receipts?.length" class="dr-ledger">
            <li v-for="(row, index) in item.receipts" :key="`${row.moduleCode}-${index}`" :class="receiptTone(row.status)">
              <span>{{ glossReceiptModule(row.moduleCode) }}<small v-if="row.message">{{ row.message }}</small></span>
              <em>{{ glossReceiptStatus(row.status) }}</em>
            </li>
          </ul>
          <p v-else class="dr-fine">各项数据还在排队处理，结果出来后会显示在这里。</p>
          <div class="request__meta">
            <span>更新于 {{ formatWhen(item.updatedAt) }}</span>
            <span>申请编号 <code>{{ item.id }}</code></span>
            <UiButton v-if="isOpenDeletion(item.status)" variant="ghost" size="sm" :icon="RefreshCw" :pending="refreshing === item.id" @click="refresh(item.id)">刷新</UiButton>
          </div>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.request-list {
  list-style: none;
  display: grid;
  gap: var(--space-2);
}

.request {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  transition: border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

.request.is-open {
  border-color: var(--border-default);
  box-shadow: var(--shadow-xs);
}

.request__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: 10px 12px;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.request__row:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
  border-radius: var(--radius-md);
}

.request__scope {
  font-size: var(--fs-sm);
  font-weight: 600;
}

.request__when {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.request__chevron {
  color: var(--text-tertiary);
  transition: transform var(--dur-base) var(--ease-out);
}

.is-open .request__chevron {
  transform: rotate(180deg);
}

.request__detail {
  display: grid;
  gap: var(--space-3);
  padding: 0 12px 12px;
  animation: jp-rise-in var(--dur-base) var(--ease-out) both;
}

.request__note {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.request__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.request__meta code {
  font-size: var(--fs-xs);
  user-select: all;
}
</style>
