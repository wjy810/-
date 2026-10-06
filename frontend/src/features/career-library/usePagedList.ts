/**
 * First page through useLoadState (error ≠ empty), later pages appended by "加载更多" (X-9).
 * `items` is a plain deep ref so tabs can patch rows in place (upload, processing status).
 */
import { computed, ref, watch, type Ref } from 'vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import type { PageResult } from './types'

export function usePagedList<T extends { id: string }>(fetchPage: (page: number, size: number) => Promise<PageResult<T>>, size = 50) {
  const state = useLoadState(() => fetchPage(0, size))
  const items = ref([]) as Ref<T[]>
  const total = ref(0)
  const loadingMore = ref(false)
  let nextPage = 1

  watch(state.data, (page) => {
    if (!page) return
    items.value = page.items
    total.value = page.total
    nextPage = 1
  }, { flush: 'sync' })

  const hasMore = computed(() => items.value.length < total.value)

  /** Appends the next page; throws so the caller can report the failure without losing loaded rows. */
  async function loadMore(): Promise<void> {
    if (loadingMore.value || !hasMore.value) return
    loadingMore.value = true
    try {
      const page = await fetchPage(nextPage, size)
      const seen = new Set(items.value.map((item) => item.id))
      items.value = [...items.value, ...page.items.filter((item) => !seen.has(item.id))]
      total.value = page.total
      nextPage += 1
    } finally {
      loadingMore.value = false
    }
  }

  return { ...state, items, total, hasMore, loadingMore, loadMore }
}
