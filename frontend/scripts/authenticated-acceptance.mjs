import { chromium, expect } from '@playwright/test'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const base = process.env.JOBPROOF_VISUAL_URL || 'http://127.0.0.1:5174'
if (base !== 'http://127.0.0.1:5174') throw new Error('Acceptance fixtures require the isolated localhost preview')
const out = resolve(process.env.JOBPROOF_AUTH_ACCEPTANCE_OUTPUT || '../.codex_tmp/completion-acceptance-20260906/authenticated')
const viewports = process.env.JOBPROOF_AUTH_VIEWPORTS
  ? JSON.parse(process.env.JOBPROOF_AUTH_VIEWPORTS)
  : [{ width: 1440, height: 900 }, { width: 390, height: 844 }]
if (!Array.isArray(viewports) || !viewports.length || viewports.some(viewport =>
  !Number.isInteger(viewport.width) || !Number.isInteger(viewport.height) || viewport.width < 320 || viewport.width > 2560 || viewport.height < 640 || viewport.height > 1600)) {
  throw new Error('Acceptance viewports must contain bounded integer width and height values')
}
const selectedScenes = process.env.JOBPROOF_AUTH_SCENES?.split(',')
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ headless: true })
const results = []
const fixtureTitle = '本地验收 - 跨团队平台可靠性与开发者工具协作项目 / Developer Platform Reliability'
const longDescription = [
  '整理复杂项目中的需求、技术决策、实施过程与可核验成果，确保每一次内容更新都有明确来源和人工确认。',
  'Collaborated across product, platform engineering and quality assurance teams to validate release readiness, rollback procedures and observable service boundaries.',
  '这是一条用于检查移动端长文本自然换行的合成资料，包含中英文混排、多段描述、较长技术名称以及清晰的时间线。',
  '检查可访问的表单、版本冲突提示与安全文件状态，在小屏幕上仍保留完整操作入口和内容阅读能力。',
].join('\n')
try {
  const fixture = await readFile(resolve('e2e/mobile-layout.spec.ts'), 'utf8')
  const password = fixture.match(/email:\s*'seeker',\s*password:\s*'([^']+)'/)?.[1]
  if (!password) throw new Error('Missing isolated test-account fixture')
  for (const viewport of viewports) {
    const context = await browser.newContext({ viewport, reducedMotion: 'reduce', locale: 'zh-CN', hasTouch: viewport.width === 390 })
    try {
      await request('/api/v1/auth/login', 'POST', { identifier: 'seeker', password })
      const records = await request('/api/v1/career-library/records?size=100')
      if (!records.items.some(record => record.title === fixtureTitle)) {
        await request('/api/v1/career-library/records', 'POST', {
          type: 'PROJECT', title: fixtureTitle, organization: '本地合成验收数据与用户体验研究中心', role: '平台研发与交付协作负责人',
          startDate: '2024-01-01', endDate: '2025-12-01', location: '杭州 / Remote collaboration', description: longDescription,
          coreOutcome: '保留可追溯的确认记录，覆盖桌面与手机的实际加载、长文本和弹层状态。',
          payload: { skills: ['Java', 'Spring Boot', 'OpenTelemetry', 'Distributed Systems', '可访问性与移动端适配'] },
        })
      }
      const routes = [
        ['library-profile', '/career-library', '#career-profile-form'],
        ['library-records', '/career-library?view=records', '.timeline-record'],
        ['library-files', '/career-library?view=files', '.file-grid'],
        ['resumes', '/resumes', '.main h1'],
        ['matching', '/job-match', '.jm-stats'],
        ['matching-create', '/job-match/new', '.jm-source-tabs'],
        ['interview', '/mock-interviews', '.mi-home-stats'],
        ['interview-create', '/mock-interviews/new', '.mi-step-content'],
        ['planning', '/career-planning', '.canvas-list-section'],
        ['planning-create', '/career-planning/new', 'h1'],
        ['templates', '/resume-templates', 'h1'],
        ['notifications', '/notifications', '.n-list, .empty'],
        ['updates', '/updates', 'h1'],
        ['account', '/account', '.security-summary'],
        ['account-ai', '/account/ai', '.settings-section'],
        ['account-data', '/account/data-rights', '.settings-section'],
      ]
      for (const [name, route, ready] of routes) {
        if (selectedScenes && !selectedScenes.includes(name)) continue
        const page = await context.newPage()
        const errors = []
        const pending = new Set()
        const recordError = (kind, details) => errors.push({ kind, ...details })
        page.on('pageerror', error => recordError('pageerror', { message: error.message }))
        page.on('console', message => { if (message.type() === 'error') recordError('console', { message: message.text() }) })
        page.on('request', request => { if (request.resourceType() !== 'eventsource') pending.add(request) })
        page.on('requestfinished', request => pending.delete(request))
        page.on('requestfailed', request => { pending.delete(request); recordError('requestfailed', { url: request.url(), message: request.failure()?.errorText }) })
        page.on('response', response => { if (response.status() >= 400) recordError('http', { url: response.url(), status: response.status() }) })
        try {
          await page.goto(`${base}${route}`, { waitUntil: 'domcontentloaded' })
          await expect(page.locator(ready).first()).toBeVisible({ timeout: 30000 })
          await settled()
          expect(new URL(page.url()).pathname).toBe(new URL(route, base).pathname)
          await capture(name)
          if (name === 'resumes' && process.env.JOBPROOF_REQUIRE_RESUME_DATA === '1') {
            await expect(page.locator('.tbl tbody tr').first()).toBeVisible()
            const layout = await page.locator('.tbl-wrap').evaluate(wrapper => {
              const table = wrapper.querySelector('table')
              const pager = document.querySelector('.pager')
              return {
                width: wrapper.clientWidth, scrollWidth: wrapper.scrollWidth,
                tableWidth: table.getBoundingClientRect().width,
                pagerWidth: pager.clientWidth, pagerScrollWidth: pager.scrollWidth,
                labels: [...wrapper.querySelectorAll('tbody tr:first-child td')].map(cell => cell.getAttribute('data-label')),
              }
            })
            expect(layout.scrollWidth).toBeLessThanOrEqual(layout.width + 1)
            expect(layout.tableWidth).toBeLessThanOrEqual(layout.width + 1)
            expect(layout.pagerScrollWidth).toBeLessThanOrEqual(layout.pagerWidth + 1)
            expect(layout.labels).toEqual(['简历名称', '状态', '目标岗位', '证据覆盖度', '更新时间', '导出状态', '操作'])
            await page.getByRole('combobox', { name: '每页条数' }).click()
            await page.getByRole('option', { name: '20 条 / 页', exact: true }).click()
            await expect(page.getByRole('combobox', { name: '每页条数' })).toContainText('20 条 / 页')
            await capture('resumes-populated-pagination')
          }
          if (name === 'library-records') {
            const card = page.locator('.timeline-record').filter({ hasText: fixtureTitle })
            await expect(card).toContainText(longDescription.split('\n')[1])
            await card.getByRole('button', { name: '编辑', exact: true }).click()
            await expect(page.getByRole('dialog')).toBeVisible()
            await expect(page.getByPlaceholder('每行填写一条真实职责、行动或成果')).toHaveValue(longDescription)
            await expect(page.getByRole('dialog').getByText('2024年1月', { exact: true })).toBeVisible()
            await capture('library-record-editor')
            const saveColor = await page.getByRole('button', { name: '保存记录', exact: true }).evaluate(button => getComputedStyle(button).backgroundColor)
            expect(saveColor).not.toBe('rgba(0, 0, 0, 0)')
            expect(saveColor).not.toBe('rgb(255, 255, 255)')
            await page.getByRole('button', { name: '取消', exact: true }).click()
            await page.getByRole('combobox', { name: '经历类型' }).click()
            await expect(page.getByRole('listbox')).toBeVisible()
            await capture('library-record-select')
            await page.keyboard.press('Escape')
          }
          if (name === 'library-files') {
            await page.getByRole('button', { name: '新建文件夹', exact: true }).click()
            await expect(page.getByRole('dialog')).toBeVisible()
            await page.getByPlaceholder('例如：证书与证明').fill('本地验收文件夹 - 长名称与中英文内容检查')
            await capture('library-folder-dialog')
          }
          if (name === 'matching-create') {
            await page.locator('.jm-textarea-wrap textarea').fill(`${fixtureTitle}\n岗位职责：\n${longDescription}\n任职要求：Java、Spring Boot、SQL 与团队协作经验。`)
            const counter = await page.locator('.jm-counter').boundingBox()
            const input = await page.locator('.jm-textarea-wrap textarea').boundingBox()
            expect(counter.y + counter.height).toBeLessThanOrEqual(input.y)
            await capture('matching-long-jd')
          }
          if (name === 'account' && viewport.width === 390) {
            const toggle = page.getByTestId('mobile-nav-toggle')
            await toggle.click()
            await expect(toggle).toHaveAttribute('aria-expanded', 'true')
            await expect(page.locator('#app-sidebar')).toHaveCSS('visibility', 'visible')
            await capture('mobile-navigation')
            await page.keyboard.press('Escape')
            await expect(toggle).toHaveAttribute('aria-expanded', 'false')
          }
          expect(errors).toEqual([])
        } catch (error) {
          results.push({ ...viewport, name, route, failed: error.message, errors })
          await page.screenshot({ path: resolve(out, `${viewport.width}x${viewport.height}-${name}-failure.png`), fullPage: true }).catch(() => {})
        } finally {
          page.removeAllListeners('requestfailed')
          await page.close()
        }

        async function settled() {
          await expect.poll(() => pending.size, { timeout: 30000 }).toBe(0)
          await expect(page.locator('.career-shell-loading, .records-loading, .files-loading, .jm-loading, .mi-loading, .overview-loading, [aria-busy="true"]').filter({ visible: true })).toHaveCount(0)
          await page.evaluate(async () => {
            await document.fonts.ready
            await Promise.all([...document.images].map(image => image.decode().catch(() => {})))
            await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)))
          })
        }
        async function capture(scene) {
          await settled()
          const geometry = await page.evaluate(() => ({
            overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
            brokenImages: [...document.images].filter(image => image.complete && image.naturalWidth === 0).map(image => image.currentSrc),
            title: document.querySelector('h1')?.textContent?.trim(),
            textLength: document.querySelector('.main')?.textContent?.length || 0,
            overflowingElements: [...document.querySelectorAll('main, section, article, form, button, input, textarea, [role="dialog"]')]
              .filter(element => {
                const rect = element.getBoundingClientRect()
                return rect.width > 0 && (rect.left < -1 || rect.right > innerWidth + 1) && !element.closest('#app-sidebar')
              }).slice(0, 12).map(element => ({ tag: element.tagName, className: element.className })),
          }))
          await page.screenshot({ path: resolve(out, `${viewport.width}x${viewport.height}-${scene}.png`) })
          if (!await page.getByRole('dialog').count()) {
            await page.screenshot({ path: resolve(out, `${viewport.width}x${viewport.height}-${scene}-full.png`), fullPage: true })
          }
          results.push({ ...viewport, name: scene, route, ...geometry, errors: [...errors] })
          expect(geometry.overflow).toBeLessThanOrEqual(1)
          expect(geometry.brokenImages).toEqual([])
        }
      }

      async function request(path, method = 'GET', data) {
        const response = await context.request.fetch(`${base}${path}`, { method, data })
        const body = await response.json()
        if (!response.ok()) throw new Error(`${method} ${path}: ${response.status()} ${body.error?.reason || ''}`)
        return body.data
      }
    } finally { await context.close() }
  }
} finally {
  await browser.close()
  await writeFile(resolve(out, 'results.json'), JSON.stringify(results, null, 2))
}
const failures = results.filter(result => result.failed || result.overflow > 1 || result.brokenImages?.length || result.errors?.length)
console.log(JSON.stringify({ out, scenes: results.length, failures }, null, 2))
if (failures.length) process.exitCode = 1
