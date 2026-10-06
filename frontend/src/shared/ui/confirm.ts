/** Promise-based confirmation dialog: `if (await confirm({...})) doIt()` (docs/04 §6.4). */
import { reactive } from 'vue'

export type ConfirmOptions = {
  title: string
  message?: string
  confirmText?: string
  cancelText?: string
  tone?: 'primary' | 'danger'
  /** Require typing this text before confirming (for irreversible actions). */
  requireText?: string
}

type PendingConfirm = ConfirmOptions & { resolve: (value: boolean) => void }

export const confirmState = reactive<{ current: PendingConfirm | null }>({ current: null })

export function confirm(options: ConfirmOptions): Promise<boolean> {
  confirmState.current?.resolve(false)
  return new Promise((resolve) => {
    confirmState.current = { ...options, resolve }
  })
}

export function settleConfirm(result: boolean): void {
  const current = confirmState.current
  confirmState.current = null
  current?.resolve(result)
}

export function useConfirm(): typeof confirm {
  return confirm
}
