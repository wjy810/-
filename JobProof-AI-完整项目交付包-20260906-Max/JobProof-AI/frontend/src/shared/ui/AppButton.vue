<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'ink' | 'danger' | 'text' | 'wax'
    type?: 'button' | 'submit'
    disabled?: boolean
    pending?: boolean
  }>(),
  {
    variant: 'primary',
    type: 'button',
    disabled: false,
    pending: false,
  },
)
</script>

<template>
  <button
    class="btn"
    :class="[`btn--${variant}`, { 'is-pending': pending }]"
    :type="type"
    :disabled="disabled || pending"
    :aria-busy="pending"
  >
    <span class="btn__label"><slot /></span>
  </button>
</template>
