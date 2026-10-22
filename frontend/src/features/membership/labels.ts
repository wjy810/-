import type { Entitlements, Price } from './types'

/** 1900 → "19"; 16800 → "168"; 4950 → "49.5". */
export function yuan(fen: number): string {
  const value = fen / 100
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

/** What a price works out to per month, for comparing the plans. */
export function perMonth(price: Price): string {
  return yuan(Math.round((price.amountFen / price.days) * 30))
}

/** The part of a price saved against paying month by month, in whole percent. */
export function saving(price: Price, monthly: Price | undefined): number {
  if (!monthly || price.code === monthly.code) return 0
  const full = (monthly.amountFen / monthly.days) * price.days
  return Math.max(0, Math.round((1 - price.amountFen / full) * 100))
}

export function planLabel(plan?: string | null): string {
  return plan === 'PRO' ? '专业版' : '免费版'
}

export type Benefit = { key: string; label: string; free: string; pro: string }

/** The rows the plan comparison shows; numbers come from the server, not from copy. */
export function benefits(free: Entitlements, pro: Entitlements): Benefit[] {
  return [
    { key: 'ai', label: '每月 AI 次数', free: `${free.aiMonthlyUnits} 次`, pro: `${pro.aiMonthlyUnits} 次` },
    { key: 'links', label: '同时有效的专属简历链接', free: `${free.activeLinks} 条`, pro: `${pro.activeLinks} 条` },
    { key: 'receipts', label: '简历查看回执（打开、读者、设备、阅读时长）', free: '包含', pro: '包含' },
    { key: 'tracking', label: '投递看板、漏斗与下一步提醒', free: '包含', pro: '包含' },
    { key: 'templates', label: '全部简历模板与 PDF 导出', free: '包含', pro: '包含' },
    { key: 'privacy', label: '隐藏联系方式、链接有效期与撤回', free: '包含', pro: '包含' },
  ]
}

/**
 * Typing help for the 16 characters after the fixed "JP-" label, grouped as XXXX-XXXX-XXXX-XXXX.
 * A typed or pasted "JP" prefix is dropped when it is clearly the prefix (followed by a separator, or
 * the input is longer than a code); a code whose own characters start with JP is kept intact.
 */
export function formatCodeInput(input: string): string {
  const prefixed = /^\s*JP[\s\-_]/i.test(input)
  let compact = input.toUpperCase().replace(/[^0-9A-Z]/g, '')
  if (compact.startsWith('JP') && (prefixed || compact.length > 16)) compact = compact.slice(2)
  return (compact.slice(0, 16).match(/.{1,4}/g) ?? []).join('-')
}

export function isCompleteCode(body: string): boolean {
  return /^[0-9A-Z]{4}(-[0-9A-Z]{4}){3}$/.test(body)
}

/** The code as issued: "JP-" and the four groups. */
export function fullCode(body: string): string {
  return `JP-${body}`
}

export const BATCH_STATUS: Record<string, { label: string; tone: 'success' | 'neutral' | 'warning' | 'danger' }> = {
  ACTIVE: { label: '可兑换', tone: 'success' },
  USED_UP: { label: '已兑完', tone: 'neutral' },
  EXPIRED: { label: '已过截止日', tone: 'warning' },
  REVOKED: { label: '已停用', tone: 'danger' },
}

export function batchStatus(status: string): { label: string; tone: 'success' | 'neutral' | 'warning' | 'danger' } {
  return BATCH_STATUS[status] ?? { label: status, tone: 'neutral' }
}

/** Redeemed share of a batch in whole percent. */
export function redemptionRate(redeemed: number, quantity: number): number {
  return quantity > 0 ? Math.min(100, Math.round((redeemed / quantity) * 100)) : 0
}

/** "2026-11-30" (a picked day) → the last millisecond of that local day, as the instant the server stores. */
export function endOfDayIso(day: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day)
  if (!match) return null
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 23, 59, 59, 999).toISOString()
}

/** A spreadsheet cell: quoted, and never read as a formula when it starts with = + - @ or a control character. */
function csvCell(value: string): string {
  const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value
  return `"${safe.replace(/"/g, '""')}"`
}

/** The codes of a freshly created batch as CSV (with a BOM so Excel opens the Chinese header correctly). */
export function codesCsv(batch: { name: string; channel?: string | null; durationDays: number; balanceAmount?: number | null }, codes: string[]): string {
  const header = ['兑换码', '批次', '渠道', batch.balanceAmount != null ? '人民币面额' : '专业版天数'].map(csvCell).join(',')
  const rows = codes.map(code => [code, batch.name, batch.channel ?? '', String(batch.balanceAmount ?? batch.durationDays)].map(csvCell).join(','))
  return `﻿${[header, ...rows].join('\r\n')}\r\n`
}

/** The upgrade nudge for a plan-limit refusal; numbers come from the account's own membership. */
export function planLimitCopy(reason: string, free: Entitlements, pro: Entitlements): { title: string; description: string } {
  if (reason === 'RESUME_LINK_LIMIT') {
    return {
      title: `免费版最多同时保留 ${free.activeLinks} 条有效链接`,
      description: `专业版可同时保留 ${pro.activeLinks} 条；也可以先撤回不再需要的链接。`,
    }
  }
  return {
    title: '本月的免费 AI 次数用完了',
    description: `专业版每月 ${pro.aiMonthlyUnits} 次，开通当月立即补足；免费次数下个月自动重置。`,
  }
}
