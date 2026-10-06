import assert from 'node:assert/strict'
import test from 'node:test'
import { applyPendingChangeOverlay, shouldOverlayCardPayload } from './changeSetOverlay.ts'
import type { AiResumeChangeSet } from '../types.ts'

function set(items: AiResumeChangeSet['items']): AiResumeChangeSet {
  return { id: 'set-1', messageId: 'message-1', branchId: 'branch-1', baseRevisionId: 'revision-1',
    status: 'PENDING', actionCode: 'RESUME_CHANGE', qualityPolicyVersion: 'resume-writing-v1', version: 0,
    items, createdAt: '', updatedAt: '' }
}

function item(overrides: Partial<AiResumeChangeSet['items'][number]>): AiResumeChangeSet['items'][number] {
  return { id: crypto.randomUUID(), sequence: 1, module: 'EXPERIENCE',
    targetPath: '/experiences/0/description', operation: 'APPEND_SEGMENT', beforeValue: '', proposedValue: '',
    reason: '', sourceFacts: [], factStatus: 'SUPPORTED', quality: {}, status: 'PENDING', version: 0,
    createdAt: '', updatedAt: '', ...overrides }
}

test('pending changes overlay independently without mutating confirmed content', () => {
  const confirmed = { summary: '旧简介', experiences: [{ description: '• 旧要点' }] }
  const overlay = applyPendingChangeOverlay(confirmed, [set([
    item({ operation: 'REPLACE_SEGMENT', beforeValue: '旧要点', proposedValue: '新要点' }),
    item({ sequence: 2, operation: 'APPEND_SEGMENT', proposedValue: '补充要点' }),
  ])], 'branch-1')
  assert.equal((overlay.experiences as Array<{ description: string }>)[0].description, '• 新要点\n• 补充要点')
  assert.equal(confirmed.experiences[0].description, '• 旧要点')
})

test('decided and other-branch changes do not enter the overlay', () => {
  const confirmed = { summary: '旧简介' }
  const applied = item({ module: 'SUMMARY', targetPath: '/summary', operation: 'REPLACE_TEXT',
    beforeValue: '旧简介', proposedValue: '新简介', status: 'APPLIED' })
  assert.equal(applyPendingChangeOverlay(confirmed, [set([applied])], 'branch-1').summary, '旧简介')
  assert.equal(applyPendingChangeOverlay(confirmed, [{ ...set([applied]), branchId: 'branch-2' }], 'branch-1').summary, '旧简介')
})

test('only local or persisted drafts override confirmed resume content', () => {
  assert.equal(shouldOverlayCardPayload('PENDING', false), false)
  assert.equal(shouldOverlayCardPayload('CONFIRMED', false), false)
  assert.equal(shouldOverlayCardPayload('SKIPPED', false), false)
  assert.equal(shouldOverlayCardPayload('EDITING', false), true)
  assert.equal(shouldOverlayCardPayload('CONFIRMED', true), true)
})
