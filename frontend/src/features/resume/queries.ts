import { computed, type MaybeRefOrGetter, toValue } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useSessionStore } from '@/stores/session'
import { listResumes } from './services/resumeApi'

export const resumeKeys = {
  all: ['resumes'] as const,
  list: () => [...resumeKeys.all, 'list'] as const,
  detail: (id: string) => [...resumeKeys.all, 'detail', id] as const,
}

export function useResumeList(enabled: MaybeRefOrGetter<boolean> = true) {
  const session = useSessionStore()
  return useQuery({
    queryKey: resumeKeys.list(),
    queryFn: ({ signal }) => listResumes(signal),
    enabled: computed(() => session.signedIn && !session.isAdmin && toValue(enabled)),
  })
}
