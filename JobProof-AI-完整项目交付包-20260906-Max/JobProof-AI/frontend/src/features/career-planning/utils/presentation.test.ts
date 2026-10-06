import assert from 'node:assert/strict'
import test from 'node:test'
import type { CareerCanvas } from '../types'
import {
  careerCanvasNodeStatusLabel, careerCanvasNodeTypeLabel, careerVersionAuthorLabel, careerVersionReasonLabel,
  hasActionablePlanNodes, hasCanvasContent, initialCareerWorkbenchView, isCareerExecutionPhase,
} from './presentation.ts'

function canvas(nodes: CareerCanvas['nodes']): CareerCanvas {
  return { versionId: 'v1', version: 1, reason: 'TEST', graphHash: 'hash', nodes, relations: [], createdAt: '2026-08-27T00:00:00Z' }
}

const root = { logicalNodeId: 'root', type: 'CAREER' as const, status: 'PLANNED' as const, title: '后端开发', detail: {}, sourceRefs: [], x: 0, y: 0, locked: true, sortOrder: 0 }

test('root-only canvas cannot open optimization or plan creation', () => {
  assert.equal(hasCanvasContent(canvas([root])), false)
  assert.equal(hasActionablePlanNodes(canvas([root])), false)
})

test('plan creation requires an unfinished skill, knowledge or task node', () => {
  const domain = { ...root, logicalNodeId: 'domain', type: 'DOMAIN' as const, locked: false }
  const skill = { ...root, logicalNodeId: 'skill', type: 'SKILL' as const, status: 'LEARNING' as const, locked: false }
  assert.equal(hasCanvasContent(canvas([root, domain])), true)
  assert.equal(hasActionablePlanNodes(canvas([root, domain])), false)
  assert.equal(hasActionablePlanNodes(canvas([root, domain, skill])), true)
  assert.equal(hasActionablePlanNodes(canvas([root, { ...skill, status: 'MASTERED' as const }])), false)
})

test('canvas, plan and validation phases all stay inside the execution workbench', () => {
  for (const phase of ['CANVAS', 'PLAN', 'VALIDATION']) assert.equal(isCareerExecutionPhase(phase), true)
  for (const phase of ['PROFILE', 'RECOMMENDATIONS', 'WELCOME']) assert.equal(isCareerExecutionPhase(phase), false)
  assert.equal(initialCareerWorkbenchView('CANVAS'), 'CANVAS')
  assert.equal(initialCareerWorkbenchView('PLAN'), 'PLAN')
  assert.equal(initialCareerWorkbenchView('VALIDATION'), 'VALIDATION')
})

test('version metadata never leaks internal enum values', () => {
  assert.equal(careerVersionReasonLabel('GOAL_CONFIRMED'), '目标确认')
  assert.equal(careerVersionReasonLabel('UNKNOWN_INTERNAL_REASON'), '版本更新')
  assert.equal(careerVersionAuthorLabel('USER'), '用户操作')
  assert.equal(careerVersionAuthorLabel('UNKNOWN_ACTOR'), '系统')
  assert.equal(careerCanvasNodeStatusLabel('NOT_STARTED'), '未开始')
  assert.equal(careerCanvasNodeStatusLabel('UNKNOWN_INTERNAL_STATUS'), '状态未知')
  assert.equal(careerCanvasNodeTypeLabel('DOMAIN'), '能力域')
  assert.equal(careerCanvasNodeTypeLabel('EVIDENCE'), '成果证据')
  assert.equal(careerCanvasNodeTypeLabel('UNKNOWN_INTERNAL_TYPE'), '能力节点')
})
