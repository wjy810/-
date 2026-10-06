<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, type RouteLocationNormalizedLoaded } from 'vue-router'
import { onKeyStroke, useMediaQuery } from '@vueuse/core'
import { useQueryClient } from '@tanstack/vue-query'
import { useSessionStore } from '@/stores/session'
import { usePreferencesStore } from '@/stores/preferences'
import { notificationKeys } from '@/features/notification/queries'
import { fetchWhatsNew } from '@/features/updates/services/updatesApi'
import type { ReleaseDetail } from '@/features/updates/types'
import AppSidebar from './parts/AppSidebar.vue'
import AppTopbar from './parts/AppTopbar.vue'
import PublicTopbar from './parts/PublicTopbar.vue'

const UpdateNotificationDrawer = defineAsyncComponent(() => import('@/features/updates/components/UpdateNotificationDrawer.vue'))
const WhatsNewModal = defineAsyncComponent(() => import('@/features/updates/components/WhatsNewModal.vue'))

const route = useRoute()
const session = useSessionStore()
const preferences = usePreferencesStore()
const queryClient = useQueryClient()
const compact = useMediaQuery('(max-width: 1279px)')
const mobile = useMediaQuery('(max-width: 959px)')

const mobileNavOpen = ref(false)
const notificationsOpen = ref(false)
const whatsNew = ref<ReleaseDetail | null>(null)

const collapsed = computed(() => !mobile.value && (preferences.sidebarCollapsed || compact.value))
const wide = computed(() => Boolean(route.meta.wide))

watch(() => route.fullPath, () => {
  mobileNavOpen.value = false
})
watch(mobile, isMobile => {
  if (!isMobile) mobileNavOpen.value = false
})
onKeyStroke('Escape', () => {
  mobileNavOpen.value = false
}, { dedupe: true })

/** Remount pages on param changes, but keep nested layouts (e.g. settings) mounted across children. */
function viewKey(viewRoute: RouteLocationNormalizedLoaded): string {
  return viewRoute.matched.length > 2 ? viewRoute.matched[1].path : viewRoute.path
}

function refreshUnread(): void {
  void queryClient.invalidateQueries({ queryKey: notificationKeys.all })
}

onMounted(() => {
  if (!session.signedIn) return
  fetchWhatsNew().then((value) => {
    whatsNew.value = value ?? null
  }).catch(() => undefined)
})
</script>

<template>
  <div v-if="session.signedIn" class="shell" :class="{ 'is-collapsed': collapsed }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <AppSidebar :collapsed="collapsed" :mobile-open="mobileNavOpen" @close-mobile="mobileNavOpen = false" />
    <Transition name="fade">
      <!-- Pointer-only affordance: the menu toggle and Escape are the accessible ways out. -->
      <div v-if="mobileNavOpen" class="shell__scrim" aria-hidden="true" @click="mobileNavOpen = false" />
    </Transition>
    <div class="shell__main">
      <AppTopbar :mobile-open="mobileNavOpen" @toggle-mobile="mobileNavOpen = !mobileNavOpen" @open-notifications="notificationsOpen = true" />
      <main id="main-content" class="shell__content" :class="{ 'is-wide': wide }" tabindex="-1">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <component :is="Component" :key="viewKey(viewRoute)" />
        </RouterView>
      </main>
    </div>
    <UpdateNotificationDrawer v-if="notificationsOpen" :open="notificationsOpen" @close="notificationsOpen = false" @changed="refreshUnread" />
    <WhatsNewModal v-if="whatsNew" :detail="whatsNew" @close="whatsNew = null" />
  </div>

  <div v-else class="public-shell">
    <PublicTopbar />
    <main id="main-content" class="public-shell__content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.shell__main {
  min-width: 0;
  min-height: 100vh;
  margin-left: var(--sidebar-width);
  display: flex;
  flex-direction: column;
  transition: margin-left var(--dur-slow) var(--ease-out);
}

.shell.is-collapsed .shell__main {
  margin-left: var(--sidebar-width-collapsed);
}

.shell__content {
  flex: 1;
  min-width: 0;
  outline: none;
  view-transition-name: page;
}

.shell__scrim {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-sidebar) - 1);
  background: var(--scrim);
  backdrop-filter: blur(2px);
}

.skip-link {
  position: fixed;
  top: 8px;
  left: 8px;
  z-index: var(--z-command);
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  transform: translateY(-200%);
  transition: transform var(--dur-base) var(--ease-out);
}

.skip-link:focus {
  transform: translateY(0);
  color: #fff;
}

.public-shell {
  min-height: 100vh;
}

.public-shell__content {
  min-width: 0;
}

@media (max-width: 959px) {
  .shell__main,
  .shell.is-collapsed .shell__main {
    margin-left: 0;
  }
}
</style>
