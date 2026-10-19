import { api } from '@/shared/api/client'
import type { OperationsOverview } from '../types'

export const fetchOperationsOverview = () => api<OperationsOverview>('/api/v1/admin/overview')
