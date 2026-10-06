/** Keyboard helpers shared by shortcuts, tooltips and the command palette. */

export function isMacPlatform(): boolean {
  if (typeof navigator === 'undefined') return false
  const platform = (navigator as Navigator & { userAgentData?: { platform?: string } }).userAgentData?.platform
    ?? navigator.platform
    ?? navigator.userAgent
  return /mac|iphone|ipad|ipod/i.test(platform)
}

const MAC_SYMBOLS: Record<string, string> = { mod: '⌘', alt: '⌥', shift: '⇧', ctrl: '⌃', enter: '↵', esc: 'Esc' }
const PC_NAMES: Record<string, string> = { mod: 'Ctrl', alt: 'Alt', shift: 'Shift', ctrl: 'Ctrl', enter: 'Enter', esc: 'Esc' }

/** `"mod+k"` → `["⌘", "K"]` on macOS, `["Ctrl", "K"]` elsewhere. */
export function formatShortcut(keys: string | string[], mac = isMacPlatform()): string[] {
  const parts = Array.isArray(keys) ? keys : keys.split('+')
  const table = mac ? MAC_SYMBOLS : PC_NAMES
  return parts.map((part) => {
    const key = part.trim().toLowerCase()
    return table[key] ?? (key.length === 1 ? key.toUpperCase() : part.trim())
  })
}

/** True when the event originates from a text-editing element. */
export function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  if (target.isContentEditable) return true
  const tag = target.tagName
  if (tag === 'TEXTAREA' || tag === 'SELECT') return true
  if (tag !== 'INPUT') return false
  const type = (target as HTMLInputElement).type
  return !['button', 'checkbox', 'radio', 'submit', 'reset', 'range', 'color', 'file'].includes(type)
}

/** Matches an event against `"mod+k"`, `"shift+?"`, `"escape"` style descriptors. */
export function matchesShortcut(event: KeyboardEvent, descriptor: string, mac = isMacPlatform()): boolean {
  const parts = descriptor.toLowerCase().split('+').map(part => part.trim())
  const key = parts[parts.length - 1]
  const wantMod = parts.includes('mod')
  const wantShift = parts.includes('shift')
  const wantAlt = parts.includes('alt')
  const modPressed = mac ? event.metaKey : event.ctrlKey
  if (wantMod !== modPressed) return false
  if (wantAlt !== event.altKey) return false
  if (wantShift && !event.shiftKey) return false
  const pressed = event.key.toLowerCase()
  if (key === pressed) return true
  // Layout-independent digits/letters when Alt changes event.key (macOS).
  if (event.code === `Key${key.toUpperCase()}` || event.code === `Digit${key}`) return true
  return false
}
