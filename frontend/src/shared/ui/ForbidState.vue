<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft } from 'lucide-vue-next'
import { isApiClientError } from '@/shared/api/types'
import UiButton from './UiButton.vue'
import UiIllustration from './UiIllustration.vue'

const props = defineProps<{ error: unknown }>()

const reason = computed(() => (isApiClientError(props.error) ? props.error.reason : 'FORBIDDEN'))
const message = computed(() =>
  isApiClientError(props.error) ? props.error.message : '没有权限执行此操作。',
)
</script>

<template>
  <section class="forbid" role="alert">
    <UiIllustration name="privacy-shield" :size="132" />
    <p class="forbid__code">403</p>
    <h1 class="forbid__title">无权访问</h1>
    <p class="forbid__message">{{ message }}</p>
    <p class="forbid__hint">
      每个人只能查看和修改自己的数据；后台账号也看不到求职者的原文。若你认为这是误会，请联系管理员并提供错误代码
      <code>{{ reason }}</code>。
    </p>
    <UiButton variant="secondary" :icon="ArrowLeft" to="/">回到首页</UiButton>
  </section>
</template>

<style scoped>
.forbid {
  min-height: min(70vh, 640px);
  padding: var(--space-10) var(--space-6);
  display: grid;
  place-content: center;
  justify-items: center;
  gap: var(--space-2);
  text-align: center;
}

.forbid__code {
  margin-top: var(--space-2);
  color: var(--color-primary-text);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.06em;
}

.forbid__title {
  font-size: var(--fs-h2);
  line-height: var(--lh-h2);
}

.forbid__message {
  max-width: 520px;
  color: var(--text-secondary);
  font-size: var(--fs-body-lg);
  line-height: var(--lh-body-lg);
}

.forbid__hint {
  max-width: 520px;
  margin-bottom: var(--space-3);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: var(--lh-xs);
}

.forbid__hint code {
  font-family: var(--font-mono);
}
</style>
