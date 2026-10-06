import { useQuery } from '@tanstack/vue-query'
import { api } from '@/shared/api/client'
import type { WorkspaceOverview } from './types'

export const dashboardKeys = { overview: ['workspace', 'overview'] as const }

export function fetchWorkspaceOverview(): Promise<WorkspaceOverview> {
  return api<WorkspaceOverview>('/api/v1/workspace/overview')
}

export function useWorkspaceOverview() {
  return useQuery({ queryKey: dashboardKeys.overview, queryFn: fetchWorkspaceOverview, staleTime: 15_000 })
}
