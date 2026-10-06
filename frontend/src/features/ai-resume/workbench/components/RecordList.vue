<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ChevronDown, ChevronUp, GripVertical, Plus, Trash2 } from 'lucide-vue-next'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import { confirm } from '@/shared/ui/confirm'
import { useSortableList } from '@/shared/composables/useSortableList'
import AiCertificateSuggestionPanel from '../../components/AiCertificateSuggestionPanel.vue'
import AiSkillSuggestionPanel from '../../components/AiSkillSuggestionPanel.vue'
import type { AiResumeCard } from '../../types'
import { CREDENTIAL_CARD_TYPES } from '../cardConfig'
import { cleanItems, recordHeadline, structuredTitle } from '../structured'
import { useWorkbench } from '../useWorkbench'
import RecordFields from './RecordFields.vue'

const props = defineProps<{ card: AiResumeCard }>()

const wb = useWorkbench()
const conversation = wb.session.conversation
const list = ref<HTMLElement | null>(null)
const collapsed = reactive<Record<number, boolean>>({})

const items = computed(() => wb.drafts.records(props.card))
const title = computed(() => structuredTitle(props.card.cardType))
const sortable = computed(() => items.value.length > 1)

useSortableList(list, {
  handle: '.record__grip',
  onMove: (from, to) => {
    wb.drafts.moveRecord(props.card, from, to)
    clearCollapsed()
  },
})

function clearCollapsed(): void {
  for (const key of Object.keys(collapsed)) delete collapsed[Number(key)]
}

function move(index: number, direction: -1 | 1): void {
  wb.drafts.moveRecord(props.card, index, index + direction)
  clearCollapsed()
}

async function remove(index: number): Promise<void> {
  const item = items.value[index]
  if (item && cleanItems([item]).length > 0) {
    const ok = await confirm({
      title: `删除${title.value} ${index + 1}？`,
      message: `“${recordHeadline(props.card.cardType, item) || '这条记录'}”会从草稿中移除；确认此模块前，正式简历不会变化。`,
      confirmText: '删除',
      tone: 'danger',
    })
    if (!ok) return
  }
  wb.drafts.removeRecord(props.card, index)
  clearCollapsed()
}
</script>

<template>
  <div class="record-list" :class="{ 'is-single': !sortable }">
    <AiSkillSuggestionPanel
      v-if="card.cardType === 'SKILLS' && conversation"
      :conversation-id="conversation.id"
      :card-id="card.id"
      :current-items="items"
      :disabled-reason="wb.assist.disabledReason(card, 0)"
      @apply="wb.drafts.applySkillGroups(card, $event)"
      @quota="wb.session.setRemainingQuota"
    />
    <AiCertificateSuggestionPanel
      v-if="CREDENTIAL_CARD_TYPES.has(card.cardType) && conversation"
      :conversation-id="conversation.id"
      :card-id="card.id"
      :current-items="items"
      :kind="card.cardType === 'HONORS' ? 'HONOR' : 'CERTIFICATE'"
      :career-library-evidence-enabled="conversation.careerLibraryEvidence.enabled"
      :disabled-reason="wb.assist.disabledReason(card, 0)"
      @apply="wb.drafts.applyCredentialItems(card, $event)"
      @quota="wb.session.setRemainingQuota"
    />

    <div ref="list" class="record-list__items">
      <fieldset v-for="(item, index) in items" :key="index" class="record" :class="{ 'is-collapsed': collapsed[index] }">
        <legend class="record__head" :class="{ 'is-hidden-visually': !sortable }">
          <span v-if="sortable" class="record__grip" title="拖动调整顺序" aria-hidden="true"><GripVertical :size="15" /></span>
          <button type="button" class="record__title" :aria-expanded="!collapsed[index]" @click="collapsed[index] = !collapsed[index]">
            <strong>{{ title }} {{ index + 1 }}</strong>
            <small v-if="recordHeadline(card.cardType, item)">{{ recordHeadline(card.cardType, item) }}</small>
          </button>
          <span v-if="sortable" class="record__actions">
            <UiIconButton :icon="ChevronUp" size="sm" :label="`上移${title} ${index + 1}`" :disabled="index === 0" @click="move(index, -1)" />
            <UiIconButton :icon="ChevronDown" size="sm" :label="`下移${title} ${index + 1}`" :disabled="index === items.length - 1" @click="move(index, 1)" />
            <UiIconButton :icon="Trash2" size="sm" :label="`删除${title} ${index + 1}`" @click="remove(index)" />
          </span>
        </legend>
        <RecordFields v-show="!collapsed[index]" :card="card" :item="item" :index="index" />
      </fieldset>
    </div>

    <button class="record-list__add" type="button" @click="wb.drafts.addRecord(card)">
      <Plus :size="15" aria-hidden="true" />添加一段{{ title }}
    </button>
  </div>
</template>

<style scoped>
.record-list {
  display: grid;
  gap: var(--space-3);
  container-type: inline-size;
}

.record-list__items {
  display: grid;
  gap: var(--space-3);
}

.record {
  min-width: 0;
  margin: 0;
  padding: var(--space-3) var(--space-4) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  transition: border-color var(--dur-fast), box-shadow var(--dur-base) var(--ease-out);
}

.record:focus-within {
  border-color: var(--border-default);
  box-shadow: var(--shadow-sm);
}

.record.is-collapsed {
  padding-bottom: var(--space-3);
}

/* While sorting, records collapse to their headings so every drop target is on screen. */
.record-list__items.is-sorting .record-fields {
  display: none;
}

.record-list__items.is-sorting .record__head {
  margin-bottom: 0;
}

.record.is-drag-ghost {
  opacity: 0.4;
}

.record.is-drag-chosen {
  box-shadow: var(--shadow-lg);
}

.record__head {
  /* Floated so the legend sits inside the box instead of cutting through its border. */
  float: left;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: var(--space-3);
  padding: 0;
}

.record.is-collapsed .record__head {
  margin-bottom: 0;
}

.record__head + * {
  clear: both;
}

/* A single record needs no box of its own inside the card. */
.record-list.is-single .record {
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.record__head.is-hidden-visually {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.record__grip {
  width: 22px;
  height: 28px;
  display: grid;
  place-items: center;
  margin-left: -6px;
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  cursor: grab;
}

.record__grip:hover {
  background: var(--surface-2);
  color: var(--text-secondary);
}

.record__title {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 2px;
  border-radius: var(--radius-sm);
  text-align: left;
}

.record__title strong {
  flex: none;
  font-size: var(--fs-sm);
  font-weight: 680;
}

.record__title small {
  min-width: 0;
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record__title:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.record__actions {
  flex: none;
  display: inline-flex;
  gap: 1px;
  opacity: 0.55;
  transition: opacity var(--dur-fast);
}

.record:hover .record__actions,
.record:focus-within .record__actions {
  opacity: 1;
}

.record-list__add {
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px dashed var(--color-primary-border);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--color-primary-soft) 55%, transparent);
  color: var(--color-primary-text);
  font-size: var(--fs-sm);
  font-weight: 600;
  transition: background-color var(--dur-fast), border-color var(--dur-fast);
}

.record-list__add:hover {
  border-style: solid;
  background: var(--color-primary-soft);
}

.record-list__add:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

@media (hover: none) {
  .record__actions {
    opacity: 1;
  }
}
</style>
