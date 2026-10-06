<script setup lang="ts">
import { computed } from 'vue'
import { useWindowScroll } from '@vueuse/core'
import { useRoute } from 'vue-router'
import { Bell, Menu, Moon, Search, Sun } from 'lucide-vue-next'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiKbd from '@/shared/ui/UiKbd.vue'
import { usePreferencesStore } from '@/stores/preferences'
import { useCommandPaletteStore } from '@/stores/commandPalette'
import { useUnreadCount } from '@/features/notification/queries'
import { findActiveNav } from '../navigation'

defineProps<{ mobileOpen: boolean }>()
const emit = defineEmits<{ 'toggle-mobile': []; 'open-notifications': [] }>()

const route = useRoute()
const preferences = usePreferencesStore()
const palette = useCommandPaletteStore()
const unread = useUnreadCount()

const section = computed(() => findActiveNav(route.path))
const title = computed(() => (route.meta.title as string | undefined) ?? section.value?.label ?? '')
const showSection = computed(() => section.value && title.value !== section.value.label)
const { y: scrollY } = useWindowScroll()
const scrolled = computed(() => scrollY.value > 4)
</script>

<template>
  <header class="topbar" :class="{ 'is-scrolled': scrolled }">
    <button
      class="topbar__menu"
      type="button"
      data-testid="mobile-nav-toggle"
      :aria-label="mobileOpen ? '关闭导航' : '打开导航'"
      :aria-expanded="mobileOpen"
      aria-controls="app-sidebar"
      @click="emit('toggle-mobile')"
    >
      <Menu :size="20" />
    </button>

    <nav class="topbar__crumbs" aria-label="当前位置">
      <template v-if="showSection && section">
        <RouterLink :to="section.to" class="topbar__crumb">{{ section.label }}</RouterLink>
        <span class="topbar__sep" aria-hidden="true">/</span>
      </template>
      <span class="topbar__title">{{ title }}</span>
    </nav>

    <div class="topbar__tools">
      <button class="search-trigger" type="button" aria-label="搜索或跳转（快捷键 Ctrl K）" @click="palette.open = true">
        <Search :size="15" />
        <span class="search-trigger__text">搜索或跳转…</span>
        <UiKbd keys="mod+k" class="search-trigger__kbd" />
      </button>
      <UiIconButton
        class="topbar__search-mobile"
        label="搜索"
        :icon="Search"
        @click="palette.open = true"
      />
      <UiIconButton
        :label="preferences.resolvedTheme === 'dark' ? '切换到浅色' : '切换到深色'"
        :icon="preferences.resolvedTheme === 'dark' ? Sun : Moon"
        @click="preferences.toggleTheme()"
      />
      <UiIconButton
        label="更新与通知"
        :icon="Bell"
        :badge="unread.data.value ? (unread.data.value > 99 ? '99+' : unread.data.value) : null"
        @click="emit('open-notifications')"
      />
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-topbar);
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-6) 0 var(--space-8);
  background: color-mix(in srgb, var(--bg-app) 82%, transparent);
  backdrop-filter: saturate(1.4) blur(14px);
  -webkit-backdrop-filter: saturate(1.4) blur(14px);
  border-bottom: 1px solid transparent;
  transition: border-color var(--dur-base), background-color var(--dur-base);
  view-transition-name: app-topbar;
}

.topbar.is-scrolled {
  border-bottom-color: var(--border-subtle);
}

.topbar__menu {
  display: none;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
}

.topbar__menu:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}

.topbar__crumbs {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-sm);
}

.topbar__crumb {
  color: var(--text-tertiary);
  white-space: nowrap;
}

.topbar__crumb:hover {
  color: var(--text-primary);
}

.topbar__sep {
  color: var(--border-strong);
}

.topbar__title {
  overflow: hidden;
  color: var(--text-primary);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar__tools {
  display: flex;
  align-items: center;
  gap: 4px;
}

.search-trigger {
  width: 260px;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 6px;
  padding: 0 6px 0 11px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  box-shadow: var(--shadow-xs);
  transition: border-color var(--dur-fast), box-shadow var(--dur-base) var(--ease-out);
}

.search-trigger:hover {
  border-color: var(--border-strong);
  color: var(--text-secondary);
}

.search-trigger__text {
  flex: 1;
  text-align: left;
}

.topbar__search-mobile {
  display: none !important;
}

@media (max-width: 1100px) {
  .search-trigger {
    width: 200px;
  }
}

@media (max-width: 959px) {
  .topbar {
    padding: 0 var(--space-3);
  }

  .topbar__menu {
    display: grid;
  }
}

@media (max-width: 720px) {
  .search-trigger {
    display: none;
  }

  .topbar__search-mobile {
    display: inline-grid !important;
  }
}
</style>
