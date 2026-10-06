import { computed, ref } from 'vue'
import { setUnauthenticatedHandler } from '@/shared/api/client'
import { errorMessage, isUnauthenticated } from '@/shared/api/types'
import { fetchMe } from './services/authApi'
import type { AccountView } from './types'

const account = ref<AccountView | null>(null)
const ready = ref(false)
const bootError = ref<string | null>(null)
let hydrateInFlight: Promise<void> | null = null

export const session = {
  account,
  ready,
  bootError,
  signedIn: computed(() => account.value !== null),
}

export function rememberAccount(next: AccountView | null): void {
  account.value = next
  bootError.value = null
}

export function clearSession(): void {
  account.value = null
}

export async function hydrateSession(): Promise<void> {
  if (hydrateInFlight) {
    return hydrateInFlight
  }
  hydrateInFlight = doHydrate()
  try {
    await hydrateInFlight
  } finally {
    hydrateInFlight = null
  }
}

async function doHydrate(): Promise<void> {
  try {
    account.value = await fetchMe()
    bootError.value = null
  } catch (error) {
    account.value = null
    if (!isUnauthenticated(error)) {
      bootError.value = errorMessage(error)
    } else {
      bootError.value = null
    }
  } finally {
    ready.value = true
  }
}

export function bindSessionExpiry(onExpired: () => void): void {
  setUnauthenticatedHandler(() => {
    if (!account.value) {
      return
    }
    account.value = null
    onExpired()
  })
}
