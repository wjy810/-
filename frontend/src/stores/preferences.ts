/**
 * Client-side preferences (docs/01 SET-02): theme, motion, sidebar.
 * Persisted to localStorage; index.html applies the theme before first paint.
 */
import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

export type ThemePreference = 'light' | 'dark' | 'system'
export type MotionPreference = 'system' | 'reduce' | 'full'

export const PREFERENCES_KEY = 'jp:preferences'

type Persisted = { theme: ThemePreference; motion: MotionPreference; sidebarCollapsed: boolean; splitRatio?: Record<string, number> }

const DEFAULTS: Persisted = { theme: 'system', motion: 'system', sidebarCollapsed: false, splitRatio: {} }

function read(): Persisted {
  try {
    const raw = window.localStorage.getItem(PREFERENCES_KEY)
    if (!raw) return { ...DEFAULTS }
    const parsed = JSON.parse(raw) as Partial<Persisted>
    return {
      theme: parsed.theme === 'light' || parsed.theme === 'dark' ? parsed.theme : 'system',
      motion: parsed.motion === 'reduce' || parsed.motion === 'full' ? parsed.motion : 'system',
      sidebarCollapsed: parsed.sidebarCollapsed === true,
      splitRatio: typeof parsed.splitRatio === 'object' && parsed.splitRatio ? parsed.splitRatio : {},
    }
  } catch {
    return { ...DEFAULTS }
  }
}

function write(value: Persisted): void {
  try {
    window.localStorage.setItem(PREFERENCES_KEY, JSON.stringify(value))
  } catch {
    /* Storage can be unavailable (private mode); preferences then last for the session only. */
  }
}

export const usePreferencesStore = defineStore('preferences', () => {
  const initial = typeof window === 'undefined' ? { ...DEFAULTS } : read()
  const theme = ref<ThemePreference>(initial.theme)
  const motion = ref<MotionPreference>(initial.motion)
  const sidebarCollapsed = ref(initial.sidebarCollapsed)
  const splitRatio = ref<Record<string, number>>(initial.splitRatio ?? {})

  const systemDark = ref(false)
  if (typeof window !== 'undefined' && window.matchMedia) {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    systemDark.value = media.matches
    media.addEventListener?.('change', (event) => {
      systemDark.value = event.matches
    })
  }

  const resolvedTheme = computed<'light' | 'dark'>(() =>
    theme.value === 'system' ? (systemDark.value ? 'dark' : 'light') : theme.value,
  )

  function apply(): void {
    if (typeof document === 'undefined') return
    const root = document.documentElement
    root.dataset.theme = resolvedTheme.value
    if (motion.value === 'system') delete root.dataset.motion
    else root.dataset.motion = motion.value
    const meta = document.querySelector('meta[name="theme-color"]')
    meta?.setAttribute('content', resolvedTheme.value === 'dark' ? '#121118' : '#f9f7f3')
  }

  watch([resolvedTheme, motion], apply, { immediate: true })
  watch([theme, motion, sidebarCollapsed, splitRatio], () => {
    write({ theme: theme.value, motion: motion.value, sidebarCollapsed: sidebarCollapsed.value, splitRatio: splitRatio.value })
  }, { deep: true })

  function setTheme(next: ThemePreference): void {
    const update = () => {
      theme.value = next
    }
    // Cross-fade the whole page when the browser supports view transitions.
    const doc = document as Document & { startViewTransition?: (cb: () => void) => unknown }
    if (doc.startViewTransition && motion.value !== 'reduce') doc.startViewTransition(update)
    else update()
  }

  function toggleTheme(): void {
    setTheme(resolvedTheme.value === 'dark' ? 'light' : 'dark')
  }

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function rememberSplit(key: string, ratio: number): void {
    splitRatio.value = { ...splitRatio.value, [key]: Math.round(ratio * 10) / 10 }
  }

  return { theme, motion, sidebarCollapsed, splitRatio, resolvedTheme, setTheme, toggleTheme, toggleSidebar, rememberSplit }
})
