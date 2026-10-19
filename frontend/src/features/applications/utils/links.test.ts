import assert from 'node:assert/strict'
import test from 'node:test'
import { filterLinks, linkState, linkTitle, sortLinks } from './links.ts'
import type { ResumeLink } from '../types'

const now = new Date('2026-10-19T04:00:00Z')
function link(patch: Partial<ResumeLink>): ResumeLink {
  return { id: 'l', token: 't', path: '/r/t', label: '', resumeVersionId: 'v', hideContact: false, status: 'ACTIVE',
    viewCount: 0, visitorCount: 0, readSeconds: 0, createdAt: '2026-10-01T00:00:00Z', ...patch }
}

test('a link says whether it still works and for how long', () => {
  assert.deepEqual(linkState(link({}), now), { label: '长期有效', tone: 'success' })
  assert.equal(linkState(link({ expiresAt: '2026-10-29T04:00:00Z' }), now).label, '10 天后到期')
  assert.deepEqual(linkState(link({ expiresAt: '2026-10-21T03:00:00Z' }), now), { label: '2 天后到期', tone: 'warning' })
  assert.equal(linkState(link({ expiresAt: '2026-10-19T20:00:00Z' }), now).label, '今天到期')
  assert.equal(linkState(link({ status: 'REVOKED' }), now).label, '已撤回')
})

test('general links are those without an application, and active ones come first', () => {
  const general = link({ id: 'g', label: '秋招双选会' })
  const applied = link({ id: 'a', applicationId: 'app', label: '合成科技 · Java', lastViewedAt: '2026-10-18T00:00:00Z' })
  const revoked = link({ id: 'r', status: 'REVOKED', lastViewedAt: '2026-10-19T00:00:00Z' })
  assert.equal(linkTitle(link({})), '通用链接')
  assert.deepEqual(filterLinks([general, applied, revoked], 'general').map(item => item.id), ['g', 'r'])
  assert.deepEqual(filterLinks([general, applied, revoked], 'active').map(item => item.id), ['g', 'a'])
  assert.deepEqual(sortLinks([general, revoked, applied]).map(item => item.id), ['a', 'g', 'r'])
})
