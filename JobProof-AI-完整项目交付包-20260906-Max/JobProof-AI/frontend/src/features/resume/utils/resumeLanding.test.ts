import assert from 'node:assert/strict'
import test from 'node:test'
import { recentActiveResume, resumeLandingPath } from './resumeLanding.ts'

test('resume landing opens the most recently updated active resume', () => {
  const recent = recentActiveResume([
    { id: 'older', status: 'DRAFT', version: 1, updatedAt: '2026-08-20T10:00:00Z' },
    { id: 'archived-newest', status: 'ARCHIVED', version: 2, updatedAt: '2026-08-26T10:00:00Z' },
    { id: 'newer', status: 'READY_TO_EXPORT', version: 3, updatedAt: '2026-08-25T10:00:00Z' },
  ])
  assert.equal(recent?.id, 'newer')
  assert.equal(resumeLandingPath(recent ? [recent] : []), '/resumes/newer')
})

test('resume landing starts AI creation when no active resume exists', () => {
  assert.equal(resumeLandingPath([]), '/ai-resume/new')
  assert.equal(resumeLandingPath([
    { id: 'archived', status: 'ARCHIVED', version: 1, updatedAt: '2026-08-26T10:00:00Z' },
  ]), '/ai-resume/new')
})
