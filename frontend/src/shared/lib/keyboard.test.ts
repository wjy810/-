import assert from 'node:assert/strict'
import test from 'node:test'
import { formatShortcut, matchesShortcut } from './keyboard.ts'

test('formatShortcut renders platform specific labels', () => {
  assert.deepEqual(formatShortcut('mod+k', true), ['⌘', 'K'])
  assert.deepEqual(formatShortcut('mod+k', false), ['Ctrl', 'K'])
  assert.deepEqual(formatShortcut(['shift', '?'], false), ['Shift', '?'])
})

test('matchesShortcut respects modifiers', () => {
  const event = (init: Partial<KeyboardEvent>) => ({ key: 'k', code: 'KeyK', metaKey: false, ctrlKey: false, altKey: false, shiftKey: false, ...init }) as KeyboardEvent
  assert.equal(matchesShortcut(event({ metaKey: true }), 'mod+k', true), true)
  assert.equal(matchesShortcut(event({ ctrlKey: true }), 'mod+k', false), true)
  assert.equal(matchesShortcut(event({}), 'mod+k', false), false)
  assert.equal(matchesShortcut(event({ ctrlKey: true, altKey: true }), 'mod+k', false), false)
  assert.equal(matchesShortcut(event({ key: '¬', code: 'Digit2', altKey: true }), 'alt+2', true), true)
})
