// Check the real app shell: print.html alone cannot reveal app-theme CSS leaking into paper.
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from '@playwright/test'

const base = process.env.JOBPROOF_VISUAL_URL || 'http://127.0.0.1:5174'
const out = resolve(process.env.JOBPROOF_TEMPLATE_OUTPUT || '../.local-run/template-visual-review-20261021')
const { templates } = JSON.parse(await readFile('../backend/src/main/resources/templates/builtin-manifests.json', 'utf8'))
const selected = templates.filter(m => !process.env.JOBPROOF_TEMPLATE_IDS || process.env.JOBPROOF_TEMPLATE_IDS.split(',').includes(m.id))
const recordOnly = process.argv.includes('--record-only')
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const results = [], errors = []
try {
  for (const theme of ['dark', 'light']) {
    const context = await browser.newContext({ viewport: { width: 1540, height: 1700 }, colorScheme: theme })
    if (process.env.JOBPROOF_TEST_PASSWORD) {
      const response = await context.request.post(base + '/api/v1/auth/login', { data: { identifier: process.env.JOBPROOF_TEST_ACCOUNT, password: process.env.JOBPROOF_TEST_PASSWORD } })
      assert.equal(response.status(), 200, 'local test login')
    }
    await context.addInitScript(theme => localStorage.setItem('jp:preferences', JSON.stringify({ theme, motion: 'reduce' })), theme)
    const page = await context.newPage()
    page.on('pageerror', error => errors.push(error.message))
    for (const manifest of selected) {
      await page.goto(base + '/resume-templates/' + manifest.id, { waitUntil: 'networkidle' })
      await page.locator('.rr-pages:not(.rr-pages--pending) > .rr-page').first().waitFor({ state: 'visible' })
      // Font arrival can trigger a second pagination pass after the initial paper becomes visible.
      await page.evaluate(async () => {
        await document.fonts.ready
        await new Promise((resolve, reject) => {
          let previous = '', changedAt = performance.now()
          const started = performance.now()
          function check() {
            const root = document.querySelector('.rr-root'), pages = document.querySelector('.rr-pages')
            const stamp = [pages?.className, pages?.childElementCount, pages?.firstElementChild?.textContent, root && getComputedStyle(root).getPropertyValue('--r-gap')].join('|')
            if (stamp !== previous) { previous = stamp; changedAt = performance.now() }
            if (performance.now() - changedAt > 400 && document.fonts.status === 'loaded') return resolve()
            if (performance.now() - started > 10000) return reject(new Error('Resume preview did not settle'))
            requestAnimationFrame(check)
          }
          requestAnimationFrame(check)
        })
      })
      const paper = page.locator('.rr-pages > .rr-page').first()
      const metrics = await paper.evaluate(paper => {
        function rgb(color) { return color.match(/[\d.]+/g)?.slice(0, 3).map(Number) || [255, 255, 255] }
        function luminance(color) {
          return rgb(color).map(v => { v /= 255; return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4 }).reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0)
        }
        function background(element) {
          const bounds = element.getBoundingClientRect()
          while (element) {
            if (element === paper) {
              // Some existing templates paint a separate band behind their transparent header.
              const band = [...paper.querySelectorAll('.rr-decor--always')].find(band => {
                const b = band.getBoundingClientRect()
                return bounds.left >= b.left && bounds.right <= b.right && bounds.top >= b.top && bounds.bottom <= b.bottom && getComputedStyle(band).backgroundColor !== 'rgba(0, 0, 0, 0)'
              })
              if (band) return getComputedStyle(band).backgroundColor
            }
            const value = getComputedStyle(element).backgroundColor
            if (value !== 'rgba(0, 0, 0, 0)' && value !== 'transparent') return value
            element = element.parentElement
          }
          return 'rgb(255, 255, 255)'
        }
        return [...paper.querySelectorAll('h1, h4.rr-entry__title')].map(el => {
          const color = getComputedStyle(el).color, bg = background(el)
          const a = luminance(color), b = luminance(bg)
          return { text: el.textContent, color, background: bg, contrast: (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) }
        })
      })
      if (theme === 'dark' && templates.indexOf(manifest) >= 16) {
        await paper.screenshot({ path: resolve(out, manifest.id + '-app-dark.png'), animations: 'disabled' })
        if (manifest.id === 'constellation') await page.locator('.tpl-detail__grid').screenshot({ path: resolve(out, 'constellation-detail-dark.png'), animations: 'disabled' })
      }
      const outside = await page.locator('.rr-pages').evaluate(pages => [...pages.querySelectorAll('[data-region]')].flatMap(region => {
        const bounds = region.getBoundingClientRect()
        return [...region.querySelectorAll('[data-block]')].filter(block => {
          const b = block.getBoundingClientRect()
          return b.bottom > bounds.bottom + 2 || b.top < bounds.top - 2
        }).map(block => block.dataset.block)
      }))
      results.push({ template: manifest.id, theme, pageCount: await page.locator('.rr-pages > .rr-page').count(), headings: metrics, outside })
      console.log(manifest.id, theme, 'minimum heading contrast', Math.min(...metrics.map(h => h.contrast)).toFixed(2))
    }
    await context.close()
  }
} finally {
  await browser.close()
  await writeFile(resolve(out, 'app-theme-results.json'), JSON.stringify({ results, errors }, null, 2))
}
if (!recordOnly) {
  assert.deepEqual(errors, [], 'application errors')
  for (const manifest of selected) {
    const dark = results.find(r => r.template === manifest.id && r.theme === 'dark')
    const light = results.find(r => r.template === manifest.id && r.theme === 'light')
    assert.ok(dark.headings.length > 0, manifest.id + ': no rendered headings')
    assert.deepEqual(dark.headings, light.headings, manifest.id + ': the app theme changed resume paper')
    assert.equal(dark.pageCount, light.pageCount, manifest.id + ': theme-dependent pagination')
    assert.deepEqual([...dark.outside, ...light.outside], [], manifest.id + ': visible content outside its region')
    assert.ok(dark.headings.every(h => h.contrast >= 4.5), manifest.id + ': low-contrast resume heading')
  }
}
console.log('Recorded', results.length, 'app theme cases', recordOnly ? '(baseline only)' : 'PASS')
