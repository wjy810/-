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
