/**
 * Integration: real Chromium + the built print entry. Skipped unless both are available: Chromium from
 * RENDERER_CHROMIUM_PATH or `npx playwright-core install chromium`, the print entry from `npm run build`:
 *   RENDERER_PRINT_DIR=../frontend/dist npm test
 */
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { after, before, test } from 'node:test'
import { chromium as playwrightChromium } from 'playwright-core'
import { loadConfig } from '../src/config.ts'
import { startServer } from '../src/server.ts'

const printDir = resolve(process.env.RENDERER_PRINT_DIR ?? '../frontend/dist')
const chromium = process.env.RENDERER_CHROMIUM_PATH || playwrightChromium.executablePath()
const available = Boolean(chromium && existsSync(chromium) && existsSync(resolve(printDir, 'print.html')))
const token = 'integration-token-0123456789'
const port = 3199
let running: Awaited<ReturnType<typeof startServer>> | undefined

const content = {
  basics: { name: '测试用户', email: 'test@example.com', phone: '13800000000', links: ['github.com/test'] },
  intentions: { targetJob: '工程师' },
  experiences: [{ company: '示例公司', role: '工程师', startDate: '2020-01', current: true, description: '- 第一条成果\n- 第二条成果' }],
}

async function call(body: unknown, headers: Record<string, string> = { Authorization: `Bearer ${token}` }) {
  return fetch(`http://127.0.0.1:${port}/v1/render`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) })
}

before(async () => {
  if (!available) return
  running = await startServer({ ...loadConfig({ RENDERER_TOKEN: token, RENDERER_PRINT_DIR: printDir, RENDERER_CHROMIUM_PATH: chromium, RENDERER_POOL_SIZE: '1' }), host: '127.0.0.1', port })
  for (let i = 0; i < 60; i += 1) {
    const ready = await fetch(`http://127.0.0.1:${port}/readyz`).then(r => r.ok).catch(() => false)
    if (ready) return
    await new Promise(r => setTimeout(r, 500))
  }
  throw new Error('renderer did not become ready')
})
after(async () => { await running?.close() })

test('renders a tagged PDF and reports the page count', { skip: !available }, async () => {
  const response = await call({ payload: { templateId: 'classic', content, title: '测试简历' } })
  assert.equal(response.status, 200)
  assert.equal(response.headers.get('content-type'), 'application/pdf')
  assert.equal(response.headers.get('x-page-count'), '1')
  const bytes = Buffer.from(await response.arrayBuffer())
  assert.equal(bytes.subarray(0, 5).toString(), '%PDF-')
  assert.ok(bytes.includes(Buffer.from('/StructTreeRoot')), 'PDF is tagged')
})

test('rejects missing tokens and unknown templates', { skip: !available }, async () => {
  assert.equal((await call({ payload: { templateId: 'classic', content } }, {})).status, 401)
  const unknown = await call({ payload: { templateId: 'does-not-exist', content } })
  assert.equal(unknown.status, 422)
  assert.equal((await unknown.json()).code, 'TEMPLATE_UNKNOWN')
})

test('the print page cannot reach external hosts', { skip: !available }, async () => {
  // A link is fine (not fetched); an external image in content is never requested because content
  // is rendered as text. We assert the page's network policy by evaluating a fetch from inside it.
  const slot = await running!.pool.acquire()
  try {
    const outcome = await slot.page.evaluate(() => fetch('https://example.com/').then(() => 'reached', () => 'blocked'))
    assert.equal(outcome, 'blocked')
  } finally {
    await running!.pool.release(slot, false)
  }
})
