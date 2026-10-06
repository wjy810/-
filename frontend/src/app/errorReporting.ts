/**
 * Last line of defence for errors no page handled: a render error, an unhandled promise rejection
 * or a failed lazy route. The user gets one calm message (with the request id when the server was
 * involved) instead of a silently broken page; the details stay in the console.
 */
import type { App } from 'vue'
import type { Router } from 'vue-router'
import { errorMessage, isApiClientError, isUnauthenticated } from '@/shared/api/types'
import { isAbortError } from '@/shared/lib/pollTask'
import { toast } from '@/shared/ui/toast'

const CHUNK_ERROR = /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS/i
const RELOAD_KEY = 'jobproof:chunk-reload'

/** True when the error is only noise for the user (cancelled request, expired session handled elsewhere). */
function ignorable(reason: unknown): boolean {
  return isAbortError(reason) || isUnauthenticated(reason)
}

export function reportUnexpectedError(reason: unknown, where: string): void {
  if (ignorable(reason)) return
  console.error(`[jobproof] unhandled error in ${where}`, reason)
  if (isApiClientError(reason)) {
    toast.error(errorMessage(reason), {
      dedupeKey: 'unexpected-error',
      description: reason.requestId ? `请求编号 ${reason.requestId}` : undefined,
    })
    return
  }
  toast.error('页面出现了意外错误，当前操作没有完成。', {
    dedupeKey: 'unexpected-error',
    description: '刷新页面后通常可以继续；详细信息已记录在浏览器控制台。',
  })
}

/**
 * A deploy replaces the hashed chunks; a tab opened before it fails to load the next route.
 * Reload once into the target path, and only report the error if that already happened.
 */
function recoverFromStaleChunk(error: unknown, targetPath: string): boolean {
  if (!(error instanceof Error) || !CHUNK_ERROR.test(error.message)) return false
  try {
    if (sessionStorage.getItem(RELOAD_KEY) === targetPath) return false
    sessionStorage.setItem(RELOAD_KEY, targetPath)
  } catch {
    return false
  }
  window.location.assign(targetPath)
  return true
}

export function installErrorReporting(app: App, router: Router): void {
  app.config.errorHandler = (error, _instance, info) => reportUnexpectedError(error, `component (${info})`)
  window.addEventListener('unhandledrejection', (event) => reportUnexpectedError(event.reason, 'promise'))
  router.onError((error, to) => {
    if (!recoverFromStaleChunk(error, to.fullPath)) reportUnexpectedError(error, `route ${to.fullPath}`)
  })
  router.afterEach(() => {
    try { sessionStorage.removeItem(RELOAD_KEY) } catch { /* storage unavailable: nothing to clear */ }
  })
}
