<script setup lang="ts">
/** Resumes as cards with their real first page; frozen versions fold into a chip on their resume. */
import { Layers, Lock, MoreHorizontal } from 'lucide-vue-next'
import AppTag from '@/shared/ui/AppTag.vue'
import UiDropdownMenu, { type MenuItem } from '@/shared/ui/UiDropdownMenu.vue'
import { formatRelative, formatWhen } from '@/shared/lib/datetime'
import ResumeThumbnail from './ResumeThumbnail.vue'
import { rowAction, rowLink, type ResumeRow } from '../utils/resumeRows'

defineProps<{
  rows: ResumeRow[]
  menu: (row: ResumeRow) => MenuItem[]
  /** Frozen-version counts per resume, shown as a chip when versions are folded away. */
  snapshotCounts?: Map<string, number>
  busy?: boolean
}>()
const emit = defineEmits<{ snapshots: [row: ResumeRow] }>()
</script>

<template>
  <ul class="rgrid">
    <li
      v-for="(row, index) in rows"
      :key="row.key"
      class="rcard"
      :class="[`rcard--${row.rowTone}`, { 'is-archived': row.archived }]"
      :style="{ '--i': index }"
      data-testid="resume-row"
    >
      <RouterLink class="rcard__paper" :to="rowLink(row)" :aria-label="`${rowAction(row)}：${row.title}`">
        <ResumeThumbnail v-if="row.kind === 'MASTER'" :resume-id="row.masterId" :updated-at="row.updatedAt" :title="row.title">
          <span class="rcard__sketch" aria-hidden="true"><i /><i /><i /><i /><i /></span>
        </ResumeThumbnail>
        <span v-else class="rcard__sketch" aria-hidden="true"><i /><i /><i /><i /><i /></span>
        <span v-if="row.lockNote" class="rcard__lock"><Lock :size="12" />内容不可修改</span>
        <span class="rcard__open">{{ rowAction(row) }}</span>
      </RouterLink>
      <div class="rcard__body">
        <div class="rcard__title">
          <strong :title="row.title">{{ row.title }}</strong>
          <UiDropdownMenu v-if="menu(row).length" :items="menu(row)">
            <button class="rcard__more" type="button" :disabled="busy" :aria-label="`${row.title} 的更多操作`">
              <MoreHorizontal :size="17" />
            </button>
          </UiDropdownMenu>
        </div>
        <div class="rcard__meta">
          <AppTag :tone="row.rowTone">{{ row.rowType }}<template v-if="row.ordinal"> · 第 {{ row.ordinal }} 版</template></AppTag>
          <span class="rcard__status"><span class="dot" :class="`dot--${row.statusTone}`" />{{ row.statusLabel }}</span>
        </div>
        <div class="rcard__foot">
          <span :title="formatWhen(row.updatedAt)">{{ formatRelative(row.updatedAt) }}更新</span>
          <button
            v-if="row.kind === 'MASTER' && snapshotCounts?.get(row.masterId)"
            class="rcard__versions"
            type="button"
            @click="emit('snapshots', row)"
          >
            <Layers :size="13" />{{ snapshotCounts.get(row.masterId) }} 个冻结版本
          </button>
        </div>
      </div>
    </li>
  </ul>
</template>

<style scoped>
.rgrid {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 18px;
  padding: 18px;
}

.rcard {
  display: grid;
  grid-template-rows: auto 1fr;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  box-shadow: var(--shadow-xs);
  animation: jp-rise-in var(--dur-slow) var(--ease-out) both;
  animation-delay: calc(min(var(--i, 0), 8) * 35ms);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out), border-color var(--dur-base) var(--ease-out);
}

.rcard:hover,
.rcard:focus-within {
  transform: translateY(-3px);
  border-color: var(--border-default);
  box-shadow: var(--shadow-md);
}

.rcard.is-archived .rcard__paper {
  filter: grayscale(0.85);
  opacity: 0.7;
}

.rcard__paper {
  position: relative;
  display: block;
  aspect-ratio: 210 / 168;
  overflow: hidden;
  margin: 10px 10px 0;
  border-radius: var(--radius-sm);
  background: var(--sheet-bg);
  box-shadow: inset 0 0 0 1px var(--border-subtle);
}

.rcard__paper::after {
  content: '';
  position: absolute;
  inset: auto 0 0;
  height: 30%;
  background: linear-gradient(to bottom, transparent, color-mix(in srgb, var(--sheet-bg) 92%, transparent));
  pointer-events: none;
}

.rcard__sketch {
  position: absolute;
  inset: 0;
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 18px 20px;
}

.rcard__sketch i {
  display: block;
  height: 6px;
  border-radius: 3px;
  background: var(--surface-3);
}

.rcard__sketch i:first-child {
  width: 46%;
  height: 10px;
  margin-bottom: 6px;
  background: color-mix(in srgb, var(--color-primary) 40%, var(--surface-3));
}

.rcard--orange .rcard__sketch i:first-child {
  background: color-mix(in srgb, var(--color-warning) 45%, var(--surface-3));
}

.rcard__sketch i:nth-child(2) {
  width: 86%;
}

.rcard__sketch i:nth-child(3) {
  width: 72%;
}

.rcard__sketch i:nth-child(4) {
  width: 80%;
}

.rcard__sketch i:nth-child(5) {
  width: 54%;
}

.rcard__lock {
  position: absolute;
  z-index: 1;
  left: 8px;
  bottom: 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  background: var(--surface-glass);
  color: var(--color-warning-text);
  font-size: 11.5px;
  font-weight: 600;
  backdrop-filter: blur(6px);
}

.rcard__open {
  position: absolute;
  z-index: 1;
  right: 8px;
  bottom: 8px;
  padding: 5px 12px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  color: var(--text-on-primary);
  font-size: 12px;
  font-weight: 600;
  box-shadow: var(--shadow-primary);
  opacity: 0;
  transform: translateY(4px);
  transition: opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out);
}

.rcard:hover .rcard__open,
.rcard__paper:focus-visible .rcard__open {
  opacity: 1;
  transform: none;
}

.rcard__paper:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.rcard__body {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 12px 14px 14px;
  min-width: 0;
}

.rcard__title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.rcard__title strong {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rcard__more {
  width: 28px;
  height: 28px;
  flex: none;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.rcard__more:hover,
.rcard__more[data-state='open'] {
  background: var(--surface-3);
  color: var(--text-primary);
}

.rcard__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.rcard__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12.5px;
}

.rcard__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.rcard__versions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
  background: var(--surface-2);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.rcard__versions:hover {
  border-color: var(--color-warning);
  color: var(--color-warning-text);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot--green {
  background: var(--color-success);
}

.dot--orange {
  background: var(--color-warning);
}

.dot--red {
  background: var(--color-danger);
}

.dot--gray {
  background: var(--text-tertiary);
}

.dot--blue {
  background: var(--color-primary);
}

@media (max-width: 560px) {
  .rgrid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    padding: 10px;
  }

  .rcard__body {
    padding: 10px;
  }

  .rcard__open {
    display: none;
  }

}
</style>
