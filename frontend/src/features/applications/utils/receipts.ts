/**
 * What a card says about its resume link: whether anyone opened it, and how recently. The wording
 * stays factual — an opening is a signal, not a promise of an interview.
 */
import { formatRelative } from '../../../shared/lib/datetime.ts'
import type { ApplicationReceipts, ApplicationStage, ApplicationStats, JobApplication, ResumeLink } from '../types'
import { readTime } from '../labels.ts'

export type ReceiptTone = 'fresh' | 'seen' | 'waiting' | 'off' | 'none'
export type Receipt = { tone: ReceiptTone; label: string; detail: string }

const DAY = 24 * 60 * 60 * 1000

/**
 * {@code totals}: an application's receipts across all its links. Without them the current link alone is used
 * (a general link outside any application).
 */
export function receiptOf(link: ResumeLink | null | undefined, now: Date = new Date(),
  totals?: ApplicationReceipts | null): Receipt {
  if (!link) return { tone: 'none', label: '未附简历链接', detail: '生成专属链接后，对方打开时你会收到提醒' }
  const views = totals?.views ?? link.viewCount
  const visitors = totals?.visitors ?? link.visitorCount
  const lastViewedAt = totals ? totals.lastViewedAt : link.lastViewedAt
  if (views > 0 && lastViewedAt) {
    const recent = now.getTime() - new Date(lastViewedAt).getTime() < DAY
    const readers = visitors > 1 ? ` · ${visitors} 位读者` : ''
    const earlier = totals && totals.linkCount > 1 ? `（含之前的 ${totals.linkCount - 1} 条链接）` : ''
    return {
      tone: recent ? 'fresh' : 'seen',
      label: `已打开 ${views} 次${readers}`,
      detail: `最近 ${formatRelative(lastViewedAt, now)} · 累计阅读 ${readTime(totals?.readSeconds ?? link.readSeconds)}${earlier}`,
    }
  }
  if (link.status !== 'ACTIVE') return { tone: 'off', label: link.status === 'EXPIRED' ? '链接已过期' : '链接已撤回', detail: '对方无法再打开这份简历' }
  return { tone: 'waiting', label: '还没有被打开', detail: `链接生成于 ${formatRelative(link.createdAt, now)}` }
}

export type FunnelStep = { key: string; label: string; value: number; rate: number | null; hint: string }

/** 投递 → 简历被打开 → 进入面试 → 拿到 Offer. Rates compare with the step before, when there is one. */
export function funnelOf(stats: ApplicationStats): FunnelStep[] {
  const rate = (value: number, base: number) => (base > 0 ? Math.round((value / base) * 100) : null)
  return [
    { key: 'applied', label: '已投递', value: stats.applied, rate: null, hint: `其中 ${stats.linked} 份附了专属链接` },
    { key: 'opened', label: '简历被打开', value: stats.opened, rate: rate(stats.opened, stats.linked), hint: '按附了链接的投递计算' },
    { key: 'interviews', label: '进入面试', value: stats.interviews, rate: rate(stats.interviews, stats.applied), hint: '按已投递计算' },
    { key: 'offers', label: '拿到 Offer', value: stats.offers, rate: rate(stats.offers, stats.interviews), hint: '按进入面试计算' },
  ]
}

/** Soonest next step first (overdue ones lead), then the most recently touched. */
export function sortForColumn(items: JobApplication[]): JobApplication[] {
  const due = (item: JobApplication) => (item.nextStepAt ? new Date(item.nextStepAt).getTime() : Number.POSITIVE_INFINITY)
  return [...items].sort((left, right) => {
    const leftDue = due(left)
    const rightDue = due(right)
    if (leftDue !== rightDue) return leftDue - rightDue
    return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()
  })
}

/** Same company and role recorded within the last 30 days: probably the same application typed twice. */
export function duplicateOf(items: JobApplication[], company: string, positionTitle: string,
  now: Date = new Date(), ignoreId?: string): JobApplication | null {
  const key = (value: string) => value.replace(/\s+/g, '').toLowerCase()
  const since = now.getTime() - 30 * DAY
  return items.find(item => item.id !== ignoreId && key(item.company) === key(company)
    && key(item.positionTitle) === key(positionTitle) && new Date(item.createdAt).getTime() >= since) ?? null
}

export type ApplicationFilter = { stage: ApplicationStage | 'ALL'; channel: string; overdue: boolean; opened: boolean }
export type ApplicationSort = 'UPDATED' | 'NEXT_STEP' | 'APPLIED' | 'COMPANY'

export function filterApplications(items: JobApplication[], filter: ApplicationFilter, now: Date = new Date()): JobApplication[] {
  return items.filter(item => (filter.stage === 'ALL' || item.stage === filter.stage)
    && (!filter.channel || filter.channel === 'ALL' || item.channel === filter.channel)
    && (!filter.overdue || (item.stage !== 'CLOSED' && Boolean(item.nextStepAt) && new Date(item.nextStepAt!).getTime() < now.getTime()))
    && (!filter.opened || (item.receipts?.views ?? item.link?.viewCount ?? 0) > 0))
}

export function sortApplications(items: JobApplication[], sort: ApplicationSort): JobApplication[] {
  const time = (value?: string | null, fallback = 0) => (value ? new Date(value).getTime() : fallback)
  const sorted = [...items]
  if (sort === 'NEXT_STEP') return sortForColumn(sorted)
  if (sort === 'APPLIED') return sorted.sort((left, right) => time(right.appliedOn) - time(left.appliedOn))
  if (sort === 'COMPANY') return sorted.sort((left, right) => left.company.localeCompare(right.company, 'zh-CN'))
  return sorted.sort((left, right) => time(right.updatedAt) - time(left.updatedAt))
}

export type DueState = { tone: 'overdue' | 'soon' | 'later'; label: string }

const shanghaiDay = (value: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(value)
const shanghaiTime = (value: Date) =>
  new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Shanghai' }).format(value)

export function dueOf(nextStepAt: string | null | undefined, now: Date = new Date()): DueState | null {
  if (!nextStepAt) return null
  const at = new Date(nextStepAt)
  if (Number.isNaN(at.getTime())) return null
  if (at.getTime() < now.getTime()) return { tone: 'overdue', label: `已逾期 · ${formatRelative(nextStepAt, now)}` }
  const days = Math.round((Date.parse(shanghaiDay(at)) - Date.parse(shanghaiDay(now))) / DAY)
  const label = days === 0 ? `今天 ${shanghaiTime(at)}`
    : days === 1 ? `明天 ${shanghaiTime(at)}`
      : days === 2 ? `后天 ${shanghaiTime(at)}`
        : new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', timeZone: 'Asia/Shanghai' }).format(at)
  return { tone: days < 3 ? 'soon' : 'later', label }
}
