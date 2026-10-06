import { QueryClient } from '@tanstack/vue-query'
import { isApiClientError } from '@/shared/api/types'

/** Shared TanStack Query client (docs/03 §4.4). */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      refetchOnWindowFocus: true,
      retry: (failureCount, error) => {
        // Never retry client errors (4xx) — they will not fix themselves.
        if (isApiClientError(error) && error.status >= 400 && error.status < 500) return false
        return failureCount < 2
      },
      retryDelay: attempt => Math.min(1000 * 2 ** attempt, 8000),
    },
    mutations: {
      retry: false,
    },
  },
})
