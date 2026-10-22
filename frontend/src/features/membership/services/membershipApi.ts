import { api } from '@/shared/api/client'
import type { BatchCreated, Membership, PlanCatalog, RedeemBatch } from '../types'

export const fetchMembership = () => api<Membership>('/api/v1/membership')
export const fetchPlanCatalog = () => api<PlanCatalog>('/api/v1/membership/plans')
export const redeemCode = (code: string) =>
  api<Membership>('/api/v1/membership/redeem', { method: 'POST', body: JSON.stringify({ code }) })

export const fetchRedeemBatches = () => api<RedeemBatch[]>('/api/v1/admin/redeem-codes')
export const createRedeemBatch = (command: { name: string; channel?: string | null; balanceAmount: number; quantity: number; redeemBefore?: string | null }) =>
  api<BatchCreated>('/api/v1/admin/redeem-codes', { method: 'POST', body: JSON.stringify(command) })
export const revokeRedeemBatch = (id: string) =>
  api<RedeemBatch>(`/api/v1/admin/redeem-codes/${encodeURIComponent(id)}/revoke`, { method: 'POST' })
