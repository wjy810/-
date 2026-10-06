<script setup lang="ts">
import { useRoute } from 'vue-router'
import BrandMark from '@/shared/ui/BrandMark.vue'
import UiButton from '@/shared/ui/UiButton.vue'

const route = useRoute()
</script>

<template>
  <header class="public-topbar">
    <RouterLink to="/" class="public-topbar__brand" aria-label="JobProof AI 首页"><BrandMark :size="30" /></RouterLink>
    <nav class="public-topbar__nav" aria-label="公共导航">
      <RouterLink to="/resume-templates" class="public-topbar__link">模板中心</RouterLink>
      <RouterLink to="/updates" class="public-topbar__link">更新日志</RouterLink>
      <UiButton variant="ghost" size="sm" :to="{ name: 'home', query: { auth: 'login', next: route.fullPath } }">登录</UiButton>
      <UiButton size="sm" :to="{ name: 'home', query: { auth: 'register', next: route.fullPath } }">免费注册</UiButton>
    </nav>
  </header>
</template>

<style scoped>
.public-topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-topbar);
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 0 var(--space-8);
  background: color-mix(in srgb, var(--bg-app) 84%, transparent);
  backdrop-filter: saturate(1.4) blur(14px);
  border-bottom: 1px solid var(--border-subtle);
}

.public-topbar__brand {
  display: flex;
  color: var(--text-primary);
}

.public-topbar__nav {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.public-topbar__link {
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 500;
}

.public-topbar__link:hover,
.public-topbar__link.router-link-active {
  color: var(--text-primary);
  background: var(--surface-3);
}

@media (max-width: 640px) {
  .public-topbar {
    padding: 0 var(--space-4);
  }

  .public-topbar__link {
    display: none;
  }
}
</style>
