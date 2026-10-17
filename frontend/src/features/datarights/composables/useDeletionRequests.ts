/**
 * Deletion requests (whole account or one item): the recent ones survive a reload, and an open one
 * is followed until it settles so the page never shows a stale status.
 */
import { computed, onScopeDispose, ref, shallowRef } from 'vue'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { isAbortError } from '@/shared/lib/pollTask'
import { isOpenDeletion, isPollingDeletion } from '../labels'
import { fetchDeletion, listDeletions, submitDeletion } from '../services/dataRightsApi'
import type { DeletionView, SubmitDeletionBody } from '../types'

const POLL_MS = 1400
const POLL_LIMIT_MS = 180_000

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('轮询已取消', 'AbortError'))
      return
    }
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    const onAbort = (): void => {
      window.clearTimeout(timer)
      reject(new DOMException('轮询已取消', 'AbortError'))
    }
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

export function useDeletionRequests(onForbidden: (error: unknown) => void) {
  const requests = shallowRef<DeletionView[]>([])
  const loaded = ref(false)
  const loadError = shallowRef<unknown>(null)
  const error = ref('')
  let poll: AbortController | null = null

  const open = computed(() => requests.value.find(item => isOpenDeletion(item.status)) ?? null)
  const accountOpen = computed(() => requests.value.find(item => item.scope !== 'OBJECT' && isOpenDeletion(item.status)) ?? null)

  function upsert(view: DeletionView): void {
    requests.value = [view, ...requests.value.filter(item => item.id !== view.id)]
      .sort((a, b) => String(b.createdAt ?? '').localeCompare(String(a.createdAt ?? '')))
      .slice(0, 10)
  }

  function stopPolling(): void {
    poll?.abort()
    poll = null
  }

  async function follow(id: string): Promise<void> {
    stopPolling()
    const controller = new AbortController()
    poll = controller
    const started = Date.now()
    try {
      let current = await fetchDeletion(id)
      upsert(current)
      while (isPollingDeletion(current.status)) {
        if (Date.now() - started > POLL_LIMIT_MS) {
          error.value = '删除仍在处理中，页面已停止自动刷新，可稍后点“刷新”查看。'
          return
        }
        await wait(POLL_MS, controller.signal)
        current = await fetchDeletion(id)
        upsert(current)
      }
    } catch (reason) {
      if (isAbortError(reason)) return
      if (isForbidden(reason)) {
        onForbidden(reason)
        return
      }
      error.value = errorMessage(reason, '删除进度读取失败')
    }
  }

  /** Loads the recent requests (plus one a notification linked to) and follows any still open. */
  async function load(deletionId?: string): Promise<void> {
    stopPolling()
    error.value = ''
    try {
      requests.value = await listDeletions()
      loadError.value = null
      loaded.value = true
    } catch (reason) {
      if (isForbidden(reason)) {
        onForbidden(reason)
        return
      }
      if (!isAbortError(reason)) loadError.value = reason
    }
    const target = deletionId || requests.value.find(item => isPollingDeletion(item.status))?.id
    if (target) void follow(target)
  }

  async function submit(body: SubmitDeletionBody): Promise<DeletionView> {
    const view = await submitDeletion(body)
    upsert(view)
    if (isPollingDeletion(view.status)) void follow(view.id)
    return view
  }

  async function refresh(id: string): Promise<void> {
    error.value = ''
    try {
      upsert(await fetchDeletion(id))
    } catch (reason) {
      if (isForbidden(reason)) {
        onForbidden(reason)
        return
      }
      error.value = errorMessage(reason, '删除进度读取失败')
    }
  }

  onScopeDispose(stopPolling)

  return { requests, loaded, loadError, error, open, accountOpen, load, submit, refresh, stopPolling }
}

export type DeletionRequests = ReturnType<typeof useDeletionRequests>
