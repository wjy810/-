<script setup lang="ts">
/** The only renderer of card fields — shared by the guided card and the module editor (docs/03 §4.7). */
import { computed } from 'vue'
import UiField from '@/shared/ui/UiField.vue'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
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
</script>

<template>
  <div class="card-fields">
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
</style>
