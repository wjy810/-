<script setup lang="ts">
/**
 * Wraps a panel teleported to <body> (pickers, date pickers) so it keeps working inside a modal
 * dialog: it joins Reka's focus-scope stack (pausing the dialog's focus trap while open) and carries
 * the floating-layer marker that re-enables pointer events and tells UiDialog the interaction is its own.
 * Renders no element of its own; the single child receives the attributes.
 */
import { FocusScope } from 'reka-ui'
import { FLOATING_LAYER_ATTR } from '@/shared/lib/floatingLayer'

const marker = { [FLOATING_LAYER_ATTR]: '' }
</script>

<template>
  <!-- Focus stays where the user put it (no keyboard pop-up on mobile); closing returns it to the trigger. -->
  <FocusScope as-child v-bind="marker" @mount-auto-focus.prevent>
    <slot />
  </FocusScope>
</template>
