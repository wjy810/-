import assert from 'node:assert/strict'
import test from 'node:test'
import { InvalidRequest, parseRenderRequest } from '../src/validate.ts'

const content = { basics: { name: '林晓' } }

test('accepts a minimal PDF request and defaults the format', () => {
  const parsed = parseRenderRequest({ payload: { templateId: 'classic', content } })
  assert.equal(parsed.format, 'pdf')
  assert.equal(parsed.payload.photo, null)
  assert.equal(parsed.options.pngScale, 1)
})

test('rejects unsafe or malformed fields', () => {
  const cases: unknown[] = [
    null,
    { payload: { templateId: '../etc', content } },
    { payload: { templateId: 'classic', content: 'text' } },
    { format: 'html', payload: { templateId: 'classic', content } },
    { payload: { templateId: 'classic', content, photo: 'https://evil.example/photo.png' } },
    { payload: { templateId: 'classic', content, photo: 'data:image/svg+xml;base64,PHN2Zz4=' } },
    { payload: { templateId: 'classic', content, title: 'x'.repeat(201) } },
  ]
  for (const body of cases) assert.throws(() => parseRenderRequest(body), InvalidRequest)
})

test('keeps a PNG/JPEG data URL photo and clamps options', () => {
  const parsed = parseRenderRequest({ format: 'png', payload: { templateId: 'meridian', content, photo: 'data:image/png;base64,iVBORw0KGgo=' }, options: { pngScale: 9, pageLimit: 0 } })
  assert.equal(parsed.payload.photo, 'data:image/png;base64,iVBORw0KGgo=')
  assert.equal(parsed.options.pngScale, 1)
  assert.equal(parsed.options.pageLimit, undefined)
})
