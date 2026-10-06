<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{ modelValue?: string | null; autosize?: boolean; minRows?: number; maxRows?: number; invalid?: boolean }>(),
  { modelValue: '', autosize: false, minRows: 3, maxRows: 12, invalid: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const el = ref<HTMLTextAreaElement | null>(null)

function resize(): void {
  if (!props.autosize || !el.value) return
  const style = getComputedStyle(el.value)
  const line = Number.parseFloat(style.lineHeight) || 22
  const padding = Number.parseFloat(style.paddingTop) + Number.parseFloat(style.paddingBottom)
  el.value.style.height = 'auto'
  const height = Math.min(Math.max(el.value.scrollHeight, props.minRows * line + padding), props.maxRows * line + padding)
  el.value.style.height = `${height}px`
}

watch(() => props.modelValue, () => nextTick(resize))
onMounted(resize)
defineExpose({ focus: () => el.value?.focus(), el })
</script>

<template>
  <textarea
    ref="el"
    v-bind="$attrs"
    class="ui-textarea"
    :class="{ 'is-invalid': invalid, 'is-autosize': autosize }"
    :rows="minRows"
    :value="modelValue ?? ''"
    :aria-invalid="invalid || undefined"
    @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value); resize()"
  />
</template>

<style scoped>
.ui-textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-primary);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  box-shadow: var(--shadow-xs);
  resize: vertical;
  transition: border-color var(--dur-fast) var(--ease-standard), box-shadow var(--dur-base) var(--ease-out);
}

.ui-textarea.is-autosize {
  resize: none;
  overflow-y: auto;
}

.ui-textarea:hover {
  border-color: var(--border-strong);
}

.ui-textarea:focus {
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
  border-radius: var(--radius-md);
}

.ui-textarea.is-invalid {
  border-color: var(--color-danger);
}

.ui-textarea:disabled {
  background: var(--surface-3);
  color: var(--text-disabled);
  cursor: not-allowed;
}
</style>
