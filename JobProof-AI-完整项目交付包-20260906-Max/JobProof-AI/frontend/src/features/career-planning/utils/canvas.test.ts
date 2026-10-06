import assert from 'node:assert/strict'
import test from 'node:test'
import type { CareerCanvas } from '../types'
import { buildCanvasTree, canvasStatusCounts, layoutCanvas, virtualCanvasRange, visibleCanvasNodeIds } from './canvas.ts'

const canvas: CareerCanvas = {
  versionId: 'v2', version: 2, reason: 'TEST', graphHash: 'hash', createdAt: '2026-08-27T00:00:00Z',
  nodes: [
    { logicalNodeId: 'root', type: 'CAREER', status: 'PLANNED', title: 'Java 全栈工程师', detail: {}, sourceRefs: [], x: 0, y: 0, locked: true, sortOrder: 0 },
    { logicalNodeId: 'backend', type: 'DOMAIN', status: 'LEARNING', title: '后端开发', detail: {}, sourceRefs: [], x: 0, y: 0, locked: false, sortOrder: 1 },
    { logicalNodeId: 'java', type: 'SKILL', status: 'MASTERED', title: 'Java 核心', detail: {}, sourceRefs: [], x: 0, y: 0, locked: false, sortOrder: 2 },
    { logicalNodeId: 'spring', type: 'KNOWLEDGE', status: 'LEARNING', title: 'Spring Boot', detail: { summary: '企业应用' }, sourceRefs: [], x: 0, y: 0, locked: false, sortOrder: 3 },
  ],
  relations: [
    { id: 'p1', fromNodeId: 'backend', toNodeId: 'root', type: 'TREE_PARENT' },
    { id: 'p2', fromNodeId: 'java', toNodeId: 'backend', type: 'TREE_PARENT' },
    { id: 'p3', fromNodeId: 'spring', toNodeId: 'backend', type: 'TREE_PARENT' },
    { id: 'd1', fromNodeId: 'java', toNodeId: 'spring', type: 'PREREQUISITE' },
  ],
}

test('builds one deterministic hierarchy shared by graph and mobile list', () => {
  const tree = buildCanvasTree(canvas)
  assert.equal(tree.length, 1)
  assert.equal(tree[0].node.logicalNodeId, 'root')
  assert.deepEqual(tree[0].children[0].children.map(item => item.node.logicalNodeId), ['java', 'spring'])
})

test('lays the career root to the right of deeper ability nodes', () => {
  const layout = new Map(layoutCanvas(canvas).map(item => [item.node.logicalNodeId, item]))
  assert.ok(layout.get('root')!.x > layout.get('backend')!.x)
  assert.ok(layout.get('backend')!.x > layout.get('spring')!.x)
})

test('search keeps the matching node and its complete parent chain', () => {
  assert.deepEqual([...visibleCanvasNodeIds(canvas, 'Spring', 'ALL')].sort(), ['backend', 'root', 'spring'])
})

test('focused path includes descendants and prerequisite context', () => {
  assert.deepEqual([...visibleCanvasNodeIds(canvas, '', 'ALL', 'spring')].sort(), ['backend', 'java', 'root', 'spring'])
})

test('counts all durable node states without inventing progress', () => {
  assert.deepEqual(canvasStatusCounts(canvas), {
    NOT_STARTED: 0, PLANNED: 1, LEARNING: 2, PENDING_VALIDATION: 0, MASTERED: 1, PAUSED: 0,
  })
})

test('virtualizes a large mobile canvas with stable overscan and total height', () => {
  assert.deepEqual(virtualCanvasRange(100, 630, 630, 63, 2), {
    start: 8, end: 22, offsetTop: 504, totalHeight: 6300,
  })
  assert.deepEqual(virtualCanvasRange(3, -20, 800, 63, 6), {
    start: 0, end: 3, offsetTop: 0, totalHeight: 189,
  })
})
