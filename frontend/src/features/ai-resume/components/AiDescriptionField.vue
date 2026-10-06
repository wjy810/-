<script setup lang="ts">
import { ref, watch } from 'vue'
import { Check, CircleAlert, LoaderCircle, RefreshCw, ShieldCheck, Sparkles, X } from 'lucide-vue-next'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
import type { DescriptionSuggestionState } from '../utils/descriptionSuggestion'

const props = withDefaults(defineProps<{
  modelValue: string
  state?: DescriptionSuggestionState
  disabledReason?: string
  placeholder?: string
  sourceLabels?: string[]
  label?: string
}>(), {
  label: '补充描述',
  state: undefined,
  disabledReason: '',
  placeholder: '只填写可以确认的课程、职责和成果',
  sourceLabels: () => [],
})

const verificationConfirmed = ref(false)
watch(() => props.state?.requestId, () => { verificationConfirmed.value = false })

defineEmits<{
  'update:modelValue': [value: string]
  generate: []
  cancel: []
  discard: []
  apply: [mode: 'append' | 'replace']
}>()
</script>

<template>
  <div class="description-field">
    <div class="description-field__head">
      <span>{{ label }}</span>
      <button
        type="button"
        class="ai-write"
        :class="{ 'is-loading': state?.status === 'loading' }"
        :disabled="Boolean(disabledReason) || state?.status === 'loading'"
        :title="disabledReason || '根据当前记录生成内容；事实不足时会提示补充'"
        @click="$emit('generate')"
      >
        <LoaderCircle v-if="state?.status === 'loading'" class="ai-write__spin" :size="13" aria-hidden="true" />
        <Sparkles v-else :size="13" aria-hidden="true" />
        {{ state?.status === 'loading' ? '生成中' : 'AI 帮写' }}
      </button>
    </div>
    <UiTextarea
      :model-value="modelValue"
      autosize
      :min-rows="3"
      :max-rows="14"
      :placeholder="placeholder"
      :aria-label="label"
      @update:model-value="$emit('update:modelValue', $event)"
    />

    <div v-if="state?.status === 'loading'" class="helper-state helper-state--loading" role="status">
      <span class="helper-dots" aria-hidden="true"><i /><i /><i /></span>
      <span>正在核对当前信息并生成可确认内容</span>
      <button type="button" @click="$emit('cancel')">取消</button>
    </div>

    <div v-else-if="state?.status === 'error'" class="helper-state helper-state--error" role="alert">
      <CircleAlert :size="15" aria-hidden="true" />
      <span>{{ state.error }}</span>
      <button type="button" @click="$emit('generate')"><RefreshCw :size="12" aria-hidden="true" />重试</button>
      <button type="button" aria-label="关闭提示" title="关闭" @click="$emit('discard')"><X :size="13" aria-hidden="true" /></button>
    </div>

    <section v-else-if="state?.status === 'ready' && state.candidate" class="description-candidate" aria-label="AI 帮写结果">
      <header>
        <span><Sparkles :size="14" aria-hidden="true" />{{ state.candidate.verificationRequired ? 'AI 参考方案' : 'AI 生成内容' }}</span>
        <small v-if="state.candidate.verificationRequired">含 {{ state.candidate.verificationItems.length }} 项待确认推测</small>
        <small v-else>仅依据当前记录 {{ state.candidate.sourceFields.length }} 项事实</small>
      </header>
      <p>{{ state.candidate.suggestion }}</p>
      <div v-if="sourceLabels?.length" class="description-candidate__sources" aria-label="使用的事实字段">
        <span v-for="source in sourceLabels" :key="source">{{ source }}</span>
      </div>
      <small class="description-candidate__reason">{{ state.candidate.reason }}</small>
      <div v-if="state.candidate.verificationRequired" class="description-verification">
        <strong><ShieldCheck :size="14" aria-hidden="true" />AI 已根据基础信息补充参考，使用前请确认</strong>
        <ul>
          <li v-for="item in state.candidate.verificationItems" :key="item">{{ item }}</li>
        </ul>
        <label>
          <input v-model="verificationConfirmed" type="checkbox">
          <span>以上待确认内容均符合我的真实情况</span>
        </label>
      </div>
      <footer>
        <button
          type="button"
          class="candidate-action candidate-action--primary"
          :disabled="state.candidate.verificationRequired && !verificationConfirmed"
          :title="state.candidate.verificationRequired && !verificationConfirmed ? '请先确认待核实内容符合真实情况' : ''"
          @click="$emit('apply', modelValue.trim() ? 'append' : 'replace')"
        >
          <Check :size="14" aria-hidden="true" />{{ modelValue.trim() ? '追加到描述' : '使用此内容' }}
        </button>
        <button
          v-if="modelValue.trim()"
          type="button"
          class="candidate-action"
          :disabled="state.candidate.verificationRequired && !verificationConfirmed"
          @click="$emit('apply', 'replace')"
        >替换原内容</button>
        <button type="button" class="candidate-action" @click="$emit('generate')"><RefreshCw :size="12" aria-hidden="true" />重新生成</button>
        <button type="button" class="candidate-action candidate-action--quiet" @click="$emit('discard')">放弃</button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.description-field {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.description-field__head {
  min-height: 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.description-field__head > span {
  color: var(--text-primary);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.ai-write {
  height: 26px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: var(--radius-full);
  background: var(--gradient-ai-soft);
  color: var(--color-primary-text);
  box-shadow: inset 0 0 0 1px var(--color-primary-border);
  font-size: 12px;
  font-weight: 650;
  transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast);
}

.ai-write:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: inset 0 0 0 1px var(--color-primary), var(--shadow-sm);
}

.ai-write:disabled {
  cursor: not-allowed;
  background: var(--surface-2);
  color: var(--text-disabled);
  box-shadow: inset 0 0 0 1px var(--border-subtle);
}

.ai-write:focus-visible,
.candidate-action:focus-visible,
.helper-state button:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.ai-write__spin {
  animation: ai-write-spin 0.9s linear infinite;
}

.helper-state {
  min-width: 0;
  min-height: 36px;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  font-size: var(--fs-xs);
  animation: helper-enter var(--dur-base) var(--ease-out) both;
}

.helper-state > span:not(.helper-dots) {
  min-width: 0;
  flex: 1;
}

.helper-state button {
  flex: none;
  min-height: 26px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: var(--radius-sm);
  color: inherit;
  font-size: var(--fs-xs);
  font-weight: 650;
}

.helper-state button:hover {
  background: color-mix(in srgb, currentColor 10%, transparent);
}

.helper-state--error {
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
}

.helper-dots {
  width: 26px;
  display: inline-flex;
  gap: 3px;
}

.helper-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  animation: helper-dot 1.1s var(--ease-standard) infinite;
}

.helper-dots i:nth-child(2) {
  animation-delay: 0.13s;
}

.helper-dots i:nth-child(3) {
  animation-delay: 0.26s;
}

.description-candidate {
  min-width: 0;
  padding: 12px 14px;
  display: grid;
  gap: 8px;
  border-radius: var(--radius-lg);
  background: var(--gradient-ai-soft);
  box-shadow: inset 0 0 0 1px var(--color-primary-border);
  animation: helper-enter var(--dur-slow) var(--ease-spring) both;
}

.description-candidate header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.description-candidate header > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--color-primary-text);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.description-candidate header small,
.description-candidate__reason {
  color: var(--text-tertiary);
  font-size: 11.5px;
}

.description-candidate p {
  color: var(--text-primary);
  font-size: var(--fs-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.description-candidate__sources {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.description-candidate__sources span {
  padding: 1px 8px;
  border-radius: var(--radius-full);
  background: var(--surface-1);
  color: var(--text-secondary);
  font-size: 11px;
  box-shadow: inset 0 0 0 1px var(--border-subtle);
}

.description-verification {
  padding: 10px 12px;
  display: grid;
  gap: 6px;
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.description-verification strong {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-xs);
}

.description-verification ul {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 3px;
}

.description-verification li {
  font-size: 12px;
  line-height: 1.55;
}

.description-verification label {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.5;
  cursor: pointer;
}

.description-verification input {
  width: 15px;
  height: 15px;
  flex: none;
  margin: 2px 0 0;
  accent-color: var(--color-primary);
}

.description-candidate footer {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.candidate-action {
  min-height: 30px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-1);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  transition: border-color var(--dur-fast), color var(--dur-fast), background-color var(--dur-fast);
}

.candidate-action:hover:not(:disabled) {
  border-color: var(--color-primary-border);
  color: var(--color-primary-text);
}

.candidate-action--primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--text-on-primary);
}

.candidate-action--primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  color: var(--text-on-primary);
}

.candidate-action--quiet {
  border-color: transparent;
  background: transparent;
}

.candidate-action:disabled {
  cursor: not-allowed;
  border-color: var(--border-subtle);
  background: var(--surface-2);
  color: var(--text-disabled);
}

@keyframes ai-write-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes helper-dot {
  0%,
  70%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  35% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

@keyframes helper-enter {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}

@media (max-width: 520px) {
  .description-candidate header {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }

  .candidate-action {
    flex: 1 1 auto;
    justify-content: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-write,
  .ai-write__spin,
  .helper-dots i,
  .helper-state,
  .description-candidate {
    animation: none;
    transition: none;
  }
}
</style>
