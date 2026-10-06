import assert from 'node:assert/strict'
import test from 'node:test'
import { formatPdfOverflow, isPdfOverflowBlocked, pdfOverflowItems } from './pdfPreflight.ts'

test('keeps a valid layout exportable', () => {
  const layout = { status: 'VALID', overflow: { valid: true, items: [] } }

  assert.equal(isPdfOverflowBlocked(layout), false)
  assert.deepEqual(pdfOverflowItems(layout), [])
})

test('formats the first overflowing resume section for PDF preflight', () => {
  const item = { page: 2, slotKey: 'experience', excessUnits: 146 }
  const layout = { status: 'OVERFLOW', overflow: { valid: false, items: [item] } }

  assert.equal(isPdfOverflowBlocked(layout), true)
  assert.deepEqual(pdfOverflowItems(layout), [item])
  assert.equal(formatPdfOverflow(item), '第 2 页的工作与实习经历超出 146 个容量单位')
})

test('fails closed when the report is invalid even if the cached status is stale', () => {
  assert.equal(isPdfOverflowBlocked({ status: 'VALID', overflow: { valid: false, items: [] } }), true)
  assert.equal(formatPdfOverflow(null), '当前模板的 PDF 容量不足')
})
