<script setup lang="ts">
/** Shown in place of a page whose feature is switched off on this deployment. */
import { computed } from 'vue'
import { ArrowLeft } from 'lucide-vue-next'
import type { FeatureKey } from '@/shared/api/capabilities'
import { FEATURE_LABELS, useCapabilitiesStore } from '@/stores/capabilities'
import UiButton from '@/shared/ui/UiButton.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import type { IllustrationName } from '@/shared/ui/illustrations'

const props = defineProps<{ feature: FeatureKey }>()
const capabilities = useCapabilitiesStore()
const label = computed(() => FEATURE_LABELS[props.feature])
const illustration = computed<IllustrationName>(() => props.feature === 'careerPlanning' ? 'dash-planning' : 'dash-match')
const contact = computed(() => capabilities.operator?.contactEmail || '')
</script>

<template>
  <section class="feature-unavailable">
    <UiEmptyState
      :title="`${label}暂未开放`"
      :description="`当前站点还没有开启${label}。开放后会出现在这里，你在其他功能里的资料不受影响。`"
      :illustration="illustration"
      size="lg"
    >
      <UiButton variant="secondary" :icon="ArrowLeft" @click="$router.push('/dashboard')">返回工作台</UiButton>
    </UiEmptyState>
    <p v-if="contact" class="feature-unavailable__contact">想提前试用？可以发邮件到 <a :href="`mailto:${contact}`">{{ contact }}</a></p>
  </section>
</template>

<style scoped>
.feature-unavailable { min-height: 60vh; display: grid; align-content: center; justify-items: center; }
.feature-unavailable__contact { color: var(--text-tertiary); font-size: var(--fs-sm); }
.feature-unavailable__contact a { color: var(--color-primary-text); }
</style>
