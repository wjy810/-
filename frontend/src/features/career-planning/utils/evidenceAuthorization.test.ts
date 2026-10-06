import assert from 'node:assert/strict'
import test from 'node:test'
import type { EvidenceOption } from '../types'
import {
  evidenceSelectionCount,
  hasEvidenceScope,
  hydrateEvidenceScopeSelections,
  setEvidenceScope,
  toEvidenceAuthorizationSelections,
} from './evidenceAuthorization.ts'
import type { EvidenceScopeSelections } from './evidenceAuthorization.ts'

function option(overrides: Partial<EvidenceOption> = {}): EvidenceOption {
  return {
    sourceId: 'record-1', sourceType: 'CAREER_RECORD', sourceVersion: 1,
    title: '后端项目', subtitle: '项目经历', excerpt: '实现结构化接口', strength: 'STRONG',
    selected: false, scopes: [], ...overrides,
  }
}

test('unselected evidence stays fully disabled even when a legacy scope is present', () => {
  const value = hydrateEvidenceScopeSelections([option({ scopes: ['RECOMMENDATION'] })])
  assert.deepEqual(value, { 'record-1': [] })
  assert.equal(evidenceSelectionCount(value), 0)
})

test('active evidence restores only valid scopes in the stable product order', () => {
  const value = hydrateEvidenceScopeSelections([option({
    selected: true,
    scopes: ['VALIDATION', 'PROFILE_INTERVIEW', 'INVALID', 'VALIDATION'],
  })])
  assert.deepEqual(value, { 'record-1': ['PROFILE_INTERVIEW', 'VALIDATION'] })
})

test('scope decisions are immutable and empty evidence is omitted from authorization payloads', () => {
  const initial: EvidenceScopeSelections = { 'record-1': [], 'record-2': ['CANVAS'] }
  const selected = setEvidenceScope(initial, 'record-1', 'PLAN', true)
  assert.deepEqual(initial, { 'record-1': [], 'record-2': ['CANVAS'] })
  assert.equal(hasEvidenceScope(selected, 'record-1', 'PLAN'), true)
  assert.deepEqual(toEvidenceAuthorizationSelections(selected), [
    { sourceId: 'record-1', scopes: ['PLAN'] },
    { sourceId: 'record-2', scopes: ['CANVAS'] },
  ])
})
