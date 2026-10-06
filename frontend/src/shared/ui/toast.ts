/**
 * Toast API backed by vue-sonner (docs/04 §6.3).
 * Keeps the historical signature: toast.success(message, { duration, dedupeKey, onClose }).
 */
import { onScopeDispose, watch, type Ref } from 'vue'
import { toast as sonner } from 'vue-sonner'

export type ToastTone = 'success' | 'error' | 'warning' | 'info'

export interface ToastOptions {
  /** Milliseconds; 0 keeps the toast until dismissed. */
  duration?: number
  /** Toasts sharing a key replace each other instead of stacking. */
  dedupeKey?: string
  description?: string
  action?: { label: string; onClick: () => void }
  onClose?: () => void
}

const DEFAULT_DURATION: Record<ToastTone, number> = { success: 4000, info: 4000, warning: 6000, error: 6000 }

function show(tone: ToastTone, message: string, options: ToastOptions = {}): string | number | null {
  const text = message.trim()
  if (!text) return null
  const duration = options.duration === 0 ? Number.POSITIVE_INFINITY : options.duration ?? DEFAULT_DURATION[tone]
  return sonner[tone](text, {
    id: options.dedupeKey?.trim() || `${tone}:${text}`,
    duration,
    description: options.description,
    action: options.action ? { label: options.action.label, onClick: () => options.action?.onClick() } : undefined,
    onDismiss: () => options.onClose?.(),
    onAutoClose: () => options.onClose?.(),
  })
}

export const toast = {
  success: (message: string, options?: ToastOptions) => show('success', message, options),
  error: (message: string, options?: ToastOptions) => show('error', message, options),
  warning: (message: string, options?: ToastOptions) => show('warning', message, options),
  info: (message: string, options?: ToastOptions) => show('info', message, options),
  loading: (message: string, options?: Pick<ToastOptions, 'dedupeKey'>) =>
    sonner.loading(message, { id: options?.dedupeKey, duration: Number.POSITIVE_INFINITY }),
  promise: sonner.promise,
  dismiss: (id?: string | number | null) => {
    if (id === null) return
    sonner.dismiss(id ?? undefined)
  },
}

export function clearToasts(): void {
  sonner.dismiss()
}

/** Mirrors a message ref into a toast; clearing the ref dismisses it, dismissing clears the ref. */
export function useToastFeedback(source: Ref<string>, tone: ToastTone, dedupeKey: string): void {
  let activeId: string | number | null = null
  const stop = watch(
    source,
    (message) => {
      if (activeId !== null) {
        const previous = activeId
        activeId = null
        sonner.dismiss(previous)
      }
      const text = message.trim()
      if (!text) return
      activeId = show(tone, text, {
        dedupeKey,
        onClose: () => {
          activeId = null
          if (source.value === message) source.value = ''
        },
      })
    },
    { immediate: true },
  )

  onScopeDispose(() => {
    stop()
    if (activeId !== null) sonner.dismiss(activeId)
  })
}
