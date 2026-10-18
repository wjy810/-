/**
 * Drafts kept in this browser so typing survives a closed tab: workbench cards, resume design and
 * mock interview answers. They can hold personal details, so signing out removes all of them.
 */
const LOCAL_DRAFT_PREFIXES = ['jp:card-draft:', 'jobproof:ai-resume:design:', 'jobproof:mock-interview:']

export function clearLocalDrafts(storage: Storage | null = browserStorage()): void {
  if (!storage) return
  try {
    const keys: string[] = []
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index)
      if (key && LOCAL_DRAFT_PREFIXES.some(prefix => key.startsWith(prefix))) keys.push(key)
    }
    keys.forEach(key => storage.removeItem(key))
  } catch { /* blocked storage holds nothing to clear */ }
}

function browserStorage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}
