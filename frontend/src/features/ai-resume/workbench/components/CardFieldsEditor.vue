<script setup lang="ts">
/** The only renderer of card fields — shared by the guided card and the module editor (docs/03 §4.7). */
import { computed } from 'vue'
import { GitMerge } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
import { formatRelative } from '@/shared/lib/datetime'
import AiSummarySuggestionPanel from '../../components/AiSummarySuggestionPanel.vue'
import JobTaxonomyPicker from '../../components/JobTaxonomyPicker.vue'
import type { AiResumeCard } from '../../types'
import { cardMeta, STRUCTURED_CARD_TYPES } from '../cardConfig'
import { useWorkbench } from '../useWorkbench'
import ContactFields from './ContactFields.vue'
import RecordList from './RecordList.vue'

const props = defineProps<{ card: AiResumeCard }>()

const wb = useWorkbench()
const conversation = wb.session.conversation
const payload = computed(() => wb.drafts.payloadOf(props.card))
const text = computed(() => String(payload.value.text ?? ''))
/** A local draft the server refused because this card changed elsewhere. */
const conflict = computed(() => wb.drafts.conflicts[props.card.id] ?? null)
</script>

<template>
  <div class="card-fields">
    <div v-if="conflict" class="card-conflict" role="status" data-testid="draft-conflict">
      <GitMerge class="card-conflict__icon" :size="17" aria-hidden="true" />
      <p>
        <strong>这个模块在别处更新过</strong>
        <span>下面是最新内容；你在本机的修改{{ conflict.savedAt ? `（${formatRelative(conflict.savedAt)}）` : '' }}另存着，没有被覆盖。</span>
      </p>
      <div class="card-conflict__actions">
        <UiButton size="sm" variant="secondary" @click="wb.drafts.keepLocalDraft(card)">改用我的修改</UiButton>
        <UiButton size="sm" variant="ghost" @click="wb.drafts.discardLocalDraft(card)">保留最新内容</UiButton>
      </div>
    </div>
    <UiField v-if="card.cardType === 'TARGET_JOB'" label="主目标岗位" hint="从标准岗位分类中选择；后续技能和内容建议都会以它为准">
      <JobTaxonomyPicker
        :selected-name="String(payload.targetJob ?? '')"
        :selected-node-id="String(payload.taxonomyNodeId ?? '')"
        @select="wb.drafts.chooseJob(card, $event)"
      />
    </UiField>

    <RecordList v-else-if="STRUCTURED_CARD_TYPES.has(card.cardType)" :card="card" />

    <ContactFields v-else-if="card.cardType === 'CONTACT'" :card="card" />

    <template v-else-if="card.cardType === 'SUMMARY'">
      <AiSummarySuggestionPanel
        v-if="conversation"
        :conversation-id="conversation.id"
        :card-id="card.id"
        :current-summary="text"
        :disabled-reason="wb.assist.summaryDisabledReason()"
        @apply="wb.drafts.applySummaryCandidate(card, $event)"
        @quota="wb.session.setRemainingQuota"
        @busy="wb.assist.summaryPending.value = $event"
      />
      <UiField v-slot="{ id }" label="个人简介草稿" :counter="`${text.length} 字`">
        <UiTextarea
          :id="id"
          :model-value="text"
          autosize
          :min-rows="5"
          data-autofocus
          placeholder="填写个人简介，或从上方三个 AI 建议中选择一个版本"
          @update:model-value="wb.drafts.updateField(card, 'text', $event)"
        />
      </UiField>
    </template>

    <UiField v-else v-slot="{ id }" label="确认事实">
      <UiTextarea
        :id="id"
        :model-value="text"
        autosize
        :min-rows="5"
        data-autofocus
        :placeholder="`填写${cardMeta(card.cardType).label}，只写可以确认的事实`"
        @update:model-value="wb.drafts.updateField(card, 'text', $event)"
      />
    </UiField>
  </div>
</template>

<style scoped>
.card-fields {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.card-conflict {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 4px var(--space-3);
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--color-warning) 38%, var(--border-subtle));
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  animation: card-conflict-in var(--dur-base, 200ms) var(--ease-out);
}

.card-conflict__icon {
  margin-top: 2px;
  color: var(--color-warning-text);
}

.card-conflict p {
  display: grid;
  gap: 2px;
  font-size: var(--fs-sm);
  line-height: 1.55;
}

.card-conflict strong {
  color: var(--color-warning-text);
  font-weight: 650;
}

.card-conflict span {
  color: var(--text-secondary);
}

.card-conflict__actions {
  grid-column: 2;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: 6px;
}

@keyframes card-conflict-in {
  from { opacity: 0; transform: translateY(-4px); }
}

@media (prefers-reduced-motion: reduce) {
  .card-conflict { animation: none; }
}
</style>
