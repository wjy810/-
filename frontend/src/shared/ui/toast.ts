import { onScopeDispose, readonly, reactive, watch, type Ref } from 'vue'

export type ToastTone = 'success' | 'error' | 'warning' | 'info'

export interface ToastOptions {
  duration?: number
  dedupeKey?: string
  onClose?: () => void
}

export interface ToastItem {
  id: number
  message: string
  tone: ToastTone
  dedupeKey: string
}

const MAX_VISIBLE_TOASTS = 4
const DEFAULT_TRANSIENT_DURATION = 4_000
const items = reactive<ToastItem[]>([])
const timers = new Map<number, ReturnType<typeof window.setTimeout>>()
const closeCallbacks = new Map<number, (() => void) | undefined>()
let nextId = 1

function defaultDuration(tone: ToastTone): number {
  return tone === 'success' || tone === 'info' ? DEFAULT_TRANSIENT_DURATION : 0
}

function clearTimer(id: number): void {
  const timer = timers.get(id)
  if (timer !== undefined) window.clearTimeout(timer)
  timers.delete(id)
}

function scheduleDismiss(item: ToastItem, duration: number): void {
  clearTimer(item.id)
  if (duration <= 0) return
  timers.set(item.id, window.setTimeout(() => dismiss(item.id), duration))
}

function dismiss(id: number): void {
  const index = items.findIndex(item => item.id === id)
  if (index < 0) return
  clearTimer(id)
  items.splice(index, 1)
  const onClose = closeCallbacks.get(id)
  closeCallbacks.delete(id)
  onClose?.()
}

function show(tone: ToastTone, message: string, options: ToastOptions = {}): number | null {
  const normalizedMessage = message.trim()
  if (!normalizedMessage) return null

  const dedupeKey = options.dedupeKey?.trim() || `${tone}:${normalizedMessage}`
  const duration = options.duration ?? defaultDuration(tone)
  const duplicateIndex = items.findIndex(item => item.dedupeKey === dedupeKey)
  if (duplicateIndex >= 0) {
    const [duplicate] = items.splice(duplicateIndex, 1)
    duplicate.message = normalizedMessage
    duplicate.tone = tone
    items.unshift(duplicate)
    closeCallbacks.set(duplicate.id, options.onClose)
    scheduleDismiss(duplicate, duration)
    return duplicate.id
  }

  const item: ToastItem = { id: nextId++, message: normalizedMessage, tone, dedupeKey }
  items.unshift(item)
  closeCallbacks.set(item.id, options.onClose)
  scheduleDismiss(item, duration)

  while (items.length > MAX_VISIBLE_TOASTS) dismiss(items[items.length - 1].id)
  return item.id
}

export const toastItems = readonly(items)

export const toast = {
  success: (message: string, options?: ToastOptions) => show('success', message, options),
  error: (message: string, options?: ToastOptions) => show('error', message, options),
  warning: (message: string, options?: ToastOptions) => show('warning', message, options),
  info: (message: string, options?: ToastOptions) => show('info', message, options),
  dismiss,
}

export function clearToasts(): void {
  for (const item of [...items]) dismiss(item.id)
}

export function useToastFeedback(source: Ref<string>, tone: ToastTone, dedupeKey: string): void {
  let activeId: number | null = null
  const stop = watch(source, (message) => {
    if (activeId !== null) {
      const previousId = activeId
      activeId = null
      dismiss(previousId)
    }
    const normalizedMessage = message.trim()
    if (!normalizedMessage) return
    const onClose = () => {
      activeId = null
      if (source.value === message) source.value = ''
    }
    activeId = show(tone, normalizedMessage, { dedupeKey, onClose })
  }, { immediate: true })

  onScopeDispose(() => {
    stop()
    if (activeId !== null) dismiss(activeId)
  })
}
