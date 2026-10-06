/**
 * v-reveal: fades/rises an element in when it scrolls into view (docs/04 §8).
 * Respects reduced motion by revealing immediately.
 */
import type { Directive } from 'vue'

let observer: IntersectionObserver | null = null

function reduced(): boolean {
  return document.documentElement.dataset.motion === 'reduce'
    || (document.documentElement.dataset.motion !== 'full' && window.matchMedia('(prefers-reduced-motion: reduce)').matches)
}

function ensureObserver(): IntersectionObserver {
  observer ??= new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue
      entry.target.classList.add('is-revealed')
      observer?.unobserve(entry.target)
    }
  }, { threshold: 0.12, rootMargin: '0px 0px -40px' })
  return observer
}

export const vReveal: Directive<HTMLElement, number | undefined> = {
  mounted(el, binding) {
    el.classList.add('reveal')
    if (binding.value) el.style.setProperty('--reveal-delay', `${binding.value}ms`)
    if (typeof IntersectionObserver === 'undefined' || reduced()) {
      el.classList.add('is-revealed')
      return
    }
    ensureObserver().observe(el)
  },
  unmounted(el) {
    observer?.unobserve(el)
  },
}
