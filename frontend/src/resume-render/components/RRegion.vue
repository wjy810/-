<script setup lang="ts">
/** A flow region of the page; the page decides which blocks land here. */
import { inject } from 'vue'
import BlockView from './BlockView.vue'
import { PAGE_CONTEXT } from './context'

defineProps<{ id: string; locale: 'zh-CN' | 'en' }>()
const page = inject(PAGE_CONTEXT)!
</script>

<template>
  <div class="rr-region" :data-region="id">
    <BlockView v-for="block in page.blocks(id)" :key="block.id" :block="block" :locale="locale" />
  </div>
</template>
