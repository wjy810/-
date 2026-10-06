import { chromium } from '@playwright/test'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const base = process.env.JOBPROOF_VISUAL_URL || 'http://127.0.0.1:5174'
if (!/^http:\/\/127\.0\.0\.1:5174$/.test(base)) throw new Error('Visual fixtures are restricted to the isolated localhost preview')
const phase = process.argv[2] || 'after'
const out = resolve('../.codex_tmp/style-acceptance-20260905', phase)
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ headless: true })
const results = []
try {
  for (const width of [390, 768, 1440, 1920]) {
    const context = await browser.newContext({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' })
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const routes = ['/', '/?auth=login', '/?auth=register', '/?auth=reset']
    const authFixture = await readFile(resolve('e2e/mobile-layout.spec.ts'), 'utf8')
    const testPassword = authFixture.match(/email:\s*'seeker',\s*password:\s*'([^']+)'/)?.[1]
    for (const route of routes) await capture(route, route === '/' ? 'home' : route.slice(7))
    const response = testPassword && await page.request.post(`${base}/api/v1/auth/login`, { data: { identifier: 'seeker', password: testPassword } })
    if (response && response.ok()) {
      for (const [name, route] of Object.entries({ library: '/career-library', resumes: '/resumes', planning: '/career-planning', matching: '/job-match', interview: '/mock-interviews', templates: '/resume-templates', notifications: '/notifications', updates: '/updates' })) {
        await capture(route, name)
      }
    } else results.push({ width, authenticated: false, status: response?.status() })
    await context.close()

    async function capture(route, name) {
      await page.goto(`${base}${route}`, { waitUntil: 'networkidle' })
      await page.locator('h1').first().waitFor({ state: 'visible' }).catch(() => {})
      const geometry = await page.evaluate(() => ({
        actualPath: location.pathname,
        title: document.querySelector('h1')?.textContent?.trim(),
        overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        brokenImages: [...document.images].filter(image => image.complete && image.naturalWidth === 0).length,
      }))
      await page.screenshot({ path: resolve(out, `${width}-${name}.png`), fullPage: false })
      results.push({ width, route, ...geometry, errors: [...errors] })
    }
  }
} finally { await browser.close() }
await writeFile(resolve(out, 'results.json'), JSON.stringify(results, null, 2))
console.log(JSON.stringify({ out, pages: results.length, failures: results.filter(r => r.overflow > 1 || r.brokenImages > 0 || r.errors?.length), authenticated: results.filter(r => r.authenticated === false) }, null, 2))
