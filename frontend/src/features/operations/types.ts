/** 运营概览 (docs/phase2/04). Shapes mirror OperationsOverviewService. */
export type OverviewUsers = { total: number; new7: number; new30: number; active7: number; active30: number }
export type OverviewMembership = { pro: number; proShare?: number | null; redeemed30: number; codesIssued: number; codesRedeemed: number; activeBatches: number }
export type OverviewModule = { key: string; label: string; total: number; last7: number }
export type OverviewAi = { period: string; usedUnits: number; accounts: number }
export type FunnelStep = { key: string; label: string; accounts: number; rate?: number | null }
export type OverviewDay = { day: string; signups: number; applications: number; opens: number }

export type OperationsOverview = {
  generatedAt: string
  users: OverviewUsers
  membership: OverviewMembership
  modules: OverviewModule[]
  ai: OverviewAi
  funnel: FunnelStep[]
  days: OverviewDay[]
}
