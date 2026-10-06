import assert from 'node:assert/strict'
import test from 'node:test'
import { paginate, type MeasuredBlock } from './paginate.ts'

const block = (id: string, height: number, region = 'main', section = id.split(':')[0]!): MeasuredBlock => ({ id, height, region, section })
const cap = (first: number, next = first, region = 'main') => ({ first: { [region]: first }, next: { [region]: next } })

test('fills a page exactly and starts the next one only when needed', () => {
  const result = paginate([block('a:1', 300), block('a:2', 300), block('b:1', 400), block('b:2', 10)], cap(1000), 2)
  assert.equal(result.pageCount, 2)
  assert.deepEqual(result.pages[0]!.main, ['a:1', 'a:2', 'b:1'])
  assert.deepEqual(result.pages[1]!.main, ['b:2'])
  assert.equal(result.overflowPx, 0)
})

test('uses the smaller first-page capacity left over by the header', () => {
  const result = paginate([block('a:1', 500), block('a:2', 500)], cap(800, 1100), 2)
  assert.deepEqual(result.pages.map(page => page.main), [['a:1'], ['a:2']])
})

test('reports content beyond the page limit and where it starts', () => {
  const result = paginate([block('summary:text', 900), block('experience:1', 600), block('projects:1', 200)], cap(1000), 1)
  assert.equal(result.pageCount, 2)
  assert.equal(result.overflowPx, 800)
  assert.equal(result.firstOverflowSection, 'experience')
})

test('a block taller than a page is placed alone and flagged', () => {
  const result = paginate([block('a:1', 100), block('a:2', 1500), block('a:3', 100)], cap(1000), 3)
  assert.deepEqual(result.pages.map(page => page.main), [['a:1'], ['a:2'], ['a:3']])
  assert.deepEqual(result.oversize, ['a:2'])
})

test('regions flow independently and the page count is the longest region', () => {
  const blocks = [block('s:1', 900, 'side'), block('m:1', 600, 'main'), block('m:2', 600, 'main'), block('m:3', 600, 'main')]
  const result = paginate(blocks, { first: { main: 1000, side: 1000 }, next: { main: 1300, side: 1300 } }, 2)
  assert.equal(result.pageCount, 2)
  assert.deepEqual(result.pages[0], { side: ['s:1'], main: ['m:1'] })
  assert.deepEqual(result.pages[1], { side: [], main: ['m:2', 'm:3'] })
})

test('sub-pixel rounding does not push a fitting block to the next page', () => {
  const result = paginate([block('a:1', 500.3), block('a:2', 500)], cap(1000), 1)
  assert.equal(result.pageCount, 1)
})
