import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium, expect } from '@playwright/test'

const base = process.env.JOBPROOF_VISUAL_URL || 'http://127.0.0.1:5174'
const out = resolve(process.env.JOBPROOF_TEMPLATE_OUTPUT || '../.local-run/template-collection-20261021')
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
if (process.env.JOBPROOF_TEST_PASSWORD) {
  const response = await context.request.post(base + '/api/v1/auth/login', { data: { identifier: process.env.JOBPROOF_TEST_ACCOUNT, password: process.env.JOBPROOF_TEST_PASSWORD } })
  assert.equal(response.status(), 200, 'local test login')
}
const page = await context.newPage()
const errors = []
const results = []
page.on('pageerror', error => errors.push(error.message))
try {
  await page.goto(base + '/resume-templates', { waitUntil: 'networkidle' })
  await expect(page.locator('.smart-card')).toHaveCount(32)
  for (const [label, count] of [['稳健', 3], ['现代', 9], ['设计感', 12], ['行业', 8], ['全部', 32]]) {
    await page.locator('.smart-gallery__head button[aria-label="' + label + '"]').click()
    await expect(page.locator('.smart-card')).toHaveCount(count)
  }
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 })
    await page.locator('.smart-gallery__head button[aria-label="设计感"]').click()
    const cards = page.locator('.smart-card')
    for (let index = 0; index < await cards.count(); index++) {
      const card = cards.nth(index)
      await card.scrollIntoViewIfNeeded()
      await expect(card.locator('.rr-pages:not(.rr-pages--pending)')).toBeVisible()
    }
    const metrics = await page.evaluate(() => ({
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      cardOverflows: [...document.querySelectorAll('.smart-card')].flatMap(card => {
        const bounds = card.getBoundingClientRect()
        return [...card.querySelectorAll('.smart-card__title h3,.smart-card__title small,.smart-card__palettes')].filter(el => {
          const b = el.getBoundingClientRect()
          return b.left < bounds.left || b.right > bounds.right
        }).map(el => ({ text: el.textContent, class: el.className }))
      }),
      brokenImages: [...document.querySelectorAll('.smart-card img')].filter(img => img.complete && !img.naturalWidth).map(img => img.src),
    }))
    await page.locator('.smart-gallery__head').scrollIntoViewIfNeeded()
    await page.screenshot({ path: resolve(out, 'gallery-' + width + '.png'), fullPage: true })
    results.push({ width, ...metrics })
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(base + '/resume-templates/botanica', { waitUntil: 'networkidle' })
  await expect(page.getByRole('button', { name: '用此模板创建简历', exact: true })).toBeVisible()
  await expect(page.locator('.rr-pages:not(.rr-pages--pending)')).toBeVisible()
  await page.screenshot({ path: resolve(out, 'detail-botanica.png'), fullPage: true })
} finally {
  await writeFile(resolve(out, 'gallery-results.json'), JSON.stringify({ results, errors }, null, 2))
  await browser.close()
}
console.log(JSON.stringify({ results, errors }, null, 2))
assert.deepEqual(errors, [])
assert.ok(results.every(r => r.overflow <= 1 && !r.cardOverflows.length && !r.brokenImages.length), 'gallery geometry')
