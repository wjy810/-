<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/shared/ui/AppIcon.vue'
import ForbidState from '@/shared/ui/ForbidState.vue'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiCard from '@/shared/ui/UiCard.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { fetchResumeVersion } from '../services/resumeApi'

const route = useRoute()
const router = useRouter()
const pageError = ref('')
const forbidden = ref<unknown>(null)

onMounted(async () => {
  const versionId = String(route.params.versionId ?? '')
  try {
    const version = await fetchResumeVersion(versionId)
    await router.replace({ path: `/resumes/${version.masterId}`, query: { versionId: version.id } })
  } catch (error) {
    if (isForbidden(error)) {
      forbidden.value = error
      return
    }
    pageError.value = errorMessage(error, '简历版本不存在或无法打开')
  }
})
</script>

<template>
  <ForbidState v-if="forbidden" :error="forbidden" />
  <section v-else class="page-container animate-rise redirect-page">
    <UiCard v-if="!pageError" as="div" padding="none" class="redirect-card" aria-busy="true">
      <span class="redirect-card__icon redirect-card__icon--spin"><AppIcon name="loader" :size="20" /></span>
      <h1 class="redirect-card__title">正在打开简历版本…</h1>
      <p class="fine">正在定位所属主档并跳转到编辑器。</p>
      <div class="bones">
        <UiSkeleton />
        <UiSkeleton width="55%" />
      </div>
    </UiCard>
    <UiCard v-else as="div" padding="none" class="redirect-card">
      <span class="redirect-card__icon redirect-card__icon--bad"><AppIcon name="alert-circle" :size="20" /></span>
      <h1 class="redirect-card__title">简历版本打不开</h1>
      <UiBanner tone="danger">{{ pageError }}</UiBanner>
      <RouterLink class="text-link" to="/resumes">
        <AppIcon name="chevron-left" :size="14" />
        回到简历工作台
      </RouterLink>
    </UiCard>
  </section>
</template>

<style scoped>
.redirect-page {
  display: grid;
  gap: var(--space-6);
  align-content: start;
}

/* Nested so it outranks UiCard's own display: block. */
.redirect-page .redirect-card {
  max-width: 480px;
  margin: 48px auto;
  padding: 32px;
  display: grid;
  gap: 12px;
  justify-items: center;
  text-align: center;
}

.redirect-card__icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  display: grid;
  place-items: center;
}

.redirect-card__icon--spin {
  animation: redirect-spin 1s linear infinite;
}

.redirect-card__icon--bad {
  background: var(--color-danger-soft);
  color: var(--color-danger);
  animation: none;
}

.redirect-card__title {
  font-size: 17px;
}

.bones {
  width: 100%;
  display: grid;
  gap: 8px;
}

@keyframes redirect-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
