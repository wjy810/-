<script setup lang="ts">
import SettingsNav from '@/shared/ui/SettingsNav.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
</script>

<template>
  <div class="page-container settings">
    <UiPageHeader title="设置" description="管理账号安全、外观偏好、AI 用量与个人数据。" />
    <div class="settings__body">
      <aside class="settings__nav"><SettingsNav /></aside>
      <section class="settings__content">
        <RouterView v-slot="{ Component, route }">
          <Transition name="rise" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </RouterView>
      </section>
    </div>
  </div>
</template>

<style scoped>
.settings {
  display: grid;
  gap: var(--space-6);
}

.settings__body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: var(--space-8);
  align-items: start;
}

.settings__nav {
  position: sticky;
  top: calc(var(--topbar-height) + 24px);
}

.settings__content {
  min-width: 0;
}

@media (max-width: 860px) {
  .settings__body {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }

  .settings__nav {
    position: static;
  }
}
</style>
