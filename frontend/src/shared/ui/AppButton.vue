<script setup lang="ts">
/** @deprecated Use UiButton. Kept as an adapter while pages migrate. */
import { computed } from 'vue'
import UiButton, { type ButtonVariant } from './UiButton.vue'

const props = withDefaults(
  defineProps<{ variant?: 'primary' | 'ghost' | 'ink' | 'danger' | 'text' | 'wax'; type?: 'button' | 'submit'; disabled?: boolean; pending?: boolean }>(),
  { variant: 'primary', type: 'button', disabled: false, pending: false },
)
const MAP: Record<NonNullable<typeof props.variant>, ButtonVariant> = {
  primary: 'primary', wax: 'primary', ink: 'primary', ghost: 'secondary', danger: 'danger', text: 'link',
}
const variant = computed(() => MAP[props.variant])
</script>

<template>
  <UiButton :variant="variant" :type="type" :disabled="disabled" :pending="pending"><slot /></UiButton>
</template>
