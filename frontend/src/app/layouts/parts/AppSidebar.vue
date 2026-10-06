<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronsUpDown, Keyboard, LogOut, Moon, PanelLeftClose, PanelLeftOpen, Plus, Settings, Sun } from 'lucide-vue-next'
import BrandMark from '@/shared/ui/BrandMark.vue'
import UiAvatar from '@/shared/ui/UiAvatar.vue'
import UiDropdownMenu, { type MenuItem } from '@/shared/ui/UiDropdownMenu.vue'
import UiTooltip from '@/shared/ui/UiTooltip.vue'
import { useSessionStore } from '@/stores/session'
import { usePreferencesStore } from '@/stores/preferences'
import { useCommandPaletteStore } from '@/stores/commandPalette'
import { useUnreadCount } from '@/features/notification/queries'
import { NAV_GROUPS, isNavActive } from '../navigation'

const props = defineProps<{ collapsed: boolean; mobileOpen: boolean }>()
const emit = defineEmits<{ 'close-mobile': [] }>()

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const preferences = usePreferencesStore()
const palette = useCommandPaletteStore()
const unread = useUnreadCount()

const groups = computed(() => NAV_GROUPS.filter(group => !group.adminOnly || session.isAdmin))
const unreadText = computed(() => {
  const count = unread.data.value ?? 0
  return count > 99 ? '99+' : count > 0 ? String(count) : ''
})
const showLabels = computed(() => !props.collapsed || props.mobileOpen)

const userMenu = computed<MenuItem[]>(() => [
  { type: 'label', label: session.displayName || '我的账号' },
  { label: '账号与安全', icon: Settings, onSelect: () => router.push('/account') },
  {
    label: preferences.resolvedTheme === 'dark' ? '切换浅色模式' : '切换深色模式',
    icon: preferences.resolvedTheme === 'dark' ? Sun : Moon,
    onSelect: () => preferences.toggleTheme(),
  },
  { label: '键盘快捷键', icon: Keyboard, shortcut: '?', onSelect: () => palette.openShortcuts() },
  { type: 'separator' },
  { label: '退出登录', icon: LogOut, danger: true, onSelect: () => void logout() },
])

async function logout(): Promise<void> {
  await session.logout().catch(() => undefined)
  await router.replace({ name: 'home', query: { auth: 'login', reason: 'logged_out' } })
}
</script>

<template>
  <aside
    id="app-sidebar"
    class="sidebar"
    :class="{ 'is-collapsed': collapsed && !mobileOpen, 'is-mobile-open': mobileOpen }"
    aria-label="主导航"
  >
    <div class="sidebar__top">
      <RouterLink to="/dashboard" class="sidebar__brand" aria-label="JobProof AI 工作台">
        <BrandMark :size="30" :wordmark="showLabels" />
      </RouterLink>
      <UiTooltip :content="collapsed ? '展开侧边栏' : '收起侧边栏'" side="right">
        <button v-if="!mobileOpen" class="sidebar__collapse" type="button" :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'" @click="preferences.toggleSidebar()">
          <PanelLeftOpen v-if="collapsed" :size="17" />
          <PanelLeftClose v-else :size="17" />
        </button>
      </UiTooltip>
    </div>

    <div class="sidebar__cta">
      <UiTooltip :content="showLabels ? '' : '新建简历'" side="right">
        <RouterLink to="/ai-resume/new" class="sidebar__new" :aria-label="showLabels ? undefined : '新建简历'" @click="emit('close-mobile')">
          <Plus :size="17" :stroke-width="2.2" />
          <span v-if="showLabels">新建简历</span>
        </RouterLink>
      </UiTooltip>
    </div>

    <nav class="sidebar__nav">
      <section v-for="group in groups" :key="group.key" class="nav-group" :aria-label="showLabels ? undefined : group.label">
        <h2 v-if="showLabels" class="nav-group__label">{{ group.label }}</h2>
        <div v-else class="nav-group__rule" aria-hidden="true" />
        <ul>
          <li v-for="item in group.items" :key="item.key">
            <UiTooltip :content="showLabels ? '' : item.label" side="right">
              <RouterLink
                :to="item.to"
                class="nav-link"
                :class="{ 'is-active': isNavActive(item, route.path) }"
                :aria-current="isNavActive(item, route.path) ? 'page' : undefined"
                :aria-label="showLabels ? undefined : item.label"
                @click="emit('close-mobile')"
              >
                <component :is="item.icon" class="nav-link__icon" :size="18" :stroke-width="1.85" />
                <span v-if="showLabels" class="nav-link__label">{{ item.label }}</span>
                <span v-if="item.badge === 'unread' && unreadText" class="nav-link__badge" :class="{ 'is-dot': !showLabels }">
                  {{ showLabels ? unreadText : '' }}
                </span>
              </RouterLink>
            </UiTooltip>
          </li>
        </ul>
      </section>
    </nav>

    <div class="sidebar__foot">
      <UiDropdownMenu :items="userMenu" side="top" align="start" :width="220">
        <button class="user-card" type="button" aria-label="账号菜单">
          <UiAvatar :name="session.displayName" :size="32" />
          <template v-if="showLabels">
            <span class="user-card__text">
              <strong :title="session.displayName">{{ session.displayName }}</strong>
              <small>{{ session.isAdmin ? '管理员' : '求职者' }}</small>
            </span>
            <ChevronsUpDown :size="15" class="user-card__chevron" />
          </template>
        </button>
      </UiDropdownMenu>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: var(--z-sidebar);
  width: var(--sidebar-width);
  display: flex;
  flex-direction: column;
  background: color-mix(in srgb, var(--surface-1) 72%, var(--bg-app));
  border-right: 1px solid var(--border-subtle);
  transition: width var(--dur-slow) var(--ease-out), transform var(--dur-slow) var(--ease-out);
  view-transition-name: app-sidebar;
}

.sidebar.is-collapsed {
  width: var(--sidebar-width-collapsed);
}

.sidebar__top {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 12px 0 18px;
}

.is-collapsed .sidebar__top {
  flex-direction: column;
  justify-content: center;
  height: auto;
  padding: 16px 0 8px;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
}

.sidebar__collapse {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  opacity: 0;
  transition: opacity var(--dur-fast), background-color var(--dur-fast), color var(--dur-fast);
}

.sidebar:hover .sidebar__collapse,
.sidebar__collapse:focus-visible,
.is-collapsed .sidebar__collapse {
  opacity: 1;
}

.sidebar__collapse:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}

.sidebar__cta {
  padding: 4px 12px 12px;
}

.sidebar__new {
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--text-on-primary);
  font-weight: 600;
  font-size: var(--fs-body);
  box-shadow: var(--shadow-xs), inset 0 1px 0 rgba(255, 255, 255, 0.16);
  transition: background-color var(--dur-fast), box-shadow var(--dur-base) var(--ease-out), transform var(--dur-instant);
}

.sidebar__new:hover {
  color: var(--text-on-primary);
  background: var(--color-primary-hover);
  box-shadow: var(--shadow-primary);
}

.sidebar__new:active {
  transform: scale(0.98);
}

.is-collapsed .sidebar__new {
  width: 44px;
  margin: 0 auto;
}

.sidebar__nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 12px 12px;
  scrollbar-width: none;
}

.nav-group + .nav-group {
  margin-top: 16px;
}

.nav-group__label {
  margin: 0 0 4px;
  padding: 0 10px;
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 20px;
}

.nav-group__rule {
  height: 1px;
  margin: 0 14px 10px;
  background: var(--border-subtle);
}

.nav-group ul {
  display: grid;
  gap: 2px;
  list-style: none;
}

.nav-link {
  position: relative;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: var(--fs-body);
  font-weight: 500;
  transition: background-color var(--dur-fast) var(--ease-standard), color var(--dur-fast) var(--ease-standard);
}

.is-collapsed .nav-link {
  width: 44px;
  margin: 0 auto;
  justify-content: center;
  padding: 0;
}

.nav-link:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}

.nav-link.is-active {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  font-weight: 600;
}

.nav-link::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--color-primary);
  transform: scaleY(0);
  transition: transform var(--dur-base) var(--ease-out);
}

.nav-link.is-active::before {
  transform: scaleY(1);
}

.nav-link__icon {
  flex-shrink: 0;
}

.nav-link__label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-link__badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-accent);
  color: var(--text-on-primary);
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.nav-link__badge.is-dot {
  position: absolute;
  top: 7px;
  right: 9px;
  min-width: 8px;
  width: 8px;
  height: 8px;
  padding: 0;
  box-shadow: 0 0 0 2px var(--surface-1);
}

.sidebar__foot {
  padding: 10px 12px 14px;
  border-top: 1px solid var(--border-subtle);
}

.user-card {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border-radius: var(--radius-md);
  text-align: left;
  transition: background-color var(--dur-fast);
}

.is-collapsed .user-card {
  justify-content: center;
}

.user-card:hover,
.user-card[data-state='open'] {
  background: var(--surface-3);
}

.user-card__text {
  flex: 1;
  min-width: 0;
  display: grid;
  line-height: 1.3;
}

.user-card__text strong {
  overflow: hidden;
  font-size: var(--fs-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card__text small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.user-card__chevron {
  color: var(--text-tertiary);
}

@media (max-width: 959px) {
  .sidebar {
    width: 280px;
    transform: translateX(-100%);
    box-shadow: none;
  }

  .sidebar.is-mobile-open {
    transform: translateX(0);
    box-shadow: var(--shadow-lg);
    background: var(--surface-1);
  }
}
</style>
