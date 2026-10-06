<script setup lang="ts">
import { hydrateSession, session } from '@/features/identity/session'
import AppButton from '@/shared/ui/AppButton.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppToastViewport from '@/shared/ui/AppToastViewport.vue'

async function retryBoot(): Promise<void> {
  session.ready.value = false
  await hydrateSession()
}
</script>

<template>
  <div class="app-root">
    <div v-if="!session.ready.value" class="boot" role="status">
      <span class="boot__brand"><AppIcon name="shield" :size="22" /></span>
      <h1>正在载入 JobProof AI</h1>
      <p class="muted">正在恢复你的登录会话…</p>
    </div>
    <div v-else-if="session.bootError.value" class="boot" role="alert">
      <span class="boot__brand"><AppIcon name="shield" :size="22" /></span>
      <h1>暂时连不上服务</h1>
      <p class="muted">{{ session.bootError.value }}</p>
      <AppButton @click="retryBoot">重试</AppButton>
    </div>
    <RouterView v-else />
    <AppToastViewport />
  </div>
</template>
