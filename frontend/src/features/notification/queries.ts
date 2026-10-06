import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useSessionStore } from '@/stores/session'
import { fetchUnreadCount } from './services/notificationApi'

export const notificationKeys = {
  all: ['notifications'] as const,
  unread: () => [...notificationKeys.all, 'unread'] as const,
  list: (filter: string, page: number) => [...notificationKeys.all, 'list', filter, page] as const,
}

/** Unread badge count; refreshes every minute and when the window regains focus (docs/01 NTF-04). */
export function useUnreadCount() {
  const session = useSessionStore()
  return useQuery({
    queryKey: notificationKeys.unread(),
    queryFn: fetchUnreadCount,
    enabled: computed(() => session.signedIn),
    refetchInterval: 60_000,
    staleTime: 15_000,
  })
}
