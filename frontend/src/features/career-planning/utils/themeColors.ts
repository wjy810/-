/**
 * Concrete colours from design tokens for canvas parts that only accept colour strings: SVG
 * presentation attributes (minimap, background pattern) and Web Animations keyframes.
 */
import { onBeforeUnmount, onMounted, shallowRef } from 'vue'

export function readThemeColor(token: string): string {
  if (typeof document === 'undefined' || typeof getComputedStyle !== 'function') return 'currentColor'
  return getComputedStyle(document.documentElement).getPropertyValue(token).trim() || 'currentColor'
}

/** Token colours that are read again whenever the theme on <html> changes. */
export function useThemeColors<T extends Record<string, string>>(tokens: T) {
  const read = () => Object.fromEntries(
    Object.entries(tokens).map(([key, token]) => [key, readThemeColor(token)]),
  ) as { [K in keyof T]: string }
  const colors = shallowRef(read())
  let observer: MutationObserver | null = null

  onMounted(() => {
    colors.value = read()
    if (typeof MutationObserver === 'undefined') return
    observer = new MutationObserver(() => { colors.value = read() })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class', 'style'] })
  })
  onBeforeUnmount(() => observer?.disconnect())

  return colors
}
