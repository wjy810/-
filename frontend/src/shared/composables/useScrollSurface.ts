/**
 * Scroll container state: edge shadows, "return to bottom" and stick-to-bottom following
 * that pauses as soon as the reader scrolls up (chat threads, previews, drawers).
 */
import { onBeforeUnmount, onMounted, reactive, ref, type Ref } from 'vue'
import { usePreferredReducedMotion } from '@vueuse/core'
import { nextAutoFollowState, scrollEdges, shouldShowReturnToBottom, type ScrollMetrics } from '@/shared/lib/scrollSurface'

export function useScrollSurface(element: Ref<HTMLElement | null>) {
  const edges = reactive({ canScrollUp: false, canScrollDown: false })
  const following = ref(true)
  const showReturn = ref(false)
  const reducedMotion = usePreferredReducedMotion()
  let previousTop = 0
  let frame = 0
  let scrollFrame = 0
  let mutations: MutationObserver | null = null
  let resizes: ResizeObserver | null = null

  function metrics(target: HTMLElement): ScrollMetrics {
    return { scrollTop: target.scrollTop, scrollHeight: target.scrollHeight, clientHeight: target.clientHeight }
  }

  function measure(): void {
    frame = 0
    const target = element.value
    if (!target) {
      edges.canScrollUp = false
      edges.canScrollDown = false
      showReturn.value = false
      return
    }
    const current = metrics(target)
    following.value = nextAutoFollowState(following.value, previousTop, current)
    previousTop = target.scrollTop
    const next = scrollEdges(current)
    edges.canScrollUp = next.canScrollUp
    edges.canScrollDown = next.canScrollDown
    showReturn.value = shouldShowReturnToBottom(current)
  }

  function refresh(): void {
    if (!frame && typeof window !== 'undefined') frame = window.requestAnimationFrame(measure)
  }

  function scrollToBottom(smooth = true): void {
    const target = element.value
    if (!target) return
    following.value = true
    target.scrollTo({ top: target.scrollHeight, behavior: smooth && reducedMotion.value !== 'reduce' ? 'smooth' : 'auto' })
    refresh()
  }

  /** `force` scrolls even when the reader scrolled away; otherwise only while following. */
  function requestBottom(smooth = false, force = true): void {
    if (!force && !following.value) {
      refresh()
      return
    }
    if (scrollFrame) return
    scrollFrame = window.requestAnimationFrame(() => {
      scrollFrame = 0
      scrollToBottom(smooth)
    })
  }

  onMounted(() => {
    const target = element.value
    if (!target) return
    mutations = new MutationObserver(refresh)
    mutations.observe(target, { childList: true, subtree: true, characterData: true })
    if (typeof ResizeObserver !== 'undefined') {
      resizes = new ResizeObserver(refresh)
      resizes.observe(target)
    }
    refresh()
  })

  onBeforeUnmount(() => {
    mutations?.disconnect()
    resizes?.disconnect()
    window.cancelAnimationFrame(frame)
    window.cancelAnimationFrame(scrollFrame)
  })

  return { edges, following, showReturn, onScroll: measure, refresh, scrollToBottom, requestBottom }
}
