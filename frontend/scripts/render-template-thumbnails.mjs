#!/usr/bin/env node
// Renders the first page of every built-in template (sample content, default design) to a small
// JPEG for the landing page, where 16 live renders would be too heavy. Needs the dev server:
//   npm run dev -- --port 5174 &  node scripts/render-template-thumbnails.mjs [baseUrl]
import { mkdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const out = join(root, 'src/features/landing/assets/templates')
const base = process.argv[2] ?? 'http://127.0.0.1:5174'
const ids = ['classic', 'clarity', 'harvard', 'meridian', 'timeline', 'ledger', 'bold', 'aurora', 'editorial', 'inkwash', 'watercolor', 'contour', 'banker', 'engineer', 'academic', 'campus']
mkdirSync(out, { recursive: true })
const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {})
// 794 px A4 width × 0.5 → ~400 px wide thumbnails, sharp on 1× and acceptable on 2× screens.
const page = await browser.newPage({ viewport: { width: 900, height: 1300 }, deviceScaleFactor: 0.5 })
for (const id of ids) {
  const sample = id === 'harvard' ? 'english' : id === 'campus' ? 'campus' : 'professional'
  await page.goto(`${base}/print.html?template=${id}&sample=${sample}`, { waitUntil: 'networkidle' })
  await page.waitForFunction(() => document.documentElement.dataset.ready === '1', null, { timeout: 30000 })
  const file = join(out, `${id}.jpg`)
  await page.locator('.rr-pages > .rr-page').first().screenshot({ path: file, type: 'jpeg', quality: 82 })
  console.log(`${id}.jpg ${Math.round(statSync(file).size / 1024)} KB`)
}
await browser.close()
