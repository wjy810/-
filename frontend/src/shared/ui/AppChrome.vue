<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Bell, ChevronDown, Menu, X } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { logoutAccount } from '@/features/identity/services/authApi'
import { clearSession, session } from '@/features/identity/session'
import { fetchUnreadCount } from '@/features/notification/services/notificationApi'
import UpdateNotificationDrawer from '@/features/updates/components/UpdateNotificationDrawer.vue'
import WhatsNewModal from '@/features/updates/components/WhatsNewModal.vue'
import { fetchWhatsNew } from '@/features/updates/services/updatesApi'
import type { ReleaseDetail } from '@/features/updates/types'
import { errorMessage, isUnauthenticated } from '@/shared/api/types'
import brandLogo from '@/assets/jobproof-ai-logo.png'
import AppIcon from './AppIcon.vue'
import JobProofIcon from './JobProofIcon.vue'
import { useToastFeedback } from './toast'
import type { IconName } from './icons'
import { jobProofIconIds, type JobProofIconName } from './jobProofIcons'

const props = withDefaults(defineProps<{ immersiveCareer?: boolean }>(), { immersiveCareer: false })

type NavItem = {
  to: string
  label: string
  icon: IconName
  brandIcon?: JobProofIconName
  match?: string[]
}

const NAV: NavItem[] = [
  { to: '/career-library', label: '求职资料库', icon: 'folder', brandIcon: jobProofIconIds.careerLibrary },
  { to: '/resumes', label: '简历工作台', icon: 'file-text', brandIcon: jobProofIconIds.resumeWorkbench },
  { to: '/job-match', label: '岗位匹配', icon: 'target', brandIcon: jobProofIconIds.jobMatching },
  { to: '/career-planning', label: '职业规划', icon: 'trending-up', brandIcon: jobProofIconIds.careerPlanning },
  { to: '/mock-interviews', label: '模拟面试', icon: 'mic', brandIcon: jobProofIconIds.mockInterview },
  { to: '/resume-templates', label: '模板中心', icon: 'book', brandIcon: jobProofIconIds.templateCenter },
  { to: '/notifications', label: '通知中心', icon: 'bell', brandIcon: jobProofIconIds.notificationCenter },
  { to: '/updates', label: '更新日志', icon: 'info' },
]
const visibleNav = computed<NavItem[]>(() => session.account.value?.role === 'ADMIN'
  ? [...NAV, { to: '/admin/resume-templates', label: '模板运营', icon: 'settings' }, { to: '/admin/ai-channels', label: 'AI 通道', icon: 'sparkles' }, { to: '/admin/changelog', label: '更新发布', icon: 'file-text' }]
  : NAV)

const router = useRouter()
const route = useRoute()
const mobileNavOpen = ref(false)
const pending = ref(false)
const error = ref('')
useToastFeedback(error, 'error', 'app-chrome-error')
const unread = ref(0)
const notificationDrawerOpen = ref(false)
const whatsNew = ref<ReleaseDetail | null>(null)

const identifier = computed(() => session.account.value?.displayIdentifier || session.account.value?.email || '')
const initial = computed(() => (identifier.value.trim().replace('+86 ', '').charAt(0) || '·').toUpperCase())
const unreadText = computed(() => (unread.value > 99 ? '99+' : String(unread.value)))
const careerMode = computed(() => route.path === '/career-library' || route.path.startsWith('/job-match') || route.path.startsWith('/career-planning'))

function isActive(item: NavItem): boolean {
  if (route.path === item.to || route.path.startsWith(`${item.to}/`)) {
    return true
  }
  return (item.match ?? []).some((prefix) => route.path.startsWith(prefix))
}

const activeNavIndex = computed(() => visibleNav.value.findIndex(isActive))

async function refreshUnread(): Promise<void> {
  if (!session.signedIn.value) {
    unread.value = 0
    return
  }
  try {
    unread.value = await fetchUnreadCount()
  } catch {
    // 角标读取失败不打扰主流程，保持上次值
  }
}

async function onLogout(): Promise<void> {
  error.value = ''
  pending.value = true
  try {
    await logoutAccount()
  } catch (err) {
    if (!isUnauthenticated(err)) {
      error.value = errorMessage(err, '退出失败')
      pending.value = false
      return
    }
  }
  try {
    clearSession()
    await router.replace({ name: 'home', query: { auth: 'login', reason: 'logged_out' } })
  } finally {
    pending.value = false
  }
}

function onWindowKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && mobileNavOpen.value) {
    mobileNavOpen.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', onWindowKeydown)
  if (session.signedIn.value) {
    void refreshUnread()
    void fetchWhatsNew().then(value => { whatsNew.value = value ?? null }).catch(() => undefined)
  }
})

onBeforeUnmount(() => window.removeEventListener('keydown', onWindowKeydown))

watch(
  () => route.fullPath,
  () => {
    mobileNavOpen.value = false
    if (session.signedIn.value) {
      void refreshUnread()
    }
  },
)
</script>

<template>
  <div v-if="!session.signedIn.value" class="public-shell">
    <header class="public-topbar">
      <RouterLink class="public-brand" to="/resume-templates" aria-label="JobProof AI 模板中心">
        <img class="brand-lockup" :src="brandLogo" alt="JobProof AI" width="124" height="27">
      </RouterLink>
      <nav class="public-nav" aria-label="公共导航">
        <RouterLink to="/resume-templates">模板中心</RouterLink>
        <RouterLink to="/updates">更新日志</RouterLink>
        <RouterLink class="btn btn--ghost" :to="{ name: 'home', query: { auth: 'login', next: route.fullPath } }">登录</RouterLink>
        <RouterLink class="btn btn--primary" :to="{ name: 'home', query: { auth: 'register', next: route.fullPath } }">注册</RouterLink>
      </nav>
    </header>
    <main class="public-main">
      <slot />
    </main>
  </div>

  <div v-else class="shell" :class="{ 'shell--immersive-career': props.immersiveCareer }">
    <button v-if="mobileNavOpen" class="sidebar-scrim" type="button" aria-label="关闭导航" @click="mobileNavOpen = false" />
    <aside id="app-sidebar" class="sidebar" :class="{ 'is-career': careerMode, 'is-mobile-open': mobileNavOpen }">
      <RouterLink class="sidebar__brand" to="/resume-home" aria-label="打开最近使用的简历">
        <span class="sidebar__brand-asset" aria-hidden="true">
          <img :src="brandLogo" alt="" width="124" height="27">
        </span>
      </RouterLink>

      <nav
        class="nav"
        :class="{ 'has-active': activeNavIndex >= 0 }"
        :style="{ '--nav-index': Math.max(0, activeNavIndex) }"
        aria-label="主导航"
      >
        <span class="nav__active-indicator" aria-hidden="true" />
        <RouterLink
          v-for="item in visibleNav"
          :key="item.to"
          class="nav__item"
          :class="{ 'is-active': isActive(item) }"
          :to="item.to"
        >
          <JobProofIcon v-if="item.brandIcon" :name="item.brandIcon" :size="18" />
          <AppIcon v-else :name="item.icon" :size="17" />
          <span class="nav__label">{{ item.label }}</span>
          <span v-if="item.to === '/notifications' && unread > 0" class="nav__badge">
            {{ unreadText }}
          </span>
        </RouterLink>
      </nav>

      <div class="sidebar__user">
        <span class="avatar" aria-hidden="true">{{ initial }}</span>
        <span class="sidebar__userinfo">
          <strong :title="identifier">{{ identifier }}</strong>
          <RouterLink class="sidebar__account" to="/account">账号与安全</RouterLink>
        </span>
        <button
          class="icon-btn"
          type="button"
          title="退出登录"
          :disabled="pending"
          @click="onLogout"
        >
          <AppIcon name="logout" :size="16" />
        </button>
      </div>
    </aside>

    <div class="main" :class="{ 'is-career': careerMode }">
      <header class="topbar" :class="{ 'topbar--career': careerMode }">
        <button
          class="mobile-menu"
          type="button"
          data-testid="mobile-nav-toggle"
          :aria-label="mobileNavOpen ? '关闭导航' : '打开导航'"
          :aria-expanded="mobileNavOpen"
          aria-controls="app-sidebar"
          @click="mobileNavOpen = !mobileNavOpen"
        >
          <X v-if="mobileNavOpen" :size="20" />
          <Menu v-else :size="20" />
        </button>
        <div v-if="careerMode" class="topbar__center"><slot name="topbar" /></div>
        <div class="topbar__tools">
          <button class="icon-btn" type="button" title="更新与通知" @click="notificationDrawerOpen = true">
            <Bell v-if="careerMode" :size="19" />
            <AppIcon v-else name="bell" :size="18" />
            <span v-if="unread > 0" class="icon-btn__badge">{{ unreadText }}</span>
          </button>
          <RouterLink v-if="careerMode" class="topbar-account" to="/account" title="账号与安全">
            <span class="avatar">{{ initial }}</span>
            <ChevronDown :size="14" />
          </RouterLink>
        </div>
      </header>
      <main class="main__body">
        <slot />
      </main>
    </div>
  </div>
  <UpdateNotificationDrawer :open="notificationDrawerOpen" @close="notificationDrawerOpen = false" @changed="refreshUnread" />
  <WhatsNewModal :detail="whatsNew" @close="whatsNew = null" />
</template>

<style scoped>
.public-shell {
  min-height: 100vh;
  background: var(--bg);
}

.public-topbar {
  position: sticky;
  top: 0;
  z-index: 40;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 24px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.96);
}

.public-brand {
  display: flex;
  align-items: center;
  color: var(--text);
}

.brand-lockup {
  width: 124px;
  height: 27px;
  object-fit: contain;
}

.public-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.public-nav > a:first-child {
  padding: 8px 10px;
  color: var(--primary);
  font-weight: 600;
}

.public-main {
  min-width: 0;
}

.shell {
  min-height: 100vh;
  background: var(--bg);
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  width: 232px;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  z-index: 40;
  box-shadow: 1px 0 0 rgba(15, 23, 42, 0.015);
  view-transition-name: app-sidebar;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px 14px;
  color: var(--text);
  transition: background-color 220ms ease;
}

.sidebar__brand:hover {
  background: #f9fbff;
}

.sidebar__brand-asset {
  width: 124px;
  height: 32px;
  display: flex;
  align-items: center;
  overflow: hidden;
  flex-shrink: 0;
  transition: transform 260ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.sidebar__brand-asset img {
  width: 124px;
  height: 27px;
  max-width: none;
  flex: 0 0 auto;
  object-fit: contain;
}

.sidebar__brand:hover .sidebar__brand-asset {
  transform: none;
}

.nav {
  position: relative;
  isolation: isolate;
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  display: grid;
  gap: 2px;
  align-content: start;
}

.nav__active-indicator {
  position: absolute;
  z-index: 0;
  top: 8px;
  right: 12px;
  left: 12px;
  height: 40px;
  border-radius: var(--radius);
  background: var(--primary);
  box-shadow: 0 7px 16px rgba(37, 99, 235, 0.2);
  opacity: 0;
  pointer-events: none;
  transform: translateY(calc(var(--nav-index) * 42px));
  transition:
    transform var(--motion-slow) var(--motion-ease),
    opacity var(--motion-fast) ease,
    box-shadow var(--motion-base) var(--motion-ease);
  view-transition-name: app-primary-nav-indicator;
}

.nav.has-active .nav__active-indicator {
  opacity: 1;
}

.nav__item {
  position: relative;
  isolation: isolate;
  min-height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  overflow: hidden;
  border-radius: var(--radius);
  color: var(--text-2);
  font-size: 14px;
  transition: color 220ms ease, transform 260ms cubic-bezier(0.2, 0.8, 0.2, 1);
  z-index: 1;
}

.nav__item::before {
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background: var(--primary-soft);
  content: '';
  opacity: 0;
  transform: scale(0.975);
  transition: opacity 220ms ease, transform 260ms cubic-bezier(0.2, 0.8, 0.2, 1), background-color 220ms ease, box-shadow 260ms ease;
}

.nav__item > svg,
.nav__item > :first-child {
  flex: 0 0 auto;
  transition: transform 260ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.nav__item:hover {
  color: var(--primary);
  transform: translateX(2px);
}

.nav__item:hover::before {
  opacity: 1;
  transform: scale(1);
}

.nav__item:hover > svg,
.nav__item:hover > :first-child {
  transform: scale(1.06);
}

.nav__item.is-active {
  color: #fff;
  transform: translateX(0);
}

.nav__item.is-active::before {
  opacity: 0;
  transform: scale(1);
}

.nav__item.is-active > svg,
.nav__item.is-active > :first-child {
  transform: scale(1.04);
}

.nav__item:active {
  transform: translateX(1px) scale(0.99);
}

.nav__item:focus-visible {
  outline: 2px solid rgba(37, 99, 235, 0.42);
  outline-offset: 2px;
}

.nav__label {
  flex: 1;
  transition: font-weight 180ms ease;
}

.nav__item.is-active .nav__label {
  font-weight: 650;
}

.nav__badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 0 0 2px #fff;
  animation: nav-badge-in 260ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.sidebar__user {
  border-top: 1px solid var(--border);
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: background-color 220ms ease;
}

.sidebar__user:hover {
  background: #f9fbff;
}

.sidebar__userinfo {
  flex: 1;
  min-width: 0;
  display: grid;
  line-height: 1.35;
}

.sidebar__userinfo strong {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar__account {
  font-size: 12px;
  color: var(--text-3);
  transition: color 180ms ease;
}

.sidebar__account:hover {
  color: var(--primary);
}

.main {
  margin-left: 232px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 60px;
  background: rgba(255, 255, 255, 0.97);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 30;
  view-transition-name: app-topbar;
}

.topbar--career {
  height: 64px;
  justify-content: space-between;
  gap: 18px;
  padding: 0 22px;
  border-color: var(--border);
}

.topbar__center {
  width: min(520px, 52vw);
  margin-left: auto;
}

.topbar-account {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #6b7b91;
}

.topbar-account .avatar {
  width: 34px;
  height: 34px;
  background: var(--primary);
}

.mobile-menu,
.sidebar-scrim {
  display: none;
}

.topbar__tools {
  display: flex;
  align-items: center;
  gap: 6px;
}

.main__body {
  flex: 1;
  min-width: 0;
  animation: main-content-enter var(--motion-base) var(--motion-ease-out) both;
}

@keyframes main-content-enter {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

:global(html:active-view-transition) .main__body {
  animation: none;
}

@media (min-width: 641px) {
  .shell--immersive-career .topbar--career {
    display: none;
  }
}

@keyframes nav-badge-in {
  from { opacity: 0; transform: scale(0.72); }
  to { opacity: 1; transform: scale(1); }
}

@media (max-width: 900px) {
  .sidebar {
    width: 64px;
  }

  .nav__label,
  .sidebar__userinfo {
    display: none;
  }

  .sidebar__brand {
    justify-content: center;
    padding: 14px 0 10px;
  }

  .sidebar__brand-asset {
    width: 32px;
  }

  .nav__item {
    min-width: 44px;
    min-height: 44px;
    justify-content: center;
    padding: 10px 0;
  }

  .nav {
    padding-inline: 10px;
  }

  .nav__badge {
    position: absolute;
    top: 4px;
    right: 8px;
  }

  .nav__item {
    position: relative;
  }

  .sidebar__user {
    flex-direction: column;
    justify-content: center;
    gap: 4px;
    padding: 8px 4px;
  }

  .main {
    margin-left: 64px;
  }
}

@media (max-width: 640px) {
  .public-topbar {
    height: 58px;
    padding: 0 14px;
  }

  .public-nav > a:first-child,
  .public-nav .btn--ghost {
    display: none;
  }

  .public-nav .btn {
    min-width: 44px;
    height: 44px;
    padding: 0 12px;
  }

  .sidebar__user > .icon-btn {
    width: 44px;
    height: 44px;
  }

  .shell {
    min-width: 0;
    overflow-x: clip;
  }

  .main,
  .main.is-career {
    width: 100%;
    min-width: 0;
    margin-left: 0;
  }

  .sidebar,
  .sidebar.is-career {
    width: min(82vw, 272px);
    transform: translateX(-100%);
    visibility: hidden;
    transition: transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1), visibility 0s linear 220ms;
    box-shadow: 12px 0 32px rgba(15, 23, 42, 0.14);
  }

  .sidebar.is-mobile-open,
  .sidebar.is-career.is-mobile-open {
    transform: translateX(0);
    visibility: visible;
    transition-delay: 0s;
  }

  .sidebar .nav__label {
    display: block;
  }

  .sidebar .sidebar__userinfo {
    display: grid;
  }

  .sidebar .sidebar__brand,
  .sidebar .nav__item,
  .sidebar .sidebar__user {
    justify-content: flex-start;
  }

  .sidebar .sidebar__brand-asset {
    width: 124px;
  }

  .sidebar .nav {
    padding: 8px 12px;
  }

  .sidebar .nav__item {
    justify-content: flex-start;
    gap: 11px;
    padding: 10px 12px;
  }

  .sidebar .nav__badge {
    position: static;
    margin-left: auto;
  }

  .sidebar .sidebar__user {
    flex-direction: row;
    gap: 10px;
    padding: 12px 12px calc(12px + env(safe-area-inset-bottom));
  }

  .sidebar-scrim {
    position: fixed;
    inset: 0;
    z-index: 39;
    display: block;
    border: 0;
    background: rgba(15, 23, 42, 0.35);
  }

  .mobile-menu {
    width: 44px;
    height: 44px;
    display: grid;
    place-items: center;
    flex: 0 0 auto;
    border: 0;
    border-radius: 8px;
    color: #43546d;
    background: #f5f7fb;
  }

  .mobile-menu:focus-visible {
    outline: 2px solid rgba(37, 99, 235, 0.42);
    outline-offset: 2px;
  }

  .topbar {
    height: 60px;
    justify-content: space-between;
    gap: 8px;
    padding: 0 12px;
  }

  .topbar--career {
    height: 64px;
    padding: 0 12px;
    gap: 8px;
  }

  .topbar__center {
    width: auto;
    flex: 1;
  }

  .topbar--career .topbar-account > svg {
    display: none;
  }

  .topbar--career .topbar__tools > .icon-btn {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sidebar,
  .sidebar__brand,
  .sidebar__brand-asset,
  .nav__item,
  .nav__item::before,
  .nav__item > svg,
  .nav__item > :first-child,
  .sidebar__user,
  .sidebar__account {
    transition: none;
  }

  .nav__active-indicator { transition: none; }
  .main__body,
  .nav__badge { animation: none; }
}
</style>
