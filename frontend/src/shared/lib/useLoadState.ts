/**
 * Loading state for a page's primary data (docs/phase2/00 X-1). A failed load is an error, never
 * an empty list: `error` stays set until a retry succeeds, and data from an earlier success is
 * kept so the page can show it with a "could not refresh" notice instead of blanking.
 */
import { onScopeDispose, ref, shallowRef } from 'vue'
import { isAbortError } from './pollTask'

export function useLoadState<T>(loader: (signal: AbortSignal) => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const error = shallowRef<unknown>(null)
  const loading = ref(false)
  /** True once any load succeeded. */
  const loaded = ref(false)
  let controller: AbortController | null = null

  async function load(): Promise<T | null> {
    controller?.abort()
    const current = new AbortController()
    controller = current
    loading.value = true
    try {
      const value = await loader(current.signal)
      if (controller !== current) return data.value
      data.value = value
      error.value = null
      loaded.value = true
      return value
    } catch (reason) {
      if (controller === current && !isAbortError(reason)) error.value = reason
      return null
    } finally {
      if (controller === current) loading.value = false
    }
  }

  onScopeDispose(() => controller?.abort())

  return { data, error, loading, loaded, load }
}

export type LoadState<T> = ReturnType<typeof useLoadState<T>>
