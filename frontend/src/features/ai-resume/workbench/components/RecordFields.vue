<script setup lang="ts">
import { computed } from 'vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
import AiDescriptionField from '../../components/AiDescriptionField.vue'
import MonthInput from '../../components/MonthInput.vue'
import type { AiResumeCard } from '../../types'
import { credentialDescriptionCharacters } from '../../utils/certificateSuggestions'
import { descriptionPlaceholder, languageOptionsFor, RECORD_FIELDS, type FieldSpec } from '../cardConfig'
import { parseSkillItems, skillItemsText } from '../structured'
import type { StructuredItem } from '../types'
import { useWorkbench } from '../useWorkbench'

const props = defineProps<{ card: AiResumeCard; item: StructuredItem; index: number }>()

const wb = useWorkbench()
const fields = computed(() => RECORD_FIELDS[props.card.cardType] ?? [])
const credentialChars = computed(() => credentialDescriptionCharacters(props.item.description))
const skillChips = computed(() => parseSkillItems(skillItemsText(props.item)))

function text(key: string): string {
  return String(props.item[key] ?? '')
}

function update(key: string, value: unknown): void {
  wb.drafts.updateRecordField(props.card, props.index, key, value)
}

function fieldClass(field: FieldSpec): Record<string, boolean> {
  return { 'is-wide': Boolean(field.wide) || field.control === 'ai-description', 'is-current': field.control === 'current' }
}
</script>

<template>
  <div class="record-fields">
    <template v-for="field in fields" :key="field.key">
      <div v-if="field.control === 'current'" class="record-fields__current" :class="fieldClass(field)">
        <UiCheckbox :model-value="Boolean(item.current)" :label="field.label" @update:model-value="update('current', $event)" />
      </div>

      <AiDescriptionField
        v-else-if="field.control === 'ai-description'"
        :class="fieldClass(field)"
        :label="field.label"
        :model-value="text('description')"
        :state="wb.assist.state(card, index)"
        :disabled-reason="wb.assist.disabledReason(card, index)"
        :placeholder="descriptionPlaceholder(card.cardType)"
        :source-labels="wb.assist.sourceLabels(card, index)"
        @update:model-value="update('description', $event)"
        @generate="wb.assist.generate(card, index)"
        @cancel="wb.assist.cancel(card, index)"
        @discard="wb.assist.discard(card, index)"
        @apply="wb.assist.apply(card, index, $event)"
      />

      <UiField
        v-else-if="field.control === 'credential-description'"
        v-slot="{ id }"
        :class="fieldClass(field)"
        :label="field.label"
        :counter="`${credentialChars} / 至少 60 个有效字符`"
        :error="text('description') && credentialChars < 60 ? '说明不足 60 个有效字符，确认前请补充取得过程或可核实成果' : null"
      >
        <UiTextarea
          :id="id"
          :model-value="text('description')"
          autosize
          :min-rows="4"
          :invalid="credentialChars < 60"
          placeholder="填写取得过程、评审范围、实践内容或可核实成果，也可使用上方 AI 补充说明"
          @update:model-value="update('description', $event)"
        />
      </UiField>

      <UiField v-else v-slot="{ id }" :class="fieldClass(field)" :label="field.label">
        <UiSelect
          v-if="field.control === 'select'"
          :id="id"
          :model-value="text(field.key)"
          :aria-label="field.label"
          :options="field.options ?? []"
          :data-autofocus="field.autofocus || undefined"
          @update:model-value="update(field.key, String($event))"
        />
        <UiSelect
          v-else-if="field.control === 'language'"
          :id="id"
          :model-value="text(field.key)"
          :aria-label="field.label"
          searchable
          search-placeholder="搜索语言"
          :options="languageOptionsFor(item[field.key])"
          :data-autofocus="field.autofocus || undefined"
          @update:model-value="update(field.key, String($event))"
        />
        <MonthInput
          v-else-if="field.control === 'month'"
          :model-value="text(field.key)"
          :label="field.label"
          :disabled="field.key === 'endDate' && Boolean(item.current)"
          @update:model-value="update(field.key, $event)"
        />
        <template v-else-if="field.control === 'skills'">
          <UiInput
            :id="id"
            :model-value="skillItemsText(item)"
            :placeholder="field.placeholder"
            @update:model-value="wb.drafts.updateSkillItems(card, index, $event)"
          />
          <div v-if="skillChips.length" class="record-fields__chips" aria-hidden="true">
            <span v-for="chip in skillChips" :key="chip">{{ chip }}</span>
          </div>
        </template>
        <UiTextarea
          v-else-if="field.control === 'textarea'"
          :id="id"
          :model-value="text(field.key)"
          autosize
          :min-rows="3"
          :placeholder="field.placeholder"
          @update:model-value="update(field.key, $event)"
        />
        <UiInput
          v-else
          :id="id"
          :model-value="text(field.key)"
          :placeholder="field.placeholder"
          :data-autofocus="field.autofocus || undefined"
          @update:model-value="update(field.key, $event)"
        />
      </UiField>
    </template>
  </div>
</template>

<style scoped>
.record-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-3);
}

.record-fields .is-wide {
  grid-column: 1 / -1;
}

.record-fields__current {
  align-self: end;
  min-height: var(--control-md);
  display: flex;
  align-items: center;
}

.record-fields__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.record-fields__chips span {
  padding: 2px 9px;
  border-radius: var(--radius-full);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  font-size: 11.5px;
  font-weight: 600;
}

@container (max-width: 420px) {
  .record-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
