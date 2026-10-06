import assert from 'node:assert/strict'
import test from 'node:test'
import { nextAutoFollowState, scrollEdges, shouldShowReturnToBottom } from './scrollSurface.ts'

test('reports both content edges without exposing browser scrollbar state', () => {
  assert.deepEqual(scrollEdges({ scrollTop: 300, scrollHeight: 1200, clientHeight: 600 }), {
    canScrollUp: true,
    canScrollDown: true,
    bottomDistance: 300,
  })
  assert.deepEqual(scrollEdges({ scrollTop: 600, scrollHeight: 1200, clientHeight: 600 }), {
    canScrollUp: true,
    canScrollDown: false,
    bottomDistance: 0,
  })
})

test('stops following when the user scrolls upward and resumes near the bottom', () => {
  assert.equal(nextAutoFollowState(true, 500, {
    scrollTop: 420,
    scrollHeight: 1400,
    clientHeight: 600,
  }), false)
  assert.equal(nextAutoFollowState(false, 720, {
    scrollTop: 760,
    scrollHeight: 1400,
    clientHeight: 600,
  }), true)
})

test('shows the return control only after the reader leaves the bottom area', () => {
  assert.equal(shouldShowReturnToBottom({ scrollTop: 500, scrollHeight: 1400, clientHeight: 600 }), true)
  assert.equal(shouldShowReturnToBottom({ scrollTop: 590, scrollHeight: 1200, clientHeight: 600 }), false)
})
