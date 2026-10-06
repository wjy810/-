<script setup lang="ts">
/** Operator line for footers and legal pages; each part appears only when configured. */
import { computed, onMounted } from 'vue'
import { useCapabilitiesStore } from '@/stores/capabilities'

const capabilities = useCapabilitiesStore()
onMounted(() => void capabilities.load())
const operator = computed(() => capabilities.operator)
const visible = computed(() => {
  const value = operator.value
  return Boolean(value && (value.name || value.icpRecord || value.publicSecurityRecord || value.contactEmail || value.feedbackUrl))
})
</script>

<template>
  <p v-if="visible && operator" class="operator-info">
    <span v-if="operator.name">运营方：{{ operator.name }}</span>
    <a v-if="operator.icpRecord" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">{{ operator.icpRecord }}</a>
    <span v-if="operator.publicSecurityRecord">{{ operator.publicSecurityRecord }}</span>
    <a v-if="operator.contactEmail" :href="`mailto:${operator.contactEmail}`">{{ operator.contactEmail }}</a>
    <span v-if="operator.contactPhone">{{ operator.contactPhone }}</span>
    <span v-if="operator.address">{{ operator.address }}</span>
    <a v-if="operator.feedbackUrl" :href="operator.feedbackUrl" target="_blank" rel="noopener noreferrer">意见反馈</a>
  </p>
</template>

<style scoped>
.operator-info {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: var(--lh-xs);
}
.operator-info a { color: var(--text-secondary); text-decoration: none; }
.operator-info a:hover { color: var(--color-primary-text); text-decoration: underline; }
</style>
