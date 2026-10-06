import { chromium, expect } from '@playwright/test'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const base = process.env.JOBPROOF_VISUAL_URL || 'http://127.0.0.1:5174'
if (base !== 'http://127.0.0.1:5174') throw new Error('Resume fixtures are restricted to the isolated localhost preview')
const out = resolve(process.env.JOBPROOF_RESUME_ACCEPTANCE_OUTPUT || '../.codex_tmp/completion-acceptance-20260906/resume')
const viewports = [{ width: 390, height: 844 }, { width: 768, height: 1024 }, { width: 1440, height: 900 }, { width: 1920, height: 1080 }]
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: viewports[2], reducedMotion: 'reduce' })
const errors = []
const results = []
let conversationId
try {
  const authFixture = await readFile(resolve('e2e/mobile-layout.spec.ts'), 'utf8')
  const password = authFixture.match(/email:\s*'seeker',\s*password:\s*'([^']+)'/)?.[1]
  if (!password) throw new Error('Missing isolated test-account fixture')
  await request('/api/v1/auth/login', 'POST', { identifier: 'seeker', password })
  let conversation = await request('/api/v1/ai-resume/conversations', 'POST', { identityType: 'PROFESSIONAL', title: '本地验收示例 - 多条记录与混排' })
  conversationId = conversation.id
  const root = `/api/v1/ai-resume/conversations/${conversation.id}`
  const fixtures = {
    TARGET_JOB: { targetJob: 'Platform Engineer / 平台工程师' },
    CONTACT: { name: '林知远', email: 'visual-qa@example.com', phone: '13800001111', location: '杭州', links: ['https://example.com/portfolio'] },
    SUMMARY: { text: '本地合成验收数据：关注结构化内容、可验证结果与可靠交付。具有平台开发和项目协作经验，持续整理技术决策、实施过程与复盘信息。' },
    EDUCATION: { items: [{ school: '视觉验收大学计算机与软件工程学院', major: 'Computer Science', degree: '本科', startDate: '2018-09', endDate: '2022-06', description: '完成数据库、计算机网络和软件工程课程。' }, { school: '示例开放教育中心', major: '继续教育', startDate: '2023-03', endDate: '2023-09', description: '软件测试与技术写作专项课程。' }] },
    EXPERIENCE: { items: [{ company: '示例基础设施与开发者效率研究有限公司', role: 'Platform Engineer', startDate: '2022-07', current: true, location: '杭州', description: '整理发布与回滚流程，维护可追溯的工程交付记录。', highlights: ['保持正式简历与待确认内容分离', 'Improved observability and tested rollback paths'] }] },
    PROJECTS: { items: [{ name: 'JobProof Resume Platform', role: 'Backend Owner', startDate: '2024-03', endDate: '2025-02', description: '构建结构化简历快照与异步导出流程。', highlights: ['验证多种模板的文字完整性', 'Verified immutable snapshot hashes'] }] },
    SKILLS: { items: [{ category: 'Backend & Platform', items: ['Java', 'Spring Boot', 'SQL', 'Docker', 'OpenTelemetry'] }] },
  }
  for (const [type, payload] of Object.entries(fixtures)) {
    const card = conversation.cards.find(item => item.cardType === type)
    if (!card) throw new Error(`Missing card ${type}`)
    conversation = await request(`${root}/cards/${card.id}/submit`, 'POST', { payload, expectedVersion: card.versionNo })
  }
  const templates = await request(`${root}/smart-templates`)
  expect(templates).toHaveLength(12)
  const canonicalBefore = JSON.stringify(conversation.canonicalContent)
  for (const template of templates) {
    conversation = await request(`${root}/template`, 'POST', { templateId: template.templateId })
    if (canonicalBefore !== undefined) expect(JSON.stringify(conversation.canonicalContent)).toBe(canonicalBefore)
    const page = await context.newPage()
    const recordError = (type, details) => errors.push({ template: template.templateId, type, ...details })
    page.on('pageerror', error => recordError('pageerror', { message: error.message }))
    page.on('console', message => { if (message.type() === 'error') recordError('console', { message: message.text() }) })
    page.on('response', response => {
      if (response.status() >= 400) recordError('http', { status: response.status(), url: response.url() })
    })
    page.on('requestfailed', failed => recordError('requestfailed', { url: failed.url(), message: failed.failure()?.errorText }))
    try {
      const conversationLoaded = page.waitForResponse(response => new URL(response.url()).pathname === root && response.request().method() === 'GET')
      const templatesLoaded = page.waitForResponse(response => new URL(response.url()).pathname === `${root}/smart-templates`)
      await page.goto(`${base}/ai-resume/${conversation.id}`, { waitUntil: 'domcontentloaded' })
      expect((await conversationLoaded).ok()).toBeTruthy()
      expect((await templatesLoaded).ok()).toBeTruthy()
      await expect(page.locator('.workbench-loading')).toHaveCount(0)
      await expect(page.locator('.workbench-grid')).toBeVisible()
      await expect(page.locator('.preview-template strong')).toHaveText(template.displayName)
      await expect(page.locator('.preview-stage .resume-sheet__identity h2')).toHaveText(fixtures.CONTACT.name)
      for (const viewport of viewports) {
        await page.setViewportSize(viewport)
        const mobile = await page.getByRole('tablist', { name: '工作台视图' }).isVisible()
        if (mobile) {
          await page.getByRole('tab', { name: 'AI 对话', exact: true }).click()
          await capture('conversation')
          await page.getByRole('tab', { name: 'A4 预览', exact: true }).click()
        }
        await expect(page.locator('.preview-stage .resume-sheet').first()).toBeVisible()
        await capture('preview')

        async function capture(pane) {
          await page.evaluate(async () => {
            await document.fonts.ready
            await Promise.all([...document.images].map(image => image.decode().catch(() => {})))
            await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)))
          })
          await expect(page.getByText('当前页面载入失败', { exact: true })).toHaveCount(0)
          const geometry = await page.evaluate(() => ({
            overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
            brokenImages: [...document.images].filter(image => image.complete && image.naturalWidth === 0).map(image => image.currentSrc),
            previewPages: document.querySelectorAll('.preview-stage .resume-sheet').length,
            previewText: document.querySelector('.preview-stage')?.textContent || '',
            loading: Boolean(document.querySelector('.workbench-loading')),
          }))
          expect(geometry.overflow).toBeLessThanOrEqual(1)
          expect(geometry.brokenImages).toEqual([])
          expect(geometry.loading).toBe(false)
          expect(geometry.previewText).toContain(fixtures.EDUCATION.items[0].school)
          expect(geometry.previewText).toContain(fixtures.PROJECTS.items[0].name)
          await page.screenshot({ path: resolve(out, `${viewport.width}x${viewport.height}-${template.templateId}-${pane}.png`) })
          results.push({ template: template.templateId, ...viewport, pane, ...geometry })
        }
      }
    } finally {
      // Closing a completed page deliberately cancels its event stream.
      page.removeAllListeners('requestfailed')
      await page.close()
    }
    if (process.env.JOBPROOF_SKIP_PDF === '1') continue
    const task = await request(`${root}/export-pdf`, 'POST', { exportMode: 'STANDARD' })
    let complete = task
    for (let attempt = 0; attempt < 80 && complete.status !== 'SUCCEEDED' && complete.status !== 'FAILED'; attempt++) {
      await new Promise(done => setTimeout(done, 150))
      complete = await request(`/api/v1/tasks/${task.id}`)
    }
    expect(complete.status).toBe('SUCCEEDED')
    const pdf = await context.request.get(`${base}${complete.downloadUrl}`)
    expect(pdf.ok()).toBeTruthy()
    const bytes = await pdf.body()
    expect(bytes.subarray(0, 5).toString()).toBe('%PDF-')
    await writeFile(resolve(out, `${template.templateId}.pdf`), bytes)
    results.push({ template: template.templateId, pdfBytes: bytes.length, status: complete.status })
  }
  expect(errors).toEqual([])
} finally {
  results.push({ conversationId, errors })
  await context.close()
  await browser.close()
  await writeFile(resolve(out, 'results.json'), JSON.stringify(results, null, 2))
}
console.log(JSON.stringify({ out, cases: results.length, errors }))

async function request(path, method = 'GET', data) {
  const response = await context.request.fetch(`${base}${path}`, { method, data })
  const body = await response.json()
  if (!response.ok()) throw new Error(`${method} ${path}: ${response.status()} ${body.error?.reason || ''}`)
  return body.data
}
