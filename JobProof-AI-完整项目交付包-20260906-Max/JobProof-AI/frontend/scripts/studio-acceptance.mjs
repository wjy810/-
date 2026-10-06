import { chromium, expect } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const out = resolve('../.codex_tmp/style-acceptance-20260905/studio')
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ headless: true })
const results = []

async function screenshotSignal(page, locator) {
  const png = await locator.screenshot()
  return page.evaluate(async encoded => {
    const image = new Image()
    image.src = `data:image/png;base64,${encoded}`
    await image.decode()
    const scratch = document.createElement('canvas')
    scratch.width = image.naturalWidth
    scratch.height = image.naturalHeight
    const context = scratch.getContext('2d', { willReadFrequently: true })
    if (!context) return { pixels: 0, distinct: 0 }
    context.drawImage(image, 0, 0)
    const data = context.getImageData(0, 0, scratch.width, scratch.height).data
    const baseline = [data[0], data[1], data[2], data[3]]
    let distinct = 0
    for (let index = 0; index < data.length; index += 4) {
      const distance = Math.abs(data[index] - baseline[0])
        + Math.abs(data[index + 1] - baseline[1])
        + Math.abs(data[index + 2] - baseline[2])
        + Math.abs(data[index + 3] - baseline[3])
      if (distance > 18) distinct++
    }
    return { pixels: scratch.width * scratch.height, distinct }
  }, png.toString('base64'))
}

try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await context.route('**/api/v1/me', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ ok: true, data: null }),
  }))
  const page = await context.newPage()
  const errors = []
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('http://127.0.0.1:5174/', { waitUntil: 'networkidle' })
  const scene = page.locator('.evidence-studio')
  const canvas = page.locator('.studio-canvas')
  await expect(scene).toHaveAttribute('data-renderer', 'webgl')
  await page.waitForTimeout(1400)
  const pixelSignal = await screenshotSignal(page, canvas)
  expect(pixelSignal.distinct).toBeGreaterThan(pixelSignal.pixels * 0.02)
  await page.screenshot({ path: resolve(out, '1440-webgl.png') })
  results.push({ scenario: 'desktop-webgl', renderer: await scene.getAttribute('data-renderer'), pixelSignal })

  await page.waitForTimeout(3400)
  const idleFrame = await canvas.screenshot()
  await page.waitForTimeout(350)
  expect((await canvas.screenshot()).equals(idleFrame)).toBe(true)
  results.push({ scenario: 'standard-idle', stopped: true })

  await page.getByRole('button', { name: '细腻', exact: true }).click()
  await expect(scene).toHaveAttribute('data-quality', 'high')
  const highFrame = await canvas.screenshot()
  await page.waitForTimeout(350)
  expect((await canvas.screenshot()).equals(highFrame)).toBe(false)
  results.push({ scenario: 'high-motion', moving: true })

  await page.getByRole('button', { name: '电影', exact: true }).click()
  await expect(scene).toHaveAttribute('data-quality', 'cinematic')
  await page.waitForTimeout(500)
  const cinematicFrame = await canvas.screenshot()
  await page.waitForTimeout(350)
  expect((await canvas.screenshot()).equals(cinematicFrame)).toBe(false)
  await page.screenshot({ path: resolve(out, '1440-cinematic.png') })
  results.push({ scenario: 'cinematic-motion', moving: true, quality: 'cinematic' })

  await page.getByRole('button', { name: '暂停场景动态', exact: true }).click()
  await page.waitForTimeout(180)
  const pausedFrame = await canvas.screenshot()
  await page.waitForTimeout(350)
  expect((await canvas.screenshot()).equals(pausedFrame)).toBe(true)
  results.push({ scenario: 'pause-cinematic', stopped: true })

  await page.locator('.studio-canvas').evaluate(canvas => canvas.dispatchEvent(new Event('webglcontextlost', { cancelable: true })))
  await expect(scene).toHaveAttribute('data-renderer', 'static')
  await page.screenshot({ path: resolve(out, '1440-context-loss.png') })
  results.push({ scenario: 'context-loss', renderer: 'static' })

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('http://127.0.0.1:5174/', { waitUntil: 'networkidle' })
  await expect(scene).toHaveAttribute('data-renderer', 'static')
  await page.screenshot({ path: resolve(out, '1440-reduced-motion.png') })
  results.push({ scenario: 'reduced-motion', renderer: 'static' })
  await context.close()

  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await mobile.route('**/api/v1/me', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ ok: true, data: null }),
  }))
  const mobilePage = await mobile.newPage()
  mobilePage.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  mobilePage.on('pageerror', error => errors.push(error.message))
  await mobilePage.goto('http://127.0.0.1:5174/', { waitUntil: 'networkidle' })
  const mobileScene = mobilePage.locator('.evidence-studio')
  await expect(mobileScene).toHaveAttribute('data-renderer', 'static')
  const mobileGeometry = await mobilePage.evaluate(() => ({
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    brokenImages: [...document.images].filter(image => image.complete && image.naturalWidth === 0).length,
  }))
  expect(mobileGeometry).toEqual({ overflow: 0, brokenImages: 0 })
  await mobilePage.screenshot({ path: resolve(out, '390-static.png') })
  results.push({ scenario: 'mobile-static', renderer: 'static', ...mobileGeometry })
  await mobile.close()

  expect(errors).toEqual([])
} finally { await browser.close() }
await writeFile(resolve(out, 'results.json'), JSON.stringify(results, null, 2))
console.log(JSON.stringify(results))
