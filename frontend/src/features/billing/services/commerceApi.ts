import { api } from '@/shared/api/client'
export type Price = { code: string; name: string; amount: number | null; currency: string; days: number; enabled: boolean; version: number }
export type Shop = { enabled: boolean; url: string; name: string; description: string; version: number }
export type Wallet = { availableBalance: number; heldBalance: number; currency: string }
export type Subscription = { status: string; currentPeriodEnd: string | null; autoRenew: boolean }
export type History = { content: Record<string, unknown>[]; totalElements: number; page: number; size: number }
export const catalog = () => api<{prices: Price[];shop: Shop}>('/api/v1/commerce/catalog')
export const wallet = () => api<Wallet>('/api/v1/commerce/wallet')
export type RedemptionReceipt = { kind: string; amount: number; balance: number; previouslyRedeemed: boolean }
export const redeem = (code: string) => api<RedemptionReceipt>('/api/v1/commerce/redeem', { method: 'POST', body: JSON.stringify({ code }) })
export const subscription = () => api<Subscription>('/api/v1/commerce/subscription')
export const purchase = (p: Price, requestId: string) => api('/api/v1/commerce/purchase', {method:'POST',body:JSON.stringify({code:p.code,priceVersion:p.version,requestId})})
export const history = (query: URLSearchParams, admin = false) => api<History>(`/api/v1/commerce/${admin ? 'admin/' : ''}history?${query}`)
export const savePrice = (p: Price) => api<Price>('/api/v1/commerce/admin/prices',{method:'PUT',body:JSON.stringify(p)})
export const saveShop = (s: Shop) => api<Shop>('/api/v1/commerce/admin/shop',{method:'PUT',body:JSON.stringify(s)})
export const account = (id: string) => api<{wallet:Wallet;subscription:Subscription}>(`/api/v1/commerce/admin/account/${encodeURIComponent(id)}`)
