import assert from 'node:assert/strict'
import test from 'node:test'
import type { ApplicationStats, JobApplication, ResumeLink } from '../types.ts'
import { readTime } from '../labels.ts'
import { dueOf, funnelOf, receiptOf, sortForColumn } from './receipts.ts'

const NOW = new Date('2026-10-18T08:00:00Z')

function link(patch: Partial<ResumeLink> = {}): ResumeLink {
  return {
    id: 'l', token: 't', path: '/r/t', label: '合成科技', resumeVersionId: 'v', hideContact: false, status: 'ACTIVE',
    viewCount: 0, visitorCount: 0, readSeconds: 0, createdAt: '2026-10-17T08:00:00Z', ...patch,
  }
}

test('a receipt says whether, how often and how recently the resume was opened', () => {
  assert.equal(receiptOf(null, NOW).tone, 'none')
  assert.equal(receiptOf(link(), NOW).label, '还没有被打开')
  const fresh = receiptOf(link({ viewCount: 3, visitorCount: 2, readSeconds: 95, lastViewedAt: '2026-10-18T07:30:00Z' }), NOW)
  assert.equal(fresh.tone, 'fresh')
  assert.equal(fresh.label, '已打开 3 次 · 2 位读者')
  assert.match(fresh.detail, /30 分钟前 · 累计阅读 1 分 35 秒/)
  assert.equal(receiptOf(link({ viewCount: 1, visitorCount: 1, lastViewedAt: '2026-10-10T07:30:00Z' }), NOW).tone, 'seen')
  // An opened link keeps its receipt after it is revoked; an unopened one says it is off.
  assert.equal(receiptOf(link({ status: 'REVOKED' }), NOW).label, '链接已撤回')
  assert.equal(receiptOf(link({ status: 'REVOKED', viewCount: 1, visitorCount: 1, lastViewedAt: '2026-10-18T07:00:00Z' }), NOW).tone, 'fresh')
})

test('funnel rates compare each step with the one before it, never divide by zero', () => {
  const stats: ApplicationStats = { total: 12, active: 9, applied: 10, linked: 8, opened: 6, interviews: 3, offers: 1, overdue: 0, upcoming: 0 }
  assert.deepEqual(funnelOf(stats).map(step => [step.value, step.rate]), [[10, null], [6, 75], [3, 30], [1, 33]])
  const empty = funnelOf({ ...stats, applied: 0, linked: 0, opened: 0, interviews: 0, offers: 0 })
  assert.deepEqual(empty.map(step => step.rate), [null, null, null, null])
})

test('due steps read as today, tomorrow or a date, and overdue ones say so', () => {
  assert.equal(dueOf('2026-10-18T10:00:00Z', NOW)?.label, '今天 18:00')
  assert.equal(dueOf('2026-10-19T01:30:00Z', NOW)?.label, '明天 09:30')
  assert.equal(dueOf('2026-10-25T01:30:00Z', NOW)?.tone, 'later')
  assert.equal(dueOf('2026-10-18T07:00:00Z', NOW)?.tone, 'overdue')
  assert.equal(dueOf(null, NOW), null)
})

test('cards with the nearest next step come first', () => {
  const card = (id: string, nextStepAt: string | null, updatedAt: string) => ({ id, nextStepAt, updatedAt }) as JobApplication
  const sorted = sortForColumn([
    card('later', null, '2026-10-18T07:00:00Z'),
    card('soon', '2026-10-19T00:00:00Z', '2026-10-01T00:00:00Z'),
    card('recent', null, '2026-10-18T07:59:00Z'),
  ])
  assert.deepEqual(sorted.map(item => item.id), ['soon', 'recent', 'later'])
})

test('reading time is short and human', () => {
  assert.equal(readTime(0), '不到 1 秒')
  assert.equal(readTime(42), '42 秒')
  assert.equal(readTime(95), '1 分 35 秒')
  assert.equal(readTime(1800), '30 分钟')
})
