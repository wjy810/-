#!/usr/bin/env node
// Renders the first page of every built-in template (sample content, default design) to a small
// JPEG for the landing page, where live renders would be too heavy. Needs the dev server:
//   npm run dev -- --port 5174 &  node scripts/render-template-thumbnails.mjs [baseUrl]
import { mkdirSync, readFileSync, statSync } from 'node:fs'
import { writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout } from 'node:timers/promises'
import { chromium } from '@playwright/test'

const root = fileURLToPath(new URL('..', import.meta.url))
const out = join(root, 'src/features/landing/assets/templates')
const base = process.argv[2] ?? 'http://127.0.0.1:5174'
// Generated from the frontend manifests by export:manifests; check:manifests enforces parity.
const { templates } = JSON.parse(readFileSync(join(root, '../backend/src/main/resources/templates/builtin-manifests.json'), 'utf8'))
mkdirSync(out, { recursive: true })
const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {})
// 794 px A4 width × 0.5 → ~400 px wide thumbnails, sharp on 1× and acceptable on 2× screens.
const page = await browser.newPage({ viewport: { width: 900, height: 1300 }, deviceScaleFactor: 0.5 })
try { for (const { id, locale } of templates) {
  const sample = locale === 'en' ? 'english' : id === 'campus' ? 'campus' : 'professional'
  await page.goto(`${base}/print.html?template=${id}&sample=${sample}`, { waitUntil: 'networkidle' })
  await page.waitForFunction(() => document.documentElement.dataset.ready === '1', null, { timeout: 30000 })
  const file = join(out, `${id}.jpg`)
  const bytes = await page.locator('.rr-pages > .rr-page').first().screenshot({ type: 'jpeg', quality: 82 })
  // Vite/file indexing can briefly lock a watched JPEG on Windows. Retry only transient file locks.
  for (let attempt = 0; ; attempt++) {
    try { await writeFile(file, bytes); break }
    catch (error) {
      if (attempt >= 4 || !['EBUSY', 'EPERM', 'EACCES', 'UNKNOWN'].includes(error.code)) throw error
      await setTimeout(100 * 2 ** attempt)
    }
  }
  console.log(`${id}.jpg ${Math.round(statSync(file).size / 1024)} KB`)
} } finally { await browser.close() }
