<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Database, Lock, Palette, Sparkles } from 'lucide-vue-next'

const route = useRoute()
const items = [
  { to: '/account', label: '账号与安全', icon: Lock },
  { to: '/account/appearance', label: '外观', icon: Palette },
  { to: '/account/ai', label: 'AI 用量', icon: Sparkles },
  { to: '/account/data-rights', label: '数据与隐私', icon: Database },
]

function isActive(to: string): boolean {
  return to === '/account' ? route.path === '/account' : route.path.startsWith(to)
}
</script>

<template>
  <nav class="settings-nav" aria-label="设置">
    <RouterLink
      v-for="item in items"
      :key="item.to"
      :to="item.to"
      class="settings-nav__item"
      :class="{ 'is-active': isActive(item.to) }"
      :aria-current="isActive(item.to) ? 'page' : undefined"
    >
      <component :is="item.icon" :size="17" :stroke-width="1.9" />
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.settings-nav {
  display: grid;
  gap: 2px;
}

.settings-nav__item {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: var(--fs-body);
  font-weight: 500;
  transition: background-color var(--dur-fast), color var(--dur-fast);
}

.settings-nav__item:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}

.settings-nav__item.is-active {
  background: var(--surface-1);
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm), inset 0 0 0 1px var(--border-subtle);
}

.settings-nav__item.is-active svg {
  color: var(--color-primary);
}

@media (max-width: 860px) {
  .settings-nav {
    display: flex;
    overflow-x: auto;
    gap: 4px;
    scrollbar-width: none;
  }

  .settings-nav__item {
    flex-shrink: 0;
  }
}
</style>
