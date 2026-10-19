<script setup lang="ts">
import { computed, onMounted } from 'vue'
import type { AiReadinessKey } from '@/shared/api/capabilities'
import { useCapabilitiesStore } from '@/stores/capabilities'
import UiBanner from './UiBanner.vue'

/**
 * Says up front that a feature's model is unreachable, before the user fills in a form that would only fail
 * at the last step. Renders nothing while AI is available or not yet known.
 */
const props = defineProps<{ kind: AiReadinessKey; feature: string; fallback?: string }>()
const capabilities = useCapabilitiesStore()
const unavailable = computed(() => !capabilities.aiReady(props.kind))
onMounted(() => { void capabilities.load() })
</script>

<template>
  <UiBanner v-if="unavailable" tone="warning" title="AI 服务暂时不可用">
    {{ feature }}需要 AI 才能完成，管理员配置好 AI 通道后即可使用。<template v-if="fallback">{{ fallback }}</template>
  </UiBanner>
</template>
