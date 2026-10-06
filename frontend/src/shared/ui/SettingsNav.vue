<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'
import type { IconName } from './icons'

const route = useRoute()

const items: Array<{ to: string; label: string; icon: IconName }> = [
  { to: '/account', label: '账号与安全', icon: 'lock' },
  { to: '/account/ai', label: 'AI 配置', icon: 'sparkles' },
  { to: '/account/data-rights', label: '授权与数据权利', icon: 'shield' },
]

function isActive(to: string): boolean {
  return route.path === to
}

const activeIndex = computed(() => Math.max(0, items.findIndex((item) => isActive(item.to))))
</script>

<template>
  <nav class="settings-nav" aria-label="设置" :style="{ '--settings-index': activeIndex }">
    <RouterLink
      v-for="item in items"
      :key="item.to"
      class="settings-nav__item"
      :class="{ 'is-active': isActive(item.to) }"
      :to="item.to"
    >
      <AppIcon :name="item.icon" :size="16" />
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.settings-nav {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings-nav::before {
  position: absolute;
  inset: 0 0 auto;
  z-index: -1;
  height: 44px;
  border-radius: 9px;
  background: var(--primary-soft);
  box-shadow: inset 3px 0 0 var(--primary);
  content: '';
  transform: translateY(calc(var(--settings-index) * 48px));
  transition: transform 260ms cubic-bezier(0.2, 0.8, 0.2, 1), box-shadow 220ms ease;
}

.settings-nav__item {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 9px;
  font-size: 13.5px;
  color: var(--text-2);
  cursor: pointer;
  transition: color 200ms ease, transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.settings-nav__item:hover {
  color: var(--primary);
  transform: translateX(2px);
}

.settings-nav__item.is-active {
  color: var(--primary);
  font-weight: 600;
}

.settings-nav__item :deep(.app-icon) {
  transition: transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.settings-nav__item:hover :deep(.app-icon),
.settings-nav__item.is-active :deep(.app-icon) {
  transform: scale(1.08);
}

.settings-nav__item:active {
  transform: translateX(1px) scale(0.985);
}

.settings-nav__item:focus-visible {
  outline: 2px solid rgba(37, 99, 235, 0.38);
  outline-offset: -2px;
}

@media (max-width: 940px) {
  .settings-nav {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .settings-nav::before {
    width: calc((100% - 8px) / 3);
    height: 44px;
    box-shadow: inset 0 -3px 0 var(--primary);
    transform: translateX(calc(var(--settings-index) * (100% + 4px)));
  }

  .settings-nav__item {
    justify-content: center;
    padding-inline: 8px;
  }

  .settings-nav__item:hover {
    transform: translateY(-1px);
  }
}

@media (max-width: 540px) {
  .settings-nav__item {
    gap: 6px;
    font-size: 12px;
  }
}

@media (max-width: 390px) {
  .settings-nav__item :deep(.app-icon) {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .settings-nav::before,
  .settings-nav__item,
  .settings-nav__item :deep(.app-icon) {
    transition: none !important;
  }
}
</style>
