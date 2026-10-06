<script setup lang="ts">
import { ArrowLeft, Home } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
</script>

<template>
  <div class="page-container notfound">
    <UiEmptyState
      illustration="error-plane"
      size="lg"
      title="这个页面飞走了"
      description="链接可能已经失效，或者页面已被移动。你可以返回上一页，或回到首页继续。"
    >
      <UiButton variant="secondary" :icon="ArrowLeft" @click="$router.back()">返回上一页</UiButton>
      <UiButton :icon="Home" :to="session.signedIn ? '/dashboard' : '/'">{{ session.signedIn ? '回到工作台' : '回到首页' }}</UiButton>
    </UiEmptyState>
  </div>
</template>

<style scoped>
.notfound {
  min-height: calc(100vh - var(--topbar-height) - 40px);
  display: grid;
  place-items: center;
}
</style>
