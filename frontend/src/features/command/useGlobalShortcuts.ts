/**
 * App-wide keyboard shortcuts (docs/04 §7.4):
 *   mod+K command palette · ? shortcut help · "G then D/R/M/P/I" quick navigation.
 */
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { isEditableTarget, matchesShortcut } from '@/shared/lib/keyboard'
import { useCommandPaletteStore } from '@/stores/commandPalette'
import { useSessionStore } from '@/stores/session'

const GO_TARGETS: Record<string, string> = {
  d: '/dashboard',
  r: '/resumes',
  m: '/job-match',
  p: '/career-planning',
  i: '/mock-interviews',
  l: '/career-library',
  t: '/resume-templates',
  n: '/notifications',
}

export function useGlobalShortcuts(): void {
  const router = useRouter()
  const palette = useCommandPaletteStore()
  const session = useSessionStore()
  let goPending = false
  let goTimer: ReturnType<typeof setTimeout> | undefined

  function onKeydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || event.isComposing) return
    if (matchesShortcut(event, 'mod+k')) {
      if (!session.signedIn) return
      event.preventDefault()
      palette.toggle()
      return
    }
    if (isEditableTarget(event.target) || event.metaKey || event.ctrlKey || event.altKey) return
    if (event.key === '?') {
      event.preventDefault()
      palette.openShortcuts()
      return
    }
    if (!session.signedIn) return
    const key = event.key.toLowerCase()
    if (goPending) {
      goPending = false
      clearTimeout(goTimer)
      const target = GO_TARGETS[key]
      if (target) {
        event.preventDefault()
        void router.push(target)
      }
      return
    }
    if (key === 'g') {
      goPending = true
      goTimer = setTimeout(() => {
        goPending = false
      }, 900)
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onKeydown)
    clearTimeout(goTimer)
  })
}
