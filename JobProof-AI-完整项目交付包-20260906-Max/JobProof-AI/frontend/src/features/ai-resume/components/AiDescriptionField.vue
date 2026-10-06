<script setup lang="ts">
import { ref, watch } from 'vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import type { DescriptionSuggestionState } from '../utils/descriptionSuggestion'

const props = withDefaults(defineProps<{
  modelValue: string
  state?: DescriptionSuggestionState
  disabledReason?: string
  placeholder?: string
  sourceLabels?: string[]
}>(), {
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
      <span>补充描述</span>
      <button
        type="button"
        class="ai-write"
        :class="{ 'is-loading': state?.status === 'loading' }"
        :disabled="Boolean(disabledReason) || state?.status === 'loading'"
        :title="disabledReason || '根据当前记录生成内容；事实不足时会提示补充'"
        @click="$emit('generate')"
      >
        <AppIcon :name="state?.status === 'loading' ? 'loader' : 'sparkles'" :size="13" />
        {{ state?.status === 'loading' ? '生成中' : 'AI 帮写' }}
      </button>
    </div>
    <textarea
      class="textarea"
      rows="4"
      :value="modelValue"
      :placeholder="placeholder"
      @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />

    <div v-if="state?.status === 'loading'" class="helper-state helper-state--loading" role="status">
      <span class="helper-dots" aria-hidden="true"><i /><i /><i /></span>
      <span>正在核对当前信息并生成可确认内容</span>
      <button type="button" @click="$emit('cancel')">取消</button>
    </div>

    <div v-else-if="state?.status === 'error'" class="helper-state helper-state--error" role="alert">
      <AppIcon name="alert-circle" :size="15" />
      <span>{{ state.error }}</span>
      <button type="button" @click="$emit('generate')"><AppIcon name="refresh" :size="12" />重试</button>
      <button type="button" aria-label="关闭提示" title="关闭" @click="$emit('discard')"><AppIcon name="x" :size="13" /></button>
    </div>

    <section v-else-if="state?.status === 'ready' && state.candidate" class="description-candidate">
      <header>
        <span><AppIcon name="sparkles" :size="14" />{{ state.candidate.verificationRequired ? 'AI 参考方案' : 'AI 生成内容' }}</span>
        <small v-if="state.candidate.verificationRequired">含 {{ state.candidate.verificationItems.length }} 项待确认推测</small>
        <small v-else>仅依据当前记录 {{ state.candidate.sourceFields.length }} 项事实</small>
      </header>
      <p>{{ state.candidate.suggestion }}</p>
      <div v-if="sourceLabels?.length" class="description-candidate__sources" aria-label="使用的事实字段">
        <span v-for="label in sourceLabels" :key="label">{{ label }}</span>
      </div>
      <small class="description-candidate__reason">{{ state.candidate.reason }}</small>
      <div v-if="state.candidate.verificationRequired" class="description-verification">
        <strong><AppIcon name="shield" :size="13" />AI 已根据基础信息补充参考，使用前请确认</strong>
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
          <AppIcon name="check" :size="13" />{{ modelValue.trim() ? '追加到描述' : '使用此内容' }}
        </button>
        <button
          v-if="modelValue.trim()"
          type="button"
          class="candidate-action"
          :disabled="state.candidate.verificationRequired && !verificationConfirmed"
          @click="$emit('apply', 'replace')"
        >替换原内容</button>
        <button type="button" class="candidate-action" @click="$emit('generate')"><AppIcon name="refresh" :size="12" />重新生成</button>
        <button type="button" class="candidate-action" @click="$emit('discard')">放弃</button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.description-field { min-width: 0; display: grid; gap: 6px; }
.description-field__head { min-height: 24px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.description-field__head > span { color: var(--text-2); font-size: 11px; font-weight: 600; }
.ai-write { min-height: 26px; padding: 0 8px; display: inline-flex; align-items: center; gap: 5px; color: #1f5fbf; background: #f4f8ff; border: 1px solid #bfd2f2; border-radius: 5px; font-size: 10px; font-weight: 700; transition: background-color .18s ease, border-color .18s ease, transform .18s ease; }
.ai-write:hover:not(:disabled) { background: #eaf2ff; border-color: #8fb0e7; transform: translateY(-1px); }
.ai-write:disabled { cursor: not-allowed; color: #8492a6; background: #f5f7fa; border-color: #d7dee8; }
.ai-write.is-loading .app-icon { animation: ai-write-spin 1s linear infinite; }
.helper-state { min-width: 0; min-height: 34px; padding: 7px 9px; display: flex; align-items: center; gap: 7px; border-left: 2px solid #8eb0ed; background: #f7faff; color: var(--text-2); font-size: 10px; animation: helper-enter .24s ease-out both; }
.helper-state > span:not(.helper-dots) { min-width: 0; flex: 1; }
.helper-state button { flex: none; min-height: 24px; padding: 0 5px; display: inline-flex; align-items: center; gap: 4px; color: var(--primary); background: transparent; border: 0; font-size: 10px; font-weight: 600; }
.helper-state--error { border-left-color: #e08585; background: #fff7f7; color: #9f2d2d; }
.helper-dots { width: 24px; display: inline-flex; gap: 3px; }
.helper-dots i { width: 4px; height: 4px; border-radius: 50%; background: #4b7fcc; animation: helper-dot 1.1s ease-in-out infinite; }
.helper-dots i:nth-child(2) { animation-delay: .13s; }
.helper-dots i:nth-child(3) { animation-delay: .26s; }
.description-candidate { min-width: 0; padding: 10px 11px; display: grid; gap: 8px; border-left: 2px solid #5d8bd1; background: #f7faff; animation: helper-enter .28s cubic-bezier(.16, 1, .3, 1) both; }
.description-candidate header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.description-candidate header > span { display: inline-flex; align-items: center; gap: 5px; color: #23599f; font-size: 10px; font-weight: 700; }
.description-candidate header small, .description-candidate__reason { color: var(--text-3); font-size: 9px; }
.description-candidate p { color: var(--text); font-size: 11px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.description-candidate__sources { display: flex; flex-wrap: wrap; gap: 5px; }
.description-candidate__sources span { padding: 2px 5px; color: #426796; background: #eaf2ff; border-radius: 3px; font-size: 9px; }
.description-verification { padding: 8px 9px; display: grid; gap: 6px; border-left: 2px solid #d29b35; background: #fffbf2; color: #624617; }
.description-verification strong { display: inline-flex; align-items: center; gap: 5px; font-size: 10px; }
.description-verification ul { margin: 0; padding-left: 17px; display: grid; gap: 3px; }
.description-verification li { font-size: 9px; line-height: 1.5; }
.description-verification label { display: flex; align-items: flex-start; gap: 6px; color: var(--text-2); font-size: 9px; line-height: 1.45; cursor: pointer; }
.description-verification input { width: 14px; height: 14px; flex: none; margin: 0; accent-color: var(--primary); }
.description-candidate footer { display: flex; flex-wrap: wrap; gap: 6px; }
.candidate-action { min-height: 28px; padding: 0 7px; display: inline-flex; align-items: center; gap: 4px; color: var(--text-2); background: #fff; border: 1px solid #cfd8e5; border-radius: 4px; font-size: 10px; font-weight: 600; }
.candidate-action:hover { border-color: #8eace0; color: var(--primary); }
.candidate-action--primary { color: #fff; background: var(--primary); border-color: var(--primary); }
.candidate-action--primary:hover { color: #fff; background: #1f57a8; }
.candidate-action:disabled { cursor: not-allowed; color: #8793a5; background: #eef1f5; border-color: #d6dce5; }
@keyframes ai-write-spin { to { transform: rotate(360deg); } }
@keyframes helper-dot { 0%, 70%, 100% { opacity: .35; transform: translateY(0); } 35% { opacity: 1; transform: translateY(-2px); } }
@keyframes helper-enter { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 520px) {
  .description-candidate header { align-items: flex-start; flex-direction: column; gap: 3px; }
  .candidate-action { flex: 1 1 auto; justify-content: center; }
}
@media (prefers-reduced-motion: reduce) {
  .ai-write, .ai-write .app-icon, .helper-dots i, .helper-state, .description-candidate { animation: none; transition: none; }
}
</style>
