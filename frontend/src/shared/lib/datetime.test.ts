import assert from 'node:assert/strict'
import test from 'node:test'
import { formatRelative, greeting } from './datetime.ts'

const now = new Date('2026-10-06T06:00:00Z') // 14:00 in Shanghai

test('formatRelative uses short human phrases', () => {
  assert.equal(formatRelative('2026-10-06T05:59:40Z', now), '刚刚')
  assert.equal(formatRelative('2026-10-06T05:45:00Z', now), '15 分钟前')
  assert.equal(formatRelative('2026-10-06T01:00:00Z', now), '今天 09:00')
  assert.equal(formatRelative('2026-10-05T01:00:00Z', now), '昨天 09:00')
  assert.equal(formatRelative('2026-10-02T01:00:00Z', now), '4 天前')
  assert.equal(formatRelative(null, now), '—')
})

test('greeting follows Shanghai local time', () => {
  assert.equal(greeting(now), '下午好')
  assert.equal(greeting(new Date('2026-10-06T00:30:00Z')), '早上好')
})
