import { ref } from 'vue'
import { defineStore } from 'pinia'

const RECENT_KEY = 'jp:command-recent'

function readRecent(): string[] {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(RECENT_KEY) ?? '[]') as unknown
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string').slice(0, 6) : []
  } catch {
    return []
  }
}

export const useCommandPaletteStore = defineStore('commandPalette', () => {
  const open = ref(false)
  const shortcutsOpen = ref(false)
  const recent = ref<string[]>(typeof window === 'undefined' ? [] : readRecent())

  function toggle(): void {
    open.value = !open.value
  }

  function openShortcuts(): void {
    open.value = false
    shortcutsOpen.value = true
  }

  function remember(id: string): void {
    recent.value = [id, ...recent.value.filter(item => item !== id)].slice(0, 6)
    try {
      window.localStorage.setItem(RECENT_KEY, JSON.stringify(recent.value))
    } catch {
      /* ignore unavailable storage */
    }
  }

  return { open, shortcutsOpen, recent, toggle, openShortcuts, remember }
})
