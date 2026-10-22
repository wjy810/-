// Real Chromium checks for template layouts, independent of accounts or AI calls.
// node scripts/template-collection-acceptance.mjs [--quick]
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from '@playwright/test'

const base = process.env.JOBPROOF_VISUAL_URL || 'http://127.0.0.1:5174'
const out = resolve(process.env.JOBPROOF_TEMPLATE_OUTPUT || '../.local-run/template-collection-20261021')
const { templates } = JSON.parse(await readFile('../backend/src/main/resources/templates/builtin-manifests.json', 'utf8'))
const samples = JSON.parse(await readFile('../backend/src/test/resources/templates/render-samples.json', 'utf8'))
const quick = process.argv.includes('--quick')
const ids = process.env.JOBPROOF_TEMPLATE_IDS?.split(',')
const selected = templates.slice(16).filter(m => !ids || ids.includes(m.id))
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const context = await browser.newContext({ viewport: { width: 920, height: 1300 }, deviceScaleFactor: 1 })
const page = await context.newPage()
const errors = []
page.on('pageerror', e => errors.push(e.message))
await page.goto(base + '/print.html', { waitUntil: 'networkidle' })
await page.waitForFunction(() => window.__JP_READY__ === true)
const photo = 'data:image/svg+xml;base64,' + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="120" height="150"><rect width="120" height="150" fill="#e2e9ed"/><circle cx="60" cy="53" r="24" fill="#879ba9"/><path d="M15 150v-25a45 45 0 0 1 90 0v25" fill="#879ba9"/></svg>').toString('base64')
const results = []
try {
  for (const manifest of selected) {
    const content = manifest.locale === 'en' ? samples.english : samples.professional
    const scenarios = [{ name: 'default', content, design: {} }]
    if (!quick) {
      for (const palette of manifest.palettes.slice(1)) scenarios.push({ name: 'palette-' + palette.id, content, design: { paletteId: palette.id } })
      scenarios.push({ name: 'compact', content, design: { headerVariant: 'compact' } })
      scenarios.push({ name: 'minimal', content: samples.minimal, design: {} })
      scenarios.push({ name: 'font', content, design: { fontPairing: manifest.fontPairings[1] } })
      scenarios.push({ name: 'large-wide', content, design: { fontSize: 'XL', pageMargin: 'WIDE', pageTarget: 'TWO' }, allowOverflow: true })
      const long = manifest.locale === 'en' ? { ...content, experiences: [...content.experiences, ...content.experiences, ...content.experiences], projects: [...content.projects, ...content.projects, ...content.projects] } : samples.long
      scenarios.push({ name: 'overflow', content: long, design: { pageTarget: 'ONE' }, allowOverflow: true, requireOverflow: true })
      scenarios.push({ name: 'sections', content, design: { hiddenSections: ['honors', 'certificates'], sectionTitles: { projects: '代表项目' }, sectionOrder: ['skills', 'projects', 'summary', 'education', 'experience', 'organizations', 'languages', 'honors', 'certificates'] } })
      const mixed = structuredClone(content)
      mixed.basics.name = '林知远 Alex Chen'
      mixed.basics.email = 'long-contact-name.platform-team@example.com'
      mixed.basics.links = ['https://portfolio.example.com/long-path/research-and-development/platform-engineering']
      scenarios.push({ name: 'mixed', content: mixed, design: {} })
      if (manifest.decorations) scenarios.push({ name: 'no-decor', content, design: { decorations: false } })
      if (manifest.photo === 'optional') {
        scenarios.push({ name: 'photo', content, design: { photo: { mode: 'SHOW', shape: 'ROUNDED' } }, photo })
        scenarios.push({ name: 'photo-hide', content, design: { photo: { mode: 'HIDE' } }, photo })
      }
      if (manifest.regions.length > 1) scenarios.push({ name: 'reassign', content, design: { regionAssignments: { skills: 'main', education: 'side' } } })
      if (manifest.paperSizes.includes('LETTER')) scenarios.push({ name: 'letter', content, design: { paperSize: 'LETTER' } })
    }
    for (const scenario of scenarios) {
      const label = manifest.id + '/' + scenario.name
      const layout = await page.evaluate(payload => window.__JP_RENDER__(payload), {
        templateId: manifest.id, content: scenario.content, design: scenario.design, photo: scenario.photo ?? null, title: manifest.name + ' / ' + scenario.name,
      })
      const geometry = await page.evaluate(() => {
        const problems = []
        const shown = [...document.querySelectorAll('.rr-pages > .rr-page')]
        for (const paper of shown) {
          const paperBox = paper.getBoundingClientRect()
          for (const region of paper.querySelectorAll('[data-region]')) {
            const r = region.getBoundingClientRect()
            for (const block of region.querySelectorAll('[data-block]')) {
              const b = block.getBoundingClientRect()
              if (b.bottom > r.bottom + 2 || b.top < r.top - 2 || b.left < paperBox.left - 1 || b.right > paperBox.right + 1)
                problems.push('block outside region: ' + block.dataset.block)
            }
          }
          for (const text of paper.querySelectorAll('.rr-entry__title,.rr-entry__date,.rr-heading,.collection-contact,.collection-name')) {
            const b = text.getBoundingClientRect()
            if (b.left < paperBox.left - 1 || b.right > paperBox.right + 1 || text.scrollWidth > text.clientWidth + 2)
              problems.push('horizontal overflow: ' + text.className)
          }
        }
        const measured = [...document.querySelectorAll('.rr-measure > .rr-page:first-child [data-block]')].map(el => el.dataset.block)
        const rendered = [...document.querySelectorAll('.rr-pages [data-block]')].map(el => el.dataset.block)
        const missing = measured.filter(id => !rendered.includes(id))
        const duplicates = rendered.filter((id, i) => rendered.indexOf(id) !== i)
        const broken = [...document.querySelectorAll('.rr-pages img')].filter(img => !img.complete || !img.naturalWidth).map(img => img.src.slice(0, 100))
        return { problems, missing, duplicates, broken, pageCount: shown.length, photos: document.querySelectorAll('.rr-pages .rr-photo').length,
          visibleDecor: [...document.querySelectorAll('.rr-pages .rr-decor')].filter(el => getComputedStyle(el).display !== 'none').length }
      })
      const failures = [...geometry.problems, ...geometry.missing.map(x => 'missing: ' + x), ...geometry.duplicates.map(x => 'duplicate: ' + x), ...geometry.broken.map(x => 'broken: ' + x)]
      if (!scenario.allowOverflow && layout.pageCount > manifest.maxPages) failures.push('exceeds page limit')
      if (scenario.requireOverflow && layout.overflowMm <= 0 && layout.pageCount <= 1) failures.push('overflow not reported')
      if (scenario.name === 'no-decor' && geometry.visibleDecor) failures.push('decorations remain visible')
      if (scenario.name === 'photo' && geometry.photos !== 1) failures.push('photo missing')
      if (scenario.name === 'photo-hide' && geometry.photos) failures.push('photo not hidden')
      if (scenario.name === 'default') {
        for (let index = 0; index < geometry.pageCount; index++) {
          await page.locator('.rr-pages > .rr-page').nth(index).screenshot({ path: resolve(out, manifest.id + '-page-' + (index + 1) + '.png') })
        }
        await page.pdf({ path: resolve(out, manifest.id + '.pdf'), preferCSSPageSize: true, printBackground: true, tagged: true })
      } else {
        await page.locator('.rr-pages > .rr-page').first().screenshot({ path: resolve(out, manifest.id + '-' + scenario.name + '.jpg'), type: 'jpeg', quality: 76 })
      }
      results.push({ template: manifest.id, scenario: scenario.name, layout, geometry, failures })
      console.log(label, layout.pageCount + 'p', failures.length ? JSON.stringify(failures) : 'PASS')
    }
  }
} finally {
  await writeFile(resolve(out, quick ? 'quick-results.json' : 'render-results.json'), JSON.stringify({ results, errors }, null, 2))
  await browser.close()
}
assert.deepEqual(errors, [], 'browser errors')
assert.deepEqual(results.filter(r => r.failures.length).map(r => ({ template: r.template, scenario: r.scenario, failures: r.failures })), [], 'template acceptance failures')
console.log('Verified', results.length, 'scenarios:', out)
