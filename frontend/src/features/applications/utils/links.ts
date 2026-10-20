import type { ResumeLink } from '../types'

export type LinkFilter = 'all' | 'active' | 'general'
export type LinkState = { label: string; tone: 'success' | 'warning' | 'neutral' }

const DAY = 24 * 60 * 60 * 1000

/** A link that belongs to no application is a "general" one (job fairs, referral groups, a personal page). */
export const isGeneral = (link: ResumeLink) => !link.applicationId

export function linkTitle(link: ResumeLink): string {
  return link.label?.trim() || (isGeneral(link) ? '通用链接' : '投递链接')
}

export function linkState(link: ResumeLink, now: Date = new Date()): LinkState {
  if (link.status === 'REVOKED') return { label: '已撤回', tone: 'neutral' }
  if (link.status === 'SUSPENDED') return { label: '已停用', tone: 'neutral' }
  if (link.status === 'EXPIRED') return { label: '已过期', tone: 'neutral' }
  if (link.expiresAt) {
    const days = Math.ceil((new Date(link.expiresAt).getTime() - now.getTime()) / DAY)
    if (days <= 3) return { label: days <= 1 ? '今天到期' : `${days} 天后到期`, tone: 'warning' }
    return { label: `${days} 天后到期`, tone: 'success' }
  }
  return { label: '长期有效', tone: 'success' }
}

export function filterLinks(links: ResumeLink[], filter: LinkFilter): ResumeLink[] {
  if (filter === 'active') return links.filter(link => link.status === 'ACTIVE')
  if (filter === 'general') return links.filter(isGeneral)
  return links
}

/** What the owner can still change on a link: everything but a revoked one, which only keeps its receipts. */
export const canAdjust = (link: ResumeLink) => link.status !== 'REVOKED'

/** Only a link that opens is worth previewing or copying; the others show an error page. */
export const opens = (link: ResumeLink) => link.status === 'ACTIVE'

/** Why a link that is off is off, in the owner's words. */
export function offReason(link: ResumeLink): string | null {
  if (link.status === 'SUSPENDED') return '投递已归档，链接已停用，不占有效链接名额。恢复投递后会自动重新启用（名额已满时需手动启用）。'
  if (link.status === 'EXPIRED') return '已过期，对方打不开。重新设置有效期即可启用，地址不变。'
  if (link.status === 'REVOKED') return '已撤回，地址不再可用；打开记录保留。'
  return null
}

/** Active links first, then the most recently opened, then the newest. */
export function sortLinks(links: ResumeLink[]): ResumeLink[] {
  const time = (value?: string | null) => (value ? new Date(value).getTime() : 0)
  return [...links].sort((left, right) => {
    const active = Number(right.status === 'ACTIVE') - Number(left.status === 'ACTIVE')
    if (active) return active
    const opened = time(right.lastViewedAt) - time(left.lastViewedAt)
    if (opened) return opened
    return time(right.createdAt) - time(left.createdAt)
  })
}
