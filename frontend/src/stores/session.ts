/** Signed-in account state (docs/03 §4.4). Server data stays in TanStack Query; this is identity only. */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { errorMessage, isUnauthenticated } from '@/shared/api/types'
import { fetchMe, logoutAccount } from '@/features/identity/services/authApi'
import type { AccountView } from '@/features/identity/types'
import { queryClient } from '@/app/queryClient'

export const useSessionStore = defineStore('session', () => {
  const account = ref<AccountView | null>(null)
  const ready = ref(false)
  const bootError = ref<string | null>(null)
  let inflight: Promise<void> | null = null

  const signedIn = computed(() => account.value !== null)
  const isAdmin = computed(() => account.value?.role === 'ADMIN')
  const displayName = computed(() => account.value?.displayIdentifier || account.value?.email || '')

  async function hydrate(): Promise<void> {
    if (inflight) return inflight
    inflight = (async () => {
      try {
        account.value = await fetchMe()
        bootError.value = null
      } catch (error) {
        account.value = null
        bootError.value = isUnauthenticated(error) ? null : errorMessage(error)
      } finally {
        ready.value = true
      }
    })()
    try {
      await inflight
    } finally {
      inflight = null
    }
  }

  async function retryBoot(): Promise<void> {
    ready.value = false
    await hydrate()
  }

  function remember(next: AccountView | null): void {
    account.value = next
    bootError.value = null
  }

  /** Drops identity and every cached server response (another user may sign in next). */
  function clear(): void {
    account.value = null
    queryClient.clear()
  }

  async function logout(): Promise<void> {
    try {
      await logoutAccount()
    } finally {
      clear()
    }
  }

  return { account, ready, bootError, signedIn, isAdmin, displayName, hydrate, retryBoot, remember, clear, logout }
})
