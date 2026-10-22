/** 会员与兑换码 (docs/phase2/02). Shapes mirror MembershipService. */

export type PlanCode = 'FREE' | 'PRO'

export type Entitlements = { plan: PlanCode; aiMonthlyUnits: number; activeLinks: number }

export type Price = { code: 'MONTH' | 'QUARTER' | 'YEAR'; label: string; amountFen: number; days: number }

export type PlanCatalog = { free: Entitlements; pro: Entitlements; proPrices: Price[] }

export type MembershipEvent = {
  id: string
  type: 'REDEEMED' | string
  plan: PlanCode | 'BALANCE'
  days: number
  expiresAt?: string | null
  detail?: string | null
  createdAt: string
}

export type Membership = {
  plan: PlanCode
  expiresAt?: string | null
  expiredAt?: string | null
  entitlements: Entitlements
  free: Entitlements
  pro: Entitlements
  activeLinks: number
  events: MembershipEvent[]
}

export type RedeemBatch = {
  id: string
  name: string
  channel?: string | null
  plan: PlanCode | 'BALANCE'
  durationDays: number
  balanceAmount?: number | null
  quantity: number
  redeemed: number
  redeemBefore?: string | null
  revokedAt?: string | null
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'USED_UP'
  createdAt: string
}

export type BatchCreated = { batch: RedeemBatch; codes: string[] }
